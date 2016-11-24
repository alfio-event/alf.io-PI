package alfio.pi.repository

import alfio.pi.model.PersistedScanLog
import alfio.pi.model.ScanResult
import ch.digitalfondue.npjt.Bind
import ch.digitalfondue.npjt.Query
import ch.digitalfondue.npjt.QueryRepository

@QueryRepository
interface ScanLogRepository {
    @Query("select * from scan_log")
    fun loadAll():List<PersistedScanLog>

    @Query("select * from scan_log where event_id = :eventId")
    fun loadAllForEvent(@Bind("eventId") eventId: Int):List<PersistedScanLog>

    @Query("insert into scan_log (event_id, ticket_uuid, user, result) values(:eventId, :ticketUuid, :user, :result)")
    fun insert(@Bind("eventId") eventId: Int, @Bind("ticketUuid") ticketUuid: String, @Bind("user") user: String, @Bind("result") result: ScanResult): Int
}