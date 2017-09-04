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
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.util.*

private val logger = LoggerFactory.getLogger("ScanRepository")

@QueryRepository
interface ScanLogRepository {
    @Query("select * from scan_log order by scan_ts desc")
    fun loadAll():List<ScanLog>

    @Query("select * from scan_log where scan_ts > :ts order by scan_ts desc")
    fun loadNew(@Bind("ts") timestamp: Date): List<ScanLog>

    @Query("select * from scan_log order by scan_ts desc limit :n")
    fun loadLastN(@Bind("n") n: Int): List<ScanLog>

    @Query("select * from scan_log where event_id_fk = :eventId")
    fun loadAllForEvent(@Bind("eventId") eventId: Int):List<ScanLog>

    @Query("insert into scan_log (scan_ts, event_id_fk, ticket_uuid, user_id_fk, local_result, remote_result, badge_printed, ticket_data) values(:scanTs, :eventId, :ticketUuid, :userId, :localResult, :remoteResult, :badgePrinted, :ticketData)")
    fun insert(@Bind("scanTs") scanTs: ZonedDateTime,
               @Bind("eventId") eventId: Int,
               @Bind("ticketUuid") ticketUuid: String,
               @Bind("userId") userId: Int,
               @Bind("localResult") localResult: CheckInStatus,
               @Bind("remoteResult") remoteResult: CheckInStatus,
               @Bind("badgePrinted") badgePrinted: Boolean,
               @Bind("ticketData") ticketData: String?): AffectedRowCountAndKey<Int>

    @Query("select * from scan_log where event_id_fk = :eventId and ticket_uuid = :ticketUuid")
    fun loadSuccessfulScanForTicket(@Bind("eventId") eventId: Int, @Bind("ticketUuid") ticketUuid: String) : Optional<ScanLog>

    @Query("select * from scan_log where id = :id")
    fun findOptionalById(@Bind("id") id: Int): Optional<ScanLog>

    @Query("select * from scan_log where id = :id")
    fun findById(@Bind("id") id: Int): ScanLog

    @Query("select * from scan_log where id = :id and event_id_fk = :eventId")
    fun findOptionalByIdAndEventId(@Bind("id") id: Int, @Bind("eventId") eventId: Int): Optional<ScanLog>

    @Query("select * from scan_log where remote_result = 'RETRY'")
    fun findRemoteFailures(): List<ScanLog>

    @Query("update scan_log set remote_result = :remoteResult where id = :id")
    fun updateRemoteResult(@Bind("remoteResult") remoteResult: CheckInStatus, @Bind("id") id: Int)

    @Query("select count(*) > 0 from scan_log where event_id_fk = :eventId and ticket_uuid = :uuid")
    fun existByEventAndTicketId(@Bind("eventId") eventId: Int, @Bind("uuid") uuid: String): Boolean
}

@QueryRepository
interface PrinterRepository {
    @Query("select * from printer")
    fun loadAll(): List<Printer>

    @Query("insert into printer(name, description, active) values(:name, :description, :active)")
    fun insert(@Bind("name") name: String, @Bind("description") description: String?, @Bind("active") active: Boolean): AffectedRowCountAndKey<Int>

    @Query("select printer.id as id, printer.name as name, printer.description as description from printer, user_printer where user_printer.user_id_fk = :userId and user_printer.printer_id_fk = printer.id")
    fun findByUserId(@Bind("userId") userId: Int): Optional<Printer>

    @Query("select * from printer where id = :id")
    fun findById(@Bind("id") printerId: Int): Printer

    @Query("select * from printer where id = :id")
    fun findOptionalById(@Bind("id") printerId: Int): Optional<Printer>

    @Query("update printer set active = :state where id = :id")
    fun toggleActivation(@Bind("id") id: Int, @Bind("state") state: Boolean): Int
}

@QueryRepository
interface UserPrinterRepository {

    @Query("insert into user_printer(user_id_fk, printer_id_fk) values(:userId, :printerId)")
    fun insert(@Bind("userId") userId: Int, @Bind("printerId") printerId: Int): Int

    @Query("update user_printer set printer_id_fk = :printerId where user_id_fk = :userId")
    fun update(@Bind("userId") userId: Int, @Bind("printerId") printerId: Int): Int

    @Query("delete from user_printer where user_id_fk = :userId")
    fun delete(@Bind("userId") userId: Int): Int

    @Query("select * from user_printer a, printer b where a.user_id_fk = :userId and a.printer_id_fk = b.id and b.active = true")
    fun getOptionalActivePrinter(@Bind("userId") userId: Int): Optional<UserPrinter>

    @Query("select * from user_printer a, printer b where a.user_id_fk = :userId and a.printer_id_fk = b.id")
    fun getOptionalUserPrinter(@Bind("userId") userId: Int): Optional<UserPrinter>

    @Query("select u.username as username, u.id as user_id, p.id as printer_id, p.name as printer_name, p.description as printer_description, p.active as printer_active from user u, printer p, user_printer up where up.user_id_fk = u.id and up.printer_id_fk = p.id")
    fun loadAll(): List<UserAndPrinter>

}

@QueryRepository
interface LabelConfigurationRepository {
    @Query("""merge into label_configuration using (values (:eventId, :json, :enabled))
        as vals(event_id_fk, json, enabled) on label_configuration.event_id_fk = vals.event_id_fk
        when matched then update set label_configuration.json = vals.json, label_configuration.enabled = vals.enabled
        when not matched then insert values vals.event_id_fk, vals.json, vals.enabled""")
    fun merge(@Bind("eventId") eventId: Int, @Bind("json") json: String?, @Bind("enabled") enabled: Boolean)

    @Query("select * from label_configuration where event_id_fk = :eventId")
    fun loadForEvent(@Bind("eventId") eventId: Int): Optional<LabelConfiguration>

}