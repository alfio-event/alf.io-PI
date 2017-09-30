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

import alfio.pi.model.NewScan
import alfio.pi.model.SystemEvent
import alfio.pi.model.SystemEventType
import alfio.pi.repository.EventRepository
import com.google.gson.Gson
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference

interface SystemEventHandler {
    fun notifyAllSessions(event: SystemEvent)
}

@Component
@Profile("printer")
open class SystemEventHandlerDummy : SystemEventHandler {
    override fun notifyAllSessions(event: SystemEvent) {
    }
}


@Component
@Profile("server", "full")
open class SystemEventHandlerImpl(private val gson: Gson,
                              private val kvStore: KVStore,
                              private val eventRepository: EventRepository): SystemEventHandler, TextWebSocketHandler() {
    private val logger = LoggerFactory.getLogger(SystemEventHandler::class.java)
    private val sessions = CopyOnWriteArraySet<WebSocketSession>()
    private val lastCheckTimestamp = AtomicReference<Date>(Date())

    @Async
    override fun notifyAllSessions(event: SystemEvent) {
        val payload = gson.toJson(event)
        sessions.filter { it.isOpen }.forEach { it.sendMessage(TextMessage(payload)) }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.debug("WebSocket connection established ${session.id}")
        sessions.add(session)
    }

    @Scheduled(fixedDelay = 100L)
    open fun checkSessions() {
        sessions.filter { !it.isOpen }.forEach {
            logger.debug("removing closed session with ID {}", it.id)
            sessions.remove(it)
        }
    }

    @Scheduled(fixedDelay = 1000L)
    open fun fetchAndSendNewScans() {
        kvStore.loadNew(lastCheckTimestamp.getAndSet(Date()))
            .groupBy { it.eventId }
            .map({(key, value) -> SystemEvent(SystemEventType.NEW_SCAN, NewScan(value, eventRepository.loadSingle(key).get()))})
            .forEach(this::notifyAllSessions)
    }


}


