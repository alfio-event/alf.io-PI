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

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.util.Matrix
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*
import kotlin.math.absoluteValue

interface LabelTemplate {
    fun getPageDimensions(): PDRectangle
    fun writeContent(stream: PDPageContentStream, pageWidth: Float, labelContent: LabelContent, fontLoader: (InputStream) -> PDFont)
    fun getDescription(): String
    fun getCUPSMediaName(): String
    fun supportsPrinter(name: String): Boolean
}

class LabelContent(val firstRow: String, val secondRow: String, val additionalRows: List<String>?, val qrCode: PDImageXObject, val qrText: String, val checkbox: Boolean)

@Component
open class DymoLW450Turbo41x89: LabelTemplate {

    override fun getCUPSMediaName(): String = "w118h252"

    override fun getDescription(): String = "Dymo LabelWriter 450 Turbo - 41x89 mm (S0722560 / 11356)"

    override fun getPageDimensions(): PDRectangle = PDRectangle(convertMMToPoint(41F), convertMMToPoint(89F))

    override fun writeContent(stream: PDPageContentStream,
                              pageWidth: Float,
                              labelContent: LabelContent,
                              fontLoader: (InputStream) -> PDFont) {
        val font = fontLoader.invoke(DymoLW450Turbo41x89::class.java.getResourceAsStream("/font/DejaVuSansMono.ttf"))
        stream.use {
            it.transform(Matrix(0F, 1F, -1F, 0F, pageWidth, 0F))
            val firstRowContent = optimizeText(labelContent.firstRow, arrayOf(10 to 24F, 11 to 22F, 12 to 20F, 14 to 18F), true)
            it.setFont(font, firstRowContent.second)
            it.beginText()
            it.newLineAtOffset(10F, 70F)
            it.showText(firstRowContent.first)
            val secondRowContent = optimizeText(labelContent.secondRow, arrayOf(15 to 16F, 17 to 14F), true)

            it.setFont(font, secondRowContent.second)
            it.newLineAtOffset(0F, -20F)
            it.showText(secondRowContent.first)

            val checkboxChars = when(labelContent.checkbox) {
                true -> 2
                false -> 0
            }
            val maxLengthAdditionalRows = arrayOf(23 - checkboxChars to 10F, 28 - checkboxChars to 9F, 32 - checkboxChars to 8F, 38 - checkboxChars to 7F, 43 - checkboxChars to 6F)
            val offset = if(labelContent.additionalRows?.size ?: 0 > 1) {
                -17F
            } else {
                -20F
            }
            val additionalRows = labelContent.additionalRows.orEmpty().take(2)
            printAdditionalRows(additionalRows, it, offset, labelContent, font, maxLengthAdditionalRows)

            it.endText()
            it.drawImage(labelContent.qrCode, 170F, 30F, 65F, 65F)
            it.setFont(font, 9F)
            it.beginText()
            it.newLineAtOffset(180F, 15F)
            it.showText(labelContent.qrText)
        }
    }

    override fun supportsPrinter(name: String): Boolean = name.matches(Regex("^Alfio(-DYM)?-[A-Z0-9]+$"))
}

private fun printAdditionalRows(additionalRows: List<String>, it: PDPageContentStream, offset: Float, labelContent: LabelContent, font: PDFont, maxLengthAdditionalRows: Array<Pair<Int, Float>>) {
    val rowsToPrint = if(additionalRows.isEmpty() && labelContent.checkbox) {
        arrayListOf("")
    } else {
        additionalRows
    }
    rowsToPrint.forEachIndexed { index, content ->
        it.newLineAtOffset(0F, offset)
        val displayCheckbox = index == rowsToPrint.size - 1 && labelContent.checkbox
        if (displayCheckbox) {
            it.setFont(font, offset.absoluteValue)
            it.showText("\u2610")
        }
        val optimizedContent = optimizeText(content, maxLengthAdditionalRows, true)
        it.setFont(font, optimizedContent.second)
        val text = if (displayCheckbox) {
            " ${optimizedContent.first}"
        } else {
            optimizedContent.first
        }
        it.showText(text)
    }
}

@Component
open class ZebraZD410: LabelTemplate {

    override fun getCUPSMediaName(): String = "w162h288"//"oe_w162h288_2.25x4in"

    override fun getDescription(): String = "Zebra ZD410 - 57x102 mm (800262-405)"

    override fun getPageDimensions(): PDRectangle = PDRectangle(convertMMToPoint(57F), convertMMToPoint(102F))

    override fun writeContent(stream: PDPageContentStream,
                              pageWidth: Float,
                              labelContent: LabelContent,
                              fontLoader: (InputStream) -> PDFont) {
        val font = fontLoader.invoke(ZebraZD410::class.java.getResourceAsStream("/font/DejaVuSansMono.ttf"))
        stream.use {
            it.transform(Matrix(0F, 1F, -1F, 0F, pageWidth, 0F))
            val firstRowContent = optimizeText(labelContent.firstRow, arrayOf(11 to 26F, 12 to 24F, 14 to 22F, 15 to 20F, 17 to 18F), true)
            it.setFont(font, firstRowContent.second)
            it.beginText()
            it.newLineAtOffset(10F, 115F)
            it.showText(firstRowContent.first)
            val secondRowContent = optimizeText(labelContent.secondRow, arrayOf(16 to 18F, 18 to 16F, 21 to 14F), true)

            it.setFont(font, secondRowContent.second)
            it.newLineAtOffset(0F, -25F)
            it.showText(secondRowContent.first)

            val maxLengthAdditionalRows = arrayOf(29 to 10F)
            printAdditionalRows(labelContent.additionalRows.orEmpty().take(3), it, -20F, labelContent, font, maxLengthAdditionalRows)

            it.endText()

            it.drawImage(labelContent.qrCode, 195F, 50F, 80F, 80F)
            it.setFont(font, 9F)
            it.beginText()
            it.newLineAtOffset(210F, 25F)
            it.showText(labelContent.qrText)
        }
    }

