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
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
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

fun findAllEntries(max: Int) : (ScanLogRepository) -> List<ScanLog> = {
    tryOrDefault<List<ScanLog>>().invoke({
        if(max > 0) {
            it.loadLastN(max)
        } else {
            it.loadAll()
        }
    }, {
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

fun removeUserPrinterLink(userId: Int): (PlatformTransactionManager, UserPrinterRepository) -> Boolean = { transactionManager, userPrinterRepository ->
    doInTransaction<Boolean>().invoke(transactionManager, {
        userPrinterRepository.delete(userId) == 1
    }, {
        logger.warn("cannot delete link for userId $userId", it)
        false
    })
}


fun linkUserToPrinter(userId: Int, printerId: Int): (PlatformTransactionManager, UserPrinterRepository) -> Boolean = { transactionManager, userPrinterRepository ->
    doInTransaction<Boolean>().invoke(transactionManager, {
        if(userPrinterRepository.getOptionalUserPrinter(userId).isPresent) {
            userPrinterRepository.update(userId, printerId) > 0
        } else {
            userPrinterRepository.insert(userId, printerId) > 0
        }
    }, {
        logger.warn("cannot link userId $userId to printer $printerId", it)
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

fun loadPrinterConfiguration(): (UserPrinterRepository, PrinterRepository) -> Collection<PrinterWithUsers> = { userPrinterRepository: UserPrinterRepository, printerRepository: PrinterRepository ->
    tryOrDefault<Collection<PrinterWithUsers>>().invoke({
        userPrinterRepository.loadAll()
            .groupBy({it.printer}, {it.user})
            .toList()
            .map { PrinterWithUsers(it.first, it.second) }
            .union(printerRepository.loadAll().map { PrinterWithUsers(it, emptyList()) })
            .sortedBy { it.printer.name }
    }, {
        logger.error("error while loading print configuration", it)
        emptySet()
    })
}

fun reprintBadge(scanLogId: Int, printerId: Int): (PrintManager, PrinterRepository, ScanLogRepository) -> Boolean = { printManager: PrintManager, printerRepository: PrinterRepository, scanLogRepository: ScanLogRepository ->
    tryOrDefault<Boolean>().invoke({
        scanLogRepository.findOptionalById(scanLogId)
            .filter { it.ticket != null }
            .flatMap { scanLog ->
                printerRepository.findOptionalById(printerId).map { printer -> printer to scanLog.ticket!! }
            }.map {
                printManager.printLabel(it.first, it.second)
            }.orElse(false)
    }, {
        logger.error("cannot re-print label. ",it)
        false
    })
}

fun printTestBadge(printerId: Int): (PrintManager, PrinterRepository) -> Boolean = { printManager: PrintManager, printerRepository: PrinterRepository ->
    printerRepository.findOptionalById(printerId)
        .map { printManager.printTestLabel(it) }
        .orElse(false)
}

fun printOnLocalPrinter(printerName: String, ticket: Ticket): (PrintManager) -> Boolean = { printManager ->
    val printer = printManager.getAvailablePrinters().filter { it.name.equals(printerName, true) }.firstOrNull()
    if(printer != null) {
        printManager.printLabel(Printer(-1, printer.name, null, true), ticket)
    } else {
        false
    }
}

@Component
@Profile("full", "server")
open class PrinterSynchronizer(val printerRepository: PrinterRepository, val printManager: PrintManager) {
    @Scheduled(fixedDelay = 10000L)
    open fun syncPrinters() {
        val existingPrinters = printerRepository.loadAll()
        val systemPrinters = printManager.getAvailablePrinters()
        logger.trace("getSystemPrinters returned ${systemPrinters.size} elements, $systemPrinters")
        systemPrinters.filter { sp -> existingPrinters.none { e -> e.name.equals(sp.name, true) }}.forEach {
            printerRepository.insert(it.name, "", true)
        }
    }
}
