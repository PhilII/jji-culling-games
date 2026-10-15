#!/usr/bin/env bash
# Runs all GameTests and fails unless the server reports that every required test passed.
# Gradle exits 0 even when the test server crashes on startup, so the log is the source of truth.
set -uo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
log="build/gametest.log"
mkdir -p build
./gradlew runGameTestServer --console=plain > "$log" 2>&1
if grep -q "All [0-9]* required tests passed" "$log"; then
  grep "All [0-9]* required tests passed" "$log" | sed 's/.*: //'
  exit 0
fi
grep -E "check failed|threw|required tests failed|Failed to start|Caused by" "$log" | head -20
echo "GameTests FAILED (full log: $log)"
exit 1
