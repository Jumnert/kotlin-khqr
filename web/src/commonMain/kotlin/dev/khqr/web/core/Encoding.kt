package dev.khqr.web.core

import kotlin.math.abs
import kotlin.math.round

/**
 * Multiplatform encoding primitives for the KHQR format. Length prefixes count
 * characters; CRC and MD5 operate on UTF-8 bytes — identical to the JVM library.
 */
internal object Encoding {

    fun tlv(tag: String, value: String): String {
        val length = value.length.toString().padStart(2, '0')
        return "$tag$length$value"
    }

    /** CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF) over the UTF-8 bytes of [data]. */
    fun crc16(data: String): Int {
        var crc = 0xFFFF
        val polynomial = 0x1021
        for (byte in data.encodeToByteArray()) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor polynomial else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }

    /** Trailing CRC field: "6304" + 4 uppercase hex chars over [data] + "6304". */
    fun crcField(data: String): String {
        val crc = crc16(data + Emv.CRC_TAG_AND_LENGTH)
        val hex = crc.toString(16).uppercase().padStart(4, '0')
        return "${Emv.TAG_CRC}${Emv.CRC_TAG_AND_LENGTH.substring(2)}$hex"
    }

    /** Lowercase 32-character MD5 of the UTF-8 bytes of [data]. */
    fun md5(data: String): String = Md5.digestHex(data.encodeToByteArray())

    /**
     * Format an amount to two decimals then strip trailing zeros and any trailing
     * decimal point. Multiplatform replacement for `String.format("%.2f", …)`.
     * e.g. 9800.0 -> "9800", 0.1 -> "0.1", 100.0 -> "100".
     */
    fun formatAmount(amount: Double): String {
        val cents = round(abs(amount) * 100.0).toLong()
        val whole = cents / 100
        val frac = (cents % 100).toInt()
        val fracStr = frac.toString().padStart(2, '0')
        return "$whole.$fracStr".trimEnd('0').trimEnd('.')
    }

    /** Normalise a Cambodian phone number: digits only, strip leading 855, ensure leading 0. */
    fun normalizePhone(phone: String): String {
        var digits = phone.filter { it.isDigit() }
        if (digits.startsWith("855")) digits = digits.substring(3)
        if (!digits.startsWith("0")) digits = "0$digits"
        return digits
    }
}
