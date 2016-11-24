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
import org.springframework.context.ApplicationEvent
import java.io.Serializable
import java.math.BigDecimal

enum class ScanResult {
    WAITING, SYNC_IN_PROCESS, OK
}

interface ScanLog

data class PersistedScanLog(@Column("id") val id: Int,
                   @Column("event_id") val eventId: Int,
                   @Column("ticket_uuid") val ticketUuid: String,
                   @Column("user") val user: String,
                   @Column("result") val result: ScanResult)

data class NotYetPersistedScanLog(val eventId: Int, val ticketUuid: String, val user: String, val result: ScanResult) : ScanLog

class CheckInEvent(source: Any, val scanLog: ScanLog) : ApplicationEvent(source)

class Ticket : Serializable {
    var id: Long? = null
    var uuid: String? = null
    var status: String? = null
    var ticketsReservationId: String? = null
    var fullName: String? = null
    val email: String? = null
}

interface CheckInResponse

data class TicketAndCheckInResult(val ticket: Ticket, val result: CheckInResult) : CheckInResponse

data class EmptyTicketResult(val result: CheckInResult) : CheckInResponse

data class CheckInResult(val status: CheckInStatus = CheckInStatus.TICKET_NOT_FOUND, val message: String? = null, val dueAmount: BigDecimal = BigDecimal.ZERO, val currency: String = "");

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
