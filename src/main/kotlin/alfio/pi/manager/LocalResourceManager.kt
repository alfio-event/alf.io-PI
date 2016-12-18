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

import alfio.pi.model.Event
import alfio.pi.model.Printer
import alfio.pi.model.ScanLog
import alfio.pi.repository.EventRepository
import alfio.pi.repository.PrinterRepository
import alfio.pi.repository.ScanLogRepository
import alfio.pi.wrapper.doInTransaction
import alfio.pi.wrapper.tryOrDefault
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.PlatformTransactionManager
import java.util.*

private val logger: Logger = LoggerFactory.getLogger("scanLogManager")

fun findAllEntriesForEvent(eventId: Int) : (ScanLogRepository) -> List<ScanLog> = {
    tryOrDefault<List<ScanLog>>().invoke({it.loadAllForEvent(eventId)}, {
        logger.error("unexpected error while loading entries for event $eventId", it)
        emptyList()
    })
}

fun findAllEntries() : (ScanLogRepository) -> List<ScanLog> = {
    tryOrDefault<List<ScanLog>>().invoke({it.loadAll()}, {
        logger.error("unexpected error while loading all entries", it)
        emptyList()
    })
}

fun findLocalEvents(): (EventRepository) -> List<Event> = {
    tryOrDefault<List<Event>>().invoke({it.loadAll()}, {
        logger.error("unexpected error while loading events", it)
        emptyList()
    })
}

fun findLocalEvent(eventId: Int): (EventRepository) -> Optional<Event> = {
    tryOrDefault<Optional<Event>>().invoke({it.loadSingle(eventId)}, {
        logger.error("error while loading event $eventId", it)
        Optional.empty()
    })
}

fun toggleEventActivation(id: Int, state: Boolean): (PlatformTransactionManager, EventRepository) -> Boolean = { transactionManager, eventRepository ->
    doInTransaction<Boolean>().invoke(transactionManager, {
        eventRepository.toggleActivation(id, state) == 1
    }, {
        logger.error("error while trying to update active state", it)
        false
    })
}

fun togglePrinterActivation(id: Int, state: Boolean): (PlatformTransactionManager, PrinterRepository) -> Boolean = { transactionManager, printerRepository ->
    doInTransaction<Boolean>().invoke(transactionManager, {
        printerRepository.toggleActivation(id, state) == 1
    }, {
        logger.error("error while trying to update active state to $state for printer $id", it)
        false
    })
}

fun findAllRegisteredPrinters(): (PrinterRepository) -> List<Printer> = {
    tryOrDefault<List<Printer>>().invoke({it.loadAll()}, {
        logger.error("error while loading printers", it)
        emptyList()
    })
}