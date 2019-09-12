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

import alfio.pi.model.*
import alfio.pi.model.CheckInStatus.*
import alfio.pi.repository.EventRepository
import alfio.pi.repository.UserRepository
import alfio.pi.wrapper.tryOrDefault
import com.google.gson.Gson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@Component
open class BadgeScanManager(private val eventRepository: EventRepository,
                            private val kvStore: KVStore,
                            private val userRepository: UserRepository,
                            @Value("\${checkIn.categories.blacklist:#{null}}") private val categoriesBlacklist: String?,
                            private val remoteCheckInExecutor: RemoteCheckInExecutor,
                            private val gson: Gson) {

    private val logger = LoggerFactory.getLogger(BadgeScanManager::class.java)

    open fun performBadgeScan(eventName: String, uuid: String, username: String) : CheckInResponse {
        val event = eventRepository.loadSingle(eventName)
        if(!event.isPresent) {
            return EmptyTicketResult()
        }
        val badgeScan = kvStore.retrieveBadgeScan(eventName, uuid)
        val now = ZonedDateTime.now(ZoneId.of(event.get().timezone))
        return if(badgeScan != null) {
            when(badgeScan.localStatus) {
                SUCCESS, BADGE_SCAN_SUCCESS, BADGE_SCAN_ALREADY_DONE -> {
                    if(badgeScan.checkInStrategy == CheckInStrategy.ONCE_PER_EVENT) {
                        return CheckInForbidden()
                    }
                    if(now.isBefore(badgeScan.ticketValidityFrom) || now.isAfter(badgeScan.ticketValidityTo)) {
                        return CheckInForbidden(CheckInResult(INVALID_TICKET_CATEGORY_CHECK_IN_DATE))
                    }
                    return if(ChronoUnit.DAYS.between(badgeScan.timestamp.truncatedTo(ChronoUnit.DAYS), now.truncatedTo(ChronoUnit.DAYS)) == 0L) {
                        registerBadgeScan(eventName, badgeScan, now, username, uuid, BADGE_SCAN_ALREADY_DONE)
                        DuplicatedBadgeScan()
                    } else {
                        registerBadgeScan(eventName, badgeScan, now, username, uuid, BADGE_SCAN_SUCCESS)
                        BadgeScanSuccess(badgeScan)
                    }
                }
                else -> EmptyTicketResult()
            }
        } else {
            EmptyTicketResult()
        }
    }

    private fun registerBadgeScan(eventName: String,
                                  badgeScan: BadgeScan,
                                  now: ZonedDateTime,
                                  username: String,
                                  ticketUUID: String,
                                  status: CheckInStatus) {
        kvStore.insertBadgeScan(eventName, badgeScan.copy(localStatus = status, timestamp = now))
        val userId = userRepository.findByUsername(username).map { it.id }.orElse(0)
        kvStore.insertBadgeScanLog(eventName, ticketUUID, userId, status, RETRY, now)
    }

    @Scheduled(fixedDelay = 5000L)
    open fun processPendingEntries() {
        if(kvStore.isLeader()) {
            kvStore.selectBadgeScanToSynchronize()
                .asSequence()
                .groupBy { it.eventKey }
                .mapKeys { eventRepository.loadSingle(it.key) }
                .filter { it.key.isPresent }
                .forEach { entry -> uploadEntriesForEvent(entry) }
        }
    }

    private fun uploadEntriesForEvent(entry: Map.Entry<Optional<Event>, List<ScanLog>>) {
        tryOrDefault<Unit>().invoke({
            val event = entry.key.get()
            logger.info("******** uploading badge scan for event ${event.key} **********")
            val scanLogEntries = entry.value
            val ticketIdToScanLogId = scanLogEntries.associateBy { it.ticketUuid }
            val response = remoteCheckInExecutor.remoteBulkCheckIn(event.key, scanLogEntries) {list -> list.map { mapOf("identifier" to it.ticketUuid, "code" to null)}}

            response.forEach {
                if(logger.isTraceEnabled) {
                    logger.trace("response is ${it.key} ${gson.toJson(it.value)}")
                }
                kvStore.updateBadgeScanRemoteResult(it.value.result.status, ticketIdToScanLogId[it.key] ?: error("Unexpected error during badge scan upload"))
            }
            logger.info("******** upload completed (${event.key}: ${response.size}) **********")
        }, { logger.error("unable to upload pending badge scan", it)})
    }

}