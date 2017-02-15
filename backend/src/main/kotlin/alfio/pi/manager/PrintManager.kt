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

import alfio.pi.model.Printer
import alfio.pi.model.SystemPrinter
import alfio.pi.model.Ticket
import alfio.pi.model.User
import alfio.pi.repository.PrinterRepository
import alfio.pi.repository.UserPrinterRepository
import alfio.pi.wrapper.tryOrDefault
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

private val logger = LoggerFactory.getLogger(PrintManager::class.java)

interface PrintManager {
    fun printLabel(user: User, ticket: Ticket): Boolean
    fun reprintLabel(printer: Printer, ticket: Ticket): Boolean
    fun printTestLabel(printer: Printer): Boolean
    fun getAvailablePrinters(): List<SystemPrinter>
}

@Component
@Profile("printer")
open class LocalPrintManager : PrintManager {

    override fun printLabel(user: User, ticket: Ticket): Boolean {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun reprintLabel(printer: Printer, ticket: Ticket): Boolean {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun printTestLabel(printer: Printer): Boolean {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAvailablePrinters(): List<SystemPrinter> = getConnectedPrinters()
}

@Component
@Profile("server")
open class RemotePrintManager: PrintManager {

    override fun printLabel(user: User, ticket: Ticket): Boolean {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun reprintLabel(printer: Printer, ticket: Ticket): Boolean {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun printTestLabel(printer: Printer): Boolean {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAvailablePrinters(): List<SystemPrinter> {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

@Component
@Profile("full")
open class CupsPrintManager(val userPrinterRepository: UserPrinterRepository,
                            val labelTemplates: List<LabelTemplate>,
                            val printerRepository: PrinterRepository) : PrintManager {

    override fun printLabel(user: User, ticket: Ticket): Boolean {
        return tryOrDefault<Boolean>().invoke({
            userPrinterRepository.getOptionalActivePrinter(user.id)
                .map { printerRepository.findById(it.printerId) }
                .map { printer ->
                    val labelTemplate = labelTemplates.first()
                    doPrint(labelTemplate, printer, ticket)
                }.orElse(false)

        }, {
            logger.error("cannot print label for ticket ${ticket.uuid}, username ${user.username}", it)
            false
        })
    }

    override fun reprintLabel(printer: Printer, ticket: Ticket): Boolean {
        return tryOrDefault<Boolean>().invoke({
            doPrint(labelTemplates.first(), printer, ticket)
        }, {
            logger.error("cannot reprint label for ticket ${ticket.uuid}, printer ${printer.name}", it)
            false
        })
    }

    override fun printTestLabel(printer: Printer): Boolean {
        return tryOrDefault<Boolean>().invoke({
            doPrint(labelTemplates.first(), printer, Ticket("TEST-TEST-TEST", "FirstName", "LastName", null, "Test Company Ltd."))
        }, {
            logger.error("cannot print test label", it)
            false
        })
    }

    override fun getAvailablePrinters(): List<SystemPrinter> = getCupsPrinters()

}

internal fun doPrint(labelTemplate: LabelTemplate, printer: Printer, ticket: Ticket): Boolean {
    val pdf = generatePDFLabel(ticket.firstName, ticket.lastName, ticket.company.orEmpty(), ticket.uuid).invoke(labelTemplate)
    val cmd = "/usr/bin/lpr -U anonymous -P ${printer.name} -# 1 -T ticket-${ticket.uuid.substringBefore("-")} -h -o media=${labelTemplate.getCUPSMediaName()}"
    logger.trace(cmd)
    val print = Runtime.getRuntime().exec(cmd)
    print.outputStream.use {
        it.write(pdf)
    }
    return print.waitFor(1L, TimeUnit.SECONDS) && print.exitValue() == 0
}

private val systemPrinterExtractor = Regex("printer (\\S+) .*")

private fun getCupsPrinters(): List<SystemPrinter> = tryOrDefault<List<SystemPrinter>>().invoke({
    val process = Runtime.getRuntime().exec("/usr/bin/lpstat -p")
    process.inputStream.use {
        it.bufferedReader().lines()
            .map {
                val result = systemPrinterExtractor.find(it)
                result?.groupValues?.get(1)
            }.filter { it != null }
            .map({ SystemPrinter(it!!) })
            .collect(Collectors.toList<SystemPrinter>())
    }
}, {
    logger.error("cannot load printers", it)
    mutableListOf()
})

private fun getConnectedPrinters(): List<SystemPrinter> = tryOrDefault<List<SystemPrinter>>().invoke({
    Paths.get("/dev/usb/")
        .filter { it.fileName.toString().startsWith("Alfio") }
        .map { SystemPrinter(it.fileName.toString()) }

}, {
    logger.error("cannot load local printers", it)
    listOf()
})

