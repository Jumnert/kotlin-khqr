# kotlin-bakong

A Kotlin library for **Bakong KHQR** — Cambodia's national QR payment standard from the
National Bank of Cambodia (NBC). Generate EMVCo-compliant KHQR payment strings, MD5
transaction hashes, deep links, and scannable QR images, then verify payments through
the **Bakong Open API**.

> 🇰🇭 If you build on the JVM (Kotlin, or Java via interop) and want to accept Bakong /
> KHQR payments, this library gives you the QR generation and transaction-checking
> pieces with **no Spring required** and only a tiny dependency footprint.

> ⚠️ **Unofficial & educational.** This is a community project, not affiliated with or
> endorsed by the NBC. "Bakong" and "KHQR" are NBC marks. See [LICENSE](LICENSE).
> Always test in the sandbox before handling real money.

---

## Table of Contents

- [Features](#features)
- [Web demo (Kotlin/JS → Vercel)](#web-demo-kotlinjs--vercel)
- [Requirements](#requirements)
- [Get a Bakong Developer Token (Bearer Token)](#get-a-bakong-developer-token-bearer-token)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Usage](#usage)
  - [Create a QR](#create-a-qr)
  - [`createQr` parameters](#createqr-parameters)
  - [MD5 hash](#md5-hash)
  - [QR image](#qr-image)
  - [Deep link](#deep-link)
  - [Check a payment](#check-a-payment)
  - [Get payment details](#get-payment-details)
  - [Check many payments at once](#check-many-payments-at-once)
  - [Polling for payment](#polling-for-payment)
  - [Renew token](#renew-token)
- [Environments & IP restriction](#environments--ip-restriction)
- [KHQR card UI & branding](#khqr-card-ui--branding)
- [Building from source](#building-from-source)
- [Releasing](#releasing)
- [Continuous Integration](#continuous-integration)
- [Resources](#resources)
- [License](#license)

---

## Features

- ✅ **KHQR string generation** — EMVCo TLV encoding with CRC-16/CCITT, static & dynamic QRs, USD/KHR.
- ✅ **MD5 transaction hash** — the identifier used to check payments.
- ✅ **QR images** — PNG bytes, file, base64, or `data:` URI (via ZXing).
- ✅ **Bakong Open API client** — check payment, bulk check, get payment details, deep link, renew token.
- ✅ **Bakong Relay (RBK) support** — pass an `rbk...` token to automatically target the Relay API.
- ✅ **Tiny footprint** — uses the JDK's built-in HTTP client; only `kotlinx-serialization-json` + `zxing`.
- ✅ **Java-friendly** — `@JvmOverloads` constructors/methods.

## Web demo (Kotlin/JS → Vercel)

The [`web/`](web/) folder is a **Kotlin/JS** browser demo with a simple Material UI. It
generates KHQR codes, the MD5 hash, and a scannable QR image **entirely in the browser**,
and lets you check a payment with your own credentials. See [`web/README.md`](web/README.md).

```bash
cd web
./gradlew jsBrowserDistribution                 # -> build/dist/js/productionExecutable/
npx vercel deploy build/dist/js/productionExecutable --prod
```

> ℹ️ **Hosting note.** Vercel runs static sites and serverless functions — **not** a JVM
> process — so a Ktor/JVM server can't run on Vercel. The demo sidesteps this by compiling
> **Kotlin to a static site**, which Vercel (or Netlify, Cloudflare Pages, GitHub Pages)
> serves natively. The pure KHQR logic is shared in `commonMain` and verified to match this
> library byte-for-byte. If you need server-side transaction checks (Cambodia IP / no CORS),
> host a small JVM service using this library on **Railway / Render / Fly.io / Cloud Run**.

## Requirements

- **JDK 17+** (the library is compiled to JVM 17 bytecode).
- A **Bakong account** with full KYC verification (use the Bakong mobile app).
- A **Bakong Developer Token** (bearer token) — see below.
- For transaction checks in production: a server with a **Cambodia IP address**, *or* a
  **Bakong Relay (RBK) token**. See [Environments & IP restriction](#environments--ip-restriction).

## Get a Bakong Developer Token (Bearer Token)

The transaction-checking endpoints require a **bearer token**. To get one:

1. Create and **KYC-verify** a Bakong account in the Bakong mobile app.
2. Go to the **Bakong Open API portal**: <https://api-bakong.nbc.gov.kh/> and register with
   your email (registration page: <https://api-bakong.nbc.gov.kh/register/>).
3. You'll receive a **bearer token** (a long `eyJ...` JWT). Use it when constructing `KHQR`:

   ```kotlin
   val khqr = KHQR("eyJhbGciOiJIUzI1NiIsInR5cCI6...your_token")
   ```

4. Tokens expire. You can request a fresh one programmatically with
   [`renewToken(email)`](#renew-token), or re-issue it from the portal.

> 🔒 **Keep your token secret.** Don't commit it to version control. Load it from an
> environment variable or a secrets manager. (`.env`, `*.local`, and `secrets.properties`
> are already git-ignored in this repo.)

> 💡 Alternatively, a **Bakong Relay (RBK)** token (from <https://bakongrelay.com/>) lets you
> call the API from outside Cambodia. Pass it the same way — any token starting with `rbk`
> automatically routes to the Relay API.

## Installation

The artifact id is **`kotlin-bakong`**, version **`0.1.0`**.

### Option A — JitPack (easiest, zero setup)

[![](https://jitpack.io/v/your-username/kotlin-bakong.svg)](https://jitpack.io/#your-username/kotlin-bakong)

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

`build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.your-username:kotlin-bakong:0.1.0")
}
```

> Replace `your-username` with your GitHub username/org. You can use a release tag
> (`0.1.0`), a branch (`main-SNAPSHOT`), or a commit hash as the version.

### Option B — GitHub Packages

Published automatically by the [release workflow](#releasing) under group `dev.khqr`:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/your-username/kotlin-bakong")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
dependencies {
    implementation("dev.khqr:kotlin-bakong:0.1.0")
}
```

### Option C — Maven Central

Requires owning the group coordinate — see [Releasing → Maven Central](#publishing-to-maven-central).

### Maven

```xml
<dependency>
  <groupId>com.github.your-username</groupId>
  <artifactId>kotlin-bakong</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Quick Start

```kotlin
import dev.khqr.KHQR
import dev.khqr.KHQRCurrency
import dev.khqr.TransactionStatus

val khqr = KHQR(System.getenv("BAKONG_TOKEN"))

// 1. Create a dynamic QR for 100 USD
val qr = khqr.createQr(
    accountId = "your_name@bank",   // your Bakong account id (see the mobile app)
    merchantName = "Your Shop",
    merchantCity = "Phnom Penh",
    amount = 100.0,
    currency = KHQRCurrency.USD,
    billNumber = "INV-0001",
)

// 2. Hash + image
val md5 = khqr.generateMd5(qr)
val pngBytes = khqr.qrImage(qr)          // ByteArray (PNG) to show the customer

// 3. After the customer pays, verify it
if (khqr.checkPayment(md5) == TransactionStatus.PAID) {
    println("Paid! ✅")
}
```

## Usage

### Create a QR

```kotlin
// Dynamic QR (fixed amount, expires)
val dynamic = khqr.createQr(
    accountId = "your_name@bank",
    merchantName = "Your Shop",
    merchantCity = "Phnom Penh",
    amount = 9800.0,
    currency = KHQRCurrency.KHR,
    storeLabel = "Phsar Thmei",
    phoneNumber = "012345678",
    billNumber = "TRX012345",
    terminalLabel = "POS-01",
    expirationDays = 1,
)

// Static QR (reusable, payer enters the amount)
val static = khqr.createQr(
    accountId = "your_name@bank",
    merchantName = "Your Shop",
    merchantCity = "Phnom Penh",
    static = true,   // or simply pass amount = 0.0
)
```

### `createQr` parameters

| Parameter        | Type           | Default        | Notes                                              |
|------------------|----------------|----------------|----------------------------------------------------|
| `accountId`      | `String`       | **required**   | Bakong account id, e.g. `your_name@bank` (≤32).    |
| `merchantName`   | `String`       | **required**   | ≤25 characters.                                    |
| `merchantCity`   | `String`       | `"Phnom Penh"` | ≤15 characters.                                    |
| `amount`         | `Double`       | `0.0`          | `≤ 0` produces a static QR.                         |
| `currency`       | `KHQRCurrency` | `KHR`          | `KHQRCurrency.USD` or `KHQRCurrency.KHR`.           |
| `storeLabel`     | `String?`      | `null`         | Optional.                                          |
| `phoneNumber`    | `String?`      | `null`         | Cambodian numbers are normalised (855→0…).         |
| `billNumber`     | `String?`      | `null`         | Optional bill/invoice reference.                   |
| `terminalLabel`  | `String?`      | `null`         | Optional terminal id.                              |
| `static`         | `Boolean`      | `false`        | Force a static (reusable) QR.                      |
| `expirationDays` | `Int`          | `1`            | Validity window for dynamic QRs.                   |

### MD5 hash

```kotlin
val md5 = khqr.generateMd5(qr)
// e.g. "3dc50c785e47a215feb336d44807825c"
```

### QR image

```kotlin
import dev.khqr.QrImage

khqr.qrImage(qr)                       // ByteArray (PNG, 512px)
QrImage.pngBytes(qr, size = 300)       // ByteArray
QrImage.save(qr, "out/khqr.png")       // writes a file, returns the path
QrImage.base64(qr)                     // base64 PNG (no prefix)
QrImage.dataUri(qr)                    // "data:image/png;base64,..." for <img src>
```

### Deep link

Turn a QR into a link that opens the payer's banking app (requires a token):

```kotlin
val link = khqr.generateDeeplink(
    qr = qr,
    callbackUrl = "https://your-site.com/checkout/return",
    appIconUrl = "https://your-site.com/logo.png",
    appName = "YourApp",
)   // e.g. "https://bakong.page.link/XXXX"  (null if Bakong returned none)
```

### Check a payment

```kotlin
when (khqr.checkPayment(md5)) {
    TransactionStatus.PAID   -> { /* fulfil the order */ }
    TransactionStatus.UNPAID -> { /* not paid yet / not found */ }
}
```

### Get payment details

Useful for static QRs where the payer chooses the amount:

```kotlin
val info = khqr.getPayment(md5)   // PaymentInfo? (null if unpaid/not found)
if (info != null) {
    println("${info.amount} ${info.currency} from ${info.fromAccountId}")
}
```

`PaymentInfo` exposes: `hash`, `fromAccountId`, `toAccountId`, `currency`, `amount`,
`description`, `createdDateMs`, `acknowledgedDateMs`, `trackingStatus`, `receiverBank`,
`receiverBankAccount`, `instructionRef`, `externalRef`.

### Check many payments at once

```kotlin
// Max 50 hashes per call (throws KHQRException above 50).
val paid: List<String> = khqr.checkBulkPayments(listOf(md5a, md5b, md5c))
// returns only the hashes that are PAID
```

Chunk larger lists yourself:

```kotlin
val allPaid = allMd5.chunked(50).flatMap { khqr.checkBulkPayments(it) }
```

### Polling for payment

The library keeps `checkPayment` simple (one call → one status). Implement polling with
a sensible backoff so you don't burn through API quota — for example:

```kotlin
import dev.khqr.TransactionStatus
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

suspend fun awaitPayment(khqr: KHQR, md5: String, timeout: kotlin.time.Duration = 10.minutes): Boolean {
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < timeout.inWholeMilliseconds) {
        if (khqr.checkPayment(md5) == TransactionStatus.PAID) return true
        val elapsedSec = (System.currentTimeMillis() - start) / 1000
        val delay = when {            // dynamic backoff
            elapsedSec <= 300  -> 5.seconds
            elapsedSec <= 900  -> 10.seconds
            elapsedSec <= 3600 -> 15.seconds
            else               -> 5.minutes
        }
        kotlinx.coroutines.delay(delay)
    }
    return false
}
```

### Renew token

```kotlin
val freshToken = KHQR().renewToken("your_registered_email@example.com")
val khqr = KHQR(freshToken)
```

## Environments & IP restriction

```kotlin
import dev.khqr.BakongEnvironment

KHQR(token, BakongEnvironment.PRODUCTION)  // https://api-bakong.nbc.gov.kh   (default)
KHQR(token, BakongEnvironment.SANDBOX)     // https://sit-api-bakong.nbc.gov.kh
```

- **Production transaction checks only accept requests from Cambodia IP addresses.**
  Calls from elsewhere return HTTP 403 (surfaced as a `KHQRException`).
- To call from outside Cambodia, use a **Bakong Relay (RBK) token** — any token starting
  with `rbk` is automatically sent to `https://api.bakongrelay.com`.
- QR/MD5/image generation are **fully offline** and need no token or special IP.

## KHQR card UI & branding

This library produces the QR **string and image** only. When you show KHQR to customers,
you must follow NBC's official **KHQR brand & content guidelines** and use only the
approved KHQR logos/assets obtained from the NBC. Do not recolor or distort them. This
project ships **no** NBC brand assets.

## Building from source

```bash
git clone https://github.com/your-username/kotlin-bakong.git
cd kotlin-bakong
./gradlew build              # compile + test
./gradlew test               # tests only
./gradlew publishToMavenLocal # install to ~/.m2 for local consumption
```

- Toolchain: Kotlin **2.4.0**, Gradle **9.6.1** (via the wrapper), JVM-17 bytecode.
- The tests are **offline** (golden EMVCo/CRC/MD5 vectors + an in-process HTTP server),
  so `./gradlew build` needs no network and no token.

## Releasing

Versioning lives in `build.gradle.kts` (`version = "0.1.0"`). To cut a release:

1. Bump `version` in `build.gradle.kts` and commit.
2. Tag and push:

   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

That tag triggers the [`Release` workflow](.github/workflows/release.yml), which builds,
publishes to **GitHub Packages**, and creates a **GitHub Release** with the jars attached.

### Publishing via JitPack

No workflow needed — JitPack builds on demand:

1. Push your code/tag to GitHub.
2. Visit `https://jitpack.io/#your-username/kotlin-bakong` and click **Get it** on the tag.
3. Consumers add the JitPack repo and `com.github.your-username:kotlin-bakong:<tag>`
   (see [Installation](#option-a--jitpack-easiest-zero-setup)).

`jitpack.yml` pins the build to JDK 17 and runs `publishToMavenLocal`.

### Publishing to GitHub Packages (manual)

```bash
export GITHUB_REPOSITORY="your-username/kotlin-bakong"
export GITHUB_ACTOR="your-username"
export GITHUB_TOKEN="ghp_your_personal_access_token"   # scope: write:packages
./gradlew publish
```

(In CI these are provided automatically; the `GitHubPackages` Maven repo only activates
when those three env vars are present, so local `publishToMavenLocal` is unaffected.)

### Publishing to Maven Central

1. Change `group` in `build.gradle.kts` to a coordinate you own — e.g.
   `io.github.<your-username>` (verify ownership via the Central Portal) or your own domain.
2. Add the **Sonatype Central** publishing plugin and **GPG signing** (artifacts must be
   signed; this repo already produces the required `-sources` and `-javadoc` jars).
3. Store your Central token and signing key as CI secrets and publish from a tag.

See the [Central Portal docs](https://central.sonatype.org/) for current steps.

## Continuous Integration

- **[`CI`](.github/workflows/ci.yml)** — runs `./gradlew build` (compile + all tests) on
  every push and PR to `main`, on Temurin JDK 21, and uploads the test report.
- **[`Release`](.github/workflows/release.yml)** — on a `v*` tag, builds, publishes to
  GitHub Packages, and creates a GitHub Release with the jars.

## Resources

- Bakong Open API portal (register / bearer token): <https://api-bakong.nbc.gov.kh/>
- KHQR SDK & API documentation: <https://api-bakong.nbc.gov.kh/document>
- [Bakong Open API Document (PDF)](https://bakong.nbc.gov.kh/download/KHQR/integration/Bakong%20Open%20API%20Document.pdf)
- [KHQR SDK (PDF)](https://bakong.nbc.gov.kh/download/KHQR%20SDK.pdf)
- Development (SIT) API: <https://sit-api-bakong.nbc.gov.kh/>
- Production API: <https://api-bakong.nbc.gov.kh/>
- Bakong Relay (use the API from outside Cambodia): <https://bakongrelay.com/>

Prior art that informed this port: the Python [`bakong-khqr`](https://github.com/bsthen/bakong-khqr)
package and the [Spring Boot integration example](https://github.com/tongbora/Bakong-API-Integration-with-Spring-Boot).

## License

Released under an **Educational Use License** — see [LICENSE](LICENSE). Unofficial; not
affiliated with the National Bank of Cambodia. Test in the sandbox before going live.
