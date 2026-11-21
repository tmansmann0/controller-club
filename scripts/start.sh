#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
adb=(adb -d)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then adb=(adb -s "$ANDROID_SERIAL"); fi
[[ "$("${adb[@]}" shell getprop ro.product.model | tr -d '\r')" == AYN*Thor* ]] || { echo "Connect an AYN Thor over USB first."; exit 1; }
[[ -x build/router && -s build/Controller-Club.apk ]] || { echo "Run scripts/build.sh first."; exit 1; }
remote=/data/local/tmp/controller-club
"${adb[@]}" install --no-incremental -r build/Controller-Club.apk
"${adb[@]}" shell cmd statusbar add-tile club.thor.pads/.ClubTile
"${adb[@]}" shell -T "umask 077; mkdir -p $remote; if test -s $remote/pid; then p=\$(cat $remote/pid); case \$p in ''|*[!0-9]*) ;; *) if test \"\$(readlink /proc/\$p/exe)\" = $remote/router; then kill \"\$p\"; fi ;; esac; fi"
"${adb[@]}" shell am force-stop club.thor.pads
lower=$("${adb[@]}" shell dumpsys display | sed -n 's/.*DisplayInfo{"Screen-2", displayId \([0-9][0-9]*\).*/\1/p' | sed -n '1p' | tr -d '\r')
"${adb[@]}" shell am start --display "${lower:-0}" -n club.thor.pads/.MainActivity
