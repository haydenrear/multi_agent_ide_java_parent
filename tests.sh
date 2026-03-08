#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

gradle_clean_test() {
  local module_dir="$1"
  (
    cd "$SCRIPT_DIR/$module_dir"
    ./gradlew clean
    ./gradlew test
  )
}

run_module_script() {
  local module_dir="$1"
  (
    cd "$SCRIPT_DIR/$module_dir"
    ./tests.sh
  )
}

gradle_clean_test "multi_agent_ide_lib"
run_module_script "acp-cdc-ai"
run_module_script "multi_agent_ide"
gradle_clean_test "utilitymodule"
