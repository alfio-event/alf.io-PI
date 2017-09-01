package alfio.pi.manager

import alfio.pi.model.CheckInResponse
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("server", "full")
open class JGroupsClusterRpcApi(private val appContext: ApplicationContext) {

    open var firstSyncDone: Boolean = false

    //lazy loading as we have a circular dep...
    open fun remoteCheckIn(eventKey: String, uuid: String, hmac: String, username: String) : CheckInResponse {
        return appContext.getBean(CheckInDataManager::class.java).remoteCheckIn(eventKey, uuid, hmac, username)
    }

    open fun isFirstSyncDone() : Boolean {
        return firstSyncDone
    }
}