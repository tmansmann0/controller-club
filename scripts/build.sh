#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SDK=${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
export PATH="$JAVA_HOME/bin:$PATH"
TOOLS="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
mkdir -p build/{resources,classes,dex,generated}
"$TOOLS/aapt2" compile --dir app/res -o build/resources/res.zip
"$TOOLS/aapt2" link -I "$JAR" --manifest app/AndroidManifest.xml --java build/generated -o build/base.apk build/resources/res.zip
find app/src build/generated -name '*.java' -print0 | xargs -0 javac --release 8 -classpath "$JAR" -d build/classes
(cd build/classes && jar cf ../classes.jar .)
"$TOOLS/d8" --lib "$JAR" --min-api 29 --output build/dex build/classes.jar
"${ZIG:-zig}" cc -target aarch64-linux-musl -O2 -static -s -Wall -Wextra -Werror native/router.c -o build/router
mkdir -p build/native/lib/arm64-v8a
cp build/router build/native/lib/arm64-v8a/libclub_router.so
(cd build/dex && zip -q ../base.apk classes.dex)
(cd build/native && zip -q ../base.apk lib/arm64-v8a/libclub_router.so)
"$TOOLS/zipalign" -f 4 build/base.apk build/aligned.apk
KS=${CLUB_KEYSTORE:-"$HOME/.android/debug.keystore"}
"$TOOLS/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out build/Controller-Club.apk build/aligned.apk
"$TOOLS/apksigner" verify build/Controller-Club.apk
