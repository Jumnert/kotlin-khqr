package dev.khqr.internal

/**
 * EMVCo / Bakong KHQR tag identifiers and constant values.
 *
 * These mirror the EMVCo QR Code Specification for Payment Systems as adopted by
 * the National Bank of Cambodia (NBC) for the Bakong KHQR standard.
 */
internal object Emv {
    // Point of Initiation values (tag 01 payload)
    const val POI_STATIC = "11"
    const val POI_DYNAMIC = "12"

    // Tags
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

    // Sub-tag for the global unique identifier template (tag 29)
    const val SUBTAG_ACCOUNT_ID = "00"

    // Additional data field sub-tags (tag 62)
    const val SUBTAG_BILL_NUMBER = "01"
    const val SUBTAG_MOBILE_NUMBER = "02"
    const val SUBTAG_STORE_LABEL = "03"
    const val SUBTAG_TERMINAL_LABEL = "07"

    // Timestamp sub-tags (tag 99)
    const val SUBTAG_TIMESTAMP_CREATION = "00"
    const val SUBTAG_TIMESTAMP_EXPIRATION = "01"

    // Defaults
    const val PAYLOAD_FORMAT_INDICATOR_VALUE = "01"
    const val DEFAULT_MERCHANT_CATEGORY_CODE = "5999"
    const val DEFAULT_COUNTRY_CODE = "KH"
    const val DEFAULT_MERCHANT_CITY = "Phnom Penh"

    // CRC tag + length placeholder included in the CRC computation ("6304")
    const val CRC_TAG_AND_LENGTH = "6304"

    // Maximum lengths (EMVCo / Bakong constraints)
    const val MAX_LEN_ACCOUNT_ID = 32
    const val MAX_LEN_MERCHANT_NAME = 25
    const val MAX_LEN_MERCHANT_CITY = 15
    const val MAX_LEN_AMOUNT = 13
    const val MAX_LEN_BILL_NUMBER = 25
    const val MAX_LEN_MOBILE_NUMBER = 25
    const val MAX_LEN_STORE_LABEL = 25
    const val MAX_LEN_TERMINAL_LABEL = 25
}
