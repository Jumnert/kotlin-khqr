package dev.khqr

import dev.khqr.internal.BakongClient
import dev.khqr.internal.Encoding
import dev.khqr.internal.QrEncoder
import dev.khqr.internal.QrParams
import dev.khqr.internal.intOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.http.HttpClient

/**
 * Entry point for the Bakong KHQR toolkit.
 *
 * Offline features (no token needed): [createQr], [generateMd5], [qrImage].
 * Online features (require a Bakong Developer Token): [generateDeeplink],
 * [checkPayment], [getPayment], [checkBulkPayments], [renewToken].
 *
 * Register for a developer token at https://api-bakong.nbc.gov.kh/register/.
 *
 * ```
 * val khqr = KHQR("eyJhbGciOi...your_token")
 * val qr = khqr.createQr(
 *     accountId = "your_name@bank",
 *     merchantName = "Your Shop",
 *     merchantCity = "Phnom Penh",
 *     amount = 100.0,
 *     currency = KHQRCurrency.USD,
 * )
 * val md5 = khqr.generateMd5(qr)
 * val status = khqr.checkPayment(md5)
 * ```
 */
class KHQR internal constructor(
    private val client: BakongClient,
) {
    /**
     * @param bakongToken developer token (official `eyJ...` JWT or a Bakong Relay `rbk...`
     *   token, which automatically targets the Relay API). May be `null` for offline use.
     * @param environment which Bakong API to talk to (default [BakongEnvironment.PRODUCTION]).
     * @param httpClient optional custom JDK [HttpClient] (useful for testing/proxying).
     */
    @JvmOverloads
    constructor(
        bakongToken: String? = null,
        environment: BakongEnvironment = BakongEnvironment.PRODUCTION,
        httpClient: HttpClient? = null,
    ) : this(
        if (httpClient != null) {
            BakongClient(bakongToken, environment, httpClient)
        } else {
            BakongClient(bakongToken, environment)
        },
    )

    /**
     * Build an EMVCo-compliant KHQR payload string.
     *
     * @param accountId Bakong account id, e.g. `your_name@bank` (max 32 chars).
     * @param merchantName merchant display name (max 25 chars).
     * @param merchantCity merchant city (max 15 chars).
     * @param amount transaction amount; `<= 0` produces a static (reusable) QR.
     * @param currency [KHQRCurrency.KHR] or [KHQRCurrency.USD].
     * @param storeLabel optional store label.
     * @param phoneNumber optional merchant phone (Cambodian numbers are normalised).
     * @param billNumber optional bill / invoice reference.
     * @param terminalLabel optional terminal label.
     * @param static force a static QR (no amount) even when an amount is given.
     * @param expirationDays validity window in days for dynamic QRs (default 1).
     */
    @JvmOverloads
    fun createQr(
        accountId: String,
        merchantName: String,
        merchantCity: String = "Phnom Penh",
        amount: Double = 0.0,
        currency: KHQRCurrency = KHQRCurrency.KHR,
        storeLabel: String? = null,
        phoneNumber: String? = null,
        billNumber: String? = null,
        terminalLabel: String? = null,
        static: Boolean = false,
        expirationDays: Int = 1,
    ): String {
        val params = QrParams(
            accountId = accountId,
            merchantName = merchantName,
            merchantCity = merchantCity,
            amount = amount,
            currency = currency,
            storeLabel = storeLabel,
            phoneNumber = phoneNumber,
            billNumber = billNumber,
            terminalLabel = terminalLabel,
            static = static,
            expirationDays = expirationDays,
        )
        return QrEncoder.encode(params, System.currentTimeMillis())
    }

    /**
     * Compute the MD5 hash of a KHQR string. This hash is the transaction identifier
     * used by [checkPayment], [getPayment] and [checkBulkPayments].
     */
    fun generateMd5(qr: String): String = Encoding.md5(qr)

    /**
     * Ask Bakong to turn a KHQR string into a deep link that opens the payer's banking
     * app. Returns the short link, or `null` if Bakong did not return one.
     *
     * @param qr a KHQR string from [createQr].
     * @param callbackUrl deep link / URL to return to after payment.
     * @param appIconUrl your app's icon URL.
     * @param appName your app's name.
     */
    @JvmOverloads
    fun generateDeeplink(
        qr: String,
        callbackUrl: String? = null,
        appIconUrl: String = DEFAULT_APP_ICON_URL,
        appName: String = "MyAppName",
    ): String? {
        val body = buildJsonObject {
            put("qr", qr)
            putJsonObject("sourceInfo") {
                put("appIconUrl", appIconUrl)
                put("appName", appName)
                put("appDeepLinkCallback", callbackUrl ?: DEFAULT_DEEPLINK_CALLBACK)
            }
        }
        val response = client.postObject("/generate_deeplink_by_qr", body)
        if (response.intOrNull("responseCode") == 0) {
            val data = response["data"] as? JsonObject
            return (data?.get("shortLink") as? JsonPrimitive)?.content
        }
        return null
    }

    /**
     * Check whether the transaction for [md5] has been paid.
     * @return [TransactionStatus.PAID] or [TransactionStatus.UNPAID].
     */
    fun checkPayment(md5: String): TransactionStatus {
        val response = client.postObject("/check_transaction_by_md5", BakongClient.md5Body(md5))
        return if (response.intOrNull("responseCode") == 0) TransactionStatus.PAID else TransactionStatus.UNPAID
    }

    /**
     * Retrieve full details of a paid transaction (useful for static QRs where the
     * amount is decided by the payer). Returns `null` if not paid / not found.
     */
    fun getPayment(md5: String): PaymentInfo? {
        val response = client.postObject("/check_transaction_by_md5", BakongClient.md5Body(md5))
        if (response.intOrNull("responseCode") != 0) return null
        val data = response["data"] as? JsonObject ?: return null
        return client.json.decodeFromJsonElement(PaymentInfo.serializer(), data)
    }

    /**
     * Check up to 50 transactions at once.
     * @return the subset of [md5List] that correspond to successful (paid) transactions.
     * @throws KHQRException if more than 50 hashes are supplied.
     */
    fun checkBulkPayments(md5List: List<String>): List<String> {
        if (md5List.size > MAX_BULK_SIZE) {
            throw KHQRException("The md5List exceeds the allowed limit of $MAX_BULK_SIZE hashes per request.")
        }
        if (md5List.isEmpty()) return emptyList()

        val response = client.postArray("/check_transaction_by_md5_list", BakongClient.md5ListBody(md5List))
        val data = response["data"] as? JsonArray ?: return emptyList()

        val paid = mutableListOf<String>()
        for (element in data) {
            val obj = element as? JsonObject ?: continue
            val entry = client.json.decodeFromJsonElement(BulkPaymentEntry.serializer(), obj)
            if (entry.status == "SUCCESS" && !entry.md5.isNullOrBlank()) {
                paid.add(entry.md5)
            }
        }
        return paid
    }

    /**
     * Request a fresh developer token for a registered [email]. The Bakong
     * `/v1/renew_token` endpoint emails-or-returns a new bearer token; this returns it
     * so you can cache it and build a new [KHQR] instance.
     *
     * @throws KHQRException if the API did not return a token.
     */
    fun renewToken(email: String): String {
        val body = buildJsonObject { put("email", email) }
        val response = client.postObject("/renew_token", body, requireAuth = false)
        if (response.intOrNull("responseCode") == 0) {
            val token = extractToken(response)
            if (!token.isNullOrBlank()) return token
        }
        val message = (response["responseMessage"] as? JsonPrimitive)?.content ?: "unknown error"
        throw KHQRException("Failed to renew Bakong token: $message")
    }

    /** Render [qr] to a PNG image and return the raw bytes. */
    @JvmOverloads
    fun qrImage(qr: String, size: Int = 512): ByteArray = QrImage.pngBytes(qr, size)

    private fun extractToken(response: JsonObject): String? {
        (response["data"] as? JsonObject)?.let { data ->
            (data["token"] as? JsonPrimitive)?.content?.let { return it }
        }
        (response["token"] as? JsonPrimitive)?.let { return it.content }
        (response["data"] as? JsonPrimitive)?.let { return it.content }
        return null
    }

    companion object {
        const val MAX_BULK_SIZE = 50
        const val DEFAULT_APP_ICON_URL = "https://bakong.nbc.gov.kh/images/logo.svg"
        const val DEFAULT_DEEPLINK_CALLBACK = "https://bakong.nbc.gov.kh"
    }
}
