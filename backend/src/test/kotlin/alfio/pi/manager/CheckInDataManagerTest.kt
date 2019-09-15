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

import alfio.pi.CategoryColorConfiguration
import alfio.pi.RemoteApiAuthenticationDescriptor
import alfio.pi.model.*
import alfio.pi.repository.EventRepository
import alfio.pi.repository.UserRepository
import com.google.gson.Gson
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.*
import java.security.GeneralSecurityException
import java.time.ZonedDateTime
import javax.crypto.Cipher



class CheckInDataManagerTest {

    private val eventId = "key";

    @Test
    fun testCalcHash256() {
        assertEquals("2e99758548972a8e8822ad47fa1017ff72f06f3ff6a016851f45c398732bc50c", calcHash256("this is a test"))
    }

    @Test
    fun testSuccessfulCheckIn() {
        val ticketUUid = UUID.randomUUID().toString()
        val hmac = UUID.randomUUID().toString()
        val (mockKVStore, mockPrintManager, checkInDataManager) = buildCheckInDataManager(ticketUUid, hmac)

        val response = checkInDataManager.doPerformCheckIn(eventId, hmac, "test", ticketUUid)
        assertNotNull(response.result)
        assertEquals(CheckInStatus.SUCCESS, response.result.status)
        assertNotNull(response.ticket)
        verify(mockKVStore).insertScanLog(eq(eventId), eq(ticketUUid), any(), eq(CheckInStatus.SUCCESS), eq(CheckInStatus.RETRY), eq(false), any())
        verify(mockKVStore).insertBadgeScan(eq(eventId), any())
        verify(mockPrintManager, never()).printLabel(any<User>(), any(), any())
    }

    @Test
    fun testSuccessfulCheckInWithLabelPrinting() {
        val ticketUUid = UUID.randomUUID().toString()
        val hmac = UUID.randomUUID().toString()
        val (mockKVStore, mockPrintManager, checkInDataManager) = buildCheckInDataManager(ticketUUid, hmac, labelConfiguration = LabelConfiguration(eventId, "{}", true))

        val response = checkInDataManager.doPerformCheckIn(eventId, hmac, "test", ticketUUid)
        assertNotNull(response.result)
        assertEquals(CheckInStatus.SUCCESS, response.result.status)
        assertNotNull(response.ticket)
        verify(mockKVStore).insertScanLog(eq(eventId), eq(ticketUUid), any(), eq(CheckInStatus.SUCCESS), eq(CheckInStatus.RETRY), eq(true), any())
        verify(mockKVStore).insertBadgeScan(eq(eventId), any())
        verify(mockPrintManager).printLabel(any<User>(), any(), any())
    }

    @Test
    fun testSuccessfulCheckInWithLabelPrintingDisabled() {
        val ticketUUid = UUID.randomUUID().toString()
        val hmac = UUID.randomUUID().toString()
        val (mockKVStore, mockPrintManager, checkInDataManager) = buildCheckInDataManager(ticketUUid, hmac, labelConfiguration = LabelConfiguration(eventId, "{}", false))

        val response = checkInDataManager.doPerformCheckIn(eventId, hmac, "test", ticketUUid)
        assertNotNull(response.result)
        assertEquals(CheckInStatus.SUCCESS, response.result.status)
        assertNotNull(response.ticket)
        verify(mockKVStore).insertScanLog(eq(eventId), eq(ticketUUid), any(), eq(CheckInStatus.SUCCESS), eq(CheckInStatus.RETRY), eq(false), any())
        verify(mockPrintManager, never()).printLabel(any<User>(), any(), any())
    }

    @Test
    fun testInvalidCheckInDateBeforeBegin() {
        val ticketUUid = UUID.randomUUID().toString()
        val hmac = UUID.randomUUID().toString()
        val (mockKVStore, mockPrintManager, checkInDataManager) = buildCheckInDataManager(ticketUUid, hmac, ZonedDateTime.now().plusHours(1))
        val response = checkInDataManager.doPerformCheckIn(eventId, hmac, "test", ticketUUid)
        assertNotNull(response.result)
        assertEquals(CheckInStatus.INVALID_TICKET_CATEGORY_CHECK_IN_DATE, response.result.status)
        assertNotNull(response.ticket)
        verify(mockKVStore, never()).insertScanLog(eq(eventId), eq(ticketUUid), any(), eq(CheckInStatus.INVALID_TICKET_CATEGORY_CHECK_IN_DATE), eq(CheckInStatus.RETRY), eq(false), any())
        verify(mockKVStore, never()).insertBadgeScan(eq(eventId), any())
        verify(mockPrintManager, never()).printLabel(any<User>(), any(), any())
    }

