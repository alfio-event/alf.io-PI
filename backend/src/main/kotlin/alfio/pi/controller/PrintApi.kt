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
import alfio.pi.model.*
import alfio.pi.repository.PrinterRepository
import alfio.pi.repository.UserPrinterRepository
import alfio.pi.wrapper.tryOrDefault
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

val logger: Logger = LoggerFactory.getLogger("PrintApi")

@RestController
@RequestMapping("/api/internal/user-printer")
@Profile("server", "full")
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
@Profile("server", "full")
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

@RestController
@RequestMapping("/api/printers")
@Profile("printer")
open class LocalPrinterApi(val printManager: PrintManager) {
    @RequestMapping(value="/{printerName}/print", method = arrayOf(RequestMethod.POST))
    open fun print(@PathVariable("printerName") printerName: String, @RequestBody ticket: Ticket) = printOnLocalPrinter(printerName, ticket).invoke(printManager)
}

@RestController
@RequestMapping("/api/printers")
@Profile("server")
open class RemotePrinterApi(val applicationEventPublisher: ApplicationEventPublisher) {
    @RequestMapping(value = "/register", method = arrayOf(RequestMethod.POST))
    open fun registerPrinters(@RequestBody printers: List<SystemPrinter>, request: HttpServletRequest) = tryOrDefault<ResponseEntity<Unit>>().invoke({
        val remoteAddress = request.remoteAddr
        logger.trace("registering $printers for $remoteAddress")
        applicationEventPublisher.publishEvent(PrintersRegistered(printers.map { RemotePrinter(it.name, remoteAddress) }, remoteAddress))
        ResponseEntity.ok(null)
    }, {
        logger.error("cannot register printers", it)
        ResponseEntity(HttpStatus.BAD_REQUEST)
    })
}