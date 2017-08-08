/*
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */

package alfio.pi.manager

import alfio.pi.ConnectionDescriptor
import alfio.pi.model.*
import alfio.pi.model.CheckInStatus.*
import alfio.pi.repository.*
import alfio.pi.wrapper.CannotBeginTransaction
import alfio.pi.wrapper.doInTransaction
import alfio.pi.wrapper.tryOrDefault
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


private val lastUpdatedEvent: ConcurrentHashMap<String, Long> = ConcurrentHashMap();

@Component
@Profile("server", "full")
open class CheckInDataManager(@Qualifier("masterConnectionConfiguration") val master: ConnectionDescriptor,
                              val scanLogRepository: ScanLogRepository,
                              val eventRepository: EventRepository,
                              val attendeeDataRepository: AttendeeDataRepository,
                              val userRepository: UserRepository,
                              val transactionManager: PlatformTransactionManager,
                              val gson: Gson,
                              val jdbc: NamedParameterJdbcTemplate,
                              val httpClient: OkHttpClient,
                              val printManager: PrintManager,
                              val publisher: SystemEventManager,
                              val cluster: JGroupsCluster) {


    private val logger = LoggerFactory.getLogger(CheckInDataManager::class.java)
    private val ticketDataNotFound = "ticket-not-found"


    private fun getAttendeeData(event: Event, key: String) : String? {

        if(!attendeeDataRepository.isPresent(event.key, key)) {
            syncAttendees(event.key) //ensure our copy is up to date
        }

        if(attendeeDataRepository.isPresent(event.key, key)) {
            return attendeeDataRepository.getData(event.key, key)
        } else {
            return null
        }
    }

    private fun getLocalTicketData(event: Event, uuid: String, hmac: String) : CheckInResponse {
        val key = calcHash256(hmac)
        val result = getAttendeeData(event, key)
        return tryOrDefault<CheckInResponse>().invoke({
            if(result != null && result !== ticketDataNotFound) {
                val ticketData = gson.fromJson(decrypt("$uuid/$hmac", result), TicketData::class.java)
                TicketAndCheckInResult(Ticket(uuid, ticketData.firstName, ticketData.lastName, ticketData.email, ticketData.company), CheckInResult(ticketData.checkInStatus))
            } else {
                logger.warn("no eventData found for $key.")
                EmptyTicketResult()
            }
        }, {
            logger.warn("got Exception while loading/decrypting local data", it)
            EmptyTicketResult()
        })
    }

    internal fun performCheckIn(eventName: String, uuid: String, hmac: String, username: String) : CheckInResponse = doInTransaction<CheckInResponse>()
        .invoke(transactionManager, { doPerformCheckIn(eventName, hmac, username, uuid) }, {
            if(it !is CannotBeginTransaction) {
                logger.error("error during check-in", it)
            }
            EmptyTicketResult()
        })

    private fun doPerformCheckIn(eventName: String, hmac: String, username: String, uuid: String): CheckInResponse {
        return eventRepository.loadSingle(eventName)
            .flatMap { event -> userRepository.findByUsername(username).map { user -> event to user } }
            .map { (event, user) ->
                val eventId = event.id
                scanLogRepository.loadSuccessfulScanForTicket(eventId, uuid)
                    .map(fun(existing: ScanLog) : CheckInResponse = DuplicateScanResult(originalScanLog = existing))
                    .orElseGet {
                        val localDataResult = getLocalTicketData(event, uuid, hmac)
                        if (localDataResult.isSuccessful()) {
                            localDataResult as TicketAndCheckInResult
                            val remoteResult = remoteCheckIn(event.key, uuid, hmac, username)
                            val localResult = if(arrayOf(ALREADY_CHECK_IN, MUST_PAY, INVALID_TICKET_STATE).contains(remoteResult.result.status)) {
                                remoteResult.result.status
                            } else {
                                CheckInStatus.SUCCESS
                            }
                            val ticket = localDataResult.ticket!!
                            val labelPrinted = remoteResult.isSuccessfulOrRetry() && printManager.printLabel(user, ticket)
                            val keyContainer = scanLogRepository.insert(ZonedDateTime.now(), eventId, uuid, user.id, localResult, remoteResult.result.status, labelPrinted, gson.toJson(includeHmacIfNeeded(ticket, remoteResult, hmac)))
                            publisher.publishEvent(SystemEvent(SystemEventType.NEW_SCAN, NewScan(scanLogRepository.findById(keyContainer.key), event)))
                            logger.trace("returning status $localResult for ticket $uuid (${ticket.fullName})")
                            TicketAndCheckInResult(ticket, CheckInResult(localResult))
                        } else {
                            localDataResult
                        }
                    }
            }.orElseGet{ EmptyTicketResult() }
    }

    private fun includeHmacIfNeeded(ticket: Ticket, remoteResult: CheckInResponse, hmac: String) =
        if(remoteResult.result.status == RETRY) {
            Ticket(ticket.uuid, ticket.firstName, ticket.lastName, ticket.email, ticket.company, hmac = hmac)
        } else {
            ticket
        }


    open fun loadIds(eventName: String, since: Long?) : Pair<List<Integer>, Long> {
        val changedSinceParam = if (since == null) "" else "?changedSince=$since"

        val idsUrl = "${master.url}/admin/api/check-in/$eventName/offline-identifiers$changedSinceParam"

        val request = Request.Builder()
            .addHeader("Authorization", Credentials.basic(master.username, master.password))
            .url(idsUrl)
            .build()
        return httpClient.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body().string()
                val serverTime = resp.header("Alfio-TIME").toLong()
                lastUpdatedEvent.put(eventName, serverTime)
                Pair(parseIdsResponse(body).invoke(gson), serverTime)
            } else {
                Pair(listOf(), 0)
            }
        }
    }

    // imported from https://stackoverflow.com/a/40723106
    fun <T> Iterable<T>.partition(size: Int): List<List<T>> = with(iterator()) {
        check(size > 0)
        val partitions = mutableListOf<List<T>>()
        while (hasNext()) {
            val partition = mutableListOf<T>()
            do partition.add(next()) while (hasNext() && partition.size < size)
            partitions += partition
        }
        return partitions
    }

    open fun loadCachedAttendees(eventName: String, since: Long?) : Pair<Map<String, String>, Long> {
        /*if(!cluster.isLeader()) {
            logger.info("leader address is ${cluster.getLeaderAddress().toString()}")
            val method = javaClass.getMethod("loadCachedAttendees", String::class.java, Long::class.javaObjectType)
            return cluster.remoteLoadCachedAttendees(this, method, eventName, null)
        }*/

        val idsAndTime = loadIds(eventName, since)
        val ids = idsAndTime.first

        logger.info("found ${ids.size} for event ${eventName}")

        val res = HashMap<String, String>()
        if(!ids.isEmpty()) {
            ids.partition(200).forEach {
                val partitionedIds = it
                logger.info("loading ${it.size}")
                res.putAll(fetchPartitionedAttendees(eventName, partitionedIds))
                logger.info("finished loading ${it.size}")
            }
        }

        logger.info("finished loading")

        return Pair(res, idsAndTime.second)
    }

    private fun fetchPartitionedAttendees(eventName: String, partitionedIds: List<Integer>) : Map<String, String> {
        val url = "${master.url}/admin/api/check-in/$eventName/offline"
        return tryOrDefault<Map<String, String>>().invoke({
            val request = Request.Builder()
                .addHeader("Authorization", Credentials.basic(master.username, master.password))
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(partitionedIds)))
                .build()
            httpClient.newCall(request)
                .execute()
                .use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body().string()
                        parseTicketDataResponse(body).invoke(gson).withDefault { ticketDataNotFound }
                    } else {
                        logger.warn("Cannot call remote URL $url. Response Code is ${resp.code()}")
                        mapOf()
                    }
                }
        }, {
            if (logger.isTraceEnabled) {
                logger.trace("Got exception while trying to load the attendees", it)
            } else {
                logger.error("Cannot load remote attendees: $it")
            }
            mapOf()
        })
    }

    open fun remoteCheckIn(eventKey: String, uuid: String, hmac: String, username: String) : CheckInResponse = tryOrDefault<CheckInResponse>().invoke({

        if(!cluster.isLeader()) {
            val method = javaClass.getMethod("remoteCheckIn", String::class.java, String::class.java, String::class.java, String::class.java)
            cluster.remoteCheckInToMaster(this, method, eventKey, uuid, hmac, username)
        } else {
            val requestBody = RequestBody.create(MediaType.parse("application/json"), gson.toJson(hashMapOf("code" to "$uuid/$hmac")))
            val request = Request.Builder()
                .addHeader("Authorization", Credentials.basic(master.username, master.password))
                .post(requestBody)
                .url("${master.url}/admin/api/check-in/event/$eventKey/ticket/$uuid?offlineUser=$username")
                .build()
            httpClientWithCustomTimeout(100L, TimeUnit.MILLISECONDS)
                .invoke(httpClient)
                .newCall(request)
                .execute()
                .use { resp ->
                    if (resp.isSuccessful) {
                        gson.fromJson(resp.body().string(), TicketAndCheckInResult::class.java)
                    } else {
                        EmptyTicketResult(CheckInResult(CheckInStatus.RETRY))
                    }
                }
        }
    }, {
        logger.warn("got Exception while performing remote check-in")
        EmptyTicketResult(CheckInResult(CheckInStatus.RETRY))
    })


    @Scheduled(fixedDelay = 15000L)
    open fun processPendingEntries() {
        val failures = scanLogRepository.findRemoteFailures()
        logger.trace("found ${failures.size} pending scan to upload")
        failures
            .groupBy { it.eventId }
            .mapKeys { eventRepository.loadSingle(it.key) }
            .filter { it.key.isPresent }
            .forEach { entry -> uploadEntriesForEvent(entry) }
    }

    private fun uploadEntriesForEvent(entry: Map.Entry<Optional<Event>, List<ScanLog>>) {
        logger.info("******** uploading check-in **********")
        tryOrDefault<Unit>().invoke({
            val event = entry.key.get()
            entry.value.filter { it.ticket != null }.forEach {
                val ticket = it.ticket!!
                userRepository.findById(it.userId).map {
                    val response = remoteCheckIn(event.key, ticket.uuid, ticket.hmac!!, it.username)
                    scanLogRepository.updateRemoteResult(response.result.status, it.id)
                }
            }
        }, { logger.error("unable to upload pending check-in", it)})
        logger.info("******** upload completed **********")
    }

    fun syncAttendees(eventName: String) {
        val lastUpdateForEvent = lastUpdatedEvent.get(eventName)
        val attendeesForEventAndTime = loadCachedAttendees(eventName, lastUpdateForEvent)
        val attendeesForEvent = attendeesForEventAndTime.first
        val batchedUpdate = attendeesForEvent.map {
            MapSqlParameterSource()
                .addValue("event", eventName)
                .addValue("identifier", it.key)
                .addValue("data", it.value)
                .addValue("last_update", attendeesForEventAndTime.second)
        }.toTypedArray()
        jdbc.batchUpdate(attendeeDataRepository.mergeTemplate(), batchedUpdate)
    }
}

