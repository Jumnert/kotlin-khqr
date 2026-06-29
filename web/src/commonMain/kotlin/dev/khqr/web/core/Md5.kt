package dev.khqr.web.core

/**
 * Pure-Kotlin MD5 (RFC 1321), multiplatform-safe (no `java.security`).
 *
 * Used by the browser build to compute the KHQR transaction hash client-side.
 * Verified against known vectors and the KHQR golden hash in `QrEncoderTest`.
 */
internal object Md5 {

    private val SHIFTS = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )

    // T[i] = floor(2^32 * abs(sin(i + 1)))
    private val K = longArrayOf(
        0xd76aa478, 0xe8c7b756, 0x242070db, 0xc1bdceee,
        0xf57c0faf, 0x4787c62a, 0xa8304613, 0xfd469501,
        0x698098d8, 0x8b44f7af, 0xffff5bb1, 0x895cd7be,
        0x6b901122, 0xfd987193, 0xa679438e, 0x49b40821,
        0xf61e2562, 0xc040b340, 0x265e5a51, 0xe9b6c7aa,
        0xd62f105d, 0x02441453, 0xd8a1e681, 0xe7d3fbc8,
        0x21e1cde6, 0xc33707d6, 0xf4d50d87, 0x455a14ed,
        0xa9e3e905, 0xfcefa3f8, 0x676f02d9, 0x8d2a4c8a,
        0xfffa3942, 0x8771f681, 0x6d9d6122, 0xfde5380c,
        0xa4beea44, 0x4bdecfa9, 0xf6bb4b60, 0xbebfbc70,
        0x289b7ec6, 0xeaa127fa, 0xd4ef3085, 0x04881d05,
        0xd9d4d039, 0xe6db99e5, 0x1fa27cf8, 0xc4ac5665,
        0xf4292244, 0x432aff97, 0xab9423a7, 0xfc93a039,
        0x655b59c3, 0x8f0ccc92, 0xffeff47d, 0x85845dd1,
        0x6fa87e4f, 0xfe2ce6e0, 0xa3014314, 0x4e0811a1,
        0xf7537e82, 0xbd3af235, 0x2ad7d2bb, 0xeb86d391,
    )

    private const val HEX = "0123456789abcdef"

    /** Lowercase 32-character MD5 hex digest of [message] bytes. */
    fun digestHex(message: ByteArray): String {
        var a0 = 0x67452301
        var b0 = 0xefcdab89.toInt()
        var c0 = 0x98badcfe.toInt()
        var d0 = 0x10325476

        val originalLenBits = message.size.toLong() * 8L
        val paddingLen = ((56 - (message.size + 1) % 64) + 64) % 64
        val total = message.size + 1 + paddingLen + 8
        val msg = ByteArray(total)
        message.copyInto(msg)
        msg[message.size] = 0x80.toByte()
        for (i in 0 until 8) {
            msg[total - 8 + i] = ((originalLenBits ushr (8 * i)) and 0xFF).toByte()
        }

        var offset = 0
        while (offset < total) {
            val m = IntArray(16)
            for (i in 0 until 16) {
                val j = offset + i * 4
                m[i] = (msg[j].toInt() and 0xFF) or
                    ((msg[j + 1].toInt() and 0xFF) shl 8) or
                    ((msg[j + 2].toInt() and 0xFF) shl 16) or
                    ((msg[j + 3].toInt() and 0xFF) shl 24)
            }

            var a = a0
            var b = b0
            var c = c0
            var d = d0

            for (i in 0 until 64) {
                val f: Int
                val g: Int
                when {
                    i < 16 -> {
                        f = (b and c) or (b.inv() and d)
                        g = i
                    }
                    i < 32 -> {
                        f = (d and b) or (d.inv() and c)
                        g = (5 * i + 1) % 16
                    }
                    i < 48 -> {
                        f = b xor c xor d
                        g = (3 * i + 5) % 16
                    }
                    else -> {
                        f = c xor (b or d.inv())
                        g = (7 * i) % 16
                    }
                }
                val tmp = d
                d = c
                c = b
                val sum = a + f + K[i].toInt() + m[g]
                b += sum.rotateLeft(SHIFTS[i])
                a = tmp
            }

            a0 += a
            b0 += b
            c0 += c
            d0 += d
            offset += 64
        }

        return hexLE(a0) + hexLE(b0) + hexLE(c0) + hexLE(d0)
    }

    private fun hexLE(value: Int): String {
        val sb = StringBuilder(8)
        for (i in 0 until 4) {
            val byte = (value ushr (8 * i)) and 0xFF
            sb.append(HEX[(byte ushr 4) and 0xF])
            sb.append(HEX[byte and 0xF])
        }
        return sb.toString()
    }
}
