package dev.khqr.web.core

/** Parameters for building a KHQR payload (browser-facing). */
data class QrParams(
    val accountId: String,
    val merchantName: String,
    val merchantCity: String = Emv.DEFAULT_MERCHANT_CITY,
    val amount: Double = 0.0,
    val currency: KHQRCurrency = KHQRCurrency.KHR,
    val storeLabel: String? = null,
    val phoneNumber: String? = null,
    val billNumber: String? = null,
    val terminalLabel: String? = null,
    val static: Boolean = false,
    val expirationDays: Int = 1,
)

/** Result of generating a KHQR: the EMVCo string and its MD5 transaction hash. */
data class QrResult(val qr: String, val md5: String)

/**
 * Assembles an EMVCo-compliant KHQR string. Field order and sub-field ordering are
 * identical to the `kotlin-bakong` JVM library and verified by the golden test.
 */
object QrEncoder {

    private const val ONE_DAY_MS = 86_400_000L

    /** Build the KHQR string plus its MD5, using [nowMs] as the creation timestamp. */
    fun generate(params: QrParams, nowMs: Long): QrResult {
        val qr = encode(params, nowMs)
        return QrResult(qr, Encoding.md5(qr))
    }

    fun encode(params: QrParams, nowMs: Long, expirationMsOverride: Long? = null): String {
        validate(params)
        val static = params.static || params.amount <= 0.0

        val sb = StringBuilder()
        sb.append(Encoding.tlv(Emv.TAG_PAYLOAD_FORMAT_INDICATOR, Emv.PAYLOAD_FORMAT_INDICATOR_VALUE))
        sb.append(Encoding.tlv(Emv.TAG_POINT_OF_INITIATION, if (static) Emv.POI_STATIC else Emv.POI_DYNAMIC))

        val accountTemplate = Encoding.tlv(Emv.SUBTAG_ACCOUNT_ID, params.accountId)
        sb.append(Encoding.tlv(Emv.TAG_MERCHANT_ACCOUNT_INFORMATION_INDIVIDUAL, accountTemplate))

        sb.append(Encoding.tlv(Emv.TAG_MERCHANT_CATEGORY_CODE, Emv.DEFAULT_MERCHANT_CATEGORY_CODE))
        sb.append(Encoding.tlv(Emv.TAG_TRANSACTION_CURRENCY, params.currency.numericCode))

        if (!static) {
            sb.append(Encoding.tlv(Emv.TAG_TRANSACTION_AMOUNT, Encoding.formatAmount(params.amount)))
        }

        sb.append(Encoding.tlv(Emv.TAG_COUNTRY_CODE, Emv.DEFAULT_COUNTRY_CODE))
        sb.append(Encoding.tlv(Emv.TAG_MERCHANT_NAME, params.merchantName))
        sb.append(Encoding.tlv(Emv.TAG_MERCHANT_CITY, params.merchantCity))

        buildAdditionalData(params)?.let { sb.append(it) }
        sb.append(buildTimestamp(static, params.expirationDays, nowMs, expirationMsOverride))
        sb.append(Encoding.crcField(sb.toString()))

        return sb.toString()
    }

    private fun buildAdditionalData(params: QrParams): String? {
        val sub = StringBuilder()
        params.billNumber?.takeIf { it.isNotBlank() }?.let {
            sub.append(Encoding.tlv(Emv.SUBTAG_BILL_NUMBER, it))
        }
        params.phoneNumber?.takeIf { it.isNotBlank() }?.let {
            sub.append(Encoding.tlv(Emv.SUBTAG_MOBILE_NUMBER, Encoding.normalizePhone(it)))
        }
        params.storeLabel?.takeIf { it.isNotBlank() }?.let {
            sub.append(Encoding.tlv(Emv.SUBTAG_STORE_LABEL, it))
        }
        params.terminalLabel?.takeIf { it.isNotBlank() }?.let {
            sub.append(Encoding.tlv(Emv.SUBTAG_TERMINAL_LABEL, it))
        }
        if (sub.isEmpty()) return null
        return Encoding.tlv(Emv.TAG_ADDITIONAL_DATA, sub.toString())
    }

    private fun buildTimestamp(
        static: Boolean,
        expirationDays: Int,
        nowMs: Long,
        expirationMsOverride: Long?,
    ): String {
        val creation = Encoding.tlv(Emv.SUBTAG_TIMESTAMP_CREATION, nowMs.toString())
        val inner = if (static) {
            creation
        } else {
            val expiry = expirationMsOverride ?: run {
                require(expirationDays >= 1) { "Expiration time cannot be less than 1 day." }
                nowMs + expirationDays * ONE_DAY_MS
            }
            creation + Encoding.tlv(Emv.SUBTAG_TIMESTAMP_EXPIRATION, expiry.toString())
        }
        return Encoding.tlv(Emv.TAG_TIMESTAMP, inner)
    }

    private fun validate(params: QrParams) {
        checkLen(params.accountId, "Account ID", Emv.MAX_LEN_ACCOUNT_ID, allowEmpty = false)
        checkLen(params.merchantName, "Merchant name", Emv.MAX_LEN_MERCHANT_NAME, allowEmpty = false)
        checkLen(params.merchantCity, "Merchant city", Emv.MAX_LEN_MERCHANT_CITY, allowEmpty = false)
        params.billNumber?.let { checkLen(it, "Bill number", Emv.MAX_LEN_BILL_NUMBER) }
        params.storeLabel?.let { checkLen(it, "Store label", Emv.MAX_LEN_STORE_LABEL) }
        params.terminalLabel?.let { checkLen(it, "Terminal label", Emv.MAX_LEN_TERMINAL_LABEL) }
        params.phoneNumber?.let {
            val normalized = Encoding.normalizePhone(it)
            require(normalized.length <= Emv.MAX_LEN_MOBILE_NUMBER) {
                "Phone number cannot exceed ${Emv.MAX_LEN_MOBILE_NUMBER} characters."
            }
        }
        if (params.amount > 0.0) {
            require(Encoding.formatAmount(params.amount).length <= Emv.MAX_LEN_AMOUNT) {
                "Amount exceeds the maximum length of ${Emv.MAX_LEN_AMOUNT} characters."
            }
        }
    }

    private fun checkLen(value: String, field: String, maxLength: Int, allowEmpty: Boolean = true) {
        require(allowEmpty || value.isNotBlank()) { "$field cannot be empty." }
        require(value.length <= maxLength) {
            "$field cannot exceed $maxLength characters. Your input length: ${value.length}."
        }
    }
}
