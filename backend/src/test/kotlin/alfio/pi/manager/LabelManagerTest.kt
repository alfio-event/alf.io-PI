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

import alfio.pi.model.LabelLayout
import alfio.pi.model.Ticket
import alfio.pi.repository.ConfigurationRepository
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.util.*

class LabelManagerTest {
    private val maxLengthForSize = arrayOf(11 to 24F, 12 to 22F, 13 to 20F, 15 to 18F)

    @Test
    fun testOptimizeTextLength() {
        assertEquals("12345678901" to 24F, optimizeText("12345678901", maxLengthForSize, true))
        assertEquals("123456789012" to 22F, optimizeText("123456789012", maxLengthForSize, true))
        assertEquals("1234567890123" to 20F, optimizeText("1234567890123", maxLengthForSize, true))
        assertEquals("123456789012345" to 18F, optimizeText("12345678901234567890", maxLengthForSize, true))
    }

    @Test
    fun testCompact() {
        assertEquals("George William" to 18F, optimizeText("George William", maxLengthForSize, true))
        assertEquals("George William" to 18F, optimizeText("George William", maxLengthForSize, false))
        assertEquals("George W.H." to 18F, optimizeText("George William Henry", maxLengthForSize, true))
        assertEquals("George William" to 18F, optimizeText("George William Henry Arthur", maxLengthForSize, false))
        assertEquals("George W.H.A." to 18F, optimizeText("George William Henry Arthur", maxLengthForSize, true))
        assertEquals("George V.d.M." to 18F, optimizeText("George Van der Meyde", maxLengthForSize, true))
        assertEquals("V.d.Bellen" to 24F, optimizeText("Van der Bellen", maxLengthForSize, true))
        assertEquals("Serbelloni Mazz" to 18F, optimizeText("Serbelloni Mazzanti Vien Dal Mare", maxLengthForSize, true)) //FIXME should be just "Serbelloni"
    }

    @Test
    fun testGenerateLabel() {
        val bytes = generatePDFLabel("George", "William", "Test Company", "12345678", UUID.randomUUID().toString(), "12345678").invoke(DymoLW450Turbo41x89())
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun testGenerateLabelWitLayoutNull() {
        val localPrintManager = LocalPrintManager(emptyList(), Mockito.mock(SystemEventHandler::class.java), Mockito.mock(KVStore::class.java))
        val ticket1 = ticket()
        val result = localPrintManager.buildConfigurableLabelContent(null, ticket1)
        assertEquals(ticket1.firstName, result.firstRow)
        assertEquals(ticket1.lastName, result.secondRow)
        assertEquals(ticket1.additionalInfo!!["company"], result.thirdRow)
        assertEquals(ticket1.uuid, result.qrContent)
        assertEquals(ticket1.uuid.substringBefore('-'), result.partialID)

        val ticket2 = ticket(null)
        val result2 = localPrintManager.buildConfigurableLabelContent(null, ticket2)
        assertEquals(ticket2.firstName, result.firstRow)
        assertEquals(ticket2.lastName, result.secondRow)
        assertEquals("", result2.thirdRow)
        assertEquals(ticket2.uuid, result2.qrContent)
        assertEquals(ticket2.uuid.substringBefore('-'), result2.partialID)
    }

    @Test
    fun testGenerateLabelWithLayoutNotNull() {
        val localPrintManager = LocalPrintManager(emptyList(), Mockito.mock(SystemEventHandler::class.java), Mockito.mock(KVStore::class.java))
        val jsonString = """
            {
              "qrCode": {
                "additionalInfo": ["word1","word2","word3"],
                "infoSeparator": "::"
              },
              "content": {
                "thirdRow": ["word1","word2","word3"]
              },
              "general": {
                "printPartialID": false
              }
            }"""
        val labelLayout = Gson().fromJson(jsonString, LabelLayout::class.java)
        val ticket = ticket(mapOf("word1" to "thisIsTheFirstWord", "word2" to "thisIsTheSecondWord", "word3" to "thisIsTheThirdWord"))
        val result = localPrintManager.buildConfigurableLabelContent(labelLayout, ticket)
        assertEquals(ticket.firstName, result.firstRow)
        assertEquals(ticket.lastName, result.secondRow)
        assertEquals("thisIsTheFirstWord thisIsTheSecondWord thisIsTheThirdWord", result.thirdRow)
        assertEquals("${ticket.uuid}::thisIsTheFirstWord::thisIsTheSecondWord::thisIsTheThirdWord", result.qrContent)
        assertTrue(result.partialID.isEmpty())
    }

    private fun ticket(additionalInfo: Map<String, String>? = mapOf("company" to "company")): Ticket = Ticket(uuid = "12345-678", firstName = "firstName", lastName = "lastName", additionalInfo = additionalInfo, email = null)


}