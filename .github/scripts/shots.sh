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

# 1. Fresh install: welcome, then the setup wizard.
open
shot 00_welcome 4
open --es route setup
shot 01_setup 4

# 2. Seed data, then every main screen by route.
open --ez debug_seed true
shot 02_home 5
i=3
for r in blocklist apppicker "override/pick?pkg=com.android.chrome" "override/pick" history settings pending breakglass "template/2" "blockrule/com.android.chrome" "schedule/new" "override/declare/7?pkg=com.google.android.youtube&extend=false&stmt="; do
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
  ACT=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$FIRST" | tail -1 | tr -d '\r')
  echo "launching $ACT"
  adb shell am start -n "$ACT" >/dev/null 2>&1
  shot 20_lock 6
  adb shell dumpsys window windows 2>/dev/null | grep -iE "honestlockout|Window #" | head -20 > shots/windows.txt
fi
adb shell am start -W -n $PKG/.MainActivity >/dev/null 2>&1
shot 21_home_service_on 4
# 4. An active unlock (debug only): Today shows the running card.
open --ez debug_active true
shot 22_home_active 5
adb logcat -d -s HL/Acc:* HL/Svc:* HL/Engine:* HL/Work:* AndroidRuntime:E > shots/logcat.txt 2>/dev/null
ls -la shots
true
