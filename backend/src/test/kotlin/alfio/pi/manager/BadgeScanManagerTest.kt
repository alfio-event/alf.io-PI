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
import alfio.pi.repository.EventRepository
import alfio.pi.repository.UserRepository
import com.google.gson.Gson
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class BadgeScanManagerTest {

    private val eventId = "key";

    @Test
    fun testPerformBadgeScanSuccessful() {
        val uuid = UUID.randomUUID().toString()
        val (store, scanManager) = buildBadgeScanManager(uuid, badgeScanTimestamp = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
        val response = scanManager.performBadgeScan(eventId, uuid, "test")
        assertTrue(response.isSuccessful())
        assertEquals(CheckInStatus.BADGE_SCAN_SUCCESS, response.result.status)
        assertNotNull(response.ticket)
        assertEquals("", response.ticket?.firstName)
        assertEquals("", response.ticket?.lastName)
        assertNull("", response.ticket?.email)
        assertEquals(uuid, response.ticket?.uuid)
        verify(store).insertBadgeScan(eq(eventId), any())
        verify(store).insertBadgeScanLog(eq(eventId), eq(uuid), eq(1), eq(CheckInStatus.BADGE_SCAN_SUCCESS), eq(CheckInStatus.RETRY), any())
    }

    @Test
    fun testPerformBadgeScanAlreadyScanned() {
        val uuid = UUID.randomUUID().toString()
        val (store, scanManager) = buildBadgeScanManager(uuid, badgeScanTimestamp = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS))
        val response = scanManager.performBadgeScan(eventId, uuid, "test")
        assertFalse(response.isSuccessful())
        assertEquals(CheckInStatus.BADGE_SCAN_ALREADY_DONE, response.result.status)
        assertNull(response.ticket)
        verify(store).insertBadgeScan(eq(eventId), any())
        verify(store).insertBadgeScanLog(eq(eventId), eq(uuid), eq(1), eq(CheckInStatus.BADGE_SCAN_ALREADY_DONE), eq(CheckInStatus.RETRY), any())
    }

    @Test
    fun testPerformBadgeScanNotEnabled() {
        val uuid = UUID.randomUUID().toString()
        val (store, scanManager) = buildBadgeScanManager(uuid, checkInStrategy = CheckInStrategy.ONCE_PER_EVENT)
        val response = scanManager.performBadgeScan(eventId, uuid, "test")
        assertFalse(response.isSuccessful())
        assertEquals(CheckInStatus.INVALID_TICKET_STATE, response.result.status)
        assertNull(response.ticket)
        verify(store, never()).insertBadgeScan(eq(eventId), any())
        verify(store, never()).insertBadgeScanLog(eq(eventId), eq(uuid), eq(1), any(), any(), any())
    }

    @Test
    fun testPerformBadgeScanTicketNotYetValid() {
        val uuid = UUID.randomUUID().toString()
        val (store, scanManager) = buildBadgeScanManager(uuid, ticketValidFrom = ZonedDateTime.now().plusSeconds(1))
        val response = scanManager.performBadgeScan(eventId, uuid, "test")
        assertFalse(response.isSuccessful())
        assertEquals(CheckInStatus.INVALID_TICKET_CATEGORY_CHECK_IN_DATE, response.result.status)
        assertNull(response.ticket)
        verify(store, never()).insertBadgeScan(eq(eventId), any())
        verify(store, never()).insertBadgeScanLog(eq(eventId), eq(uuid), eq(1), any(), any(), any())
    }

    @Test
    fun testPerformBadgeScanTicketExpired() {
        val uuid = UUID.randomUUID().toString()
        val (store, scanManager) = buildBadgeScanManager(uuid, ticketValidTo = ZonedDateTime.now().minusSeconds(1))
        val response = scanManager.performBadgeScan(eventId, uuid, "test")
        assertFalse(response.isSuccessful())
        assertEquals(CheckInStatus.INVALID_TICKET_CATEGORY_CHECK_IN_DATE, response.result.status)
        assertNull(response.ticket)
        verify(store, never()).insertBadgeScan(eq(eventId), any())
        verify(store, never()).insertBadgeScanLog(eq(eventId), eq(uuid), eq(1), any(), any(), any())
    }

    @Test
    fun testPerformBadgeScanTicketNotFound() {
        val uuid = UUID.randomUUID().toString()
        val (store, scanManager) = buildBadgeScanManager(uuid, badgeScanExists = false)
        val response = scanManager.performBadgeScan(eventId, uuid, "test")
        assertFalse(response.isSuccessful())
        assertEquals(CheckInStatus.TICKET_NOT_FOUND, response.result.status)
        assertNull(response.ticket)
        verify(store, never()).insertBadgeScan(eq(eventId), any())
        verify(store, never()).insertBadgeScanLog(eq(eventId), eq(uuid), eq(1), any(), any(), any())
    }

    private fun buildBadgeScanManager(ticketUUid: String,
                                      badgeScanExists: Boolean = true,
                                      badgeScanStatus: CheckInStatus = CheckInStatus.SUCCESS,
                                      checkInStrategy: CheckInStrategy = CheckInStrategy.ONCE_PER_DAY,
                                      badgeScanTimestamp: ZonedDateTime = ZonedDateTime.now().minusDays(1),
                                      ticketValidFrom: ZonedDateTime = ZonedDateTime.now().minusDays(1),
                                      ticketValidTo: ZonedDateTime = ZonedDateTime.now().plusHours(2)): Pair<KVStore, BadgeScanManager> {

        val mockBadgeScan = BadgeScan(ticketUUid, badgeScanStatus, badgeScanTimestamp, ticketValidFrom, ticketValidTo, "", checkInStrategy)
        val mockKVStore = mock<KVStore> {
            on { retrieveBadgeScan(eq(eventId), eq(ticketUUid)) } doReturn if(badgeScanExists) mockBadgeScan else null
        }
        val mockEvent = Event(eventId, "name", null, Date.from(ZonedDateTime.now().plusHours(1).toInstant()), Date.from(ZonedDateTime.now().plusHours(10).toInstant()), null, 17, true, null, "UTC")
        val mockUser = User(1, "test")
        val mockEventRepository = mock<EventRepository> {
            on { loadSingle(any()) } doReturn Optional.of(mockEvent)
        }
        val mockUserRepository = mock<UserRepository> {
            on { findByUsername(any()) } doReturn Optional.of(mockUser)
        }
        val badgeScanManager = BadgeScanManager(mockEventRepository, mockKVStore, mockUserRepository, null, mock(), Gson())

        return mockKVStore to badgeScanManager
    }
}