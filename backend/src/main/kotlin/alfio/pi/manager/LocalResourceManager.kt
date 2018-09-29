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
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.nio.file.*
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

private val logger: Logger = LoggerFactory.getLogger("alfio.ScanLogManager")

fun toggleEventActivation(eventKey: String, state: Boolean): (PlatformTransactionManager, EventRepository) -> Boolean = { transactionManager, eventRepository ->
    doInTransaction<Boolean>().invoke(transactionManager, {
        eventRepository.toggleActivation(eventKey, state) == 1
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

fun findAllRegisteredPrinters(): (PrinterRepository) -> List<Printer> = { printerRepository ->
    tryOrDefault<List<Printer>>().invoke({printerRepository.loadAll()}, {
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

fun reprintBadge(scanLogId: String, printerId: Int?, username: String, content: ConfigurableLabelContent?, desk: Boolean): (PrintManager, PrinterRepository, KVStore, UserRepository) -> Boolean = { printManager, printerRepository, kvStore, userRepository ->
    tryOrDefault<Boolean>().invoke({
        kvStore.findOptionalById(scanLogId)
            .filter { it.ticket != null }
            .flatMap { scanLog ->
                val optionalPrinter = if(printerId != null) {
                    printerRepository.findOptionalById(printerId).map { printer -> Triple(printer, scanLog.ticket!!, scanLog.eventKey) }
                } else {
                    userRepository.findByUsername(username)
                        .flatMap { (id) -> printerRepository.findByUserId(id) }
                        .map { printer -> Triple(printer, scanLog.ticket!!, scanLog.eventKey) }
                }
                when {
                    optionalPrinter.isPresent -> optionalPrinter
                    desk -> Optional.ofNullable(printManager.getAvailablePrinters().firstOrNull())
                        .map { Printer(-1, it.name, null, true) }
                        .map { Triple(it, scanLog.ticket!!, scanLog.eventKey) }
                    else -> Optional.empty()
                }
            }.map { (printer, ticket, eventKey) ->
                val labelConfiguration = kvStore.loadLabelConfiguration(eventKey).map { LabelConfigurationAndContent(it, content) }.orElse(LabelConfigurationAndContent(null, content))
                printManager.printLabel(printer, ticket, labelConfiguration)
            }.orElse(false)
    }, {
        logger.error("cannot re-print label. ",it)
        false
    })
}

fun reprintPreview(eventKey: String, scanLogId: String): (PrintManager, KVStore) -> Optional<ConfigurableLabelContent> = { printManager, kvStore ->
    kvStore.findOptionalByIdAndEventKey(scanLogId, eventKey)
        .filter {it.ticket != null}
        .map { printManager.getLabelContent(it.ticket!!, kvStore.loadLabelConfiguration(eventKey).orElse(null)) }
}

fun printTestBadge(printerId: Int): (PrintManager, PrinterRepository) -> Boolean = { printManager, printerRepository ->
    printerRepository.findOptionalById(printerId)
        .map { printManager.printTestLabel(it) }
        .orElse(false)
}

fun printOnLocalPrinter(printerName: String, ticket: Ticket, labelConfiguration: LabelConfigurationAndContent?): (PrintManager) -> Boolean = { printManager ->
    val printer = printManager.getAvailablePrinters().firstOrNull { it.name.equals(printerName, true) }
    if(printer != null) {
        printManager.printLabel(Printer(-1, printer.name, null, true), ticket, labelConfiguration)
    } else {
        false
    }
}

@Component
@Profile("full", "server")
open class PrinterSynchronizer(private val printerRepository: PrinterRepository, private val printManager: PrintManager) {
    @Scheduled(fixedDelay = 5000L)
    open fun syncPrinters() {
        val existingPrinters = printerRepository.loadAll()
        val systemPrinters = printManager.getAvailablePrinters()
        logger.trace("getSystemPrinters returned ${systemPrinters.size} elements, $systemPrinters")
        systemPrinters.filter { (name) -> existingPrinters.none { e -> e.name.equals(name, true) }}.forEach {
            printerRepository.insert(it.name, "", true)
        }
    }
}

@Component
open class LocalPrinterMonitor(private val printManager: PrintManager) {

    private val monitorInitialized = AtomicBoolean(false)
    private val watcher = FileSystems.getDefault().newWatchService()
    private val connectedPrinters = CopyOnWriteArrayList<SystemPrinter>()
    private val path = Paths.get("/dev/usb/")

    @Scheduled(fixedDelay = 1000L)
    open fun monitorPrinters() {
        synchronizeLocalPrinters()
        val cupsPrinters = printManager.getAvailablePrinters()
        cupsPrinters
            .filter { it.name.startsWith("Alfio") && !connectedPrinters.contains(it) }
            .forEach {
                logger.trace("printer ${it.name} to be removed")
                val result  = Runtime.getRuntime().exec("/usr/sbin/lpadmin -x ${it.name}").waitFor()
                if(result == 0) {
                    logger.warn("removed printer ${it.name}, symlink /dev/usb/${it.name}")
                }
            }
    }

    private fun synchronizeLocalPrinters() {
        val initialized = monitorInitialized.get()
        if(!initialized && Files.exists(path)) {
            Files.newDirectoryStream(path).use { directoryStream ->
                directoryStream.filter { it.fileName.toString().startsWith("Alfio") }
                    .mapTo(connectedPrinters) { SystemPrinter(it.fileName.toString()) }
            }
            pollEvents(path.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE))
            monitorInitialized.set(true)
        } else if(initialized) {
            pollEvents(watcher.take())
        }
    }

    private fun pollEvents(watchKey: WatchKey) {
        watchKey.pollEvents()
            .asSequence()
            .filter { it.kind() == StandardWatchEventKinds.ENTRY_CREATE || it.kind() == StandardWatchEventKinds.ENTRY_DELETE }
            .map { (it.context() as Path).fileName.toString() to it.kind() }
            .filter { it.first.startsWith("Alfio") }
            .forEach { (first, second) ->
                if (second == StandardWatchEventKinds.ENTRY_CREATE) {
                    connectedPrinters.add(SystemPrinter(first))
                } else {
                    connectedPrinters.removeIf { it.name == first }
                }
            }
        monitorInitialized.set(watchKey.reset())
    }
}
