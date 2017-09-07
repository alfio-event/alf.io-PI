package alfio.pi.manager

import alfio.pi.model.Attendee
import alfio.pi.model.CheckInResponse
import alfio.pi.model.CheckInStatus
import alfio.pi.model.LabelConfiguration
import alfio.pi.repository.AttendeeDataRepository
import alfio.pi.repository.EventRepository
import alfio.pi.repository.LabelConfigurationRepository
import alfio.pi.repository.ScanLogRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
@Profile("server", "full")
open class JGroupsClusterRpcApi(private val appContext: ApplicationContext) {

    private val logger = LoggerFactory.getLogger(JGroupsClusterRpcApi::class.java)

    open var firstSyncDone: Boolean = false

    //lazy loading as we have a circular dep...
    open fun remoteCheckIn(eventKey: String, uuid: String, hmac: String, username: String) : CheckInResponse {
        return appContext.getBean(CheckInDataManager::class.java).remoteCheckIn(eventKey, uuid, hmac, username)
    }

    open fun getIdentifiersForEvent(eventName: String, lastModified: Long) : List<String> {
        logger.info("getIdentifiersForEvent $eventName with lastModified $lastModified called")
        val res = appContext.getBean(AttendeeDataRepository::class.java).getIdentifiersForEvent(eventName, lastModified)
        logger.info("for $eventName found ${res.size}")
        return res
    }

    open fun getAttendeeData(identifiers : List<String>) : List<Attendee> {
        return appContext.getBean(AttendeeDataRepository::class.java).getAttendeeData(identifiers)
    }

    open fun loadLabelConfigurationForEventName(eventName: String) =
        appContext.getBean(EventRepository::class.java)
            .loadSingle(eventName)
            .flatMap { (id) ->
                appContext.getBean(LabelConfigurationRepository::class.java)
                    .loadForEvent(id)
            }.orElse(null)

    open fun insertInScanLog(now: ZonedDateTime, eventName: String, uuid: String, id: Int, localResult: CheckInStatus, status: CheckInStatus, labelPrinted: Boolean, jsonPayload: String?) {
        appContext.getBean(EventRepository::class.java).loadSingle(eventName).ifPresent { (eventId) ->
            val scanLogRepository = appContext.getBean(ScanLogRepository::class.java)
            if(!scanLogRepository.existByEventAndTicketId(eventId, uuid)) {
                scanLogRepository.insert(now, eventId, uuid, id, localResult, status, labelPrinted, jsonPayload)
            }
        }
    }

    open fun isFirstSyncDone() : Boolean {
        return firstSyncDone
    }
}