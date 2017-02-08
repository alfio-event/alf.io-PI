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

package alfio.pi.controller

import alfio.pi.manager.*
import alfio.pi.model.Event
import alfio.pi.model.Printer
import alfio.pi.model.ScanLog
import alfio.pi.repository.EventRepository
import alfio.pi.repository.PrinterRepository
import alfio.pi.repository.ScanLogRepository
import alfio.pi.repository.UserPrinterRepository
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/internal/scan-log")
open class ScanLogApi (val scanLogRepository: ScanLogRepository, val printManager: PrintManager, val printerRepository: PrinterRepository) {

    @RequestMapping("")
    open fun loadAll(@RequestParam(value = "max", defaultValue = "-1") max: Int) : List<ScanLog> = findAllEntries(max).invoke(scanLogRepository)

    @RequestMapping("/event/{eventId}")
    open fun loadForEvent(@PathVariable("eventId") eventId: Int) : List<ScanLog> = findAllEntriesForEvent(eventId).invoke(scanLogRepository)

    @RequestMapping(value = "/{entryId}/reprint", method = arrayOf(RequestMethod.PUT))
    open fun reprint(@PathVariable("entryId") entryId: Int,
                     @RequestBody form: ReprintForm): ResponseEntity<Boolean> {
        val printerId = form.printer;
        return if(printerId != null) {
            ResponseEntity.ok(reprintBadge(entryId, printerId).invoke(printManager, printerRepository, scanLogRepository))
        } else {
            ResponseEntity(HttpStatus.BAD_REQUEST)
        }
    }
}

class ReprintForm {
    var printer: Int?=null
}



@RestController
@RequestMapping("/api/internal/events")
open class EventApi (val transactionManager: PlatformTransactionManager,
                     val eventRepository: EventRepository) {

    @RequestMapping(value = "", method = arrayOf(RequestMethod.GET))
    open fun loadAll(): List<Event> = findLocalEvents().invoke(eventRepository)

    @RequestMapping(value = "/{eventId}", method = arrayOf(RequestMethod.GET))
    open fun getSingleEvent(@PathVariable("eventId") eventId: Int) : ResponseEntity<Event> = findLocalEvent(eventId).invoke(eventRepository)
        .map{ ResponseEntity.ok(it) }
        .orElseGet { ResponseEntity(HttpStatus.NOT_FOUND) }

    @RequestMapping(value = "/{eventId}/active", method = arrayOf(RequestMethod.PUT, RequestMethod.DELETE))
    open fun toggleActiveState(@PathVariable("eventId") eventId: Int, method: HttpMethod): Boolean = toggleEventActivation(eventId, method == HttpMethod.PUT).invoke(transactionManager, eventRepository)

}

@RestController
@RequestMapping("/api/internal/user-printer")
open class UserPrinterApi(val transactionManager: PlatformTransactionManager, val userPrinterRepository: UserPrinterRepository) {
    @RequestMapping(value = "/", method = arrayOf(RequestMethod.POST))
    open fun linkUserToPrinter(@RequestBody userPrinterForm: UserPrinterForm, method: HttpMethod): ResponseEntity<Boolean> {
        val userId = userPrinterForm.userId
        val printerId = userPrinterForm.printerId
        return if(userId != null && printerId != null) {
            val result = alfio.pi.manager.linkUserToPrinter(userId, printerId).invoke(transactionManager, userPrinterRepository)
            if(result) {
                ResponseEntity.ok(true)
            } else {
                ResponseEntity(HttpStatus.CONFLICT)
            }
        } else {
            ResponseEntity(HttpStatus.BAD_REQUEST)
        }
    }

    @RequestMapping(value = "/{userId}", method = arrayOf(RequestMethod.DELETE))
    open fun removeUserPrinterLink(@PathVariable("userId") userId: Int): ResponseEntity<Boolean> {
        val result = alfio.pi.manager.removeUserPrinterLink(userId).invoke(transactionManager, userPrinterRepository)
        return if(result) {
            ResponseEntity.ok(true)
        } else {
            ResponseEntity(HttpStatus.CONFLICT)
        }
    }
}

class UserPrinterForm {
    var userId: Int? = null
    var printerId: Int? = null
}

@RestController
@RequestMapping("/api/internal/printers")
open class PrinterApi (val transactionManager: PlatformTransactionManager,
                       val printerRepository: PrinterRepository,
                       val userPrinterRepository: UserPrinterRepository,
                       val printManager: PrintManager) {
    @RequestMapping(value = "", method = arrayOf(RequestMethod.GET))
    open fun loadAllPrinters(): List<Printer> = findAllRegisteredPrinters().invoke(printerRepository)

    @RequestMapping(value = "/{printerId}/active", method = arrayOf(RequestMethod.PUT, RequestMethod.DELETE))
    open fun toggleActiveState(@PathVariable("printerId") printerId: Int, method: HttpMethod): Boolean = togglePrinterActivation(printerId, method == HttpMethod.PUT).invoke(transactionManager, printerRepository)

    @RequestMapping(value = "/with-users", method = arrayOf(RequestMethod.GET))
    open fun loadPrintConfiguration() = loadPrinterConfiguration().invoke(userPrinterRepository, printerRepository)

    @RequestMapping(value = "/{printerId}/test", method = arrayOf(RequestMethod.PUT))
    open fun printTestPage(@PathVariable("printerId") printerId: Int) = printTestBadge(printerId).invoke(printManager, printerRepository)
}