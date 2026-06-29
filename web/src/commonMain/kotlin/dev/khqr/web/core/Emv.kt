package dev.khqr.web.core

/**
 * EMVCo / Bakong KHQR tag identifiers and constants.
 *
 * This is a multiplatform port of the same constants used by the `kotlin-bakong`
 * JVM library. It is duplicated here (rather than depending on the JVM artifact)
 * because Kotlin/JS cannot consume JVM bytecode. The [QrEncoderTest] golden vector
 * guarantees this port produces byte-identical output to the JVM library.
 */
internal object Emv {
    const val POI_STATIC = "11"
    const val POI_DYNAMIC = "12"

    const val TAG_PAYLOAD_FORMAT_INDICATOR = "00"
    const val TAG_POINT_OF_INITIATION = "01"
    const val TAG_MERCHANT_ACCOUNT_INFORMATION_INDIVIDUAL = "29"
    const val TAG_MERCHANT_CATEGORY_CODE = "52"
    const val TAG_TRANSACTION_CURRENCY = "53"
    const val TAG_TRANSACTION_AMOUNT = "54"
    const val TAG_COUNTRY_CODE = "58"
    const val TAG_MERCHANT_NAME = "59"
    const val TAG_MERCHANT_CITY = "60"
    const val TAG_ADDITIONAL_DATA = "62"
    const val TAG_TIMESTAMP = "99"
    const val TAG_CRC = "63"

    const val SUBTAG_ACCOUNT_ID = "00"

    const val SUBTAG_BILL_NUMBER = "01"
    const val SUBTAG_MOBILE_NUMBER = "02"
    const val SUBTAG_STORE_LABEL = "03"
    const val SUBTAG_TERMINAL_LABEL = "07"

    const val SUBTAG_TIMESTAMP_CREATION = "00"
    const val SUBTAG_TIMESTAMP_EXPIRATION = "01"

    const val PAYLOAD_FORMAT_INDICATOR_VALUE = "01"
    const val DEFAULT_MERCHANT_CATEGORY_CODE = "5999"
    const val DEFAULT_COUNTRY_CODE = "KH"
    const val DEFAULT_MERCHANT_CITY = "Phnom Penh"

    const val CRC_TAG_AND_LENGTH = "6304"

    const val MAX_LEN_ACCOUNT_ID = 32
    const val MAX_LEN_MERCHANT_NAME = 25
    const val MAX_LEN_MERCHANT_CITY = 15
    const val MAX_LEN_AMOUNT = 13
    const val MAX_LEN_BILL_NUMBER = 25
    const val MAX_LEN_MOBILE_NUMBER = 25
    const val MAX_LEN_STORE_LABEL = 25
    const val MAX_LEN_TERMINAL_LABEL = 25
}

/** Currencies supported by Bakong KHQR with their ISO 4217 numeric codes. */
enum class KHQRCurrency(val numericCode: String) {
    USD("840"),
    KHR("116");

    companion object {
        fun fromString(value: String): KHQRCurrency = when (value.uppercase()) {
            "USD" -> USD
            "KHR" -> KHR
            else -> throw IllegalArgumentException("Invalid currency '$value'. Use USD or KHR.")
        }
    }
}
