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

import alfio.pi.model.CheckInResponse
import org.jgroups.Address
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.jgroups.JChannel
import org.jgroups.blocks.MethodCall
import org.jgroups.blocks.ResponseMode
import org.jgroups.blocks.RequestOptions
import org.jgroups.blocks.RpcDispatcher
import java.lang.reflect.Method


@Component
@Profile("server", "full")
open class JGroupsCluster {

    val channel: JChannel = JChannel()

    init {
        channel.connect("alf.io-PI")
    }

    open fun isLeader(): Boolean {
        return getLeaderAddress() == channel.address
    }

    open fun getLeaderAddress(): Address {
        // first member is considered the leader.
        // as per doc: These addresses are ordered, and the first address is always the coordinator of the view.
        return channel.view.members[0]
    }

    open fun remoteCheckInToMaster(checkInDataManager: CheckInDataManager, method: Method, eventKey: String, uuid: String, hmac: String, username: String) : CheckInResponse {
        val opts = RequestOptions(ResponseMode.GET_ALL, 5000)
        val disp = RpcDispatcher(channel, checkInDataManager)

        val remoteCheckInCall = MethodCall(method)
        remoteCheckInCall.setArgs(eventKey, uuid, hmac, username)

        return disp.callRemoteMethod<CheckInResponse>(getLeaderAddress(), remoteCheckInCall, opts)
    }

    open fun remoteLoadCachedAttendees(checkInDataManager: CheckInDataManager, method: Method, eventName: String, since: Long?) : Map<String, String> {
        val opts = RequestOptions(ResponseMode.GET_ALL, 5000)
        val disp = RpcDispatcher(channel, checkInDataManager)

        val remoteLoadCachedAttendeesCall = MethodCall(method)
        remoteLoadCachedAttendeesCall.setArgs(eventName, since)

        return disp.callRemoteMethod<Map<String, String>>(getLeaderAddress(), remoteLoadCachedAttendeesCall, opts)
    }
}