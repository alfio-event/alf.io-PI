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

import alfio.pi.model.CheckInStatus
import alfio.pi.model.ScanLog
import ch.digitalfondue.npjt.Bind
import ch.digitalfondue.npjt.Query
import ch.digitalfondue.npjt.QueryRepository
import java.util.*

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