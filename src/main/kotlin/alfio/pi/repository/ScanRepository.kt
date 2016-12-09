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

package alfio.pi.repository

import alfio.pi.model.*
import ch.digitalfondue.npjt.AffectedRowCountAndKey
import ch.digitalfondue.npjt.Bind
import ch.digitalfondue.npjt.Query
import ch.digitalfondue.npjt.QueryRepository
import java.util.*
import javax.print.DocFlavor
import javax.print.PrintService
import javax.print.PrintServiceLookup
import javax.print.attribute.standard.PrinterIsAcceptingJobs

@QueryRepository
interface ScanLogRepository {
    @Query("select * from scan_log")
    fun loadAll():List<ScanLog>

    @Query("select * from scan_log where event_id = :eventId")
    fun loadAllForEvent(@Bind("eventId") eventId: Int):List<ScanLog>

    @Query("insert into scan_log (event_id, queue_id_fk, ticket_uuid, user, local_result, remote_result, badge_printed) values(:eventId, :queueId, :ticketUuid, :user, :localResult, :remoteResult, :badgePrinted)")
    fun insert(@Bind("eventId") eventId: Int, @Bind("queueId") queueId: Int, @Bind("ticketUuid") ticketUuid: String, @Bind("user") user: String, @Bind("localResult") localResult: CheckInStatus, @Bind("remoteResult") remoteResult: CheckInStatus, @Bind("badgePrinted") badgePrinted: Boolean): Int

    @Query("select * from scan_log where event_id = :eventId and ticket_uuid = :ticketUuid")
    fun loadSuccessfulScanForTicket(@Bind("eventId") eventId: Int, @Bind("ticketUuid") ticketUuid: String) : Optional<ScanLog>
}

@QueryRepository
interface CheckInQueueRepository {
    @Query("select * from check_in_queue")
    fun loadAll(): List<CheckInQueue>

    @Query("select * from check_in_queue where id = :id")
    fun findById(@Bind("id") id: Int) : CheckInQueue

    @Query("insert into check_in_queue(event_id, name, description, printer_id_fk) values(:eventId, :name, :description, :printerId)")
    fun insert(@Bind("eventId") eventId: Int, @Bind("name") name: String, @Bind("description") description: String?, @Bind("printerId") printerId: Int?): AffectedRowCountAndKey<Int>

}

@QueryRepository
interface PrinterRepository {
    @Query("select * from printer")
    fun loadAll(): List<Printer>

    @Query("insert into printer(name, description) values(:name, :description)")
    fun insert(@Bind("name") name: String, @Bind("description") description: String?): AffectedRowCountAndKey<Int>

    @Query("select printer.id as id, printer.name as name, printer.description as description from printer, check_in_queue where check_in_queue.id = :queueId and check_in_queue.printer_id_fk is not null and check_in_queue.printer_id_fk = printer.id")
    fun findByQueueId(@Bind("queueId") queueId: Int): Optional<Printer>
}

@QueryRepository
interface UserQueueRepository {

    @Query("insert into user_queue(user_id_fk, event_id_fk, queue_id_fk) values(:userId, :eventId, :queueId)")
    fun insert(@Bind("userId") userId: Int, @Bind("eventId") eventId: Int, @Bind("queueId") queueId: Int): Int

    @Query("select * from user_queue where user_id_fk = :userId and event_id_fk = :eventId")
    fun getUserQueue(@Bind("userId") userId: Int, @Bind("eventId") eventId: Int): UserQueue

}


fun getSystemPrinters(): Array<out PrintService> = PrintServiceLookup.lookupPrintServices(DocFlavor.INPUT_STREAM.POSTSCRIPT, null)!!
fun getActivePrinters() = getSystemPrinters().filter {
    it.attributes.get(PrinterIsAcceptingJobs::class.java) == PrinterIsAcceptingJobs.ACCEPTING_JOBS
}
fun findPrinterByName(name: String) = getActivePrinters().filter {
    name == it.name
}.firstOrNull()