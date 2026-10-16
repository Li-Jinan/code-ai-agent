#!/bin/sh

set -eu

env_file="${1:-.env}"
backup_dir="${2:-backups}"

case "$env_file" in
  /*) ;;
  *) env_file="$PWD/$env_file" ;;
esac
case "$backup_dir" in
  /*) ;;
  *) backup_dir="$PWD/$backup_dir" ;;
esac

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
compose_file="$project_dir/docker-compose.microservice.yml"

if [ ! -f "$env_file" ]; then
  echo "ERROR: env file not found: $env_file" >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker daemon is not available" >&2
  exit 1
fi

mkdir -p "$backup_dir"
timestamp=$(date '+%Y%m%d-%H%M%S')
backup_file="$backup_dir/code_ai_agent-$timestamp.sql"
temporary_file="$backup_file.part"
trap 'rm -f "$temporary_file"' EXIT INT TERM

echo "Backing up MySQL to $backup_file ..."
docker compose -f "$compose_file" --env-file "$env_file" exec -T mysql \
  sh -c 'exec mysqldump --single-transaction --routines --triggers --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" code_ai_agent' \
  >"$temporary_file"

if [ ! -s "$temporary_file" ]; then
  echo "ERROR: backup file is empty" >&2
  exit 1
fi

chmod 600 "$temporary_file"
mv "$temporary_file" "$backup_file"
trap - EXIT INT TERM

echo "MySQL backup completed: $backup_file"
