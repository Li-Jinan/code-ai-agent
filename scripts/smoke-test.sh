#!/bin/sh

set -eu

base_url="${1:-}"
demo_username="${2:-demo}"
demo_password="${3:-demo123456}"
deploy_base_url="${4:-${DEPLOY_BASE_URL:-}}"

if [ -z "$base_url" ]; then
  echo "Usage: $0 <base-url> [demo-username] [demo-password]" >&2
  exit 1
fi

base_url=${base_url%/}
cookie_file=$(mktemp)
trap 'rm -f "$cookie_file"' EXIT INT TERM

echo "[1/6] Checking frontend..."
curl -fsS "$base_url/" >/dev/null

echo "[2/6] Checking runtime domain configuration..."
runtime_config=$(curl -fsS "$base_url/runtime-config.js")
if [ -n "$deploy_base_url" ]; then
  deploy_base_url=${deploy_base_url%/}
  echo "$runtime_config" | grep -Fq "deployBaseUrl: \"$deploy_base_url\""
fi
echo "$runtime_config" | grep -Fq 'apiBaseUrl: "/api"'

echo "[3/6] Checking app-service route..."
health_response=$(curl -fsS "$base_url/api/health")
echo "$health_response" | grep -q '"status":"UP"'

echo "[4/6] Logging in with the demo account..."
login_response=$(curl -fsS \
  -c "$cookie_file" \
  -H 'Content-Type: application/json' \
  --data "{\"userAccount\":\"$demo_username\",\"userPassword\":\"$demo_password\"}" \
  "$base_url/api/user/login")
echo "$login_response" | grep -q '"code":0'

echo "[5/6] Checking the shared Redis session through user-service..."
session_response=$(curl -fsS -b "$cookie_file" "$base_url/api/user/get/login")
echo "$session_response" | grep -q '"code":0'
echo "$session_response" | grep -q "\"userAccount\":\"$demo_username\""

echo "[6/6] Checking the isolated preview origin..."
if [ -n "$deploy_base_url" ]; then
  curl -fsS "$deploy_base_url/preview-health" | grep -q '^ok'
else
  echo "SKIP: pass DEPLOY_BASE_URL as the fourth argument to validate the preview origin"
fi

echo "Smoke test passed: $base_url${deploy_base_url:+ -> $deploy_base_url}"
