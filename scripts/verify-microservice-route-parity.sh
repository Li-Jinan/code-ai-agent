#!/bin/sh

set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
temporary_dir=$(mktemp -d)
trap 'rm -rf "$temporary_dir"' EXIT INT TERM

extract_routes() {
  controller_file="$1"
  sed -nE \
    -e 's/.*@RequestMapping\("([^"]+)"\).*/BASE \1/p' \
    -e 's/.*@(Get|Post|Put|Delete|Patch)Mapping\((value = )?"([^"]+)".*/\1 \3/p' \
    "$controller_file" | sort
}

# WorkflowSseController is intentionally excluded: it exposes standalone
# LangGraph4j experiments that are not used by the production frontend.

compare_controller() {
  controller_name="$1"
  monolith_file="$2"
  microservice_file="$3"
  monolith_routes="$temporary_dir/${controller_name}.monolith"
  microservice_routes="$temporary_dir/${controller_name}.microservice"

  extract_routes "$monolith_file" > "$monolith_routes"
  extract_routes "$microservice_file" > "$microservice_routes"

  if ! diff -u "$monolith_routes" "$microservice_routes"; then
    echo "ERROR: route parity check failed for $controller_name" >&2
    return 1
  fi
  echo "OK: $controller_name routes match"
}

compare_controller \
  "user" \
  "$project_dir/src/main/java/com/jinan/codeaiagent/controller/UserController.java" \
  "$project_dir/code-ai-agent-microservice/code-ai-agent-user/src/main/java/com/jinan/codeaiagentuser/controller/UserController.java"

compare_controller \
  "app" \
  "$project_dir/src/main/java/com/jinan/codeaiagent/controller/AppController.java" \
  "$project_dir/code-ai-agent-microservice/code-ai-agent-app/src/main/java/com/jinan/codeaiagent/controller/AppController.java"

compare_controller \
  "chat-history" \
  "$project_dir/src/main/java/com/jinan/codeaiagent/controller/ChatHistoryController.java" \
  "$project_dir/code-ai-agent-microservice/code-ai-agent-app/src/main/java/com/jinan/codeaiagent/controller/ChatHistoryController.java"

compare_controller \
  "static-resource" \
  "$project_dir/src/main/java/com/jinan/codeaiagent/controller/StaticResourceController.java" \
  "$project_dir/code-ai-agent-microservice/code-ai-agent-app/src/main/java/com/jinan/codeaiagent/controller/StaticResourceController.java"

if ! grep -Fq '@GetMapping("/health")' \
  "$project_dir/code-ai-agent-microservice/code-ai-agent-common/src/main/java/com/jinan/codeaiagent/controller/HealthController.java"; then
  echo "ERROR: shared microservice health route is missing" >&2
  exit 1
fi
echo "OK: shared health route is present"

echo "Microservice product-route parity check passed."
