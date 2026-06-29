# Bakong KHQR — Kotlin Web Demo

A browser demo for the [`kotlin-khqr`](../README.md) library, built with **Kotlin/JS**
(Kotlin Multiplatform). It generates KHQR payment codes, the MD5 transaction hash, and a
scannable QR image **entirely in the browser**, and lets you check a payment with your own
Bakong credentials.

Because it compiles to a plain static site (HTML + JS), it deploys to **Vercel** (or any
static host) with no server.

> ⚠️ Unofficial & educational. Prefer the **Sandbox** environment. Your token is used only
> in your browser and sent directly to Bakong — it is never sent to the site's host.

---

## Why Kotlin/JS (and not Ktor) for Vercel

Vercel runs **static sites** and **serverless functions** (Node, Python, Go, …). It does
**not** run a long-lived JVM process, so a **Ktor/JVM server cannot be hosted on Vercel.**

This demo instead compiles **Kotlin → JavaScript**, producing a static bundle that Vercel
serves natively. The pure KHQR logic (TLV encoding, CRC-16, MD5, the EMVCo assembler) is
ported into `commonMain` so it runs in the browser. It is byte-for-byte identical to the
JVM library — guaranteed by a shared golden-vector test (`./gradlew jvmTest`).

> If you instead want the **full** library (server-side transaction checks from a Cambodia
> IP, ZXing image generation, the JDK HTTP client), run it on a JVM host — see
> [Hosting a JVM backend](#hosting-a-jvm-backend-not-vercel).

## What works where

| Feature                         | In the browser (Vercel) | Server-side (JVM lib) |
|---------------------------------|-------------------------|-----------------------|
| Generate KHQR string + MD5      | ✅ fully offline        | ✅                    |
| Render QR image                 | ✅ (via `qrcodejs`)     | ✅ (via ZXing)        |
| Check payment / get payment     | ⚠️ usually blocked by CORS¹ | ✅                |

¹ Browsers can't call the Bakong API cross-origin (no CORS headers), and production checks
require a Cambodia IP or an `rbk…` Relay token. The demo surfaces this clearly. For reliable
checks, call the API server-side with the JVM library, or use a Relay token.

## Project layout

```
web/
├── build.gradle.kts                 # Kotlin Multiplatform: jvm() (tests) + js{ browser }
├── vercel.json                      # static-deploy config (prebuilt output)
└── src/
    ├── commonMain/kotlin/.../core/  # KHQR core ported to multiplatform (pure Kotlin)
    ├── jvmTest/kotlin/.../core/     # golden-vector test (runs on the JVM, no browser)
    └── jsMain/
        ├── kotlin/.../Main.kt       # browser logic (form → generate/check)
        └── resources/index.html     # Material UI (Materialize)
```

## Build & run locally

Requires JDK 17+. The Kotlin/JS plugin downloads Node/Yarn automatically on first build.

```bash
cd web

# Run the shared-logic tests (fast, no browser needed)
./gradlew jvmTest

# Live dev server with hot reload at http://localhost:8080
./gradlew jsBrowserDevelopmentRun --continuous

# Production static bundle -> build/dist/js/productionExecutable/
./gradlew jsBrowserDistribution
```

Preview the production bundle like a static host would:

```bash
cd build/dist/js/productionExecutable && python3 -m http.server 8099
# open http://localhost:8099
```

## Deploy to Vercel

> ℹ️ **About the `404: NOT_FOUND` you may have seen.** Vercel's build image has **no JVM
> or Gradle**, so it cannot compile this Kotlin project — a plain Git import builds nothing
> and serves a 404. There are two ways to get a working site; pick one.

### Option 1 — Git integration with a committed snapshot (zero CLI)

A prebuilt copy of the site is committed at **`public/`** (both at the repo root and here
in `web/public/`), and `vercel.json` tells Vercel to **skip building** and just serve it:

```json
{ "buildCommand": "echo prebuilt-static-site", "outputDirectory": "public" }
```

So a normal Vercel **Git import just works** — every push redeploys the committed `public/`.
The trade-off: `public/` is a snapshot, so after changing the app you must regenerate it:

```bash
cd web
./gradlew jsBrowserDistribution
cp build/dist/js/productionExecutable/* public/ ../public/   # refresh both snapshots
git commit -am "web: update demo" && git push
```

> If your Vercel project's **Root Directory** is `./` it serves the repo-root `public/`;
> if it's `web` it serves `web/public/`. Both are committed, so either setting works.

### Option 2 — CLI / script (no committed artifacts)

Build locally and upload the fresh output directly — nothing committed, always current:

```bash
cd web
./deploy.sh            # builds, then deploys build/dist/js/productionExecutable to Vercel
```

The first run asks you to log in (`vercel login`) and link/create a project; after that it
just deploys. (`./deploy.sh --preview` for a non-production deploy.) Equivalent by hand:

```bash
cd web
./gradlew jsBrowserDistribution                              # -> build/dist/js/productionExecutable/
npx vercel deploy build/dist/js/productionExecutable --prod  # deploy the BUILT folder
```

### Automated (GitHub Actions — no local CLI)

`.github/workflows/web-deploy.yml` (in the repo root) builds the bundle on a runner (which
*does* have the JVM) and deploys it with the Vercel CLI. Add these repository secrets:

- `VERCEL_TOKEN` — from https://vercel.com/account/tokens
- `VERCEL_ORG_ID` and `VERCEL_PROJECT_ID` — run `vercel link` once locally and read them
  from `.vercel/project.json`, or copy them from the Vercel project settings.

Then every push to `web/**` builds and deploys automatically.

## Other static hosts

The same `build/dist/js/productionExecutable/` folder deploys anywhere static:

```bash
# Netlify
npx netlify deploy --prod --dir=build/dist/js/productionExecutable

# Cloudflare Pages
npx wrangler pages deploy build/dist/js/productionExecutable

# GitHub Pages — copy the folder's contents to your gh-pages branch
```

## Hosting a JVM backend (not Vercel)

If you want server-side transaction checks (so they aren't blocked by CORS, and can run
from a Cambodia IP), host a small JVM service that uses the `kotlin-khqr` library on a
platform that runs the JVM — e.g. **Railway, Render, Fly.io, Google Cloud Run** (Docker),
or any VPS. Then point this static frontend's check call at that backend instead of calling
Bakong directly. Those platforms run a container/JVM continuously, which Vercel does not.

## Security notes

- Use the **Sandbox** environment and a **sandbox/test token** for public demos.
- The token never leaves the browser except in the direct request to Bakong; this site's
  host never sees it. Still, **don't paste a production token into a deployment you don't
  control**.
- Always serve over **HTTPS** (Vercel does this automatically).
