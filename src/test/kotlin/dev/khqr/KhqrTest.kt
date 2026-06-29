package dev.khqr

import dev.khqr.internal.Encoding
import javax.imageio.ImageIO
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KhqrTest {

    private val khqr = KHQR()

    @Test
    fun `createQr produces a self-consistent CRC`() {
        val qr = khqr.createQr(
            accountId = "your_name@bank",
            merchantName = "Your Shop",
            merchantCity = "Phnom Penh",
            amount = 100.0,
            currency = KHQRCurrency.USD,
            billNumber = "INV-001",
        )
        // qr = body + "6304" + crc(4). Recomputing the CRC over the body must match.
        val body = qr.dropLast(4)
        assertTrue(body.endsWith("6304"))
        assertEquals(qr.takeLast(4), String.format("%04X", Encoding.crc16(body)))
        assertTrue(qr.startsWith("000201010212"))
        assertTrue(qr.contains("5303840"), "USD currency code 840 must be present")
    }

    @Test
    fun `generateMd5 matches the golden hash`() {
        assertEquals(Fixtures.GOLDEN_MD5, khqr.generateMd5(Fixtures.GOLDEN_QR))
    }

    @Test
    fun `createQr enforces account id length`() {
        val ex = assertFailsWith<KHQRException> {
            khqr.createQr(
                accountId = "x".repeat(40),
                merchantName = "Shop",
                merchantCity = "Phnom Penh",
                amount = 1.0,
            )
        }
        assertTrue(ex.message!!.contains("Account ID"))
    }

    @Test
    fun `createQr rejects blank merchant name`() {
        assertFailsWith<KHQRException> {
            khqr.createQr(accountId = "a@bank", merchantName = " ", merchantCity = "Phnom Penh", amount = 1.0)
        }
    }

    @Test
    fun `checkBulkPayments rejects more than 50 hashes`() {
        val ex = assertFailsWith<KHQRException> {
            khqr.checkBulkPayments(List(51) { "hash$it" })
        }
        assertTrue(ex.message!!.contains("50"))
    }

    @Test
    fun `online calls require a token`() {
        val ex = assertFailsWith<KHQRException> { khqr.checkPayment("abc") }
        assertTrue(ex.message!!.contains("Token", ignoreCase = true))
    }

    @Test
    fun `currency resolves from string`() {
        assertEquals(KHQRCurrency.USD, KHQRCurrency.fromString("usd"))
        assertEquals(KHQRCurrency.KHR, KHQRCurrency.fromString("KHR"))
        assertFailsWith<KHQRException> { KHQRCurrency.fromString("EUR") }
    }

    @Test
    fun `qrImage returns a decodable PNG`() {
        val png = khqr.qrImage(Fixtures.GOLDEN_QR, size = 256)
        assertTrue(png.isNotEmpty())
        // PNG magic number
        assertEquals(0x89.toByte(), png[0])
        assertEquals('P'.code.toByte(), png[1])
        val image = ImageIO.read(ByteArrayInputStream(png))
        assertNotNull(image)
        assertEquals(256, image.width)
    }
}
