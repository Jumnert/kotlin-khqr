package dev.khqr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Renders KHQR strings into scannable QR code images using ZXing.
 *
 * The KHQR *string* itself is produced by [KHQR.createQr]; this utility only turns
 * that string into a picture. To present it to a customer, place the image inside a
 * card UI that follows NBC's official KHQR branding guideline.
 */
object QrImage {

    /** Encode [qr] into a square PNG and return the raw bytes. */
    fun pngBytes(qr: String, size: Int = 512, margin: Int = 1): ByteArray {
        val image = MatrixToImageWriter.toBufferedImage(bitMatrix(qr, size, margin))
        val out = ByteArrayOutputStream()
        if (!ImageIO.write(image, "PNG", out)) {
            throw KHQRException("Failed to encode QR image as PNG (no suitable writer found).")
        }
        return out.toByteArray()
    }

    /** Encode [qr] into a PNG saved at [outputPath]; returns the absolute path. */
    fun save(qr: String, outputPath: String, size: Int = 512, margin: Int = 1): String {
        val path: Path = Paths.get(outputPath)
        path.parent?.let { Files.createDirectories(it) }
        Files.write(path, pngBytes(qr, size, margin))
        return path.toAbsolutePath().toString()
    }

    /** Base64-encoded PNG (no data-URI prefix). */
    fun base64(qr: String, size: Int = 512, margin: Int = 1): String =
        Base64.getEncoder().encodeToString(pngBytes(qr, size, margin))

    /** A `data:image/png;base64,...` URI, ready to drop into an `<img src>`. */
    fun dataUri(qr: String, size: Int = 512, margin: Int = 1): String =
        "data:image/png;base64,${base64(qr, size, margin)}"

    private fun bitMatrix(qr: String, size: Int, margin: Int): BitMatrix {
        if (size <= 0) throw KHQRException("QR image size must be a positive number of pixels.")
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to margin,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        return try {
            QRCodeWriter().encode(qr, BarcodeFormat.QR_CODE, size, size, hints)
        } catch (e: WriterException) {
            throw KHQRException("Failed to render QR image: ${e.message}", e)
        }
    }
}
