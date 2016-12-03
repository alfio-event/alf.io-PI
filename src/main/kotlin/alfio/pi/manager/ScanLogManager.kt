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
import alfio.pi.model.ScanLog
import alfio.pi.repository.ScanLogRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

private val logger: Logger = LoggerFactory.getLogger("scanLogManager")

fun findAllEntriesForEvent(eventId: Int) : (ScanLogRepository) -> List<ScanLog> = {
    try {
        it.loadAllForEvent(eventId)
    } catch (e: Exception) {
        logger.error("unexpected error while loading entries for event $eventId", e)
        emptyList<ScanLog>()
    }
}

fun findAllEntries() : (ScanLogRepository) -> List<ScanLog> = {
    try {
        it.loadAll()
    } catch (e: Exception) {
        logger.error("unexpected error while loading all entries", e)
        emptyList<ScanLog>()
    }
}

@Component
open class CheckInListener(val scanLogRepository: ScanLogRepository) : ApplicationListener<CheckInEvent> {
    override fun onApplicationEvent(event: CheckInEvent?) {
        if(event != null) {
            val scanLog = event.scanLog
            if(scanLog.id == -1) {
                val result = scanLogRepository.insert(scanLog.eventId, scanLog.queueId, scanLog.ticketUuid, scanLog.user, scanLog.localResult, scanLog.remoteResult, scanLog.badgePrinted)
                logger.debug("inserted $result log for ticket ${scanLog.ticketUuid}. Local result: ${scanLog.localResult}, remote result: ${scanLog.localResult}, badge printed: ${scanLog.badgePrinted}")
            }
        }
    }
}