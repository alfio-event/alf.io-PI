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
import alfio.pi.repository.EventDataRepository
import alfio.pi.repository.EventRepository
import alfio.pi.repository.ScanLogRepository
import alfio.pi.repository.UserRepository
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
import org.springframework.jdbc.core.support.AbstractLobCreatingPreparedStatementCallback
import org.springframework.jdbc.support.lob.DefaultLobHandler
import org.springframework.jdbc.support.lob.LobCreator
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.util.StreamUtils
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.sql.Date
import java.sql.PreparedStatement
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val eventAttendeesCache: ConcurrentMap<String, Map<String, String>> = ConcurrentHashMap()

@Component
@Profile("server", "full")
open class CheckInDataManager(@Qualifier("masterConnectionConfiguration") val master: ConnectionDescriptor,
                              val scanLogRepository: ScanLogRepository,
                              val eventRepository: EventRepository,
                              val userRepository: UserRepository,
                              val transactionManager: PlatformTransactionManager,
                              val gson: Gson,
                              val httpClient: OkHttpClient,
                              val printManager: PrintManager,
                              val publisher: SystemEventManager,
                              val cluster: JGroupsCluster) {


    private val logger = LoggerFactory.getLogger(CheckInDataManager::class.java)
    private val ticketDataNotFound = "ticket-not-found"

    private fun getLocalTicketData(event: Event, uuid: String, hmac: String) : CheckInResponse {
        val eventData = eventAttendeesCache.computeIfAbsent(event.key, {
            val result = loadCachedAttendees(it).second
            if(result.isNotEmpty()) {
                eventRepository.updateTimestamp(it, ZonedDateTime.now())
            }
            result
        })
        val key = calcHash256(hmac)
        val result = eventData[key]
        return tryOrDefault<CheckInResponse>().invoke({
            if(result != null && result !== ticketDataNotFound) {
                val ticketData = gson.fromJson(decrypt("$uuid/$hmac", result), TicketData::class.java)
                TicketAndCheckInResult(Ticket(uuid, ticketData.firstName, ticketData.lastName, ticketData.email, ticketData.company), CheckInResult(ticketData.checkInStatus))
            } else {
                logger.warn("no eventData found for $key. Cache size: ${eventData.size}")
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

    internal fun loadCachedAttendees(eventName: String) : Pair<String, Map<String, String>> {
        if(!cluster.isLeader()) {
            val method = javaClass.getMethod("loadCachedAttendees", String.javaClass)
            return cluster.remoteLoadCachedAttendees(this, method, eventName)
        }

        val url = "${master.url}/admin/api/check-in/$eventName/offline"
        return tryOrDefault<Pair<String, Map<String, String>>>().invoke({
            val request = Request.Builder()
                .addHeader("Authorization", Credentials.basic(master.username, master.password))
                .url(url)
                .build()
            httpClient.newCall(request)
                .execute()
                .use { resp ->
                    if(resp.isSuccessful) {
                        val body = resp.body().string()
                        body to parseTicketDataResponse(body).invoke(gson).withDefault { ticketDataNotFound }
                    } else {
                        logger.warn("Cannot call remote URL $url. Response Code is ${resp.code()}")
                        "" to mapOf()
                    }
                }
        }, {
            if(logger.isTraceEnabled) {
                logger.trace("Got exception while trying to load the attendees", it)
            } else {
                logger.error("Cannot load remote attendees: $it")
            }
            "" to mapOf()
        })
    }

    private fun remoteCheckIn(eventKey: String, uuid: String, hmac: String, username: String) : CheckInResponse = tryOrDefault<CheckInResponse>().invoke({

        if(!cluster.isLeader()) {
            val method = javaClass.getMethod("remoteCheckIn", String.javaClass, String.javaClass, String.javaClass, String.javaClass)
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
}

fun checkIn(eventName: String, uuid: String, hmac: String, username: String) : (CheckInDataManager) -> CheckInResponse = { manager ->
    manager.performCheckIn(eventName, uuid, hmac, username)
}

fun parseTicketDataResponse(body: String): (Gson) -> Map<String, String> = {gson -> gson.fromJson(body, object : TypeToken<Map<String, String>>() {}.type) }

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
open class CheckInDataSynchronizer(val checkInDataManager: CheckInDataManager,
                                   val eventRepository: EventRepository,
                                   val publisher: SystemEventManager,
                                   val jdbc: NamedParameterJdbcTemplate,
                                   val eventDataRepository: EventDataRepository,
                                   val gson: Gson) {

    private val logger = LoggerFactory.getLogger(CheckInDataSynchronizer::class.java)

    @EventListener
    open fun handleContextRefresh(event: ContextRefreshedEvent) {
        eventDataRepository.loadAllKeys().forEach { key ->
            logger.trace("preload event data for $key")
            jdbc.query(eventDataRepository.loadEventDataTemplate(), MapSqlParameterSource("key", key), { rs ->
                tryOrDefault<Unit>().invoke({
                    ByteArrayOutputStream().use { baos ->
                        rs.getBinaryStream("data").use({ s -> StreamUtils.copy(s, baos) })
                        eventAttendeesCache.putIfAbsent(key, parseTicketDataResponse(String(baos.toByteArray())).invoke(gson))
                        logger.trace("done.")
                    }
                }, {logger.error("cannot load stored event data for $key", it)})
            })
        }
    }

    @Scheduled(fixedDelay = 5000L, initialDelay = 5000L)
    open fun performSync() {
        logger.trace("downloading attendees data")
        eventAttendeesCache.entries
            .map {
                val dataResult = checkInDataManager.loadCachedAttendees(it.key)
                Triple(it, dataResult.second, dataResult.first)
            }
            .filter { it.second.isNotEmpty() }
            .forEach {
                val eventKey = it.first.key
                val result = eventAttendeesCache.replace(eventKey, it.first.value, it.second)
                if(result) {
                    val lastUpdate = ZonedDateTime.now()
                    eventRepository.updateTimestamp(eventKey, lastUpdate)
                    insertOrUpdateEventData(eventKey, it.third, lastUpdate)
                    publisher.publishEvent(SystemEvent(SystemEventType.EVENT_UPDATED, EventUpdated(eventKey, lastUpdate)))
                }
                logger.trace("tried to replace value for $eventKey, result: $result")
            }
    }

    private fun insertOrUpdateEventData(key: String, data: String, lastUpdate: ZonedDateTime) {
        val lobHandler = DefaultLobHandler()
        val query = if(eventDataRepository.isPresent(key) == 1) {
            eventDataRepository.updateTemplate()
        } else {
            eventDataRepository.insertTemplate()
        }
        jdbc.jdbcOperations.execute(query,
            object : AbstractLobCreatingPreparedStatementCallback(lobHandler) {
                override fun setValues(ps: PreparedStatement, lobCreator: LobCreator) {
                    lobCreator.setBlobAsBytes(ps, 1, data.toByteArray())
                    ps.setDate(2, Date(lastUpdate.withZoneSameInstant(ZoneId.of("UTC")).toInstant().toEpochMilli()))
                    ps.setString(3, key)
                }
            })
    }

    open fun onDemandSync(events: List<RemoteEvent>) {
        logger.debug("on-demand synchronization")
        val (existing, notExisting) = events.map { it.key!! to eventAttendeesCache[it.key] }
            .map { Triple(it.first, it.second, checkInDataManager.loadCachedAttendees(it.first).second) }
            .filter { it.third.isNotEmpty() }
            .partition { it.second != null }

        existing.forEach {
            val result = eventAttendeesCache.replace(it.first, it.second, it.third)
            logger.trace("tried to replace value for ${it.first}, result: $result")
        }
        notExisting.forEach {
            val result = eventAttendeesCache.putIfAbsent(it.first, it.third)
            logger.trace("tried to insert ${it.first}, result: $result")
        }
    }
}