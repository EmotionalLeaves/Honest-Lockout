# Honest Lockout

An Android app that blocks the apps you pick and unlocks one only when you say exactly what
you will do with it, and for how long. While the unlock runs, it compares what you actually do
with what you said. Everything stays on the phone: no account, no partner, no server, and no
INTERNET permission.

## Install

1. Open the repo's **Releases** page in your phone browser.
2. Download the newest `HonestLockout-<n>.apk` and install it. If Play Protect warns you, tap
   **More details**, then **Install anyway**.
3. Open the app and follow the setup wizard. On Android 13+ you also have to allow restricted
   settings (App info, ⋮, **Allow restricted settings**) before the accessibility toggle works.

Every push to `main` builds a signed release APK (`.github/workflows/build.yml`). Each build
installs over the previous one, so history and strikes are kept.

## Signing

Builds are signed with `signing/honest-public.jks` until you add the repository secrets. To keep
updates installable, upload **that same keystore** as the secrets, not a new one.
`signing/README.md` explains how.

## How it works

| Rule | What it means |
| --- | --- |
| Emergency apps | Phone, Messages, Maps and the emergency dialer are never blocked. |
| Honesty is free | **I'm done** or **I got distracted, lock it** ends an override with no strike, at any time. |
| Overrides | You need a purpose, one app and a number of minutes. Then a 20 s countdown. |
| Warnings | Drifting off-purpose gives a 30 s warning first. Ignoring it, or a 3rd warning, is a strike. |
| Strikes | Lockouts grow on a ladder. Strikes expire after 7 days, and 3 clean runs forgive one. |
| Loosening | Any change that weakens blocking waits 24 hours. Tightening is instant. |
| Break-glass | Emergency unlock of everything for 1 hour, after a 10 minute wait. Costs 2 strikes. |

## Project layout

```
app/src/main/java/com/honestlockout/app/
  AppGraph.kt, HonestLockoutApp.kt, MainActivity.kt
  data/      Room database, DataStore settings, repositories, screen rules, purpose templates
  engine/    BlockDecider, OverrideMonitor, ShapeCheck, StrikeLadder, LoosenGuard, allowlist
  service/   accessibility service, override foreground service, settings guard, receivers
  overlay/   lock / lockout / change-queued screens, warning banner, timer bubble
  ui/        Compose screens (setup, home, block list, override, history, settings)
  work/      daily reset, strike expiry, pending changes
app/src/main/assets/screen_rules.json   which in-app screens count as feeds, reels, etc.
```

## Fixing screen rules

View IDs change when apps update. To find the current ones:

1. Go to Settings > Screen rules > **Inspect current screen** and tap **Start**.
2. Open the screen you want to catch (for example Instagram Reels), then come back.
3. Copy the view ID into `app/src/main/assets/screen_rules.json` and push.

## Debug log

Long-press the **History** title to export the last 2 000 log lines to a text file.
