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
grep -q "TV LAUNCHER" /tmp/window.xml || { echo "Expected native Home screen not found"; cat /tmp/window.xml; exit 1; }
grep -q "Apps" /tmp/window.xml || { echo "Apps navigation missing"; exit 1; }
grep -q "Settings" /tmp/window.xml || { echo "Settings navigation missing"; exit 1; }
grep -q "APK Center" /tmp/window.xml || { echo "APK Center quick action missing"; exit 1; }

# Check the new sideloading UI without downloading anything or changing Home.
read -r APK_X APK_Y <<< "$(python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
root = ET.parse('/tmp/window.xml').getroot()
for node in root.iter('node'):
    if 'APK Center' in node.attrib.get('text', ''):
        nums = list(map(int, re.findall(r'\\d+', node.attrib.get('bounds', ''))))
        if len(nums) == 4:
            print((nums[0]+nums[2])//2, (nums[1]+nums[3])//2)
            break
else:
    raise SystemExit('APK Center button not found')
PY
)"
adb shell input tap "$APK_X" "$APK_Y"
sleep 2
adb shell uiautomator dump /sdcard/apkcenter.xml >/dev/null
adb shell cat /sdcard/apkcenter.xml > /tmp/apkcenter.xml
grep -q "GITHUB RELEASES" /tmp/apkcenter.xml || { echo "APK Center did not open"; cat /tmp/apkcenter.xml; exit 1; }
grep -q "DIRECT APK DOWNLOAD" /tmp/apkcenter.xml || { echo "APK Center direct source missing"; exit 1; }
adb shell input keyevent KEYCODE_BACK
sleep 1

# Verify the actual app-picker route, not just the initial Home layout.
adb shell input tap 1540 90
sleep 3
adb shell uiautomator dump /sdcard/apps.xml >/dev/null
adb shell cat /sdcard/apps.xml > /tmp/apps.xml
grep -q "Installed TV apps" /tmp/apps.xml || { echo "All Apps screen did not open"; cat /tmp/apps.xml; exit 1; }
adb shell screencap -p /sdcard/tv-apps.png
adb pull /sdcard/tv-apps.png tv-apps.png >/dev/null

# On the 1920 x 1080 TV AVD, the first visible app tile is near (160,380).
# Holding it must show a real app-actions dialog; a normal OK now launches the app.
adb shell input swipe 170 360 170 360 1200
sleep 2
adb shell uiautomator dump /sdcard/actions.xml >/dev/null
adb shell cat /sdcard/actions.xml > /tmp/actions.xml
grep -q "Set as startup app" /tmp/actions.xml || { echo "App actions unavailable"; cat /tmp/actions.xml; exit 1; }
adb shell screencap -p /sdcard/tv-app-actions.png
adb pull /sdcard/tv-app-actions.png tv-app-actions.png >/dev/null
adb shell input keyevent KEYCODE_BACK

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
