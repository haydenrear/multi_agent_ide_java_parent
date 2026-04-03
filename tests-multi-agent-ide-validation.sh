#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

export LOGGING_CONFIG="classpath:logback-multi-agent-ide-validation.xml"

cd "$ROOT_DIR"
./gradlew :multi_agent_ide_java_parent:multi_agent_ide:integrationTestAndUnitTest
