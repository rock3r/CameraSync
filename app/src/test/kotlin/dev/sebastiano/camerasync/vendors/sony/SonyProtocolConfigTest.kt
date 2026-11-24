package dev.sebastiano.camerasync.vendors.sony

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for [SonyProtocol] configuration parsing, helper commands, and geo-tagging. */
class SonyProtocolConfigTest {

    @Test
    fun `parseConfigRequiresTimezone returns true when bit 1 is set`() {
        val config = byteArrayOf(0x06, 0x10, 0x00, 0x9C.toByte(), 0x02, 0x00)
        assertTrue(SonyProtocol.parseConfigRequiresTimezone(config))
    }

    @Test
    fun `parseConfigRequiresTimezone returns true when bit 1 is set with other bits`() {
        val config = byteArrayOf(0x06, 0x10, 0x00, 0x9C.toByte(), 0x06, 0x00)
        assertTrue(SonyProtocol.parseConfigRequiresTimezone(config))
    }

    @Test
    fun `parseConfigRequiresTimezone returns false when bit 1 is not set`() {
        val config = byteArrayOf(0x06, 0x10, 0x00, 0x9C.toByte(), 0x04, 0x00)
        assertFalse(SonyProtocol.parseConfigRequiresTimezone(config))
    }

    @Test
    fun `parseConfigRequiresTimezone returns false when byte 4 is zero`() {
        val config = byteArrayOf(0x06, 0x10, 0x00, 0x9C.toByte(), 0x00, 0x00)
        assertFalse(SonyProtocol.parseConfigRequiresTimezone(config))
    }

    @Test
    fun `parseConfigRequiresTimezone returns false for short data`() {
        assertFalse(SonyProtocol.parseConfigRequiresTimezone(byteArrayOf(0x01, 0x02)))
    }

    @Test
    fun `createStatusNotifyEnable returns correct bytes`() {
        val expected = byteArrayOf(0x03, 0x01, 0x02, 0x01)
        assertArrayEquals(expected, SonyProtocol.createStatusNotifyEnable())
    }

    @Test
    fun `createStatusNotifyDisable returns correct bytes`() {
        val expected = byteArrayOf(0x03, 0x01, 0x02, 0x00)
        assertArrayEquals(expected, SonyProtocol.createStatusNotifyDisable())
    }

    @Test
    fun `createPairingInit returns correct bytes`() {
        val expected = byteArrayOf(0x06, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00)
        assertArrayEquals(expected, SonyProtocol.createPairingInit())
    }

    @Test
    fun `getPairingInitData returns same as createPairingInit`() {
        assertArrayEquals(SonyProtocol.createPairingInit(), SonyProtocol.getPairingInitData())
    }

    @Test
    fun `encodeGeoTaggingEnabled returns empty array`() {
        assertEquals(0, SonyProtocol.encodeGeoTaggingEnabled(true).size)
        assertEquals(0, SonyProtocol.encodeGeoTaggingEnabled(false).size)
    }

    @Test
    fun `decodeGeoTaggingEnabled returns false`() {
        assertFalse(SonyProtocol.decodeGeoTaggingEnabled(byteArrayOf(0x01)))
        assertFalse(SonyProtocol.decodeGeoTaggingEnabled(byteArrayOf()))
    }
}
