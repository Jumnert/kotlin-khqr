package dev.khqr.web

import dev.khqr.web.core.KHQRCurrency
import dev.khqr.web.core.QrEncoder
import dev.khqr.web.core.QrParams
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.fetch.RequestInit

// Helper JS functions defined in index.html (Material UI + QR rendering).
private external fun renderKhqr(text: String)
private external fun showToast(message: String)
private external fun copyText(text: String)

fun main() {
    // The bundle is loaded at the end of <body>, so DOMContentLoaded may already
    // have fired — in that case wire up immediately.
    val state = document.asDynamic().readyState as String
    if (state == "loading") {
        window.addEventListener("DOMContentLoaded", { setup() })
    } else {
        setup()
    }
}

private fun setup() {
    js("M.AutoInit()")
    byId("generateBtn")?.addEventListener("click", { onGenerate() })
    byId("checkBtn")?.addEventListener("click", { onCheck() })
    byId("copyQrBtn")?.addEventListener("click", { copyText(text("qrString")) })
    byId("copyMd5Btn")?.addEventListener("click", { copyText(text("md5Value")) })
}

private fun onGenerate() {
    try {
        val params = QrParams(
            accountId = value("accountId").trim(),
            merchantName = value("merchantName").trim(),
            merchantCity = value("merchantCity").trim().ifEmpty { "Phnom Penh" },
            amount = value("amount").toDoubleOrNull() ?: 0.0,
            currency = KHQRCurrency.fromString(value("currency")),
            storeLabel = value("storeLabel").trim().ifEmpty { null },
            phoneNumber = value("phoneNumber").trim().ifEmpty { null },
            billNumber = value("billNumber").trim().ifEmpty { null },
            terminalLabel = value("terminalLabel").trim().ifEmpty { null },
            static = checked("staticQr"),
            expirationDays = value("expiration").toIntOrNull() ?: 1,
        )

        val result = QrEncoder.generate(params, kotlin.js.Date.now().toLong())

        setText("qrString", result.qr)
        setText("md5Value", result.md5)
        renderKhqr(result.qr)

        // Populate the KHQR card.
        val amount = value("amount").toDoubleOrNull() ?: 0.0
        val isStatic = checked("staticQr") || amount <= 0.0
        val symbol = if (params.currency == KHQRCurrency.USD) "$" else "\u17DB" // $ or ៛
        setText("cardMerchant", params.merchantName)
        setText("cardSym", symbol)
        setText("qrBadge", symbol)
        setText("cardAmount", if (isStatic) "0" else (amount.asDynamic().toFixed(2) as String))

        show("resultCard")
        // pre-fill the MD5 used by the payment checker
        (byId("checkMd5") as? HTMLInputElement)?.value = result.md5
        js("M.updateTextFields()")
        showToast("QR generated")
    } catch (e: Throwable) {
        showToast("Could not generate: ${e.message}")
    }
}

private fun onCheck() {
    val token = value("token").trim()
    val md5 = value("checkMd5").trim()
    val env = value("environment")

    if (token.isEmpty()) {
        showToast("Enter your Bakong Developer Token to check a payment")
        return
    }
    if (md5.isEmpty()) {
        showToast("Generate a QR first (or paste an MD5 hash)")
        return
    }

    val base = when {
        token.startsWith("rbk") -> "https://api.bakongrelay.com"
        env == "SANDBOX" -> "https://sit-api-bakong.nbc.gov.kh"
        else -> "https://api-bakong.nbc.gov.kh"
    }

    setStatus("Checking…", "grey-text")

    window.fetch(
        "$base/v1/check_transaction_by_md5",
        RequestInit(
            method = "POST",
            headers = kotlin.js.json(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer $token",
            ),
            body = kotlin.js.JSON.stringify(kotlin.js.json("md5" to md5)),
        ),
    ).then { resp -> resp.asDynamic().json() }
        .then { data: dynamic ->
            val code = data.responseCode
            if (code == 0) {
                setStatus("PAID ✅", "green-text")
            } else {
                val message = data.responseMessage ?: "Not found / not paid yet"
                setStatus("UNPAID — $message", "orange-text")
            }
            Unit
        }
        .catch { err ->
            setStatus(
                "Request blocked. Browsers usually cannot call the Bakong API directly " +
                    "(CORS), and production checks require a Cambodia IP or an rbk… Relay token. " +
                    "See the note below. (${err.message})",
                "red-text",
            )
            Unit
        }
}

// --- small DOM helpers ---

private fun byId(id: String): HTMLElement? = document.getElementById(id) as? HTMLElement

private fun value(id: String): String = when (val el = document.getElementById(id)) {
    is HTMLInputElement -> el.value
    is HTMLSelectElement -> el.value
    else -> ""
}

private fun checked(id: String): Boolean = (document.getElementById(id) as? HTMLInputElement)?.checked ?: false

private fun text(id: String): String = byId(id)?.textContent ?: ""

private fun setText(id: String, value: String) {
    byId(id)?.textContent = value
}

private fun show(id: String) {
    byId(id)?.style?.display = "block"
}

private fun setStatus(message: String, cssClass: String) {
    val el = byId("checkStatus") ?: return
    el.className = "status $cssClass"
    el.textContent = message
}
