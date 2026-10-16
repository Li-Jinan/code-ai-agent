#!/bin/sh

set -eu

env_file="${1:-.env}"
case "$env_file" in
  /*) ;;
  *) env_file="$PWD/$env_file" ;;
esac

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
compose_file="$project_dir/docker-compose.microservice.yml"

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker daemon is not available" >&2
  exit 1
fi

echo "[1/6] Verifying monolith-to-microservice route parity..."
"$project_dir/scripts/verify-microservice-route-parity.sh"

echo "[2/6] Validating deployment environment..."
"$project_dir/scripts/validate-deploy-env.sh" "$env_file"

echo "[3/6] Validating Docker Compose configuration..."
docker compose -f "$compose_file" --env-file "$env_file" config --quiet

echo "[4/6] Building and starting services..."
docker compose -f "$compose_file" --env-file "$env_file" up -d --build

echo "[5/6] Current service status:"
docker compose -f "$compose_file" --env-file "$env_file" ps

read_env_value() {
  awk -F= -v expected="$1" '$1 == expected { sub(/^[^=]*=/, ""); print; exit }' "$env_file"
}

public_base_url=$(read_env_value PUBLIC_BASE_URL)
deploy_base_url=$(read_env_value DEPLOY_BASE_URL)
demo_username=$(read_env_value DEMO_ACCOUNT_USERNAME)
demo_password=$(read_env_value DEMO_ACCOUNT_PASSWORD)
demo_username=${demo_username:-demo}
demo_password=${demo_password:-demo123456}

echo "[6/6] Waiting for the public gateway and running URL smoke tests..."
attempt=1
while ! curl -fsS "$public_base_url/api/health" >/dev/null 2>&1; do
  if [ "$attempt" -ge 60 ]; then
    echo "ERROR: public gateway did not become ready: $public_base_url" >&2
    exit 1
  fi
  attempt=$((attempt + 1))
  sleep 5
done

"$project_dir/scripts/smoke-test.sh" \
  "$public_base_url" \
  "$demo_username" \
  "$demo_password" \
  "$deploy_base_url"

echo "Deployment and public URL verification completed."
