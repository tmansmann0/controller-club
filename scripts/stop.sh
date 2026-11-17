#!/usr/bin/env bash
set -euo pipefail
adb=(adb -d)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then adb=(adb -s "$ANDROID_SERIAL"); fi
"${adb[@]}" shell am force-stop club.thor.pads
"${adb[@]}" shell 'pfile=/data/local/tmp/controller-club/pid; if test -s "$pfile"; then p=$(cat "$pfile"); case $p in ""|*[!0-9]*) ;; *) if test "$(readlink "/proc/$p/exe")" = /data/local/tmp/controller-club/router; then kill "$p"; fi ;; esac; rm "$pfile"; fi'
