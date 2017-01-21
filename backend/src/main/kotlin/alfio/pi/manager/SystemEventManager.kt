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

import alfio.pi.model.SystemEvent
import com.google.gson.Gson
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.CopyOnWriteArraySet

@Component
open class SystemEventManager(val eventHandler: SystemEventHandler) {

    @Async
    fun publishEvent(event: SystemEvent) {
        eventHandler.notifyAllSessions(event)
    }
}

@Component
open class SystemEventHandler(val gson: Gson): TextWebSocketHandler() {
    private val logger = LoggerFactory.getLogger(SystemEventHandler::class.java)
    private val sessions = CopyOnWriteArraySet<WebSocketSession>()

    internal fun notifyAllSessions(event: SystemEvent) {
        val payload = gson.toJson(event)
        sessions.filter { it.isOpen }.forEach { it.sendMessage(TextMessage(payload)) }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.debug("WebSocket connection established ${session.id}")
        sessions.add(session)
    }

    @Scheduled(fixedDelay = 10L)
    open fun checkSessions() {
        sessions.filter { !it.isOpen }.forEach {
            logger.debug("removing closed session with ID {}", it.id)
            sessions.remove(it)
        }
    }
}


