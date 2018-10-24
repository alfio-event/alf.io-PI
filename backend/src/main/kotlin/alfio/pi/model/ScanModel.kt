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

package alfio.pi.model

import alfio.pi.manager.ConfigurableLabelContent
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.springframework.context.ApplicationEvent
import org.springframework.stereotype.Component
import java.io.Serializable
import java.math.BigDecimal
import java.time.ZonedDateTime

enum class Role {ADMIN, OPERATOR}
data class Event(@Column("key") val key: String,
                 @Column("name") val name: String,
                 @Column("image_url") val imageUrl: String?,
                 @Column("begin_ts") val begin: ZonedDateTime,
                 @Column("end_ts") val end: ZonedDateTime,
                 @Column("location") val location: String?,
                 @Column("api_version") val apiVersion: Int,
                 @Column("active") val active: Boolean,
                 @Column("last_update") val lastUpdate: ZonedDateTime?,
                 @Column("timezone") val timezone: String?)


data class Printer(@Column("id") val id: Int, @Column("name") val name: String, @Column("description") val description: String?, @Column("active") val active: Boolean) : Comparable<Printer> {
    override fun compareTo(other: Printer): Int = name.compareTo(other.name)
}

@Component
internal open class GsonContainer(gson: Gson) {
    init {
        GSON = gson
    }
    companion object {
        var GSON: Gson? = null
    }
}

data class ScanLog(val id: String,
                   val timestamp: ZonedDateTime,
                   val eventKey: String,
                   val ticketUuid: String,
                   val userId: Int,
                   val localResult: CheckInStatus,
                   val remoteResult: CheckInStatus,
                   val badgePrinted: Boolean,
                   val ticketData: String?) {

    val ticket: Ticket? = if(!ticketData.isNullOrEmpty()) {
        GsonContainer.GSON?.fromJson(ticketData, Ticket::class.java)
    } else {
        null
    }
}

data class User(@Column("id") val id: Int, @Column("username") val username: String)
data class UserWithPassword(val id: Int, val username: String, val password: String)
data class Authority(@Column("username") val username: String, @Column("role") val role: Role)
data class UserPrinter(@Column("user_id_fk") val userId: Int, @Column("printer_id_fk") val printerId: Int)
data class UserAndPrinter(@Column("username") private val username: String,
                          @Column("user_id") private val userId: Int,
                          @Column("printer_id") private val printerId: Int,
                          @Column("printer_name") private val printerName: String,
                          @Column("printer_description") private val printerDescription: String?,
                          @Column("printer_active") private val printerActive: Boolean) {
    val user = User(userId, username)
    val printer = Printer(printerId, printerName, printerDescription, printerActive)
}

data class LabelConfiguration(@Column("event_key_fk") val eventKey: String, @Column("json") val json: String?, @Column("enabled") val enabled: Boolean) : Serializable {
    val layout: LabelLayout? = GsonContainer.GSON?.fromJson(json, LabelLayout::class.java)
}

data class LabelConfigurationAndContent(val configuration: LabelConfiguration?, val content: ConfigurableLabelContent?)

class CheckInEvent(source: Any, val scanLog: ScanLog) : ApplicationEvent(source)

open class Ticket(val uuid: String,
                  val firstName: String,
                  val lastName: String,
                  val email: String?,
                  val additionalInfo: Map<String, String>?,
                  val fullName: String = "$firstName $lastName",
                  val hmac: String? = null,
                  val categoryName: String? = null,
                  val validCheckInFrom: String? = null,
                  val validCheckInTo: String? = null,
                  val additionalServicesInfo: List<AdditionalServiceInfo> = emptyList()) : Serializable

class TicketNotFound(uuid: String) : Ticket(uuid, "", "", "", emptyMap())

abstract class CheckInResponse(val result: CheckInResult, val ticket: Ticket?) : Serializable {
    fun isSuccessful(): Boolean = result.status.successful
    fun isSuccessfulOrRetry(): Boolean = result.status.successful || result.status == CheckInStatus.RETRY
}

class TicketAndCheckInResult(ticket: Ticket, result: CheckInResult) : CheckInResponse(result, ticket), Serializable

