#!/usr/bin/env bash
set -euo pipefail
APK="app/build/outputs/apk/debug/app-debug.apk"
PKG="io.github.tbenitz.tvautolaunch.preview"
ACT="io.github.tbenitz.tvautolaunch.MainActivity"

echo "Building TV preview APK..."
gradle --no-daemon :app:assembleDebug
adb install -r "$APK"

echo "Launch preview from Apps, not as system Home."
adb shell am start -W -n "$PKG/$ACT"
sleep 5
pid="$(adb shell pidof "$PKG" | tr -d '\r')"
test -n "$pid" || { echo "Preview process crashed or did not start"; exit 1; }
echo "Preview PID: $pid"

adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb shell cat /sdcard/window.xml > /tmp/window.xml
grep -q "AUTO LAUNCH" /tmp/window.xml || { echo "Expected native Home screen not found"; cat /tmp/window.xml; exit 1; }
grep -q "Apps" /tmp/window.xml || { echo "Apps navigation missing"; exit 1; }
grep -q "Settings" /tmp/window.xml || { echo "Settings navigation missing"; exit 1; }

adb shell input keyevent KEYCODE_DPAD_RIGHT
adb shell input keyevent KEYCODE_DPAD_LEFT
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 2
adb shell pidof "$PKG" >/dev/null || { echo "App crashed on remote navigation"; exit 1; }

adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/$ACT"
sleep 3
adb shell pidof "$PKG" >/dev/null || { echo "App crashed after relaunch"; exit 1; }
adb shell screencap -p /sdcard/tv-preview.png
adb pull /sdcard/tv-preview.png tv-preview.png >/dev/null
echo "TV preview smoke test PASSED: native UI displayed, D-pad input accepted, app relaunches."
