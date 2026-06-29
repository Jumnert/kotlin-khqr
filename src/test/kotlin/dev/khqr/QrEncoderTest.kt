package dev.khqr

import dev.khqr.internal.QrEncoder
import dev.khqr.internal.QrParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QrEncoderTest {

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
    fun `dynamic qr reproduces the reference string exactly`() {
        // The documented reference embeds an explicit expiration timestamp; inject it
        // so the assembled bytes (and their CRC) match the official example exactly.
        val qr = QrEncoder.encode(
            goldenParams(),
            nowMs = Fixtures.GOLDEN_NOW_MS,
            expirationMsOverride = Fixtures.GOLDEN_EXPIRATION_MS,
        )
        assertEquals(Fixtures.GOLDEN_QR, qr)
    }

    @Test
    fun `dynamic expiration is creation plus N days in milliseconds`() {
        val nowMs = Fixtures.GOLDEN_NOW_MS
        val qr = QrEncoder.encode(goldenParams().copy(expirationDays = 3), nowMs = nowMs)
        val expectedExpiry = (nowMs + 3L * 86_400_000L).toString()
        // timestamp template: 99 <len> 00 13 <creation> 01 13 <expiry>
        assertTrue(qr.contains("0113$expectedExpiry"), "expiry must be creation + 3 days in ms")
    }

    @Test
    fun `dynamic qr uses point-of-initiation 12 and includes the amount`() {
        val qr = QrEncoder.encode(goldenParams(), nowMs = Fixtures.GOLDEN_NOW_MS)
        assertTrue(qr.startsWith("000201" + "010212"), "dynamic QR must start with payload + POI 12")
        assertTrue(qr.contains("54049800"), "dynamic QR must carry the amount field 54")
    }

    @Test
    fun `static qr uses point-of-initiation 11 and omits the amount`() {
        val params = goldenParams().copy(static = true)
        val qr = QrEncoder.encode(params, nowMs = Fixtures.GOLDEN_NOW_MS)
        assertTrue(qr.startsWith("000201" + "010211"), "static QR must start with payload + POI 11")
        assertFalse(qr.contains("54049800"), "static QR must not carry an amount field")
        // A static QR's timestamp (tag 99) carries only the creation sub-tag (length 17),
        // whereas a dynamic QR also appends the expiration sub-tag (length 34).
        assertTrue(qr.contains("991700131773894603019"), "static timestamp must be creation-only")
    }

    @Test
    fun `zero amount forces a static qr`() {
        val params = goldenParams().copy(amount = 0.0, static = false)
        val qr = QrEncoder.encode(params, nowMs = Fixtures.GOLDEN_NOW_MS)
        assertTrue(qr.startsWith("000201" + "010211"), "amount <= 0 must produce a static QR")
    }
}
