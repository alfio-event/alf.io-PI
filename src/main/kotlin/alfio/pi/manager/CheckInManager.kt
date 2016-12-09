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

import alfio.pi.Application
import alfio.pi.ConnectionDescriptor
import alfio.pi.model.*
import alfio.pi.model.CheckInStatus.*
import alfio.pi.repository.*
import alfio.pi.wrapper.CannotBeginTransaction
import alfio.pi.wrapper.doInTransaction
import alfio.pi.wrapper.tryOrDefault
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import okhttp3.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.util.Matrix
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
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
import javax.print.DocFlavor
import javax.print.SimpleDoc

private val logger = LoggerFactory.getLogger("CheckInManager")!!

private val eventAttendeesCache: ConcurrentMap<Int, Map<String, String>> = ConcurrentHashMap()

@Component
open class CheckInDataManager(@Qualifier("masterConnectionConfiguration") val master: ConnectionDescriptor,
                              val scanLogRepository: ScanLogRepository,
                              val userQueueRepository: UserQueueRepository,
                              val userRepository: UserRepository,
                              val transactionManager: PlatformTransactionManager,
                              val printerRepository: PrinterRepository) {
    private val ticketDataNotFound = "ticket-not-found"
    private val gson = GsonBuilder().create()
    private val client = OkHttpClient()

    private fun getLocalTicketData(eventId: Int, uuid: String, hmac: String) : CheckInResponse {
        val eventData = eventAttendeesCache.computeIfAbsent(eventId, {loadCachedAttendees(it)})
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
                val localDataResult = getLocalTicketData(eventId, uuid, hmac)
                if (localDataResult.isSuccessful()) {
                    localDataResult as TicketAndCheckInResult
                    val remoteResult = remoteCheckIn(eventId, uuid, hmac)
                    val localResult = if(arrayOf(ALREADY_CHECK_IN, MUST_PAY, INVALID_TICKET_STATE).contains(remoteResult.result.status)) {
                        remoteResult.result.status
                    } else {
                        CheckInStatus.SUCCESS
                    }
                    userRepository.findByUsername(username).map(fun(user: User): CheckInResponse {
                        val userQueue = userQueueRepository.getUserQueue(user.id, eventId)
                        val labelPrinted = if(localResult.successful) {
                            printLabel(username, userQueue.queueId, localDataResult.ticket)
                        } else {
                            false
                        }
                        scanLogRepository.insert(eventId, userQueue.queueId, uuid, username, localResult, remoteResult.result.status, labelPrinted)
                        return TicketAndCheckInResult(localDataResult.ticket, CheckInResult(localResult))
                    }).orElseGet({EmptyTicketResult()})
                } else {
                    localDataResult
                }
            }
    }

    private fun printLabel(username: String, queueId: Int, ticket: Ticket): Boolean {
        return printerRepository.findByQueueId(queueId).map { printer ->
            tryOrDefault<Boolean>().invoke({
                val pdf = generatePDF(ticket.firstName, ticket.lastName, ticket.company.orEmpty(), ticket.uuid)
                val printService = findPrinterByName(printer.name)
                val printJob = printService?.createPrintJob()
                if(printJob == null) {
                    logger.warn("cannot find printer with name ${printer.name}")
                    false
                } else {
                    printJob.print(SimpleDoc(pdf, DocFlavor.BYTE_ARRAY.PDF, null), null)
                    true
                }
            }, {
                logger.error("cannot print label for ticket ${ticket.uuid}, username $username", it)
                false
            })
        }.orElse(false)
    }

    internal fun loadCachedAttendees(eventId: Int) : Map<String, String> {
        val url = "${master.url}/admin/api/check-in/$eventId/offline"
        return tryOrDefault<Map<String, String>>().invoke({
            val request = Request.Builder()
                .addHeader("Authorization", Credentials.basic(master.username, master.password))
                .url(url)
                .build()
            val resp = client.newCall(request).execute()
            if(resp.isSuccessful) {
                resp.body().use(fun(it: ResponseBody) : Map<String, String> {
                    return gson.fromJson(it.string(), object : TypeToken<Map<String, String>>() {}.type)
                }).withDefault { ticketDataNotFound }
            } else {
                logger.warn("Cannot call remote URL $url. Response Code is ${resp.code()}")
                mapOf()
            }
        }, {
            logger.warn("Got exception while trying to load the attendees", it)
            mapOf()
        })
    }

    private fun remoteCheckIn(eventId: Int, uuid: String, hmac: String) : CheckInResponse = tryOrDefault<CheckInResponse>().invoke({
        val requestBody = RequestBody.create(MediaType.parse("application/json"), gson.toJson(hashMapOf("code" to "$uuid/$hmac")))
        val request = Request.Builder()
            .addHeader("Authorization", Credentials.basic(master.username, master.password))
            .post(requestBody)
            .url("${master.url}/admin/api/check-in/$eventId/ticket/$uuid")
            .build()
        val resp = client.newCall(request).execute()
        if(resp.isSuccessful) {
            resp.body().use { gson.fromJson(it.string(), TicketAndCheckInResult::class.java) }
        } else {
            EmptyTicketResult(CheckInResult(CheckInStatus.RETRY))
        }
    }, {
        logger.warn("got Exception while performing remote check-in")
        EmptyTicketResult(CheckInResult(CheckInStatus.RETRY))
    })
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

private fun generatePDF(firstName: String, lastName: String, company: String, ticketUUID: String): ByteArray {
    val document = PDDocument()
    val font = PDType0Font.load(document, Application::class.java.getResourceAsStream("/font/DejaVuSansMono.ttf"))
    val page = PDPage(PDRectangle(convertMMToPoint(41F), convertMMToPoint(89F)))
    val pageWidth = page.mediaBox.width
    document.addPage(page)
    val qr = LosslessFactory.createFromImage(document, generateQRCode(ticketUUID))
    val contentStream = PDPageContentStream(document, page, PDPageContentStream.AppendMode.OVERWRITE, false)
    contentStream.transform(Matrix(0F, 1F, -1F, 0F, pageWidth, 0F))
    contentStream.setFont(font, 24F)
    contentStream.beginText()
    contentStream.newLineAtOffset(10F, 70F)
    contentStream.showText(firstName)
    contentStream.setFont(font, 16F)
    contentStream.newLineAtOffset(0F, -20F)
    contentStream.showText(lastName)
    contentStream.setFont(font, 10F)
    contentStream.newLineAtOffset(0F, -20F)
    contentStream.showText(company)
    contentStream.endText()
    contentStream.drawImage(qr, 175F, 30F, 70F, 70F)
    contentStream.setFont(font, 9F)
    contentStream.beginText()
    contentStream.newLineAtOffset(189F, 25F)
    contentStream.showText(ticketUUID.substringBefore('-').toUpperCase())
    contentStream.close()
    val out = ByteArrayOutputStream()
    document.save(out)
    document.close()
    return out.toByteArray()
}

private val convertMMToPoint: (Float) -> Float = {
    it * (1 / (10 * 2.54f) * 72)
}

private fun generateQRCode(value: String): BufferedImage {
    val hintMap = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
    hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 200, 200, hintMap)
    return MatrixToImageWriter.toBufferedImage(matrix)
}