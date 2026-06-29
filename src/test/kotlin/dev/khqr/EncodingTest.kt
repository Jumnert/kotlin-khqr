package dev.khqr

import dev.khqr.internal.Encoding
import kotlin.test.Test
import kotlin.test.assertEquals

class EncodingTest {

    @Test
    fun `tlv prefixes a two-digit length`() {
        assertEquals("000201", Encoding.tlv("00", "01"))
        assertEquals("5802KH", Encoding.tlv("58", "KH"))
        assertEquals("5909Your Name", Encoding.tlv("59", "Your Name"))
    }

    @Test
    fun `crc16 matches the reference golden value`() {
        // The golden QR ends with its CRC "6304A5A3"; recomputing over the body
        // (everything up to and including "6304") must reproduce "A5A3".
        val body = Fixtures.GOLDEN_QR.removeSuffix("A5A3")
        assertEquals("A5A3", String.format("%04X", Encoding.crc16(body)))
    }

    @Test
    fun `crcField builds the full CRC tag`() {
        val withoutCrc = Fixtures.GOLDEN_QR.removeSuffix("6304A5A3")
        assertEquals("6304A5A3", Encoding.crcField(withoutCrc))
    }

    @Test
    fun `md5 produces the lowercase 32-char digest`() {
        val md5 = Encoding.md5(Fixtures.GOLDEN_QR)
        assertEquals(32, md5.length)
        assertEquals(Fixtures.GOLDEN_MD5, md5)
        assertEquals(md5, md5.lowercase())
    }

    @Test
    fun `formatAmount strips trailing zeros the KHQR way`() {
        assertEquals("9800", Encoding.formatAmount(9800.0))
        assertEquals("0.1", Encoding.formatAmount(0.1))
        assertEquals("100", Encoding.formatAmount(100.00))
        assertEquals("20", Encoding.formatAmount(20.00))
        assertEquals("10.5", Encoding.formatAmount(10.50))
        assertEquals("1000", Encoding.formatAmount(1000.0))
        assertEquals("0", Encoding.formatAmount(0.0))
        assertEquals("0.01", Encoding.formatAmount(0.01))
    }

    @Test
    fun `normalizePhone produces local Cambodian format`() {
        assertEquals("012345678", Encoding.normalizePhone("012345678"))
        assertEquals("012345678", Encoding.normalizePhone("85512345678"))
        assertEquals("012345678", Encoding.normalizePhone("12345678"))
        assertEquals("012345678", Encoding.normalizePhone("+855 12 345 678"))
        assertEquals("012345", Encoding.normalizePhone("85512345"))
        assertEquals("0977888999", Encoding.normalizePhone("0977888999"))
    }
}
