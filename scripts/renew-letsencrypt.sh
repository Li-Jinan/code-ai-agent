#!/usr/bin/env bash
set -euo pipefail

readonly CERT_DIR="/opt/1panel/apps/openresty/openresty/conf/ssl/lijinan.cn"
readonly ACME_WEBROOT="/opt/1panel/apps/openresty/openresty/root"
readonly OPENRESTY_CONTAINER="1Panel-openresty-r7fz"

docker run --rm \
  -v "${CERT_DIR}:/etc/letsencrypt" \
  -v "${ACME_WEBROOT}:/var/www/acme" \
  certbot/certbot:latest renew \
  --webroot \
  --webroot-path /var/www/acme \
  --quiet

docker exec "${OPENRESTY_CONTAINER}" openresty -t
docker exec "${OPENRESTY_CONTAINER}" openresty -s reload
