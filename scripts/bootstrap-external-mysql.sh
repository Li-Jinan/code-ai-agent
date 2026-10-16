#!/bin/sh

set -eu

env_file="${1:-.env}"
mysql_container="${2:?Usage: $0 <env-file> <mysql-container> [allowed-host]}"
allowed_host="${3:-172.30.0.0/255.255.255.0}"

case "$env_file" in
  /*) ;;
  *) env_file="$PWD/$env_file" ;;
esac

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
schema_file="$project_dir/docker/mysql/init/01-schema.sql"

if [ ! -f "$env_file" ]; then
  echo "ERROR: env file not found: $env_file" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "$env_file"
set +a

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USERNAME:?MYSQL_USERNAME is required}"

case "$MYSQL_DATABASE" in
  *[!A-Za-z0-9_]*)
    echo "ERROR: database and username may only contain letters, digits, and underscores" >&2
    exit 1
    ;;
esac
case "$MYSQL_USERNAME" in
  *[!A-Za-z0-9_]*)
    echo "ERROR: database and username may only contain letters, digits, and underscores" >&2
    exit 1
    ;;
esac

case "$MYSQL_ROOT_PASSWORD" in
  *[!A-Fa-f0-9]*)
    echo "ERROR: generated application database password must be hexadecimal" >&2
    exit 1
    ;;
esac

docker inspect "$mysql_container" >/dev/null

# The schema uses the fixed application database name code_ai_agent.
docker exec -i "$mysql_container" \
  sh -lc 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < "$schema_file"

sql=$(printf \
  "CREATE USER IF NOT EXISTS '%s'@'%s' IDENTIFIED BY '%s'; ALTER USER '%s'@'%s' IDENTIFIED BY '%s'; GRANT ALL PRIVILEGES ON %s.* TO '%s'@'%s'; FLUSH PRIVILEGES;" \
  "$MYSQL_USERNAME" "$allowed_host" "$MYSQL_ROOT_PASSWORD" \
  "$MYSQL_USERNAME" "$allowed_host" "$MYSQL_ROOT_PASSWORD" \
  "$MYSQL_DATABASE" "$MYSQL_USERNAME" "$allowed_host")

printf '%s\n' "$sql" | docker exec -i "$mysql_container" \
  sh -lc 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'

mysql_image=$(docker inspect -f '{{.Config.Image}}' "$mysql_container")
table_count=$(docker run --rm \
  --network "$APP_NETWORK_NAME" \
  -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" \
  "$mysql_image" \
  mysql -N -h"$MYSQL_HOST" -u"$MYSQL_USERNAME" \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$MYSQL_DATABASE'")

echo "External MySQL initialized: $table_count application tables"
