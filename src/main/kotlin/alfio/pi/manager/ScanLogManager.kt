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

import alfio.pi.model.CheckInEvent
import alfio.pi.model.NotYetPersistedScanLog
import alfio.pi.model.PersistedScanLog
import alfio.pi.model.ScanLog
import alfio.pi.repository.ScanLogRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

private val logger: Logger = LoggerFactory.getLogger("scanLogManager")

fun findAllEntriesForEvent(eventId: Int) : (ScanLogRepository) -> List<PersistedScanLog> = {
    try {
        it.loadAllForEvent(eventId)
    } catch (e: Exception) {
        logger.error("unexpected error while loading entries for event $eventId", e)
        emptyList<PersistedScanLog>()
    }
}

fun findAllEntries() : (ScanLogRepository) -> List<PersistedScanLog> = {
    try {
        it.loadAll()
    } catch (e: Exception) {
        logger.error("unexpected error while loading all entries", e)
        emptyList<PersistedScanLog>()
    }
}

@Component
open class CheckInListener(val scanLogRepository: ScanLogRepository) : ApplicationListener<CheckInEvent> {
    override fun onApplicationEvent(event: CheckInEvent?) {
        if(event != null) {
            val scanLog = event.scanLog
            if(scanLog is NotYetPersistedScanLog) {
                val result = scanLogRepository.insert(scanLog.eventId, scanLog.ticketUuid, scanLog.user, scanLog.result)
                logger.debug("inserted $result log for ticket ${scanLog.ticketUuid}. Result: ${scanLog.result}")
            }
        }
    }
}