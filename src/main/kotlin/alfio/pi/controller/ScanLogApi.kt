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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/scan-log")
open class ScanLogApi (val scanLogRepository: ScanLogRepository) {

    @RequestMapping("/")
    open fun loadAll() : List<ScanLog> = findAllEntries().invoke(scanLogRepository)

    @RequestMapping("/event/{eventId}")
    open fun loadForEvent(@PathVariable("eventId") eventId: Int) : List<ScanLog> = findAllEntriesForEvent(eventId).invoke(scanLogRepository)
}



@RestController
@RequestMapping("/api/events")
open class EventApi (val transactionManager: PlatformTransactionManager, val eventRepository: EventRepository) {

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
@RequestMapping("/api/printers")
open class PrinterApi (val transactionManager: PlatformTransactionManager, val printerRepository: PrinterRepository) {
    @RequestMapping(value = "", method = arrayOf(RequestMethod.GET))
    open fun loadAllPrinters(): List<Printer> = findAllRegisteredPrinters().invoke(printerRepository)

    @RequestMapping(value = "/{printerId}/active", method = arrayOf(RequestMethod.PUT, RequestMethod.DELETE))
    open fun toggleActiveState(@PathVariable("printerId") printerId: Int, method: HttpMethod): Boolean = togglePrinterActivation(printerId, method == HttpMethod.PUT).invoke(transactionManager, printerRepository)
}