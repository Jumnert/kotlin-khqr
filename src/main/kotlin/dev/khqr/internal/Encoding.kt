package dev.khqr.internal

import java.security.MessageDigest
import java.util.Locale

/**
 * Low-level encoding primitives for the KHQR / EMVCo format.
 *
 * All length prefixes count *characters* (UTF-16 code units, which equal Unicode
 * code points for the Basic Multilingual Plane that Latin and Khmer text live in),
 * matching the reference KHQR implementations. Hash and CRC inputs are processed as
 * UTF-8 bytes.
 */
internal object Encoding {

    /**
     * Encode a single Tag-Length-Value field.
     *
     * The length is the character count of [value], zero-padded to two digits.
     */
    fun tlv(tag: String, value: String): String {
        val length = value.length.toString().padStart(2, '0')
        return "$tag$length$value"
    }

    /**
     * Compute the CRC-16/CCITT-FALSE checksum (polynomial 0x1021, initial value
     * 0xFFFF) over the UTF-8 bytes of [data].
     */
    fun crc16(data: String): Int {
        var crc = 0xFFFF
        val polynomial = 0x1021
        for (byte in data.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) {
                    (crc shl 1) xor polynomial
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }

    /**
     * Build the trailing CRC field for a KHQR payload.
     *
     * The checksum is computed over [data] plus the CRC tag/length ("6304"), exactly
     * as required by EMVCo, and rendered as a 4-character uppercase hex string.
     */
    fun crcField(data: String): String {
        val hex = String.format(Locale.US, "%04X", crc16(data + Emv.CRC_TAG_AND_LENGTH))
        return "${Emv.TAG_CRC}${Emv.CRC_TAG_AND_LENGTH.substring(2)}$hex"
    }

    /** Lowercase 32-character MD5 hex digest of the UTF-8 bytes of [data]. */
    fun md5(data: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(data.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Format a monetary [amount] the KHQR way: two decimal places with trailing
     * zeros (and a trailing decimal point) stripped. e.g. 9800.0 -> "9800",
     * 0.10 -> "0.1", 100.00 -> "100".
     */
    fun formatAmount(amount: Double): String {
        return String.format(Locale.US, "%.2f", amount)
            .trimEnd('0')
            .trimEnd('.')
    }

    /**
     * Normalise a Cambodian phone number to local format: keep digits only, strip a
     * leading "855" country code, and ensure a leading "0".
     */
    fun normalizePhone(phone: String): String {
        var digits = phone.filter { it.isDigit() }
        if (digits.startsWith("855")) {
            digits = digits.substring(3)
        }
        if (!digits.startsWith("0")) {
            digits = "0$digits"
        }
        return digits
    }
}
