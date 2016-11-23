package alfio.pi.repository

import alfio.pi.model.ScanLog
import ch.digitalfondue.npjt.Query
import ch.digitalfondue.npjt.QueryRepository

@QueryRepository
interface ScanLogRepository {
    @Query("select * from scan_log")
    fun loadAll():List<ScanLog>
}