    @Test
    fun testInvalidCheckInDateAfterEnd() {
        val ticketUUid = UUID.randomUUID().toString()
        val hmac = UUID.randomUUID().toString()
        val (mockKVStore, mockPrintManager, checkInDataManager) = buildCheckInDataManager(ticketUUid, hmac, checkInAllowedTo = ZonedDateTime.now().minusMinutes(1))
        val response = checkInDataManager.doPerformCheckIn(eventId, hmac, "test", ticketUUid)
        assertNotNull(response.result)
        assertEquals(CheckInStatus.INVALID_TICKET_CATEGORY_CHECK_IN_DATE, response.result.status)
        assertNotNull(response.ticket)
        verify(mockKVStore, never()).insertScanLog(eq(eventId), eq(ticketUUid), any(), eq(CheckInStatus.INVALID_TICKET_CATEGORY_CHECK_IN_DATE), eq(CheckInStatus.RETRY), eq(false), any())
        verify(mockKVStore, never()).insertBadgeScan(eq(eventId), any())
        verify(mockPrintManager, never()).printLabel(any<User>(), any(), any())
    }

    private fun buildCheckInDataManager(ticketUUid: String,
                                        hmac: String,
                                        checkInAllowedFrom: ZonedDateTime = ZonedDateTime.now().minusHours(1),
                                        checkInAllowedTo: ZonedDateTime = ZonedDateTime.now().plusHours(2),
                                        labelConfiguration: LabelConfiguration? = null): Triple<KVStore, PrintManager, CheckInDataManager> {
        val hashedHmac = calcHash256(hmac)

        val ticketData = TicketData(
            "firstName",
            "lastName",
            "email",
            "categoryName",
            "ACQUIRED",
            null,
            checkInAllowedFrom.toEpochSecond().toString(),
            checkInAllowedTo.toEpochSecond().toString(),
            CheckInStrategy.ONCE_PER_EVENT,
            null,
            null,
            null,
            null)

        val mockKVStore = mock<KVStore> {
            on { loadSuccessfulScanForTicket(eq(eventId), eq(ticketUUid)) } doReturn Optional.empty()
            on { isAttendeeDataPresent(eq(eventId), eq(hashedHmac)) } doReturn true
            on { getAttendeeData(eq(eventId), eq(hashedHmac)) } doReturn encrypt("$ticketUUid/$hmac", Gson().toJson(ticketData))
            on { loadLabelConfiguration(eq(eventId)) } doReturn Optional.ofNullable(labelConfiguration)
        }
        val mockEvent = Event(eventId, "name", null, Date.from(ZonedDateTime.now().plusHours(1).toInstant()), Date.from(ZonedDateTime.now().plusHours(10).toInstant()), null, 17, true, null, "UTC")
        val mockUser = User(1, "test")
        val mockEventRepository = mock<EventRepository> {
            on { loadSingle(any()) } doReturn Optional.of(mockEvent)
        }
        val mockUserRepository = mock<UserRepository> {
            on { findByUsername(any()) } doReturn Optional.of(mockUser)
        }
        val masterConfiguration = RemoteApiAuthenticationDescriptor("", null, null, "blabla")
        val categoryColorConfiguration = CategoryColorConfiguration("", emptyMap())
        val mockPrintManager = mock<PrintManager>()
        if(labelConfiguration != null) {
            whenever(mockPrintManager.printLabel(any<User>(), any(), any())).thenReturn(labelConfiguration.enabled)
        }
        val checkInDataManager = CheckInDataManager(masterConfiguration, mockEventRepository, mockKVStore, mockUserRepository, mock(), Gson(), mock(), mockPrintManager, mock(), true, null, categoryColorConfiguration, mock())
        return Triple(mockKVStore, mockPrintManager, checkInDataManager)
    }

    private fun encrypt(key: String, payload: String): String {
        try {
            val cipherAndSecret = getCypher(key)
            val cipher = cipherAndSecret.first
            cipher.init(Cipher.ENCRYPT_MODE, cipherAndSecret.second)
            val data = cipher.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
            val iv = cipher.iv
            return Base64.getUrlEncoder().encodeToString(iv) + "|" + Base64.getUrlEncoder().encodeToString(data)
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException(e)
        }
    }
}