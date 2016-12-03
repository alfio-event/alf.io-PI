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

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column
import org.hibernate.validator.constraints.Email
import org.springframework.context.ApplicationEvent
import java.io.Serializable
import java.math.BigDecimal

data class Event(@Column("id") val id: Int = -1, @Column("name") val name: String)
data class Printer(@Column("id") val id: Int = -1, @Column("name") val name: String, @Column("description") val description: String?)
data class CheckInQueue(@Column("id") val id: Int = -1, @Column("event_id") val eventId: Int, @Column("name") val name: String,
                        @Column("description") val description: String?, @Column("printer_id_fk") val printerId: Int)

data class ScanLog(@Column("id") val id: Int = -1,
                   @Column("event_id") val eventId: Int,
                   @Column("queue_id_fk") val queueId: Int,
                   @Column("ticket_uuid") val ticketUuid: String,
                   @Column("user") val user: String,
                   @Column("local_result") val localResult: CheckInStatus,
                   @Column("remote_result") val remoteResult: CheckInStatus,
                   @Column("badge_printed") val badgePrinted: Boolean)

class CheckInEvent(source: Any, val scanLog: ScanLog) : ApplicationEvent(source)

open class Ticket(val uuid: String, val firstName: String, val lastName: String, val email: String?) : Serializable {
    val fullName: String
        get() = "$firstName $lastName"
}

class TicketNotFound(uuid: String) : Ticket(uuid, "", "", "")

abstract class CheckInResponse(val result: CheckInResult)

class TicketAndCheckInResult(val ticket: Ticket, result: CheckInResult) : CheckInResponse(result)

class EmptyTicketResult(result: CheckInResult = CheckInResult()) : CheckInResponse(result)

class DuplicateScanResult(result: CheckInResult = CheckInResult(CheckInStatus.ALREADY_CHECK_IN), val originalScanLog: ScanLog) : CheckInResponse(result)

data class CheckInResult(val status: CheckInStatus = CheckInStatus.TICKET_NOT_FOUND, val message: String? = null, val dueAmount: BigDecimal = BigDecimal.ZERO, val currency: String = "")

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
    SUCCESS(true);
}

data class AttendeeData(val firstName: String, val lastName: String, val emailAddress: String, val company: String?)
