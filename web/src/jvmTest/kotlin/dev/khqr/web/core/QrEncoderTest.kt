package dev.khqr.web.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Verifies the multiplatform KHQR port produces output identical to the
 * `kotlin-bakong` JVM library, using the same independently-verified golden vectors.
 */
class QrEncoderTest {

    private companion object {
        const val GOLDEN_NOW_MS = 1_773_894_603_019L
        const val GOLDEN_EXPIRATION_MS = 1_773_894_775_819L
        const val GOLDEN_QR =
            "00020101021229180014your_name@bank520459995303116540498005802KH5909Your Name" +
                "6010Phnom Penh62510109TRX01234502090123456780311Phsar Thmei0706POS-01" +
                "993400131773894603019011317738947758196304A5A3"
        const val GOLDEN_MD5 = "3dc50c785e47a215feb336d44807825c"
    }

    private fun goldenParams() = QrParams(
        accountId = "your_name@bank",
        merchantName = "Your Name",
        merchantCity = "Phnom Penh",
        amount = 9800.0,
        currency = KHQRCurrency.KHR,
        storeLabel = "Phsar Thmei",
        phoneNumber = "012345678",
        billNumber = "TRX012345",
        terminalLabel = "POS-01",
        static = false,
        expirationDays = 2,
    )

    @Test
    fun `reproduces the golden KHQR string exactly`() {
        val qr = QrEncoder.encode(goldenParams(), GOLDEN_NOW_MS, expirationMsOverride = GOLDEN_EXPIRATION_MS)
        assertEquals(GOLDEN_QR, qr)
    }

    @Test
    fun `pure MD5 matches known vectors`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Encoding.md5(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Encoding.md5("abc"))
        assertEquals(
            "9e107d9d372bb6826bd81d3542a419d6",
            Encoding.md5("The quick brown fox jumps over the lazy dog"),
        )
    }

    @Test
    fun `generates the golden MD5 for the golden string`() {
        assertEquals(GOLDEN_MD5, Encoding.md5(GOLDEN_QR))
    }

    @Test
    fun `crc field reproduces the golden checksum`() {
        val withoutCrc = GOLDEN_QR.removeSuffix("6304A5A3")
        assertEquals("6304A5A3", Encoding.crcField(withoutCrc))
    }

    @Test
    fun `formatAmount strips trailing zeros`() {
        assertEquals("9800", Encoding.formatAmount(9800.0))
        assertEquals("0.1", Encoding.formatAmount(0.1))
        assertEquals("100", Encoding.formatAmount(100.0))
        assertEquals("10.5", Encoding.formatAmount(10.5))
        assertEquals("0.01", Encoding.formatAmount(0.01))
    }

    @Test
    fun `normalizePhone yields local Cambodian format`() {
        assertEquals("012345678", Encoding.normalizePhone("85512345678"))
        assertEquals("012345678", Encoding.normalizePhone("012345678"))
        assertEquals("012345678", Encoding.normalizePhone("+855 12 345 678"))
    }

    @Test
    fun `generate returns string and md5 together`() {
        val result = QrEncoder.generate(goldenParams().copy(static = true), GOLDEN_NOW_MS)
        assertTrue(result.qr.startsWith("000201010211"))
        assertEquals(32, result.md5.length)
        assertEquals(Encoding.md5(result.qr), result.md5)
    }

    @Test
    fun `dynamic expiration is creation plus N days in ms`() {
        val qr = QrEncoder.encode(goldenParams().copy(expirationDays = 3), GOLDEN_NOW_MS)
        assertTrue(qr.contains("0113${GOLDEN_NOW_MS + 3L * 86_400_000L}"))
    }

    @Test
    fun `validation rejects an oversized account id`() {
        assertFailsWith<IllegalArgumentException> {
            QrEncoder.encode(goldenParams().copy(accountId = "x".repeat(40)), GOLDEN_NOW_MS)
        }
    }
}
