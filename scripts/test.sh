#!/usr/bin/env bash
# Local tests only: never connects to or modifies an Android device.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/tests
flags=(-O2 -UNDEBUG -Wall -Wextra -Werror)
if [[ "$(uname -s)" == Darwin ]]; then
    compiler=${CC:-clang}
    zig_lib=$("${ZIG:-zig}" env | python3 -c 'import json,sys; print(json.load(sys.stdin)["lib_dir"])')
    flags+=(-include tests/host-include/compat.h -I tests/host-include -I "$zig_lib/libc/include/any-linux-any")
else
    compiler=${CC:-cc}
fi
for test in router motion virtual-players; do
    "$compiler" "${flags[@]}" "tests/$test-test.c" -o "build/tests/$test"
    "build/tests/$test"
done
java_home=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
"$java_home/bin/javac" -cp build/classes -d build/tests app/src/club/thor/pads/SteamReport.java tests/SteamReportTest.java
"$java_home/bin/java" -cp build/tests club.thor.pads.SteamReportTest
"$java_home/bin/javac" -d build/tests app/src/club/thor/pads/EdenProfiles.java tests/EdenProfilesTest.java
"$java_home/bin/java" -cp build/tests club.thor.pads.EdenProfilesTest
