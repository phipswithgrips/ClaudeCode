#!/usr/bin/env bash
# Führt Gradle aus und meldet Fehler zusätzlich als GitHub-Annotation,
# damit sie über die API lesbar sind (ohne Log-Download).
set -o pipefail
./gradlew "$@" --stacktrace --console=plain 2>&1 | tee build.log
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
  grep -E "^e: |What went wrong|^> |error:|Could not|Unresolved|Caused by|FAILED|expected|Exception:|Test.*FAILED|AssertionError|at de\.rezeptkiste" build.log \
    | grep -v "^> Task" | awk '!seen[$0]++' | head -n 80 > errors.txt || true
  msg=$(sed -e 's/%/%25/g' -e 's/\r//g' errors.txt | awk 'BEGIN{ORS="%0A"}{print}')
  echo "::error title=Gradle $*::${msg}"
fi
exit "$status"
