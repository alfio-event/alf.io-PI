package alfio.pi.manager

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.jgroups.JChannel
import org.springframework.scheduling.annotation.Scheduled

@Component
@Profile("server", "full")
open class JGroupsCluster {

    val channel: JChannel = JChannel()

    init {
        channel.connect("alf.io-PI")
    }

    open fun isLeader(): Boolean {
        val view = channel.view
        // first member is considered the leader.
        // as per doc: These addresses are ordered, and the first address is always the coordinator of the view.
        val address = view.members.get(0)
        return address.equals(channel.address)
    }
}