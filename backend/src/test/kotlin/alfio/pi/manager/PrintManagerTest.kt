package alfio.pi.manager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class PrintManagerTest {
    @Test
    fun outputPrefix() {
        assertEquals("Bus 001 Device 007:", lsUsbDevice("1.7"))
    }

    @Test
    fun outputMatcher() {
        val out = ("Bus 001 Device 007: ID 0a5f:011c Zebra ZTC ZD410-203dpi ZPL\n" +
            "Bus 001 Device 004: ID 0424:7800 Microchip Technology, Inc. (formerly SMSC) \n" +
            "Bus 001 Device 003: ID 0424:2514 Microchip Technology, Inc. (formerly SMSC) USB 2.0 Hub\n" +
            "Bus 001 Device 002: ID 0424:2514 Microchip Technology, Inc. (formerly SMSC) USB 2.0 Hub\n" +
            "Bus 001 Device 001: ID 1d6b:0002 Linux Foundation 2.0 root hub").toByteArray()
        assertTrue(matchLsUsbOutput(lsUsbDevice("1.7"), ByteArrayInputStream(out)))
        assertTrue(matchLsUsbOutput(lsUsbDevice("1.4"), ByteArrayInputStream(out)))
        assertTrue(matchLsUsbOutput(lsUsbDevice("1.3"), ByteArrayInputStream(out)))
        assertTrue(matchLsUsbOutput(lsUsbDevice("1.2"), ByteArrayInputStream(out)))
        assertTrue(matchLsUsbOutput(lsUsbDevice("1.1"), ByteArrayInputStream(out)))
        assertFalse(matchLsUsbOutput(lsUsbDevice("1.0"), ByteArrayInputStream(out)))
    }
}