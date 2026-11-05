#!/usr/bin/env bash
# Runs inside the emulator job: installs the debug APK, walks the screens, saves PNGs to shots/.
set -euo pipefail
PKG=com.honestlockout.app
APK=app/build/outputs/apk/debug/app-debug.apk
mkdir -p shots
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow || true
adb shell appops set $PKG GET_USAGE_STATS allow || true
adb shell dumpsys deviceidle whitelist +$PKG || true

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "shots/$1.png"; echo "shot $1"; }
open() { adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.MainActivity "$@" >/dev/null; }

# 1. Fresh install: setup wizard.
open
shot 01_setup 4

# 2. Seed data, then every main screen by route.
open --ez debug_seed true
shot 02_home 5
i=3
for r in blocklist apppicker "override/pick" "override/declare/2?pkg=com.android.chrome&extend=false&stmt=" history settings pending breakglass "template/2"; do
  open --es route "$r"
  shot "$(printf '%02d' $i)_$(echo "$r" | sed 's/[^a-z0-9]/_/g' | cut -c1-20)" 4
  i=$((i+1))
done

# 3. Lock overlay: enable the accessibility service, then open a blocked app. Best effort.
set +e
adb shell settings put secure enabled_accessibility_services $PKG/com.honestlockout.app.service.LockAccessibilityService
adb shell settings put secure accessibility_enabled 1
sleep 5
for CAND in com.android.chrome com.google.android.apps.maps com.android.settings com.google.android.calculator com.android.camera2 com.google.android.apps.messaging; do
  if adb shell pm path "$CAND" >/dev/null 2>&1; then FIRST=$CAND; break; fi
done
echo "blocked app candidate: ${FIRST:-none}"
if [ -n "${FIRST:-}" ]; then
  adb shell monkey -p "$FIRST" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  shot 20_lock 6
fi
adb shell am start -W -n $PKG/.MainActivity >/dev/null 2>&1
shot 21_home_service_on 4
adb logcat -d -s HL/Acc:* HL/Svc:* HL/Engine:* HL/Work:* AndroidRuntime:E > shots/logcat.txt 2>/dev/null
ls -la shots
true
