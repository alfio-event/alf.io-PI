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

import alfio.pi.RemoteApiAuthenticationDescriptor
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
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallbackWithoutResult
import org.springframework.transaction.support.TransactionTemplate
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec


@Component
@Profile("server", "full")
open class CheckInDataManager(@Qualifier("masterConnectionConfiguration") private val master: RemoteApiAuthenticationDescriptor,
                              private val eventRepository: EventRepository,
                              private val kvStore: KVStore,
                              private val userRepository: UserRepository,
                              private val transactionManager: PlatformTransactionManager,
                              private val gson: Gson,
                              private val httpClient: OkHttpClient,
                              private val printManager: PrintManager,
                              private val publisher: SystemEventHandler) {


    private val logger = LoggerFactory.getLogger(CheckInDataManager::class.java)
    private val ticketDataNotFound = "ticket-not-found"


    private fun getAttendeeData(event: Event, key: String) : String? {

        if(!kvStore.isAttendeeDataPresent(event.key, key)) {
            logger.debug("calling syncAttendees before check-in for ${event.key}")
            syncAttendees(event.key) //ensure our copy is up to date
        }

        return if(kvStore.isAttendeeDataPresent(event.key, key)) {
            kvStore.getAttendeeData(event.key, key)
        } else {
            null
        }
    }

    private fun getLocalTicketData(event: Event, uuid: String, hmac: String) : CheckInResponse {
        val key = calcHash256(hmac)
        val result = getAttendeeData(event, key)
        return tryOrDefault<CheckInResponse>().invoke({
            if(result != null && result != ticketDataNotFound) {
                val ticketData = gson.fromJson(decrypt("$uuid/$hmac", result), TicketData::class.java)
                TicketAndCheckInResult(Ticket(uuid,
                    ticketData.firstName, ticketData.lastName,
                    ticketData.email, ticketData.additionalInfo,
                    category = ticketData.category,
                    validCheckInFrom = ticketData.validCheckInFrom,
                    validCheckInTo = ticketData.validCheckInTo), CheckInResult(ticketData.checkInStatus))
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
                val eventKey = event.key
                kvStore.loadSuccessfulScanForTicket(eventKey, uuid)
                    .map(fun(existing: ScanLog) : CheckInResponse = DuplicateScanResult(originalScanLog = existing))
                    .orElseGet {
                        val localDataResult = getLocalTicketData(event, uuid, hmac)
                        if (localDataResult.isSuccessful()) {
                            localDataResult as TicketAndCheckInResult
                            val who = Optional.ofNullable(printManager.getAvailablePrinters())
                                .filter { it.size == 1 }
                                .map { it.first().name }
                                .orElse(username)
                            val remoteResult = remoteCheckIn(event.key, uuid, hmac, who)
                            val localResult = if(arrayOf(ALREADY_CHECK_IN, MUST_PAY, INVALID_TICKET_STATE, INVALID_TICKET_CATEGORY_CHECK_IN_DATE).contains(remoteResult.result.status)) {
                                remoteResult.result.status
                            } else {
                                checkValidity(localDataResult)
                            }
                            val ticket = localDataResult.ticket!!
                            val configuration = kvStore.loadLabelConfiguration(eventKey).orElse(null)
                            val printingEnabled = configuration?.enabled ?: false
                            if(!printingEnabled) {
                                logger.info("label printing disabled for event {}", eventName)
                            }
                            val labelPrinted = remoteResult.isSuccessfulOrRetry() && localResult != INVALID_TICKET_CATEGORY_CHECK_IN_DATE && printingEnabled && printManager.printLabel(user, ticket, LabelConfigurationAndContent(configuration, null))
                            val jsonPayload = gson.toJson(includeHmacIfNeeded(ticket, remoteResult, hmac))
                            kvStore.insertScanLog(eventKey, uuid, user.id, localResult, remoteResult.result.status, labelPrinted, jsonPayload)
                            logger.trace("returning status $localResult for ticket $uuid (${ticket.fullName})")
                            TicketAndCheckInResult(ticket, CheckInResult(localResult))
                        } else {
                            localDataResult
                        }
                    }
            }.orElseGet{ EmptyTicketResult() }
    }

    internal fun forcePrintLabel(eventName: String, uuid: String, hmac: String, username: String) : Boolean {
        return eventRepository.loadSingle(eventName).flatMap { event -> userRepository.findByUsername(username).map { user -> event to user}}
            .map { (event, user) ->
                val localDataResult = getLocalTicketData(event, uuid, hmac)
                val eventKey = event.key
                val ticket = localDataResult.ticket!!
                val configuration = kvStore.loadLabelConfiguration(eventKey).orElse(null)
                printManager.printLabel(user, ticket, LabelConfigurationAndContent(configuration, null))
            }.orElse(false)
    }

    private fun checkValidity(localDataResult: CheckInResponse): CheckInStatus {

        if(localDataResult.ticket != null) {
            val ticket = localDataResult.ticket
            val now = ZonedDateTime.now().toEpochSecond()

            if((ticket.validCheckInFrom != null && ticket.validCheckInFrom.toLong() > now) || (ticket.validCheckInTo != null && ticket.validCheckInTo.toLong() < now)) {
                return INVALID_TICKET_CATEGORY_CHECK_IN_DATE
            }
        }

        return CheckInStatus.SUCCESS
    }

    private fun includeHmacIfNeeded(ticket: Ticket, remoteResult: CheckInResponse, hmac: String) =
        if(remoteResult.result.status == RETRY) {
            Ticket(ticket.uuid, ticket.firstName, ticket.lastName, ticket.email, ticket.additionalInfo, hmac = hmac)
        } else {
            ticket
        }


    private fun loadIds(eventName: String, since: Long?) : Pair<List<Int>, Long> {
        val changedSinceParam = if (since == null) "" else "?changedSince=$since"

        val url = "${master.url}/admin/api/check-in/$eventName/offline-identifiers$changedSinceParam"
        logger.info("Will call remote url {}", url)

        val request = Request.Builder()
            .addHeader("Authorization", master.authenticationHeaderValue())
            .url(url)
            .build()
        return httpClientWithCustomTimeout(200L, TimeUnit.MILLISECONDS).invoke(httpClient)
            .newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body()?.string()
                    val serverTime = resp.header("Alfio-TIME")?.toLong()?:1L
                    Pair(parseIdsResponse(body).invoke(gson), serverTime)
                } else {
                    Pair(listOf(), 0)
                }
            }
    }

    internal fun loadLabelConfiguration(eventName: String): LabelConfiguration? {
        val url = "${master.url}/admin/api/check-in/$eventName/label-layout"
        val request = Request.Builder()
            .addHeader("Authorization", master.authenticationHeaderValue())
            .url(url)
            .build()
        logger.info("Will call remote url {}", url)
        return httpClient.newCall(request).execute().use { resp ->
            when {
                resp.isSuccessful -> LabelConfiguration(eventName, resp.body()?.string(), true)
                resp.code() == 412 -> LabelConfiguration(eventName, null, false)
                else -> null
            }
        }
    }

    private fun syncAttendeesForEvent(eventName: String, since: Long?) {
        TransactionTemplate(transactionManager).execute(object: TransactionCallbackWithoutResult() {
            override fun doInTransactionWithoutResult(status: TransactionStatus?) {
                val idsAndTime = loadIds(eventName, since)
                val ids = idsAndTime.first

                logger.debug("found ${ids.size} for event $eventName")

                if(!ids.isEmpty()) {
                    ids.partitionWithSize(200).forEach { partitionedIds ->
                        logger.debug("loading ${partitionedIds.size}")
                        val attendees = fetchPartitionedAttendees(eventName, partitionedIds).map { Attendee(eventName, it.key, it.value, idsAndTime.second) }
                        saveAttendees(eventName, attendees)
                        logger.debug("finished loading ${partitionedIds.size}")
                    }
                }
                logger.info("finished loading attendees for event $eventName")
            }
        })
    }

    private fun fetchPartitionedAttendees(eventName: String, partitionedIds: List<Int>) : Map<String, String> {
        val url = "${master.url}/admin/api/check-in/$eventName/offline"
        logger.info("Will call remote url {}", url)
        return tryOrDefault<Map<String, String>>().invoke({
            val begin = System.currentTimeMillis()
            val request = Request.Builder()
                .addHeader("Authorization", master.authenticationHeaderValue())
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(partitionedIds)))
                .build()
            val res = httpClientWithCustomTimeout(1L, TimeUnit.SECONDS).invoke(httpClient).newCall(request)
                .execute()
                .use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body()?.string()
                        parseTicketDataResponse(body).invoke(gson).withDefault { ticketDataNotFound }
                    } else {
                        logger.warn("Cannot call remote URL $url. Response Code is ${resp.code()}")
                        mapOf()
                    }
                }
            val end = System.currentTimeMillis()
            logger.info("Fetched from remote {} attendees in {}ms", res.size, end - begin)
            res
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

        /*if(!cluster.isLeader()) {
            val result = cluster.remoteCheckInToMaster(eventKey, uuid, hmac, username)
            result ?: EmptyTicketResult(CheckInResult(CheckInStatus.RETRY))
        } else {*/
            val requestBody = RequestBody.create(MediaType.parse("application/json"), gson.toJson(hashMapOf("code" to "$uuid/$hmac")))
            val url = "${master.url}/admin/api/check-in/event/$eventKey/ticket/$uuid?offlineUser=$username"
            val request = Request.Builder()
                .addHeader("Authorization", master.authenticationHeaderValue())
                .post(requestBody)
                .url(url)
                .build()
            logger.info("Will call remote url {}", url)
            httpClientWithCustomTimeout(100L, TimeUnit.MILLISECONDS)
                .invoke(httpClient)
                .newCall(request)
                .execute()
                .use { resp ->
                    if (resp.isSuccessful) {
                        gson.fromJson(resp.body()!!.string(), TicketAndCheckInResult::class.java)
                    } else {
                        EmptyTicketResult(CheckInResult(CheckInStatus.RETRY))
                    }
                }
        /*}*/
    }, {
        logger.warn("got Exception while performing remote check-in")
        EmptyTicketResult(CheckInResult(CheckInStatus.RETRY))
    })


    @Scheduled(fixedDelay = 15000L)
    open fun processPendingEntries() {
        if(kvStore.isLeader()) {
            val failures = kvStore.findRemoteFailures()
            logger.trace("found ${failures.size} pending scan to upload")
            failures
                .groupBy { it.eventKey }
                .mapKeys { eventRepository.loadSingle(it.key) }
                .filter { it.key.isPresent }
                .forEach { entry -> uploadEntriesForEvent(entry) }
        }
    }

    private fun uploadEntriesForEvent(entry: Map.Entry<Optional<Event>, List<ScanLog>>) {
        logger.info("******** uploading check-in **********")
        tryOrDefault<Unit>().invoke({
            val event = entry.key.get()
            entry.value.filter { it.ticket != null }.forEach {
                val ticket = it.ticket!!
                val scanLogId = it.id
                userRepository.findById(it.userId).map {
                    val response = remoteCheckIn(event.key, ticket.uuid, ticket.hmac!!, it.username)
                    kvStore.updateRemoteResult(response.result.status, scanLogId)
                }
            }
        }, { logger.error("unable to upload pending check-in", it)})
        logger.info("******** upload completed **********")
    }

    fun syncAttendees(eventName: String) {
        val lastUpdateForEvent = findLastModifiedTimeForAttendeeInEvent(eventName)
        syncAttendeesForEvent(eventName, lastUpdateForEvent)
        publisher.notifyAllSessions(SystemEvent(SystemEventType.EVENT_UPDATED, EventUpdated(eventName, ZonedDateTime.now())))
    }

    open fun saveAttendees(eventName: String, attendeesForEvent: List<Attendee>) {
        val begin = System.currentTimeMillis()
        logger.info("Saving {} attendees", attendeesForEvent.size)
        var maxLastUpdate = 0L
        attendeesForEvent.forEach({ attendee ->
            kvStore.putAttendeeData(eventName, attendee.identifier, attendee.data)
            maxLastUpdate = maxOf(maxLastUpdate, attendee.lastUpdate?:0)
        })
        kvStore.putLastUpdated(eventName, maxLastUpdate)
        val end = System.currentTimeMillis()
        logger.info("Done saving {} attendees in {}ms", attendeesForEvent.size, end - begin)
    }

    private fun findLastModifiedTimeForAttendeeInEvent(event: String): Long {
        return kvStore.getLatestUpdate(event)
    }
}

