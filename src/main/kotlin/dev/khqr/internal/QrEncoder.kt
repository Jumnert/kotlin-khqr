package dev.khqr.internal

import dev.khqr.KHQRCurrency
import dev.khqr.KHQRException

/** Parameters for building a KHQR payload. */
internal data class QrParams(
    val accountId: String,
    val merchantName: String,
    val merchantCity: String,
    val amount: Double,
    val currency: KHQRCurrency,
    val storeLabel: String?,
    val phoneNumber: String?,
    val billNumber: String?,
    val terminalLabel: String?,
    val static: Boolean,
    val expirationDays: Int,
)

/**
 * Assembles an EMVCo-compliant KHQR string from [QrParams].
 *
 * Field order follows the Bakong KHQR specification:
 * payload format indicator (00), point of initiation (01), merchant account
 * information (29), MCC (52), currency (53), amount (54, dynamic only),
 * country (58), merchant name (59), merchant city (60), additional data (62),
 * timestamp (99), and the CRC (63).
 */
internal object QrEncoder {

    private const val ONE_DAY_MS = 86_400_000L

    fun encode(params: QrParams, nowMs: Long, expirationMsOverride: Long? = null): String {
        validate(params)

        // amount <= 0 forces a static QR (no amount field), mirroring the reference SDK.
        val static = params.static || params.amount <= 0.0

        val sb = StringBuilder()

        // 00 - Payload Format Indicator
        sb.append(Encoding.tlv(Emv.TAG_PAYLOAD_FORMAT_INDICATOR, Emv.PAYLOAD_FORMAT_INDICATOR_VALUE))

        // 01 - Point of Initiation Method
        sb.append(
            Encoding.tlv(
                Emv.TAG_POINT_OF_INITIATION,
                if (static) Emv.POI_STATIC else Emv.POI_DYNAMIC,
            ),
        )

        // 29 - Merchant Account Information (global unique identifier / account id)
        val accountTemplate = Encoding.tlv(Emv.SUBTAG_ACCOUNT_ID, params.accountId)
        sb.append(Encoding.tlv(Emv.TAG_MERCHANT_ACCOUNT_INFORMATION_INDIVIDUAL, accountTemplate))

        // 52 - Merchant Category Code
        sb.append(Encoding.tlv(Emv.TAG_MERCHANT_CATEGORY_CODE, Emv.DEFAULT_MERCHANT_CATEGORY_CODE))

        // 53 - Transaction Currency
        sb.append(Encoding.tlv(Emv.TAG_TRANSACTION_CURRENCY, params.currency.numericCode))

        // 54 - Transaction Amount (dynamic QR only)
        if (!static) {
            sb.append(Encoding.tlv(Emv.TAG_TRANSACTION_AMOUNT, Encoding.formatAmount(params.amount)))
        }

        // 58 - Country Code
        sb.append(Encoding.tlv(Emv.TAG_COUNTRY_CODE, Emv.DEFAULT_COUNTRY_CODE))

        // 59 - Merchant Name
        sb.append(Encoding.tlv(Emv.TAG_MERCHANT_NAME, params.merchantName))

        // 60 - Merchant City
        sb.append(Encoding.tlv(Emv.TAG_MERCHANT_CITY, params.merchantCity))

        // 62 - Additional Data Field Template
        buildAdditionalData(params)?.let { sb.append(it) }

        // 99 - Timestamp
        sb.append(buildTimestamp(static, params.expirationDays, nowMs, expirationMsOverride))

        // 63 - CRC (covers everything before it, plus the "6304" tag/length)
        sb.append(Encoding.crcField(sb.toString()))

        return sb.toString()
    }

    private fun buildAdditionalData(params: QrParams): String? {
        val sub = StringBuilder()

        // Sub-tags are emitted in ascending order: bill (01), phone (02),
        // store (03), terminal (07) — matching the official KHQR layout.
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
                if (expirationDays < 1) {
                    throw KHQRException("Expiration time cannot be less than 1 day. Your input: $expirationDays days.")
                }
                nowMs + expirationDays * ONE_DAY_MS
            }
            creation + Encoding.tlv(Emv.SUBTAG_TIMESTAMP_EXPIRATION, expiry.toString())
        }
        return Encoding.tlv(Emv.TAG_TIMESTAMP, inner)
    }

    private fun validate(params: QrParams) {
        require(params.accountId, "Account ID", Emv.MAX_LEN_ACCOUNT_ID)
        require(params.merchantName, "Merchant name", Emv.MAX_LEN_MERCHANT_NAME, allowEmpty = false)
        require(params.merchantCity, "Merchant city", Emv.MAX_LEN_MERCHANT_CITY, allowEmpty = false)
        params.billNumber?.let { require(it, "Bill number", Emv.MAX_LEN_BILL_NUMBER) }
        params.storeLabel?.let { require(it, "Store label", Emv.MAX_LEN_STORE_LABEL) }
        params.terminalLabel?.let { require(it, "Terminal label", Emv.MAX_LEN_TERMINAL_LABEL) }
        params.phoneNumber?.let {
            val normalized = Encoding.normalizePhone(it)
            if (normalized.length > Emv.MAX_LEN_MOBILE_NUMBER) {
                throw KHQRException("Phone number cannot exceed ${Emv.MAX_LEN_MOBILE_NUMBER} characters. Your input length: ${normalized.length}.")
            }
        }
        if (params.amount > 0.0) {
            val len = Encoding.formatAmount(params.amount).length
            if (len > Emv.MAX_LEN_AMOUNT) {
                throw KHQRException("Formatted amount exceeds maximum length of ${Emv.MAX_LEN_AMOUNT} characters. Your input length: $len.")
            }
        }
    }

    private fun require(value: String, field: String, maxLength: Int, allowEmpty: Boolean = true) {
        if (!allowEmpty && value.isBlank()) {
            throw KHQRException("$field cannot be empty.")
        }
        if (value.length > maxLength) {
            throw KHQRException("$field cannot exceed $maxLength characters. Your input length: ${value.length}.")
        }
    }
}