class EmptyTicketResult(result: CheckInResult = CheckInResult(boxColorClass = "danger")) : CheckInResponse(result, null), Serializable

class CheckInForbidden(result: CheckInResult = CheckInResult(status = CheckInStatus.INVALID_TICKET_STATE, boxColorClass = "danger")) : CheckInResponse(result, null), Serializable

class DuplicateScanResult(result: CheckInResult = CheckInResult(CheckInStatus.ALREADY_CHECK_IN, boxColorClass = "danger"), val originalScanLog: ScanLog) : CheckInResponse(result, originalScanLog.ticket), Serializable

data class CheckInResult(val status: CheckInStatus = CheckInStatus.TICKET_NOT_FOUND,
                         val message: String? = null,
                         val dueAmount: BigDecimal = BigDecimal.ZERO,
                         val currency: String = "",
                         val boxColorClass: String = ""): Serializable

enum class CheckInStatus(val successful: Boolean = false) {
    RETRY(),
    EVENT_NOT_FOUND(),
    TICKET_NOT_FOUND(),
    EMPTY_TICKET_CODE(),
    INVALID_TICKET_CODE(),
    INVALID_TICKET_STATE(),
    ALREADY_CHECK_IN(),
    MUST_PAY(),
    OK_READY_TO_BE_CHECKED_IN(true),
    SUCCESS(true),
    INVALID_TICKET_CATEGORY_CHECK_IN_DATE();
}

data class TicketData(val firstName: String,
                      val lastName: String,
                      val email: String,
                      val category: String?,
                      private val status: String,
                      private val additionalInfoJson: String?,
                      val validCheckInFrom: String?,
                      val validCheckInTo: String?,
                      private val additionalServicesInfoJson: String?) {
    val checkInStatus: CheckInStatus
        get() = when(status) {
            "ACQUIRED" -> CheckInStatus.SUCCESS
            "CHECKED_IN" -> CheckInStatus.ALREADY_CHECK_IN
            "TO_BE_PAID" -> CheckInStatus.MUST_PAY
            else -> CheckInStatus.INVALID_TICKET_STATE
        }
    val additionalInfo: Map<String, String>
        get() = GsonContainer.GSON?.fromJson(additionalInfoJson, object : TypeToken<Map<String, String>>() {}.type) ?: emptyMap()

    val additionalServicesInfo: List<AdditionalServiceInfo>
        get() = GsonContainer.GSON?.fromJson(additionalServicesInfoJson, object : TypeToken<List<AdditionalServiceInfo>>() {}.type) ?: emptyList()
}

class RemoteEvent {
    var key: String? = null
    var external: Boolean = false
    var name: String? = null
    var imageUrl: String? = null
    var begin: String? = null
    var end: String? = null
    var oneDay: Boolean = false
    var location: String? = null
    var apiVersion: Int = 0
    var timeZone: String? = null
}

class AdditionalServiceInfo {
    var name: String? = null
    var count: Int = 0
    var fields: List<TicketFieldValueForAdditionalService>? = null
}

class TicketFieldValueForAdditionalService {
    var fieldName: String? = null
    var fieldValue: String? = null
    var additionalServiceId: Int = 0
}

data class PrinterWithUsers(val printer: Printer, val users: List<User>): Comparable<PrinterWithUsers> {
    override fun compareTo(other: PrinterWithUsers): Int = printer.id.compareTo(other.printer.id)

    override fun equals(other: Any?): Boolean {
        return if(other is PrinterWithUsers) {
            printer.id == other.printer.id
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return printer.id.hashCode()
    }
}

data class SystemPrinter(val name: String)

data class RemotePrinter(val name: String, val remoteHost: String)

data class LabelLayout(val qrCode: QRCode, val content: Content, val general: General) : Serializable
data class QRCode(val additionalInfo: List<String>, val infoSeparator: String) : Serializable
data class Content(val firstRow: String?, val secondRow: String?, val thirdRow: List<String>?, val additionalRows: List<String>?, val checkbox: Boolean?) : Serializable
data class General(val printPartialID: Boolean) : Serializable

