#!/usr/bin/env bash
# Smoke test after a deploy: the site serves the app, and the API rejects requests without a login.
# Usage: scripts/post-deploy-check.sh cdk-outputs.json
set -euo pipefail

outputs=${1:?usage: post-deploy-check.sh <cdk-outputs.json>}
site=$(jq -er '.StockHeatmapStack.SiteUrl' "$outputs")
api=$(jq -er '.StockHeatmapStack.ApiUrl' "$outputs")

fail() { echo "post-deploy check FAILED: $*" >&2; exit 1; }

body=$(curl -fsS --retry 5 --retry-delay 10 --retry-all-errors "$site/") || fail "$site did not respond"
grep -q '<div id="root">' <<<"$body" || fail "$site did not serve the app's index.html"

status=$(curl -s -o /dev/null -w '%{http_code}' "$api/market-data")
[ "$status" = "401" ] || fail "$api/market-data without a token returned $status, expected 401"

echo "post-deploy check passed: $site serves the app; $api requires a login"
