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
import alfio.pi.repository.*
import alfio.pi.wrapper.doInTransaction
import alfio.pi.wrapper.tryOrDefault
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.util.*

private val logger: Logger = LoggerFactory.getLogger("alfio.ScanLogManager")

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

fun findLocalEvent(eventName: String): (EventRepository) -> Optional<Event> = {
    tryOrDefault<Optional<Event>>().invoke({it.loadSingle(eventName)}, {
        logger.error("error while loading event $eventName", it)
        Optional.empty()
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

fun removeUserPrinterLink(eventId: Int, userId: Int, printerId: Int): (PlatformTransactionManager, UserPrinterRepository) -> Boolean = { transactionManager, userPrinterRepository ->
    doInTransaction<Boolean>().invoke(transactionManager, {
        userPrinterRepository.delete(userId, eventId, printerId) == 1
    }, {
        logger.warn("cannot link userId $userId to printer $printerId, event $eventId", it)
        false
    })
}


fun linkUserToPrinter(eventId: Int, userId: Int, printerId: Int): (PlatformTransactionManager, UserPrinterRepository) -> Boolean = { transactionManager, userPrinterRepository ->
    doInTransaction<Boolean>().invoke(transactionManager, {
        if(!userPrinterRepository.getOptionalUserPrinter(userId, eventId).isPresent) {
            userPrinterRepository.insert(userId, eventId, printerId) > 0
        } else {
            false
        }
    }, {
        logger.warn("cannot link userId $userId to printer $printerId, event $eventId", it)
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

fun loadPrintConfigurationForEvent(eventId: Int): (UserPrinterRepository) -> List<PrinterWithUsers> = {
    tryOrDefault<List<PrinterWithUsers>>().invoke({
        it.loadAllForEvent(eventId)
            .groupBy({it.printer}, {it.user})
            .toList()
            .sortedBy { it.first.id }
            .map { PrinterWithUsers(it.first, it.second) }
    }, {
        logger.error("error while loading print configuration for event $eventId", it)
        emptyList()
    })
}

@Component
open class PrinterManager(val printerRepository: PrinterRepository) {
    @Scheduled(fixedDelay = 5000L)
    open fun syncPrinters() {
        val existingPrinters = printerRepository.loadAll()
        getSystemPrinters().filter { sp -> existingPrinters.none { e -> e.name == sp.name }}.forEach {
            printerRepository.insert(it.name, "", true)
        }
    }
}