    override fun supportsPrinter(name: String): Boolean = name.startsWith("Alfio-ZBR-")
}

@Component
open class BixolonTX220: ZebraZD410() {

    override fun getCUPSMediaName(): String = "oe_13-x-50-d-8-mmy-101-d-6-mm_2x4in"//"oe_w162h288_2.25x4in"

    override fun getDescription(): String = "Bixolon SLP-TX220 - 57x102 mm (Zebra 800262-405)"

    override fun supportsPrinter(name: String): Boolean = name.startsWith("Alfio-BXL-")
}

private val controlCharsFinder = Regex("\\p{C}")

internal fun optimizeText(content: String, maxLengthForSize: Array<Pair<Int, Float>>, compactText: Boolean = false): Pair<String, Float> {
    val text = content.trim().replace(controlCharsFinder, "")
    val options = maxLengthForSize.size
    val sizes = maxLengthForSize.mapIndexed {
        i, (maxLength, fontSize) -> checkTextLength(compactText, text, fontSize, maxLength, i == options - 1)
    }
    return Optional.ofNullable(sizes.firstOrNull { it.first })
        .map { it!!.second to it.third }
        .orElseGet {
            val conf = maxLengthForSize[maxLengthForSize.size - 1]
            text.substring(0 until conf.first).trim() to conf.second
        }
}

private fun checkTextLength(compactText: Boolean, content: String, fontSize: Float, maxLength: Int, heavyCompact: Boolean): Triple<Boolean, String, Float> {
    val text = if (content.length <= maxLength || !compactText) {
        content
    } else {
        val res = compact(content, maxLength, true)
        if(heavyCompact && res.length > maxLength) {
            compact(content, maxLength, false)
        } else {
            res
        }
    }

    return if (text.length <= maxLength) {
        Triple(true, text, fontSize)
    } else {
        Triple(false, text, fontSize)
    }
}

private fun compact(text: String, maxLength: Int, lightOnly: Boolean = false): String {
    val lightCompactSeq = text.trim()
        .splitToSequence(" ")
        .map { it.trim() }
        .map { txt -> if(commonAffixes.any { affix -> affix == txt.toLowerCase() }) {
            "${txt.substring(0, 1)}."
        } else { "$txt " }}

    return if(!lightOnly) {
        var difference = lightCompactSeq.joinToString("").length - maxLength
        val original = lightCompactSeq.toList()
        val upsideDown = original.reversed().mapIndexed { i, part ->
            if(difference > 0 && i < original.size-1) {
                val newPart = if(part.trim().length > 2) {
                    "${part.substring(0,1)}."
                } else {
                    part
                }
                difference -= (part.length - newPart.length)
                newPart
            } else {
                part
            }
        }
        upsideDown.reversed().asSequence()
    } else {
        lightCompactSeq
    }.joinToString("").trim()
}


fun generatePDFLabel(firstRow: String,
                     secondRow: String,
                     additionalRows: List<String>,
                     ticketUUID: String,
                     qrCodeContent: String = ticketUUID,
                     partialUUID: String,
                     checkbox: Boolean): (LabelTemplate) -> ByteArray = { template ->
    val document = PDDocument()
    val out = ByteArrayOutputStream()
    document.use { pdDocument ->
        val page = PDPage(template.getPageDimensions())
        val pageWidth = page.mediaBox.width
        pdDocument.addPage(page)
        val qr = LosslessFactory.createFromImage(pdDocument, generateQRCode(qrCodeContent))
        val contentStream = PDPageContentStream(pdDocument, page, PDPageContentStream.AppendMode.OVERWRITE, false)
        template.writeContent(contentStream, pageWidth, LabelContent(firstRow, secondRow, additionalRows, qr, partialUUID, checkbox)) {PDType0Font.load(document, it)}
        pdDocument.save(out)
    }
    out.toByteArray()
}

private val convertMMToPoint: (Float) -> Float = {
    it * (1 / (10 * 2.54f) * 72)
}

private fun generateQRCode(value: String): BufferedImage {
    val matrix = generateBitMatrix(value, 1, 1)
    return MatrixToImageWriter.toBufferedImage(matrix)
}

private fun generateBitMatrix(value: String, width: Int, height: Int): BitMatrix {
    val hintMap = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
    hintMap[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
    hintMap[EncodeHintType.MARGIN] = 0
    return MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, width, height, hintMap)
}

fun generateQRCodeImage(value: String): ByteArray {
    val output = ByteArrayOutputStream()
    return output.use { MatrixToImageWriter.writeToStream(generateBitMatrix(value, 350, 350), "png", it); it.toByteArray() }
}

private val commonAffixes = listOf(
    "Abu",
    "Ālam",
    "Bar",
    "Bath",
    "bat",
    "Ben",
    "bin",
    "ibn",
    "Bet",
    "Bint",
    "Das",
    "Degli",
    "Delle",
    "Del",
    "Della",
    "Der",
    "Dos",
    "Fetch",
    "Vetch",
    "Fitz",
    "Kil",
    "Gil",
    "Mac",
    "Mck",
    "Mhic",
    "Mic",
    "Mala",
    "Neder",
    "Nic",
    "Nin",
    "Nord",
    "Norr",
    "Öfver",
    "Ost",
    "öst",
    "öster",
    "øst",
    "Över",
    "Pour",
    "Stor",
    "Söder",
    "Ter",
    "Ter",
    "Tre",
    "Van",
    "Väster",
    "Vest",
    "von",
    "Woj").map { it.toLowerCase() }