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
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

internal const val STATIC_TEXT_PREFIX = "static-text:"
private const val MDNS_NAME = "alfio-server"

private val logger = LoggerFactory.getLogger(PrintManager::class.java)

interface PrintManager {
    fun printLabel(printer: Printer, ticket: Ticket, labelConfiguration: LabelConfigurationAndContent?): Boolean
    fun getAvailablePrinters(): List<SystemPrinter>
    fun printLabel(user: User, ticket: Ticket, labelConfiguration: LabelConfigurationAndContent?): Boolean
    fun printTestLabel(printer: Printer): Boolean
    fun getLabelContent(ticket: Ticket, labelConfiguration: LabelConfiguration?): ConfigurableLabelContent
    fun shutdown() {}
}

@Component
@Profile("printer")
open class PrinterAnnouncer(private val trustManager: X509TrustManager,
                            private val httpClient: OkHttpClient,
                            private val printManager: PrintManager,
                            private val gson: Gson) {

    private val masterUrl = AtomicReference<String>()

    init {

        val jmdns = JmDNS.create(InetAddress.getLocalHost())
        jmdns.addServiceListener("_http._tcp.local.", object: ServiceListener {
            override fun serviceRemoved(event: ServiceEvent?) {
                if (MDNS_NAME == event?.info?.name) {
                    logger.info("master has been removed... ${event.info}")
                }
            }

            override fun serviceAdded(event: ServiceEvent?) {
            }

            override fun serviceResolved(event: ServiceEvent?) {
                if (MDNS_NAME == event?.info?.name)  {
                    val resolvedMasterUrl = event.info.getPropertyString("url")
                    logger.info("Resolved master url: $resolvedMasterUrl")
                    masterUrl.set(resolvedMasterUrl)
                }
            }
        })
        Executors.newScheduledThreadPool(1).scheduleWithFixedDelay({tryOrDefault<Unit>().invoke({uploadPrinters()},{logger.error("error while uploading printers", it)})}, 0, 5, TimeUnit.SECONDS)
    }

    open fun uploadPrinters() {
        val url = masterUrl.get() ?: return
        logger.trace("calling master $url")
        val httpClient = httpClientBuilderWithCustomTimeout(1L to TimeUnit.SECONDS)
            .invoke(httpClient)
            .trustKeyStore(trustManager)
            .build()
        val request = Request.Builder()
            .url("$url/api/printers/register")
            .post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(printManager.getAvailablePrinters())))
            .build()
        val result = httpClient.newCall(request).execute().use { resp ->
            logger.trace("response status: ${resp.code()}")
            resp.isSuccessful
        }
        if(!result) {
            logger.warn("cannot upload printer list...")
        }
    }

}

