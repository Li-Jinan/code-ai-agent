#!/bin/sh

set -eu

escape_javascript_string() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

deploy_base_url=$(escape_javascript_string "${DEPLOY_BASE_URL:-}")
api_base_url=$(escape_javascript_string "${PUBLIC_API_BASE_URL:-/api}")
runtime_config_path="${RUNTIME_CONFIG_PATH:-/usr/share/nginx/html/runtime-config.js}"

printf '%s\n' \
  'window.__APP_RUNTIME_CONFIG__ = {' \
  "  deployBaseUrl: \"$deploy_base_url\"," \
  "  apiBaseUrl: \"$api_base_url\"" \
  '}' \
  > "$runtime_config_path"
