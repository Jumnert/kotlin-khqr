package dev.khqr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Thrown for invalid input or Bakong API/transport errors. */
class KHQRException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Currencies supported by Bakong KHQR, with their ISO 4217 numeric codes. */
enum class KHQRCurrency(val numericCode: String) {
    /** US Dollar (840). */
    USD("840"),

    /** Cambodian Riel (116). */
    KHR("116");

    companion object {
        /** Resolve a currency from "USD"/"KHR" (case-insensitive). */
        fun fromString(value: String): KHQRCurrency = when (value.uppercase()) {
            "USD" -> USD
            "KHR" -> KHR
            else -> throw KHQRException("Invalid currency '$value'. Supported codes are 'USD' and 'KHR'.")
        }
    }
}

/** Result of a single-transaction payment check. */
enum class TransactionStatus {
    /** The transaction was found and completed successfully. */
    PAID,

    /** The transaction was not found, or has not been paid yet. */
    UNPAID,
}

/**
 * Bakong API environment.
 *
 * Note: the production `check_transaction` endpoints only accept requests from IP
 * addresses located in Cambodia. From elsewhere you will receive HTTP 403.
 */
enum class BakongEnvironment(val baseUrl: String) {
    /** Production: https://api-bakong.nbc.gov.kh */
    PRODUCTION("https://api-bakong.nbc.gov.kh"),

    /** Sandbox / SIT: https://sit-api-bakong.nbc.gov.kh */
    SANDBOX("https://sit-api-bakong.nbc.gov.kh"),
}

/**
 * Details of a paid transaction returned by [KHQR.getPayment].
 * Unknown fields returned by the API are ignored.
 */
@Serializable
data class PaymentInfo(
    val hash: String? = null,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
    val currency: String? = null,
    val amount: Double? = null,
    val description: String? = null,
    val createdDateMs: Long? = null,
    val acknowledgedDateMs: Long? = null,
    val trackingStatus: String? = null,
    val receiverBank: String? = null,
    val receiverBankAccount: String? = null,
    val instructionRef: String? = null,
    val externalRef: String? = null,
)

/** Internal: a single entry of a bulk transaction check response. */
@Serializable
internal data class BulkPaymentEntry(
    val md5: String? = null,
    @SerialName("status") val status: String? = null,
)
