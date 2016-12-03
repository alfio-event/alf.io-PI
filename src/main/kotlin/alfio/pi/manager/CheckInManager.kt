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
import alfio.pi.repository.ScanLogRepository
import alfio.pi.wrapper.CannotBeginTransaction
import alfio.pi.wrapper.doInTransaction
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import okhttp3.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val logger = LoggerFactory.getLogger("CheckInManager")!!

private val eventAttendeesCache: ConcurrentMap<Int, Map<String, String>> = ConcurrentHashMap()

@Component
open class CheckInDataManager(@Qualifier("masterConnectionConfiguration") val master: ConnectionDescriptor, val scanLogRepository: ScanLogRepository, val transactionManager: PlatformTransactionManager) {
    private val ticketDataNotFound = "ticket-not-found"
    private val gson = GsonBuilder().create()
    private val client = OkHttpClient()

    private fun getLocalAttendeeData(eventId: Int, uuid: String, hmac: String) : Ticket {
        val eventData = eventAttendeesCache.computeIfAbsent(eventId, {loadCachedAttendees(it)})
        val key = calcHash256(hmac)
        val result = eventData[key]
        if(result != null && result !== ticketDataNotFound) {
            val attendeeData = gson.fromJson(decrypt("$uuid/$hmac", result), AttendeeData::class.java)
            return Ticket(uuid, attendeeData.firstName, attendeeData.lastName, attendeeData.emailAddress)
        } else {
            logger.warn("no eventData found for $key. Cache size: ${eventData.size}")
            return TicketNotFound(uuid)
        }
    }

    internal fun performCheckIn(eventId: Int, uuid: String, hmac: String, username: String) : CheckInResponse = doInTransaction<CheckInResponse>()
        .invoke(transactionManager, { doPerformCheckIn(eventId, hmac, username, uuid) }, {
            if(it !is CannotBeginTransaction) {
                logger.error("error during check-in", it)
            }
            EmptyTicketResult()
        })

    private fun doPerformCheckIn(eventId: Int, hmac: String, username: String, uuid: String): CheckInResponse {
        return scanLogRepository.loadSuccessfulScanForTicket(eventId, uuid)
            .map(fun(existing: ScanLog) : CheckInResponse {
                return DuplicateScanResult(originalScanLog = existing)
            })
            .orElseGet {
                val ticket = getLocalAttendeeData(eventId, uuid, hmac)
                if (ticket is TicketNotFound) {
                    EmptyTicketResult()
                } else {
                    val remoteResult = remoteCheckIn(eventId, uuid, hmac)
                    scanLogRepository.insert(eventId, 0, uuid, username, CheckInStatus.SUCCESS, remoteResult.result.status, false)
                    TicketAndCheckInResult(ticket, CheckInResult(CheckInStatus.SUCCESS))
                }
            }
    }

    internal fun loadCachedAttendees(eventId: Int) : Map<String, String> {
        val url = "${master.url}/admin/api/check-in/$eventId/offline"
        val request = Request.Builder()
            .addHeader("Authorization", Credentials.basic(master.username, master.password))
            .url(url)
            .build()
        val resp = client.newCall(request).execute()//TODO IOException
        return if(resp.isSuccessful) {
            resp.body().use(fun(it: ResponseBody) : Map<String, String> {
                return gson.fromJson(it.string(), object : TypeToken<Map<String, String>>() {}.type)
            }).withDefault { ticketDataNotFound }
        } else {
            logger.warn("Cannot call remote URL $url. Response Code is ${resp.code()}")
            mapOf<String, String>()
        }
    }

    internal fun remoteCheckIn(eventId: Int, uuid: String, hmac: String) : CheckInResponse {
        val requestBody = RequestBody.create(MediaType.parse("application/json"), gson.toJson(hashMapOf("code" to hmac)))
        val request = Request.Builder()
            .addHeader("Authorization", Credentials.basic(master.username, master.password))
            .post(requestBody)
            .url("${master.url}/admin/api/check-in/$eventId/ticket/$uuid")
            .build()
        val resp = client.newCall(request).execute()
        return if(resp.isSuccessful) {
            return resp.body().use { gson.fromJson(it.string(), TicketAndCheckInResult::class.java) }
        } else {
            EmptyTicketResult(CheckInResult(CheckInStatus.RETRY))
        }
    }
}

fun checkIn(eventId: Int, uuid: String, hmac: String, username: String) : (CheckInDataManager) -> CheckInResponse = { manager -> manager.performCheckIn(eventId, uuid, hmac, username)}

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
open class CheckInDataSynchronizer(val checkInDataManager: CheckInDataManager) {
    @Scheduled(fixedDelay = 5000L)
    open fun performSync() {
        logger.debug("downloading attendees data")
        eventAttendeesCache.entries
            .map { it to checkInDataManager.loadCachedAttendees(it.key) }
            .filter { !it.second.isEmpty() }
            .forEach {
                val result = eventAttendeesCache.replace(it.first.key, it.first.value, it.second)
                logger.debug("tried to replace value for ${it.first.key}, result: $result")
            }
    }
}