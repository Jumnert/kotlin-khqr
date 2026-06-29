#!/usr/bin/env bash
#
# Build the Kotlin/JS static site and deploy it to Vercel.
#
# WHY THIS SCRIPT EXISTS:
# Vercel's build image has no JVM/Gradle, so it CANNOT build a Kotlin project.
# If you import this repo in the Vercel dashboard, the build produces nothing and
# you get a "404: NOT_FOUND". The correct approach is to build here (where the JVM
# is) and upload the already-built static folder — which is exactly what this does.
#
# Usage:
#   ./deploy.sh            # production deploy (--prod)
#   ./deploy.sh --preview  # preview deploy (no --prod)
#
set -euo pipefail
cd "$(dirname "$0")"   # always run from the web/ directory

# Keep Gradle's cache off the root disk if the big-disk cache exists.
if [ -d /Volumes/Dev/.gradle-web ]; then
  export GRADLE_USER_HOME=/Volumes/Dev/.gradle-web
  export TMPDIR=/Volumes/Dev/tmp
fi

DIST="build/dist/js/productionExecutable"

echo "==> Building the static site (Kotlin/JS -> $DIST) ..."
./gradlew jsBrowserDistribution --no-daemon

if [ ! -f "$DIST/index.html" ]; then
  echo "ERROR: $DIST/index.html not found — build did not produce output." >&2
  exit 1
fi

PROD_FLAG="--prod"
if [ "${1:-}" = "--preview" ]; then
  PROD_FLAG=""
fi

echo "==> Deploying $DIST to Vercel ..."
echo "    (first run: a browser/email login + 'link to project' prompt; later runs just deploy)"
# Deploy the prebuilt folder directly. --yes auto-confirms project setup.
npx --yes vercel deploy "$DIST" $PROD_FLAG --yes

echo "==> Done."