@Component
@Profile("printer")
open class LocalPrintManager(private val labelTemplates: List<LabelTemplate>,
                             private val publisher : SystemEventHandler,
                             private val kvStore: KVStore): PrintManager {

    override fun getLabelContent(ticket: Ticket, labelConfiguration: LabelConfiguration?): ConfigurableLabelContent = buildConfigurableLabelContent(labelConfiguration?.layout, ticket)

    override fun printLabel(printer: Printer, ticket: Ticket, labelConfiguration: LabelConfigurationAndContent?): Boolean {
        return tryOrDefault<Boolean>().invoke({
            val localPrinter = getCupsPrinters().firstOrNull { it.name == printer.name }
            if(localPrinter != null) {
                logger.trace("local printer found.")
                doPrint(labelTemplates.first { it.supportsPrinter(printer.name, labelConfiguration?.configuration?.layout) }, printer.name, ticket, labelConfiguration)
            } else {
                logger.warn("cannot find local printer ${printer.name}")
                false
            }
        }, {
            logger.error("cannot print label for ticket ${ticket.uuid}, local printer ${printer.name}", it)
            false
        })
    }

    override fun getAvailablePrinters(): List<SystemPrinter> = getCupsPrinters()

    override fun printLabel(user: User, ticket: Ticket, labelConfiguration: LabelConfigurationAndContent?): Boolean = false

    override fun printTestLabel(printer: Printer): Boolean = printLabel(printer, Ticket("TEST-TEST-TEST", "FirstName", "LastName", null, mapOf("company" to "Test Company Ltd."), pin = "123456"), null)

    private val systemPrinterExtractor = Regex("printer (Alfio\\S+) .*")

    private fun getCupsPrinters(includeNotConnected: Boolean = false): List<SystemPrinter> = tryOrDefault<List<SystemPrinter>>().invoke({
        val process = Runtime.getRuntime().exec("/usr/bin/lpstat -p")
        process.inputStream.use { inputStream ->
            inputStream.bufferedReader().lines()
                .map {
                    val result = systemPrinterExtractor.find(it)
                    val extractedName = result?.groupValues?.get(1)
                    logger.trace("Processing output from lpstat: $it")
                    logger.trace("--> Printer name is: $extractedName")
                    extractedName
                }.filter { it != null }
                .filter { includeNotConnected || Files.exists(Paths.get("/dev/usb/", it)) }
                .map {
                    logger.trace("Printer $it is connected. Returning it")
                    SystemPrinter(it!!)
                }
                .collect(Collectors.toList<SystemPrinter>())
        }
    }, {
        logger.error("cannot load printers", it)
        mutableListOf()
    })

    private fun doPrint(labelTemplate: LabelTemplate, name: String, ticket: Ticket, labelConfiguration: LabelConfigurationAndContent?): Boolean {
        val configurableContent = if(labelConfiguration?.content != null) {
            logger.trace("label configuration is present. returning content {}", labelConfiguration.content)
            labelConfiguration.content
        } else {
            logger.trace("label configuration is not present. Building a new one")
            val newContent = buildConfigurableLabelContent(labelConfiguration?.configuration?.layout, ticket)
            logger.trace("done. {}", newContent)
            newContent
        }
        val pdf = generatePDFLabel(configurableContent.firstRow, configurableContent.secondRow, configurableContent.additionalRows.orEmpty(), ticket.uuid, configurableContent.qrContent, configurableContent.partialID, configurableContent.pin, configurableContent.checkbox).invoke(labelTemplate)
        val cmd = "/usr/bin/lpr -U anonymous -P $name -# 1 -T ticket-${ticket.uuid.substringBefore("-")} -h -o media=${labelTemplate.getCUPSMediaName()}"
        logger.trace("about to submit printing job: {}", cmd)
        val print = Runtime.getRuntime().exec(cmd)
        print.outputStream.use {
            logger.trace("sending pdf to printer...")
            it.write(pdf)
            logger.trace("done")
        }
        val res = print.waitFor(1L, TimeUnit.SECONDS) && print.exitValue() == 0

        if(res) {
            logger.trace("printing job submitted.")
            val labels = kvStore.getRemainingLabels(name)
            if(labels > 0) {
                logger.trace("decreasing label count")
                val updatedCount = labels - 1
                kvStore.putRemainingLabels(name, updatedCount)
                publisher.notifyAllSessions(SystemEvent(SystemEventType.UPDATE_PRINTER_REMAINING_LABEL_COUNTER, UpdatePrinterRemainingLabelCounter(updatedCount)))
            }
        }

        return res
    }

    internal fun buildConfigurableLabelContent(layout: LabelLayout?, ticket: Ticket): ConfigurableLabelContent {
        return if (layout != null) {
            val qrContent = sequenceOf(ticket.uuid).plus(retrieveQRCodeContent(layout, ticket)).joinToString(separator = layout.qrCode.infoSeparator)
            val partialID = if (layout.general.printPartialID) {
                ticket.uuid.substringBefore('-').toUpperCase()
            } else {
                ""
            }
            val ticketInfo = getAllTicketInfo(ticket)
            val firstRow = ticketInfo[layout.content.firstRow] ?: ticket.firstName
            val secondRow = ticketInfo[layout.content.secondRow] ?: ticket.lastName
            ConfigurableLabelContent(firstRow, secondRow, retrieveAllAdditionalInfo(layout, ticket), qrContent, partialID, ticket.pin, layout.content.checkbox?:false)
        } else {
            ConfigurableLabelContent(ticket.firstName, ticket.lastName, listOf(ticket.additionalInfo?.get("company").orEmpty()), ticket.uuid, ticket.uuid.substringBefore('-').toUpperCase(), ticket.pin, false)
        }
    }

    private fun retrieveAllAdditionalInfo(layout: LabelLayout, ticket: Ticket) : List<String> {
        return layout.content.thirdRow.orEmpty().asSequence()
            .plus(layout.content.additionalRows.orEmpty())
            .filter { it.isNotEmpty() }
            .map {
                if(it.startsWith(STATIC_TEXT_PREFIX)) {
                    it.substringAfter(":", "")
                } else {
                    ticket.additionalInfo?.get(it).orEmpty()
                }
            }.toList()
    }

    private fun retrieveQRCodeContent(layout: LabelLayout, ticket: Ticket) : List<String> {
        val info = getAllTicketInfo(ticket)
        return layout.qrCode.additionalInfo.map { info[it].orEmpty() }
    }

    private fun getAllTicketInfo(ticket: Ticket) = ticket.additionalInfo.orEmpty().plus(getTicketBasicInfo(ticket))

    private fun getTicketBasicInfo(ticket: Ticket) = listOf("firstName" to ticket.firstName,
            "lastName" to ticket.lastName,
            "fullName" to ticket.fullName,
            "emailAddress" to ticket.email)

    override fun shutdown() {
        logger.info("Shutting down PrintManager. Removing printers...")
        getCupsPrinters(true).forEach {
            logger.info("Removing $it")
            Runtime.getRuntime().exec("/usr/sbin/lpadmin -x $it").waitFor()
        }
    }
}