fun checkIn(eventName: String, uuid: String, hmac: String, username: String) : (CheckInDataManager) -> CheckInResponse = { manager ->
    manager.performCheckIn(eventName, uuid, hmac, username)
}

fun parseTicketDataResponse(body: String): (Gson) -> Map<String, String> = {gson -> gson.fromJson(body, object : TypeToken<Map<String, String>>() {}.type) }
fun parseIdsResponse(body: String): (Gson) -> List<Integer> = {gson ->  gson.fromJson(body, object: TypeToken<List<Integer>>() {}.type)}

private fun decrypt(key: String, payload: String): String {
    try {
        val cipherAndSecret = getCypher(key)
        val cipher = cipherAndSecret.first
        val split = payload.split(Pattern.quote("|").toRegex()).dropLastWhile(String::isEmpty).toTypedArray()
        val iv = Base64.getUrlDecoder().decode(split[0])
        val body = Base64.getUrlDecoder().decode(split[1])
        cipher.init(Cipher.DECRYPT_MODE, cipherAndSecret.second, IvParameterSpec(iv))
        val decrypted = cipher.doFinal(body)
        return String(decrypted, StandardCharsets.UTF_8)
    } catch (e: GeneralSecurityException) {
        throw IllegalStateException(e)
    }
}

private fun getCypher(key: String): Pair<Cipher, SecretKeySpec> {
    try {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val iterations = 1000
        val keyLength = 256
        val spec = PBEKeySpec(key.toCharArray(), key.toByteArray(StandardCharsets.UTF_8), iterations, keyLength)
        val secretKey = factory.generateSecret(spec)
        val secret = SecretKeySpec(secretKey.encoded, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        return cipher to secret
    } catch (e: GeneralSecurityException) {
        throw IllegalStateException(e)
    }

}

internal fun calcHash256(hmac: String) : String {
    return MessageDigest.getInstance("SHA-256")
        .digest(hmac.toByteArray()).joinToString(separator = "", transform = {
            val result = Integer.toHexString(0xff and it.toInt())
            if(result.length == 1) {
                "0" + result
            } else {
                result
            }
        })
}

@Component
@Profile("server", "full")
open class CheckInDataSynchronizer(val checkInDataManager: CheckInDataManager, val remoteResourceManager: RemoteResourceManager) {

    private val logger = LoggerFactory.getLogger(CheckInDataSynchronizer::class.java)

    @EventListener
    open fun handleContextRefresh(event: ContextRefreshedEvent) {
        performSync()
    }

    @Scheduled(fixedDelay = 5000L, initialDelay = 5000L)
    open fun performSync() {
        logger.trace("downloading attendees data")
        val remoteEvents = getRemoteEventList().invoke(remoteResourceManager)
        onDemandSync(remoteEvents)
    }

    open fun onDemandSync(events: List<RemoteEvent>) {
        logger.debug("on-demand synchronization")
        events.map {
            val eventName = it.key;
            if(eventName != null) {
                checkInDataManager.syncAttendees(eventName)
            }
        }
    }
}