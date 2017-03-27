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
import alfio.pi.repository.PrinterRepository
import alfio.pi.repository.UserPrinterRepository
import alfio.pi.wrapper.tryOrDefault
import com.google.gson.Gson
import okhttp3.*
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener


private val logger = LoggerFactory.getLogger(PrintManager::class.java)

interface PrintManager {
    fun printLabel(printer: Printer, ticket: Ticket): Boolean
    fun getAvailablePrinters(): List<SystemPrinter>
    fun printLabel(user: User, ticket: Ticket): Boolean
    fun printTestLabel(printer: Printer): Boolean
}

@Component
@Profile("printer")
open class LocalPrintManager(val labelTemplates: List<LabelTemplate>,
                             val trustManager: X509TrustManager,
                             val httpClient: OkHttpClient,
                             val gson: Gson) : PrintManager {

    private val masterUrl = AtomicReference<String>()

    private val MDNS_NAME = "alfio-server"

    init {

        val jmdns = JmDNS.create(InetAddress.getLocalHost())
        jmdns.addServiceListener("_http._tcp.local.", object: ServiceListener {
            override fun serviceRemoved(event: ServiceEvent?) {
                if (MDNS_NAME.equals(event?.info?.name)) {
                    masterUrl.set(null);
                }
            }

            override fun serviceAdded(event: ServiceEvent?) {
            }

            override fun serviceResolved(event: ServiceEvent?) {
                if (MDNS_NAME.equals(event?.info?.name))  {
                    val resolvedMasterUrl = event?.info?.getPropertyString("url");
                    logger.info("Resolved master url: " + resolvedMasterUrl)
                    masterUrl.set(resolvedMasterUrl);
                }
            }
        })
    }

    override fun printLabel(user: User, ticket: Ticket): Boolean = false

    override fun printTestLabel(printer: Printer): Boolean = false

    override fun printLabel(printer: Printer, ticket: Ticket): Boolean =
        if(getAvailablePrinters().any { it.name == printer.name }) {
            doPrint(labelTemplates.first(), printer, ticket)
        } else {
            false
        }

    override fun getAvailablePrinters(): List<SystemPrinter> = getConnectedPrinters()

    @Scheduled(fixedDelay = 10000L)
    open fun uploadPrinters() {
        val url = masterUrl.get();
        if(url == null) {
            return;
        }

        val httpClient = httpClientBuilderWithCustomTimeout(1L, TimeUnit.SECONDS)
            .invoke(httpClient)
            .trustKeyStore(trustManager)
            .build()
        val request = Request.Builder()
            .url("${url}/api/printers/register")
            .post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(getAvailablePrinters())))
            .build()
        val result = httpClient.newCall(request).execute().use { resp -> resp.isSuccessful }
        if(!result) {
            logger.warn("cannot upload printer list...")
        }

    }
}

/**
 * Here we assume that both server and printer instances are using the same SSL certificate.
 * Will be improved in the next releases.
 */
@Component
@Profile("server")
open class RemotePrintManager(val httpClient: OkHttpClient,
                              val userPrinterRepository: UserPrinterRepository,
                              val printerRepository: PrinterRepository,
                              val gson: Gson,
                              val trustManager: X509TrustManager): PrintManager {

    val printers = CopyOnWriteArraySet<RemotePrinter>()


    override fun printLabel(user: User, ticket: Ticket): Boolean =
        userPrinterRepository.getOptionalActivePrinter(user.id)
            .map { printerRepository.findById(it.printerId) }
            .map { printer -> tryOrDefault<Boolean>().invoke({remotePrint(printer.name, ticket)}, {false}) }
            .orElse(false)

    private fun remotePrint(printerName: String, ticket: Ticket): Boolean {
        val remotePrinter = printers.filter { it.name == printerName }.firstOrNull()
        return if(remotePrinter != null) {
            val httpClient = httpClientBuilderWithCustomTimeout(500L, TimeUnit.MILLISECONDS)
                .invoke(httpClient)
                .trustKeyStore(trustManager)
                .build()
            logger.info("calling ${remotePrinter.remoteHost}")
            val request = Request.Builder()
                .addHeader("Authorization", Credentials.basic("printer", "printer"))
                .url("https://${remotePrinter.remoteHost}:8443/api/printers/${remotePrinter.name}/print")
                .post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(ticket)))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                logger.debug("result: ${resp.code()} ${resp.message()}")
                resp.isSuccessful
            }
        } else {
            logger.debug("can't find printer $printerName")
            false
        }
    }

    override fun printLabel(printer: Printer, ticket: Ticket): Boolean = tryOrDefault<Boolean>().invoke({remotePrint(printer.name, ticket)}, {false})

    override fun printTestLabel(printer: Printer): Boolean = remotePrint(printer.name, Ticket("TEST-TEST-TEST", "FirstName", "LastName", null, "Test Company Ltd."))

    override fun getAvailablePrinters(): List<SystemPrinter> = printers.map { SystemPrinter(it.name) }

    @EventListener(PrintersRegistered::class)
    open fun onPrinterAdded(event: PrintersRegistered) {
        logger.trace("received ${event.printers.size} printers from ${event.remoteHost}")
        val existing = printers.filter { it.remoteHost == event.remoteHost }
        logger.trace("saved printers: $printers")
        if(event.printers.none() || event.printers.map { it.name }.filter { name -> existing.none { it.name == name } }.any()) {
            logger.info("adding ${event.printers} for ${event.remoteHost}")
            printers.removeAll(existing)
            printers.addAll(event.printers)
        }
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

    override fun printLabel(printer: Printer, ticket: Ticket): Boolean {
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
    Files.newDirectoryStream(Paths.get("/dev/usb/"))
        .filter { it.fileName.toString().startsWith("Alfio") }
        .map { SystemPrinter(it.fileName.toString()) }
}, {
    logger.error("cannot load local printers", it)
    listOf()
})

fun OkHttpClient.Builder.trustKeyStore(trustManager: X509TrustManager): OkHttpClient.Builder {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf(trustManager), null)
    this.sslSocketFactory(sslContext.socketFactory, trustManager)
    this.hostnameVerifier { hostname, sslSession -> true }//FIXME does it make sense to validate the hostname if we share the same certificate across all devices?
    return this
}