fun parseTicketDataResponse(body: String?): (Gson) -> Map<String, String> = {gson -> gson.fromJson(body, object : TypeToken<Map<String, String>>() {}.type) }
fun parseIdsResponse(body: String?): (Gson) -> List<Int> = {gson ->  gson.fromJson(body, object: TypeToken<List<Int>>() {}.type)}

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
open class CheckInDataSynchronizer(private val checkInDataManager: CheckInDataManager,
                                   private val eventRepository: EventRepository,
                                   private val kvStore: KVStore) {

    private val logger = LoggerFactory.getLogger(CheckInDataSynchronizer::class.java)

    @Scheduled(fixedDelay = 5000L, initialDelay = 5000L)
    open fun performSync() {
        logger.trace("downloading attendees data")
        val remoteEvents = eventRepository.loadAll().map {
            val remoteEvent = RemoteEvent()
            remoteEvent.key = it.key
            remoteEvent
        }
        onDemandSync(remoteEvents)
    }

    open fun onDemandSync(events: List<RemoteEvent>) {
        if(kvStore.isLeader()) {
            logger.debug("Leader begin onDemandSync")
            events.filter { it.key != null }.map {
                checkInDataManager.syncAttendees(it.key!!)
            }
            logger.debug("Leader end onDemandSync")
        }
        //TODO: handle labelConfigurationRepository replication (move it to kvstore too?)
    }
}

// imported from https://stackoverflow.com/a/40723106
private fun <T> Iterable<T>.partitionWithSize(size: Int): List<List<T>> = with(iterator()) {
    check(size > 0)
    val partitions = mutableListOf<List<T>>()
    while (hasNext()) {
        val partition = mutableListOf<T>()
        do partition.add(next()) while (hasNext() && partition.size < size)
        partitions += partition
    }
    return partitions
}