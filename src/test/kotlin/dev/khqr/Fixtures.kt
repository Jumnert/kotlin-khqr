package dev.khqr

/**
 * Golden test vectors.
 *
 * [GOLDEN_QR] is the reference KHQR string documented by the bakong-khqr project.
 * It is produced from the parameters in [goldenParams] at the fixed creation
 * timestamp [GOLDEN_NOW_MS] with a 2-day expiry. Its trailing CRC ("A5A3") and its
 * MD5 ([GOLDEN_MD5]) were independently verified.
 */
object Fixtures {
    const val GOLDEN_NOW_MS = 1_773_894_603_019L

    /**
     * The expiration timestamp embedded in the documented reference string. Note the
     * reference example predates the maintained SDK's day→millisecond fix, so this
     * value is injected directly to reproduce the documented bytes exactly. The
     * production day-based formula is locked separately in [QrEncoderTest].
     */
    const val GOLDEN_EXPIRATION_MS = 1_773_894_775_819L

    const val GOLDEN_QR =
        "00020101021229180014your_name@bank520459995303116540498005802KH5909Your Name" +
            "6010Phnom Penh62510109TRX01234502090123456780311Phsar Thmei0706POS-01" +
            "993400131773894603019011317738947758196304A5A3"

    const val GOLDEN_MD5 = "3dc50c785e47a215feb336d44807825c"
}
