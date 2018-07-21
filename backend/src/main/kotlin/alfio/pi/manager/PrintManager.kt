package alfio.pi.manager

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(PrintManager::class.java)

interface PrintManager {
}

open class LocalPrintManager(): PrintManager {
}

data class ConfigurableLabelContent(val firstRow: String,
                                    val secondRow: String,
                                    val thirdRow: String,
                                    val qrContent: String,
                                    val partialID: String)