#!/bin/sh

set -eu

env_file="${1:-.env}"

if [ ! -f "$env_file" ]; then
  echo "ERROR: env file not found: $env_file" >&2
  exit 1
fi

required_keys="
MYSQL_ROOT_PASSWORD
REDIS_PASSWORD
PUBLIC_BASE_URL
DEPLOY_BASE_URL
DEEPSEEK_API_KEY
COS_CLIENT_HOST
COS_CLIENT_SECRETID
COS_CLIENT_SECRETKEY
COS_CLIENT_REGION
COS_CLIENT_BUCKET
"

failed=0
for key in $required_keys; do
  value=$(awk -F= -v expected="$key" '$1 == expected { sub(/^[^=]*=/, ""); print; exit }' "$env_file")
  case "$value" in
    ""|replace-with-*|https://your-*|http://your-*)
      echo "ERROR: $key is missing or still uses an example value" >&2
      failed=1
      ;;
  esac
done

validate_base_url() {
  url_name="$1"
  url_value="$2"

  case "$url_value" in
    http://*|https://*) ;;
    *)
      echo "ERROR: $url_name must start with http:// or https://" >&2
      failed=1
      ;;
  esac

  case "$url_value" in
    */)
      echo "ERROR: $url_name must not end with /" >&2
      failed=1
      ;;
  esac

  case "$url_value" in
    *' '*|*'?'*|*'#'*|*'@'*)
      echo "ERROR: $url_name must be a clean origin without spaces, credentials, query, or fragment" >&2
      failed=1
      ;;
  esac

  url_without_scheme=${url_value#*://}
  case "$url_without_scheme" in
    */*)
      echo "ERROR: $url_name must not contain a path" >&2
      failed=1
      ;;
  esac
}

public_base_url=$(awk -F= '$1 == "PUBLIC_BASE_URL" { sub(/^[^=]*=/, ""); print; exit }' "$env_file")
deploy_base_url=$(awk -F= '$1 == "DEPLOY_BASE_URL" { sub(/^[^=]*=/, ""); print; exit }' "$env_file")
validate_base_url "PUBLIC_BASE_URL" "$public_base_url"
validate_base_url "DEPLOY_BASE_URL" "$deploy_base_url"

if [ "$public_base_url" = "$deploy_base_url" ]; then
  echo "ERROR: DEPLOY_BASE_URL must use a different origin from PUBLIC_BASE_URL" >&2
  failed=1
fi

case "$public_base_url:$deploy_base_url" in
  https://*:http://*)
    echo "ERROR: DEPLOY_BASE_URL must use https:// when PUBLIC_BASE_URL uses https://" >&2
    failed=1
    ;;
esac

case "$deploy_base_url" in
  https://*:8080|http://*:8080)
    echo "ERROR: DEPLOY_BASE_URL must not expose the legacy public port 8080; use the HTTPS preview origin" >&2
    failed=1
    ;;
esac

if [ "$failed" -ne 0 ]; then
  exit 1
fi

echo "Deployment environment looks valid: $env_file"