/**
 * Here we assume that both server and printer instances are using the same SSL certificate.
 * Will be improved in the next releases.
 */
@Component
@Profile("server", "full")
open class FullPrintManager(private val httpClient: OkHttpClient,
                            labelTemplates: List<LabelTemplate>,
                            private val userPrinterRepository: UserPrinterRepository,
                            private val printerRepository: PrinterRepository,
                            private val gson: Gson,
                            private val trustManager: X509TrustManager,
                            publisher : SystemEventHandler,
                            private val environment: Environment,
                            kvStore: KVStore): LocalPrintManager(labelTemplates, publisher, kvStore) {

    private val remotePrinters = CopyOnWriteArraySet<RemotePrinter>()

    private fun retrieveRegisteredPrinter(user: User): Optional<Printer> {
        val printer = userPrinterRepository.getOptionalActivePrinter(user.id).map { printerRepository.findById(it.printerId) }
        return when {
            printer.isPresent -> printer
            environment.acceptsProfiles("desk") -> Optional.ofNullable(super.getAvailablePrinters().firstOrNull()).map { Printer(-1, it.name, null, true) }
            else -> Optional.empty()
        }
    }

    override fun printLabel(user: User, ticket: Ticket, labelConfiguration: LabelConfigurationAndContent?): Boolean {
        logger.trace("entering printLabel")
        val registeredPrinter = retrieveRegisteredPrinter(user)
        return if(registeredPrinter.isPresent) {
            val printer = registeredPrinter.get()
            printLabel(printer, ticket, labelConfiguration)
        } else {
            logger.debug("can't find printer for user ${user.username}")
            false
        }
    }

    override fun getAvailablePrinters(): List<SystemPrinter> {
        val availablePrinters = super.getAvailablePrinters().toMutableList()
        availablePrinters.addAll(remotePrinters.map { SystemPrinter(it.name) })
        return availablePrinters
    }

    override fun printLabel(printer: Printer, ticket: Ticket, labelConfiguration: LabelConfigurationAndContent?): Boolean =
        if(remotePrinters.any { it.name == printer.name }) {
            logger.trace("Sending data to remote printer with name ${printer.name}")
            remotePrint(printer.name, ticket)
        } else {
            logger.trace("Sending data to local printer with name ${printer.name}")
            super.printLabel(printer, ticket, labelConfiguration)
        }

    private fun remotePrint(printerName: String, ticket: Ticket): Boolean {
        logger.trace("remote print $printerName for ticket $ticket")
        val remotePrinter = remotePrinters.firstOrNull { it.name == printerName }
        logger.trace("remote print $printerName for ticket $ticket: remote printer is $remotePrinter")
        return if(remotePrinter != null) {
            val httpClient = httpClientBuilderWithCustomTimeout(500L to TimeUnit.MILLISECONDS)
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

    @EventListener(PrintersRegistered::class)
    open fun onPrinterAdded(event: PrintersRegistered) {
        logger.trace("received ${event.printers.size} printers from ${event.remoteHost}")
        val existing = remotePrinters.filter { it.remoteHost == event.remoteHost }
        logger.trace("saved printers: $remotePrinters")
        if(event.printers.none() || event.printers.asSequence().map { it.name }.filter { name -> existing.none { it.name == name } }.any()) {
            logger.info("adding ${event.printers} for ${event.remoteHost}")
            remotePrinters.removeAll(existing)
            remotePrinters.addAll(event.printers)
        }
    }
}

fun OkHttpClient.Builder.trustKeyStore(trustManager: X509TrustManager): OkHttpClient.Builder {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf(trustManager), null)
    this.sslSocketFactory(sslContext.socketFactory, trustManager)
    this.hostnameVerifier { _, _ -> true }//FIXME does it make sense to validate the hostname if we share the same certificate across all devices?
    return this
}

data class ConfigurableLabelContent(val firstRow: String,
                                    val secondRow: String,
                                    val additionalRows: List<String>?,
                                    val qrContent: String,
                                    val partialID: String,
                                    val pin: String?,
                                    val checkbox: Boolean)



