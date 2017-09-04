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

import alfio.pi.model.Attendee
import alfio.pi.model.CheckInResponse
import alfio.pi.model.CheckInStatus
import alfio.pi.wrapper.tryOrDefault
import org.jgroups.*
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.jgroups.blocks.MethodCall
import org.jgroups.blocks.ResponseMode
import org.jgroups.blocks.RequestOptions
import org.jgroups.blocks.RpcDispatcher
import java.time.ZonedDateTime


@Component
@Profile("server", "full")
open class JGroupsCluster(private var jGroupsClusterRpcApi : JGroupsClusterRpcApi) {

    private val channel: JChannel = JChannel()
    var dispatcher : RpcDispatcher

    init {
        channel.connect("alf.io-PI")
        dispatcher = RpcDispatcher(channel, jGroupsClusterRpcApi)
    }

    open fun isLeader(): Boolean {
        return getLeaderAddress() == channel.address
    }

    private fun everybodyElseAddresses() : Collection<Address> {
        return channel.view.members.filter { it != channel.address }
    }

    open fun getLeaderAddress(): Address {
        // first member is considered the leader.
        // as per doc: These addresses are ordered, and the first address is always the coordinator of the view.
        return channel.view.members[0]
    }

    open fun remoteCheckInToMaster(eventKey: String, uuid: String, hmac: String, username: String) : CheckInResponse? {
        val opts = RequestOptions(ResponseMode.GET_ALL, 5000)
        val method = jGroupsClusterRpcApi.javaClass.getMethod("remoteCheckIn", String::class.java, String::class.java, String::class.java, String::class.java)
        val remoteCheckInCall = MethodCall(method)
        remoteCheckInCall.setArgs(eventKey, uuid, hmac, username)
        return dispatcher.callRemoteMethod<CheckInResponse>(getLeaderAddress(), remoteCheckInCall, opts)
    }

    open fun leaderHasPerformSyncDone() : Boolean = tryOrDefault<Boolean>().invoke({
        val opts = RequestOptions(ResponseMode.GET_ALL, 5000)
        val method = jGroupsClusterRpcApi.javaClass.getMethod("isFirstSyncDone")
        val remoteCall = MethodCall(method)
        dispatcher.callRemoteMethod<Boolean>(getLeaderAddress(), remoteCall, opts)
    }, {false})

    open fun getIdentifiersForEvent(eventName: String, lastModified: Long) : List<String> {
        val opts = RequestOptions(ResponseMode.GET_ALL, 5000)
        val method = jGroupsClusterRpcApi.javaClass.getMethod("getIdentifiersForEvent", String::class.java, Long::class.java)
        val remoteCall = MethodCall(method)
        remoteCall.setArgs(eventName, lastModified+1)
        return dispatcher.callRemoteMethod<List<String>>(getLeaderAddress(), remoteCall, opts)
    }

    open fun loadAttendeesWithIdentifier(partitionedIds: List<String>) : List<Attendee> {
        val opts = RequestOptions(ResponseMode.GET_ALL, 5000)
        val method = jGroupsClusterRpcApi.javaClass.getMethod("getAttendeeData", List::class.java)
        val remoteCall = MethodCall(method)
        remoteCall.setArgs(partitionedIds)
        return dispatcher.callRemoteMethod<List<Attendee>>(getLeaderAddress(), remoteCall, opts)
    }

    open fun insertInScanLog(now: ZonedDateTime, eventName: String, uuid: String, userId: Int, localResult: CheckInStatus, status: CheckInStatus, labelPrinted: Boolean, jsonPayload: String?) {
        //TODO replace userId with userName
        val opts = RequestOptions(ResponseMode.GET_ALL, 5000)
        val method = jGroupsClusterRpcApi.javaClass.getMethod("insertInScanLog",
            ZonedDateTime::class.java, String::class.java, String::class.java,
            Int::class.java, CheckInStatus::class.java, CheckInStatus::class.java,
            Boolean::class.java, String::class.java)
        val remoteCall = MethodCall(method)
        remoteCall.setArgs(now, eventName, uuid, userId, localResult, status, labelPrinted, jsonPayload)
        dispatcher.callRemoteMethodsWithFuture<Any>(everybodyElseAddresses(), remoteCall, opts)
    }

    open fun hasPerformSyncDone(status: Boolean) {
        jGroupsClusterRpcApi.firstSyncDone = status
    }

}