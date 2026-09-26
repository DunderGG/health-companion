# Verification Guide

Manual checks that unit tests can't cover: behaviour on a Wear OS emulator or a physical watch, and battery cost. Each check links to the decision whose assumption it confirms. Those decisions carry a 🟠 **Verify on device** callout in [DESIGN_DECISIONS.md](DESIGN_DECISIONS.md).

**When a check is done**
1. Tick it here and add a row to the [results log](#results-log).
2. Record the outcome in the DD entry (what was observed, on which device or image).
3. Remove the entry's 🟠 callout, its index marker, and its line in the summary at the top of DESIGN_DECISIONS.md. If the check failed, open a fix instead and leave the callout in place.

**Contents**
- [0. Setup](#0-setup)
- [1. Tools](#1-tools)
- [2. Functional checks](#2-functional-checks) (V1–V13)
- [3. Battery profiling](#3-battery-profiling) (B1–B6)
- [Results log](#results-log)

---

## 0. Setup

### Automated tests first
```powershell
.\gradlew.bat test assembleDebug lint
```
Unit tests cover the pure rules: decay, deltas, cadence, alert planning, migrations. The rest of this document only covers what they can't.

### Emulator images
| Path | Image | Covers |
| :--- | :--- | :--- |
| Wear OS 6+ | `Wear_OS_Large_Round` AVD, API 37 | Granular health permissions (`health.READ_HEART_RATE`, `health.READ_HEALTH_DATA_IN_BACKGROUND`) |
| Wear OS 4–5 | An API 33–35 Wear OS image (install through **Device Manager → Create Virtual Device → Wear OS**) | `BODY_SENSORS` + `BODY_SENSORS_BACKGROUND`, and the `POST_NOTIFICATIONS` dialog from API 33 |

A **physical watch** is required for V7 (the emulator has no step detector) and for the real battery measurements (B6).

### Install and follow the logs
```powershell
.\gradlew.bat :wearApp:installDebug
adb logcat -s PassiveDataService HealthServicesManager PassiveRegistration VitalAlertWorker SensorLiveStepSource
```
In Android Studio, use **Logcat** with the filter `package:com.healthcompanion.wear`.

### Start from a clean install
Some checks (onboarding, first reading) need a fresh state:
```powershell
adb uninstall com.healthcompanion.wear
.\gradlew.bat :wearApp:installDebug
```

### Pushing vitals into a given state
There is no debug menu yet. Vitals decay from the stored timestamp, so moving the watch clock forward ages the pet:
1. On the watch: **Settings → System → Date & time**, turn off automatic time, and set the clock forward.
2. Hydration drops 3 per hour and hunger 2.5 per hour. From 100, hydration is critical (below 25) after about 25 h and hunger after about 30 h.
3. Pick a new time **outside 22:00–07:00**, otherwise the pet is asleep and alerts are held back.
4. Turn automatic time back on afterwards.

WorkManager delays run on elapsed time, not the wall clock. A clock jump alone therefore doesn't fire a pending check; a pet write (e.g. logging a meal) triggers one.

---

## 1. Tools

### 1.1 Synthetic Health Services data
The emulator doesn't walk, so passive steps, floors and heart rate must be simulated:
- **Android Studio**: newer versions have a **Wear Health Services** panel in the running emulator's extended controls (**⋯**). It toggles capabilities and overrides sensor values.
- **ADB** (Health Services synthetic providers):
  ```powershell
  adb shell am broadcast -a "whs.USE_SYNTHETIC_PROVIDERS" com.google.android.wearable.healthservices
  adb shell am broadcast -a "whs.synthetic.user.START_WALKING" com.google.android.wearable.healthservices
  adb shell am broadcast -a "whs.synthetic.user.START_RUNNING" com.google.android.wearable.healthservices
  adb shell am broadcast -a "whs.synthetic.user.STOP_EXERCISE" com.google.android.wearable.healthservices
  adb shell am broadcast -a "whs.USE_SENSOR_PROVIDERS" com.google.android.wearable.healthservices   # back to real sensors
  ```

> [!NOTE]
> Earlier project docs used `adb shell am broadcast -a "androidx.health.services.client.action.SIMULATE_DATA"`. It isn't confirmed to work. Record which method works on the API 37 image in the results log and correct this section.

Synthetic data only feeds **Health Services** (the passive batches). It does not trigger the platform step detector used for live reactions (V7).

### 1.2 Background Task Inspector (WorkManager)
**View → Tool Windows → App Inspection → Background Task Inspector**, with the debug build running. It shows every WorkManager job, its state, initial delay and chain. Click a job to see its dependency graph.

From the command line:
```powershell
adb shell dumpsys jobscheduler | Select-String -Context 0,15 healthcompanion
```

### 1.3 Active sensors
```powershell
adb shell dumpsys sensorservice
```
Look at the **active connections** and recent registrations for `com.healthcompanion.wear`.

### 1.4 batterystats and Battery Historian
```powershell
adb shell dumpsys battery unplug                 # count as "on battery" (emulator, or a watch on the charger)
adb shell dumpsys batterystats --reset
# ... run the scenario ...
adb shell dumpsys batterystats --charged com.healthcompanion.wear > stats.txt
adb bugreport bugreport.zip                      # optional: for Battery Historian
adb shell dumpsys battery reset                  # undo the unplug
```
In `stats.txt`, look for the app's **wakelocks**, **jobs** (count and total time), **wakeup alarms**, **sensor** usage, and **CPU** time. On a physical watch the output also contains an estimated power use in mAh for the app's uid.

**Battery Historian** turns a bug report into a timeline. It runs locally in Docker:
```powershell
docker run -p 9999:9999 gcr.io/android-battery-historian/stable:3.1 --port 9999
```
Then open `http://localhost:9999` and upload `bugreport.zip`.

### 1.5 Doze (device idle)
```powershell
adb shell dumpsys battery unplug
adb shell dumpsys deviceidle force-idle          # enter Doze immediately
adb shell dumpsys deviceidle step                # advance through idle states / maintenance windows
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset
```

### 1.6 CPU and rendering
- **Profiler** (**View → Tool Windows → Profiler**) → **CPU** → **System Trace** (Perfetto): per-frame work of `ModernPetCanvas`, and main-thread activity.
- Frame statistics:
  ```powershell
  adb shell dumpsys gfxinfo com.healthcompanion.wear reset
  # ... use the screen for a while ...
  adb shell dumpsys gfxinfo com.healthcompanion.wear
  ```

---

## 2. Functional checks

### V1 — Onboarding and permissions ([DD-22](DESIGN_DECISIONS.md#dd-22--only-activity-recognition-is-core-heart-rate-is-optional), [DD-23](DESIGN_DECISIONS.md#dd-23--heart-rate-is-registered-only-with-background-access), [DD-41](DESIGN_DECISIONS.md#dd-41--one-alert-per-critical-episode-at-the-mood-threshold-never-at-night))
Run on **both** images, starting from a clean install each time.
- [ ] One onboarding request shows the dialogs for activity recognition, heart rate and (API 33+) notifications. After heart rate is granted, a separate background heart-rate dialog follows.
- [ ] Denying activity recognition gives degraded mode: the pet screen shows the **⚠ Enable sensors** chip, which opens the app's system settings.
- [ ] Granting only heart rate (plus background) registers only heart rate: `HealthServicesManager` logs `Registering passive listener for: [HEART_RATE_BPM]`.
- [ ] Denying notifications still reaches the pet screen normally.

### V2 — Passive data ([DD-08](DESIGN_DECISIONS.md#dd-08--cumulative-daily-totals-are-consumed-as-deltas), [DD-15](DESIGN_DECISIONS.md#dd-15--heart-rate-awards-are-limited-to-one-per-30-minutes), [DD-16](DESIGN_DECISIONS.md#dd-16--first-reading-credits-todays-activity-so-far))
Use synthetic walking (§1.1) and follow `PassiveDataService` in logcat.
- [ ] Each `Passive batch received` is applied once. The fitness vital and XP rise by the step delta, not by the day's total.
- [ ] A repeated batch with the same total adds nothing.
- [ ] Heart-rate awards happen at most once per 30 minutes, even with frequent heart-rate samples.
- [ ] After a clean install, the first reading credits today's steps so far (DD-16).

### V3 — Surfaces ([DD-29](DESIGN_DECISIONS.md#dd-29--surfaces-are-refreshed-by-a-repository-decorator), [DD-30](DESIGN_DECISIONS.md#dd-30--60-second-decay-ticker-only-while-collected), [DD-42](DESIGN_DECISIONS.md#dd-42--the-tile-logs-water-in-place-through-a-loadaction-deduplicated-by-a-per-render-click-id), [DD-43](DESIGN_DECISIONS.md#dd-43--the-complication-shows-mood-and-overall-health-not-step-progress))
- [ ] Add the **Pet Status** tile. Logging water in the app updates the tile within a few seconds.
- [ ] Tapping **+250ml Water** on the tile raises its hydration line (and the in-app ring) by one drink, and the tile re-renders within a second or two (DD-42).
- [ ] Tapping twice quickly logs one drink. Leaving the tile and coming back, or waiting for a refresh, doesn't log again.
- [ ] Tapping the health/hydration text opens the app.
- [ ] Add the **Pet Mood** complication to a watch face in each slot type it offers (short text, ranged value, icon). The mood face is tinted by the watch face and stays visible in ambient mode (DD-43).
- [ ] Logging water (in the app or on the tile) updates the complication's health ring within a few seconds. Tapping the complication opens the app.
- [ ] Add the **Pet Steps** complication ([DD-51](DESIGN_DECISIONS.md#dd-51--step-progress-is-a-second-complication-pet-steps)) as a ranged value and as short text. The steps match the Goals page, the ring is full at the step goal, and it follows a step-goal change in Settings and a passive batch within a few seconds. After midnight it drops back to 0 within 10 minutes.
- [ ] A synthetic sensor batch also updates the tile.
- [ ] With the pet screen left open, the vitals ring visibly decays about once a minute.

### V4 — Reboot ([DD-25](DESIGN_DECISIONS.md#dd-25--boot-re-registration-via-a-non-exported-receiver-and-workmanager), [DD-40](DESIGN_DECISIONS.md#dd-40--alerts-are-scheduled-at-the-predicted-crossing-not-polled))
```powershell
adb reboot
adb wait-for-device
adb logcat -s PassiveRegistration HealthServicesManager VitalAlertWorker
```
- [ ] Without opening the app, `HealthServicesManager` logs `Registering passive listener for: …` shortly after boot (from `PassiveRegistrationWorker`), and synthetic data arrives again.
- [ ] The pending `CriticalVitalCheck` job is still scheduled after the reboot (§1.2).

### V5 — App update ([DD-24](DESIGN_DECISIONS.md#dd-24--idempotent-registration-keyed-on-permitted-sensors--boot-count))
```powershell
.\gradlew.bat :wearApp:assembleDebug
adb install -r wearApp\build\outputs\apk\debug\wearApp-debug.apk
```
- [ ] Passive data still arrives after the update, without a reboot and without opening the app. If it doesn't, the stored registration key is skipping a needed re-registration.

### V6 — Midnight and time zones ([DD-17](DESIGN_DECISIONS.md#dd-17--a-daily-reading-belongs-to-the-day-of-its-interval-end-minus-1-ms))
- [ ] Walk (synthetically) across local midnight. The last batch before midnight counts for the old day, and the new day starts from zero without a negative or double delta.
- [ ] Change the watch's time zone (**Settings → System → Date & time**) during the day. Steps are neither lost nor counted twice.

### V7 — Live step reactions ([DD-37](DESIGN_DECISIONS.md#dd-37--live-steps-come-from-the-platform-step-detector-only-while-the-screen-is-visible)) — physical watch
- [ ] On the pet screen, `SensorLiveStepSource` logs `Live steps from …`. Note whether it is the step **detector** or the **counter** fallback.
- [ ] Walking makes the pet walk. Note how many steps or seconds pass before it reacts (many detectors confirm a few steps before reporting).
- [ ] Running (or a brisk jog) makes it run, with speed lines. Stopping returns it to idle within about 3 seconds.
- [ ] Turn the screen off. Within about 10 seconds the app's step sensor connection is gone from `dumpsys sensorservice` (§1.3).
- [ ] At night (22:00–07:00) the pet stays asleep while you walk.

### V8 — Critical-vital notifications ([DD-40](DESIGN_DECISIONS.md#dd-40--alerts-are-scheduled-at-the-predicted-crossing-not-polled), [DD-41](DESIGN_DECISIONS.md#dd-41--one-alert-per-critical-episode-at-the-mood-threshold-never-at-night))
Push hydration below 25 as described in [Pushing vitals into a given state](#pushing-vitals-into-a-given-state), then log a meal to trigger a check.
- [ ] "Aura is thirsty" appears. Tapping it opens the pet screen.
- [ ] A second pet write while still thirsty posts no new notification.
- [ ] Logging water (+250 ml, back above 25) removes the notification.
- [ ] Set the clock to 23:00 with a critical vital: no notification. At 07:00 (or after moving the clock past it and logging something) it appears.
- [ ] With notifications denied, `VitalAlertWorker` logs `Notifications not permitted; skipping vital check.` Granting them in Settings and reopening the app triggers the check.
- [ ] **Doze timing**: put hydration about 1 hour above 25 (e.g. at 28), check the scheduled delay in the Background Task Inspector, force Doze (§1.5), and note how late the alert actually arrives.

### V9 — Ambient mode ([DD-44](DESIGN_DECISIONS.md#dd-44--the-pet-screen-stays-on-in-ambient-mode-as-a-static-outline-and-releases-the-step-sensor))
Enable **Settings → Display → Always-on screen**, open the pet screen, then let it time out or cover it with a palm. `adb shell input keyevent KEYCODE_SLEEP` also dims to ambient while always-on is enabled.
- [ ] The pet screen stays visible in the ambient look: the time at the top, the name, a grey outline pet in its current mood, and a thin grey ring. No buttons, no colour, no animation.
- [ ] A tap (or wrist raise) returns to the normal, animated screen.
- [ ] The time advances every minute while ambient. After a few minutes the ring reflects decay, and at 22:00 the pet shows as asleep (outline with Zzz).
- [ ] On a physical OLED watch (burn-in protection), the content moves by a few dp each minute.
- [ ] Walking while ambient doesn't animate the pet, and `dumpsys sensorservice` shows no step listener for the app (§1.3).

### V10 — Rotary crown ([DD-45](DESIGN_DECISIONS.md#dd-45--the-crown-pages-between-the-pet-and-a-vitals-breakdown-petting-stays-a-tap))
On the emulator, use the rotary control in the extended controls (**⋯**), or `adb shell input rotaryencoder scroll --axis SCROLL,-1` (repeat a few times; positive values scroll back up).
- [ ] Turning the crown one way snaps from the pet to the Vitals, Goals and Settings pages, and the other way snaps back. The page indicator follows. On a watch, each snap gives a haptic tick.
- [ ] Swiping up and down does the same.
- [ ] The Vitals page shows all five vitals, fully inside the round display on the smallest supported screen, with values matching the tile.
- [ ] Tapping the pet and the meal/water buttons still works on the pet page.

### V11 — Haptics ([DD-47](DESIGN_DECISIONS.md#dd-47--three-vibration-patterns-goals-are-the-daily-focus-goals-foreground-only)) — physical watch for the feel
`adb shell dumpsys vibrator_manager` lists every vibration with its primitives, so the emulator can confirm what was played. Only a watch shows how it feels.
- [ ] Tapping the pet (outside its 10 s cooldown) plays the purr: four `TICK` primitives with `usage: TOUCH`. A tap during the cooldown plays nothing.
- [ ] With the app open, crossing a daily focus goal plays the goal pattern once (`QUICK_RISE` + two `CLICK`s). The quickest way is on a fresh day, or after clearing app data: log 6 × 250 ml and 2 healthy meals. Goals already reached before the app was opened play nothing.
- [ ] An evolution while the app is open plays the fanfare (`SLOW_RISE`, `QUICK_FALL`, three `CLICK`s), and a goal reached by the same write doesn't cut it off.
- [ ] With the app closed, reaching a goal through a sensor batch doesn't vibrate.
- [ ] With hydration below 100 %, tapping water until it shows 100 % plays the light tick (`TICK` + `CLICK`) once ([DD-50](DESIGN_DECISIONS.md#dd-50--a-light-vital-filled-up-tick-not-while-asleep-haptics-read-a-fresh-pet-stream)). If the same tap reaches the water goal, only the goal pattern plays. Nothing plays for a vital that fills up during the night.
- [ ] Close the app, let the pet evolve or fill hydration from the tile, wait over 5 s and reopen: nothing vibrates.
- [ ] On the wrist, the four patterns are easy to tell apart, the purr feels soft, and the tick is the lightest.

### V12 — Settings ([DD-48](DESIGN_DECISIONS.md#dd-48--a-settings-screen-for-daily-goals-bedtime-and-haptics-goals-dont-change-the-archetype))
Open the last pager page and tap **Settings**. On the emulator, turn the crown with `adb shell input rotaryencoder scroll --axis SCROLL,-1` (V10).
- [ ] The list shows Steps 6,000, Water 1,500 ml, Healthy meals 2, Sleeps at 22:00, Wakes at 07:00 and Vibration on, in the watch's 12- or 24-hour format. The crown scrolls it, and everything fits on the smallest supported round screen.
- [ ] Tapping a number opens its stepper. + and − change it by one step, the crown does too (clockwise increases), and one crown detent on a watch moves about one step. The value is saved at once, and swiping right returns to the list showing it.
- [ ] Sleeps at steps from 23:00 to 00:00 and stops at 18:00 and 03:00. Wakes at stops at 04:00 and 12:00.
- [ ] Setting a bedtime that has already started makes the pet sleep, on the pet page and on the tile and complication, and the night ends at the chosen wake-up hour.
- [ ] Lowering the step goal below today's steps doesn't vibrate, and it doesn't change the archetype chosen at `TEEN` (DD-36).
- [ ] With Vibration off, petting, a goal and an evolution play nothing (`dumpsys vibrator_manager`).
- [ ] Entering ambient mode on a settings screen shows the ambient pet. Waking returns to the same settings screen.
- [ ] Settings survive force-stopping the app and a reboot.

### V13 — Goals page ([DD-49](DESIGN_DECISIONS.md#dd-49--a-goals-page-between-the-vitals-and-the-settings-one-calculation-for-page-vibration-and-archetype))
The third pager page. Easiest on a fresh day, or after clearing app data.
- [ ] The rows show today's steps, water and healthy meals against the goals from Settings, and "Not yet" for Workout. Logging water or a meal updates them at once.
- [ ] A goal reached with the app open fills its row, shows "✓" and today's total, raises the title count, and plays the goal vibration at the same moment (V11). Water alone doesn't raise the count; water and meals together do.
- [ ] A heart rate of 100+ (synthetic data, §1.1) or a workout shows "✓ Done" for Workout.
- [ ] After midnight the page starts empty, and the rows fit on the smallest supported round screen.

---

## 3. Battery profiling

The emulator can't measure real power: its battery is simulated. Android Studio's Power Profiler needs an on-device power monitor, which Wear OS watches don't provide. The emulator checks (B1–B5) therefore verify the **behaviour** that causes drain: wake-ups, jobs, sensors and rendering. The physical-watch check (B6) measures the actual cost.

For each scenario, capture batterystats (§1.4) and record the key numbers in the results log.

### B1 — Idle, screen off (1 hour, emulator)
Open the app once, then turn the screen off (`adb shell input keyevent KEYCODE_SLEEP`) and leave it for 1 hour without sensor data.
- [ ] Jobs: at most the scheduled `CriticalVitalCheck` runs, each well under a second.
- [ ] No app wakelock held for more than a few seconds in total.
- [ ] No active sensor connections for the app (§1.3).
- [ ] No periodic jobs in `dumpsys jobscheduler`. The legacy `PetPeriodicDecayWork` is absent.

### B2 — Passive activity, screen off (30 minutes, emulator)
Screen off, synthetic walking (§1.1).
- [ ] Each sensor batch causes one short `PassiveDataService` run and one `VitalAlertWorker` run, and nothing keeps running in between.
- [ ] Job count ≈ number of batches, and total job time stays in seconds, not minutes.

### B3 — Pet screen open (5 minutes, emulator)
Capture a System Trace (§1.6) and `gfxinfo` while the pet screen is visible.
- [ ] Frame work fits the frame budget (janky frames close to 0 %). Note the average CPU time per frame. The pet redraws continuously while visible, so this is the main cost when the screen is on.
- [ ] The decay ticker fires about once a minute, not more often.

### B4 — Screen off after use (emulator)
Open the pet screen, then turn the screen off. Run this with always-on **disabled**; with it enabled, the screen enters ambient mode instead (B4a).
- [ ] Within about 10 seconds, rendering stops (no new frames in the trace) and sensor connections are released.
- [ ] The 60-second decay ticker stops too: no more once-a-minute bursts of app work in the trace.

### B4a — Ambient mode (emulator) ([DD-44](DESIGN_DECISIONS.md#dd-44--the-pet-screen-stays-on-in-ambient-mode-as-a-static-outline-and-releases-the-step-sensor))
With always-on enabled, open the pet screen and let it dim. Capture a System Trace (§1.6) for 5 minutes.
- [ ] Between the once-a-minute updates there are no new frames: the ambient pet doesn't animate.
- [ ] The app has no active sensor connections while ambient (§1.3).

### B5 — Doze (emulator)
Force Doze (§1.5) with an alert check pending.
- [ ] The check is deferred to a maintenance window rather than waking the device, and runs once the window opens (`deviceidle step`).

### B6 — Real-world cost (physical watch)
1. Charge the watch fully, run `adb shell dumpsys batterystats --reset`, then wear it for a normal day with the app installed. Open the pet screen a few times and take at least one walk.
2. Capture `dumpsys batterystats --charged com.healthcompanion.wear` and a bug report for Battery Historian.
3. Repeat on a comparable day without the app, or with it disabled, for a baseline.
- [ ] Record the app's estimated mAh and its share of the total, plus the overall battery drop with and without the app.
- [ ] Record the cost of a 15-minute walk with the pet screen on (live reactions) compared with screen off.
- [ ] Target: the app should not noticeably shorten a day's battery life. A few percent is the upper bound.

---

## Results log

| Date | Check | Device / image | Result | Notes |
| :--- | :--- | :--- | :--- | :--- |
| 2026-09-26 | V3 (Pet Steps, partial), V13 (partial) | Wear_OS_Large_Round, API 37 | Pass | Pet Steps listed in the complication picker and rendered as a ranged value in the Perfunctory face ("135K", full ring, walking icon). The Goals page showed the synthetic totals with check marks. Not checked: short text, updates after a batch, the "vital filled up" tick (every vital was already full). |
| 2026-09-26 | V12 (partial) | Wear_OS_Large_Round, API 37 | Pass | List, steppers, crown stepping (clockwise increases; one `SCROLL,-2` event moved four steps), bedtime wrapping 23:00 → 00:00, and saving all worked. Setting bedtime to 18:00 at 18:24 put the pet to sleep at once. Ambient mode on the settings list showed the ambient pet and returned to the list. Screens stay pure black. Not checked: tile and complication, reboot, vibration switch against `dumpsys`. |
| 2026-09-26 | V11 (partial), V10 haptics | Wear_OS_Large_Round, API 37 | Pass | Petting played the composed purr (4 × `TICK`, `usage: TOUCH`) per `dumpsys vibrator_manager`. Crown paging produced rotary `CLICK` feedback. The goal pattern could not be triggered: synthetic walking had already reached every goal that day, so no pattern was correct. |
| 2026-09-26 | DD-46 | Wear_OS_Large_Round, API 37 | Pass | After logging water, hydration shows 100% (was 99%). "100%" fits the Vitals rows. |
| 2026-09-26 | V10 (partial) | Wear_OS_Large_Round, API 37 | Pass | Crown (`rotaryencoder scroll --axis SCROLL,±1`) and swipe both snap between the pet and Vitals pages. The Vitals page fits the large round screen. Tapping water on the pet page still logs (hydration capped at 100, shown as 99% because values are truncated). Haptics and small screens not checked. |
| 2026-09-26 | V9 (partial) | Wear_OS_Large_Round, API 37 | Pass | `KEYCODE_SLEEP` enters the ambient look (time, name, outline pet, thin ring), also from the Vitals page. Found and fixed: Material `TimeText` drew a filled pill behind the time. Minute updates, burn-in shift and sensor release not checked. |
| | | | | |
