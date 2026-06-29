# kotlin-khqr

**Kotlin/JVM library for Bakong KHQR** — Cambodia's national QR payment standard by the National Bank of Cambodia (NBC).

Generate EMVCo-compliant KHQR payment strings, MD5 transaction hashes, scannable QR images, and deep links. Verify payments through the Bakong Open API — all with a tiny dependency footprint, no Spring required.

[![JitPack](https://jitpack.io/v/Jumnert/kotlin-khqr.svg)](https://jitpack.io/#Jumnert/kotlin-khqr)

> ⚠️ **Unofficial & educational.** Not affiliated with or endorsed by the NBC. Always test in the sandbox before handling real money. See [LICENSE](LICENSE).

---

## Features

- ✅ KHQR string generation — EMVCo TLV encoding, CRC-16/CCITT, static & dynamic QRs, USD & KHR
- ✅ MD5 transaction hash — the identifier used to check payments
- ✅ QR images — PNG bytes, file, base64, or `data:` URI (via ZXing)
- ✅ Bakong Open API — check payment, bulk check, payment details, deep link, renew token
- ✅ Bakong Relay (RBK) support — call the API from outside Cambodia
- ✅ Tiny footprint — JDK built-in HTTP client + `kotlinx-serialization-json` + `zxing` only
- ✅ Java-friendly — `@JvmOverloads` on all constructors and methods

---

## Requirements

- JDK 17+
- A **Bakong account** with full KYC verification (Bakong mobile app)
- A **Bakong Developer Token** (bearer token) for transaction-checking endpoints
- For production transaction checks: a **Cambodia IP address**, or a **Bakong Relay token**

---

## Installation

Add JitPack to `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency in `build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

dependencies {
    implementation("com.github.Jumnert:kotlin-khqr:0.2.0")
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_21 }
}
```

> Use `main-SNAPSHOT` for the latest, or pin to a specific commit hash or tag (e.g. `0.2.0`).

**Maven:**

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
<dependency>
  <groupId>com.github.Jumnert</groupId>
  <artifactId>kotlin-khqr</artifactId>
  <version>0.2.0</version>
</dependency>
```

---

## Get a Developer Token

Transaction-checking endpoints require a bearer token (`eyJ...` JWT):

1. KYC-verify your account in the **Bakong mobile app**.
2. Register at <https://api-bakong.nbc.gov.kh/register/> with your email.
3. You'll receive a bearer token. Pass it when constructing `KHQR`:

```kotlin
val khqr = KHQR("your_developer_token")
```

Tokens expire — renew programmatically with [`renewToken()`](#renew-token) or re-issue from the portal.

> 🔒 Keep your token secret. Don't commit it to version control.

> 🌐 **Outside Cambodia?** Use a [Bakong Relay (RBK) token](https://bakongrelay.com/). Any token starting with `rbk` automatically routes to `https://api.bakongrelay.com`, bypassing the Cambodia IP restriction.

---

## Quick Start

```kotlin
import dev.khqr.KHQR
import dev.khqr.KHQRCurrency
import dev.khqr.QrImage

val khqr = KHQR("your_developer_token")

// Generate a dynamic QR for 100 KHR
val qr = khqr.createQr(
    accountId = "your_name@bank",   // your Bakong account ID (from the mobile app)
    merchantName = "Your Shop",
    merchantCity = "Phnom Penh",
    amount = 100.0,
    currency = KHQRCurrency.KHR,
)

val md5 = khqr.generateMd5(qr)     // MD5 hash — use this to verify payment later
QrImage.save(qr, "out/khqr.png")   // save QR as PNG
```

---

## Usage

### Create a QR

```kotlin
// Dynamic QR — fixed amount, expires after 1 day by default
val qr = khqr.createQr(
    accountId = "your_name@bank",
    merchantName = "Your Shop",
    merchantCity = "Phnom Penh",
    amount = 9800.0,
    currency = KHQRCurrency.KHR,
    billNumber = "INV-001",
    expirationDays = 1,
)

// Static QR — reusable, payer enters the amount
val staticQr = khqr.createQr(
    accountId = "your_name@bank",
    merchantName = "Your Shop",
    merchantCity = "Phnom Penh",
    static = true,
)
```

**Parameters:**

| Parameter        | Type           | Default        | Notes                               |
|------------------|----------------|----------------|-------------------------------------|
| `accountId`      | `String`       | required       | Bakong account ID, e.g. `name@bank` (≤32) |
| `merchantName`   | `String`       | required       | ≤25 characters                      |
| `merchantCity`   | `String`       | `"Phnom Penh"` | ≤15 characters                      |
| `amount`         | `Double`       | `0.0`          | `≤ 0` produces a static QR          |
| `currency`       | `KHQRCurrency` | `KHR`          | `KHQRCurrency.USD` or `.KHR`        |
| `billNumber`     | `String?`      | `null`         | Invoice/order reference             |
| `storeLabel`     | `String?`      | `null`         | Store name label                    |
| `phoneNumber`    | `String?`      | `null`         | Cambodian numbers normalised automatically |
| `terminalLabel`  | `String?`      | `null`         | POS terminal ID                     |
| `static`         | `Boolean`      | `false`        | Force a static (reusable) QR        |
| `expirationDays` | `Int`          | `1`            | Validity for dynamic QRs            |

### QR Image

```kotlin
khqr.qrImage(qr)                   // ByteArray (PNG, 512px)
QrImage.pngBytes(qr, size = 300)   // ByteArray, custom size
QrImage.save(qr, "out/qr.png")     // write PNG to file, returns path
QrImage.base64(qr)                 // base64-encoded PNG string
QrImage.dataUri(qr)                // "data:image/png;base64,..." — embed in <img src>
```

### MD5 Hash

```kotlin
val md5 = khqr.generateMd5(qr)
// e.g. "3dc50c785e47a215feb336d44807825c"
// Use this hash to check or look up a payment.
```

### Check a Payment

Requires a valid token and a Cambodia IP (or RBK token).

```kotlin
import dev.khqr.TransactionStatus

when (khqr.checkPayment(md5)) {
    TransactionStatus.PAID   -> println("Payment confirmed ✅")
    TransactionStatus.UNPAID -> println("Not paid yet")
}
```

### Get Payment Details

Useful for static QRs where the payer sets the amount:

```kotlin
val info = khqr.getPayment(md5)   // PaymentInfo? — null if unpaid or not found
if (info != null) {
    println("${info.amount} ${info.currency} from ${info.fromAccountId}")
}
```

`PaymentInfo` fields: `hash`, `fromAccountId`, `toAccountId`, `currency`, `amount`, `description`, `createdDateMs`, `acknowledgedDateMs`, `trackingStatus`, `receiverBank`, `receiverBankAccount`, `instructionRef`, `externalRef`.

### Bulk Check (up to 50)

```kotlin
val paidHashes: List<String> = khqr.checkBulkPayments(listOf(md5a, md5b, md5c))
// returns only the hashes that are PAID

// For more than 50, chunk first:
val allPaid = allMd5s.chunked(50).flatMap { khqr.checkBulkPayments(it) }
```

### Deep Link

Opens the payer's Bakong or banking app directly:

```kotlin
val link = khqr.generateDeeplink(
    qr = qr,
    callbackUrl = "https://your-site.com/checkout/return",
    appIconUrl = "https://your-site.com/logo.png",
    appName = "YourApp",
)   // returns a "https://bakong.page.link/..." URL, or null
```

### Renew Token

```kotlin
val freshToken = KHQR().renewToken("your_registered_email@example.com")
val khqr = KHQR(freshToken)
```

---

## Environments

```kotlin
import dev.khqr.BakongEnvironment

KHQR(token, BakongEnvironment.PRODUCTION)  // default — https://api-bakong.nbc.gov.kh
KHQR(token, BakongEnvironment.SANDBOX)     // https://sit-api-bakong.nbc.gov.kh
```

> Production transaction checks **only accept requests from Cambodia IP addresses**. HTTP 403 from other IPs is surfaced as a `KHQRException`. QR generation, MD5, and image rendering are fully offline and need no token or special IP.

---

## Web Demo (Kotlin/JS)

The [`web/`](web/) folder is a Kotlin/JS browser demo — generates KHQR codes, MD5 hashes, and QR images entirely in the browser. See [`web/README.md`](web/README.md).

```bash
cd web && ./gradlew jsBrowserDistribution
```

---

## Building from Source

```bash
git clone https://github.com/Jumnert/kotlin-khqr.git
cd kotlin-khqr
./gradlew build    # compile + all tests (fully offline, no token needed)
```

Requires JDK 17+. Kotlin 2.4.0, Gradle 9.6.1 (via wrapper), JVM-17 bytecode output.

---

## Releasing

Bump `version` in `build.gradle.kts`, then tag and push:

```bash
git tag 0.2.0 && git push origin 0.2.0
```

This triggers the [Release workflow](.github/workflows/release.yml), which publishes to GitHub Packages and creates a GitHub Release with jars attached. JitPack picks up the tag automatically.

---

## Resources

- Bakong Open API portal & token registration: <https://api-bakong.nbc.gov.kh/>
- API documentation: <https://api-bakong.nbc.gov.kh/document>
- Sandbox (SIT) API: <https://sit-api-bakong.nbc.gov.kh/>
- Bakong Relay (outside Cambodia): <https://bakongrelay.com/>
- [Bakong Open API Document (PDF)](https://bakong.nbc.gov.kh/download/KHQR/integration/Bakong%20Open%20API%20Document.pdf)

---

## License

[Educational Use License](LICENSE) — unofficial, not affiliated with the National Bank of Cambodia.
