package dev.khqr

import com.sun.net.httpserver.HttpServer
import dev.khqr.internal.BakongClient
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the Bakong API methods end-to-end against an in-process HTTP server
 * (no network access required). Validates request routing and response parsing.
 */
class BakongApiTest {

    private lateinit var server: HttpServer
    private val responses = mutableMapOf<String, Pair<Int, String>>()
    private val capturedBodies = mutableMapOf<String, String>()

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            capturedBodies[path] = exchange.requestBody.readAllBytes().decodeToString()
            val (code, body) = responses[path] ?: (404 to "not found")
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
    }

    private fun khqr(token: String? = "test-token"): KHQR {
        val baseUrl = "http://127.0.0.1:${server.address.port}"
        return KHQR(BakongClient(token, BakongEnvironment.PRODUCTION, baseUrlOverride = baseUrl))
    }

    @Test
    fun `checkPayment returns PAID for responseCode 0`() {
        responses["/v1/check_transaction_by_md5"] =
            200 to """{"responseCode":0,"responseMessage":"Success","data":{"hash":"abc"}}"""
        assertEquals(TransactionStatus.PAID, khqr().checkPayment("md5hash"))
        assertTrue(capturedBodies["/v1/check_transaction_by_md5"]!!.contains("md5hash"))
    }

    @Test
    fun `checkPayment returns UNPAID for responseCode 1`() {
        responses["/v1/check_transaction_by_md5"] =
            200 to """{"responseCode":1,"responseMessage":"Not found","errorCode":1,"data":null}"""
        assertEquals(TransactionStatus.UNPAID, khqr().checkPayment("md5hash"))
    }

    @Test
    fun `getPayment maps the data object to PaymentInfo`() {
        responses["/v1/check_transaction_by_md5"] = 200 to """
            {"responseCode":0,"responseMessage":"Success","data":{
              "hash":"a7121ca1","fromAccountId":"bank@bank","toAccountId":"your_name@bank",
              "currency":"KHR","amount":9800,"description":"Cashier-01",
              "createdDateMs":1739953000,"externalRef":"100FT36550298"}}
        """.trimIndent()
        val info = khqr().getPayment("md5hash")
        assertEquals("a7121ca1", info?.hash)
        assertEquals("your_name@bank", info?.toAccountId)
        assertEquals("KHR", info?.currency)
        assertEquals(9800.0, info?.amount)
        assertEquals("100FT36550298", info?.externalRef)
    }

    @Test
    fun `getPayment returns null when unpaid`() {
        responses["/v1/check_transaction_by_md5"] =
            200 to """{"responseCode":1,"responseMessage":"Not found","data":null}"""
        assertNull(khqr().getPayment("md5hash"))
    }

    @Test
    fun `checkBulkPayments returns only successful hashes`() {
        responses["/v1/check_transaction_by_md5_list"] = 200 to """
            {"responseCode":0,"data":[
              {"md5":"hashA","status":"SUCCESS"},
              {"md5":"hashB","status":"PENDING"},
              {"md5":"hashC","status":"SUCCESS"}]}
        """.trimIndent()
        val paid = khqr().checkBulkPayments(listOf("hashA", "hashB", "hashC"))
        assertEquals(listOf("hashA", "hashC"), paid)
        // request body must be a JSON array
        assertTrue(capturedBodies["/v1/check_transaction_by_md5_list"]!!.trimStart().startsWith("["))
    }

    @Test
    fun `generateDeeplink returns the short link`() {
        responses["/v1/generate_deeplink_by_qr"] =
            200 to """{"responseCode":0,"data":{"shortLink":"https://bakong.page.link/AbC123"}}"""
        assertEquals("https://bakong.page.link/AbC123", khqr().generateDeeplink(Fixtures.GOLDEN_QR))
    }

    @Test
    fun `renewToken extracts the new token`() {
        responses["/v1/renew_token"] =
            200 to """{"responseCode":0,"responseMessage":"Success","data":{"token":"NEW.TOKEN.VALUE"}}"""
        assertEquals("NEW.TOKEN.VALUE", khqr(token = null).renewToken("dev@example.com"))
        assertTrue(capturedBodies["/v1/renew_token"]!!.contains("dev@example.com"))
    }

    @Test
    fun `http 403 surfaces the Cambodia-IP message`() {
        responses["/v1/check_transaction_by_md5"] = 403 to "Forbidden"
        val ex = assertFailsWith<KHQRException> { khqr().checkPayment("md5hash") }
        assertTrue(ex.message!!.contains("Cambodia", ignoreCase = true))
    }
}
