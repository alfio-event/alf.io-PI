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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals("George W." to 24F, optimizeText("George William", maxLengthForSize, true))
        assertEquals("George William" to 18F, optimizeText("George William", maxLengthForSize, false))
        assertEquals("George W. H." to 22F, optimizeText("George William Henry", maxLengthForSize, true))
        assertEquals("George William " to 18F, optimizeText("George William Henry Arthur", maxLengthForSize, false))
    }

    @Test
    fun testGenerateLabel() {
        val bytes = generatePDFLabel("George", "William", "Test Company", "12345678", "12345678", "123").invoke(DymoLW450Turbo41x89())
        assertTrue(bytes.isNotEmpty())
    }




}