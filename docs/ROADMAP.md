# Health Companion: Project Roadmap

A phased development roadmap guiding the evolution of the Wear OS health-mirroring companion.

---

## Phase 1: Architecture, Core Game Loop & Scaffolding (Completed)
- [x] Multi-module Gradle configuration (`:wearApp`, `:core:model`, `:core:domain`, `:core:data`, `:core:health`, `:core:ui`).
- [x] Pure domain models (`Pet`, `Vitals`, `Mood`, `HabitType`, `EvolutionStage`, `PetArchetype`).
- [x] Mathematical time-delta decay engine (`PetDecayEngine`) and dynamic mood evaluation (`MoodCalculator`).
- [x] Room database persistence (`CompanionDatabase`, `PetDao`, `PetEntity`).
- [x] Modern animated vector companion renderer (`ModernPetCanvas`) with breathing physics, eye blinking, and mood expressions.
- [x] Circular multi-vital progress ring (`VitalsRing`) for round watch displays.
- [x] Standalone Wear OS Main Activity, ViewModel, and Pet Screen with 1-tap quick actions.
- [x] Wear OS Carousel Tile skeleton (`PetStatusTileService`).
- [x] Automated unit test suite verifying decay math and mood rules (`./gradlew test`).

---

## Phase 2: Sensor Calibration & Passive Health Sync
- [x] Runtime permission flow on watch for `BODY_SENSORS` and `ACTIVITY_RECOGNITION`. *(Reworked in AR-6: API 36 health permissions, optional background heart rate.)*
- [x] Connect `HealthServicesManager` to live watch hardware sensors.
  - [x] Query device capabilities via `getCapabilitiesAsync()` to discover supported passive data types.
  - [x] Subscribe to all available passive sensors: `HEART_RATE_BPM`, `CALORIES_DAILY`, `DISTANCE_DAILY`, `FLOORS_DAILY` (in addition to existing `STEPS_DAILY`).
  - [x] Expand `PassiveDataService` to dispatch heart rate, calories, distance, and floor data to the pet engine.
  - [x] Add `HabitType.HeartRate(bpm)` to domain model and handle `SampleDataType` vs `IntervalDataType` differences.
  - [x] Integrate new sensor data into `PetDecayEngine` (heart rate → fitness, calories → workout bonus). *Superseded by AR-1: daily totals are now consumed as deltas; distance and calories are no longer consumed.*
  - [x] Graceful capability fallbacks: skip unsupported data types on watches without specific sensors.
- [x] Real-time step delta mapping: Convert real-world step bursts into instant companion animation reactions (e.g. running alongside user).
  - [x] Foreground-only `SensorLiveStepSource` (step detector, step-counter fallback), held only while the pet screen is visible (DD-37).
  - [x] Pure `StepCadence` tracker → `IDLE` / `WALKING` / `RUNNING` with hysteresis. Cosmetic only, never awarded (DD-38, DD-39).
  - [x] `ModernPetCanvas` gait: step bob, alternating paws, forward lean, speed lines when running. A sleeping pet stays asleep.
- [x] Local push notifications via WorkManager when hydration or hunger reaches critical thresholds.
  - [x] Pure `VitalAlertPlanner`: predicted threshold crossing, one alert per episode, quiet hours during the pet's night (DD-41).
  - [x] One-time `VitalAlertWorker` scheduled at the predicted crossing and re-checked after every pet write. No periodic work (DD-40).
  - [x] `POST_NOTIFICATIONS` requested during onboarding. Alerts clear themselves once the vital recovers.

On-device verification and battery profiling for this phase are tracked in [VERIFICATION.md](VERIFICATION.md): live step reactions (V7), notifications (V8), and battery (B1–B6).

---

## Phase 2a: Architecture Review Remediation
Fixes for the findings in the [2026-09-26 architecture review](reviews/2026-09-26-architecture-review.md). The steps are listed in the recommended order, and each one is sized to land as its own PR. Tick a finding off here, then add a **Resolution** to the finding in the review, remove or update its ⚠ notes in ARCHITECTURE.md, and record any non-trivial choices made along the way in [DESIGN_DECISIONS.md](DESIGN_DECISIONS.md).

1. [x] **AR-2 — Atomic pet updates** 🔴
   - [x] Wrap `recordHabit()` read-modify-write in `withTransaction { }` (via `PetRepository.updatePet { transform }`).
   - [x] Apply the same to `PetDecayWorker`, `CalculateDecayUseCase`, and the default-pet insert in `getPetFlow()` (now `insertIfAbsent`).
   - [x] Integration test (Robolectric + in-memory Room, runs in `./gradlew test`): concurrent `recordHabit()` calls lose no updates.
2. [x] **AR-1 — Daily totals → deltas** 🔴
   - [x] Persist the last-consumed total and its day per `*_DAILY` data type (DataStore `passive_sync`, avoiding a Room schema change before AR-8).
   - [x] Apply only positive deltas, in whole units with the remainder carried over. Reset the baseline on day rollover.
   - [x] Collapse each sensor batch into a single transactional write (`PetRepository.recordHabits()`).
   - [x] Drop distance (it double-counts steps). Keep floors as a delta-based climbing bonus.
   - [x] Stop consuming `CALORIES_DAILY` (no energy drain from basal burn).
   - [x] Cap heart-rate awards to one per 30 minutes.
   - [x] Unit tests: delta calculation, midnight reset, repeated/out-of-order/concurrent batches.
3. [x] **AR-8 — Data durability** 🟡 *(must land before any Room schema change)*
   - [x] `exportSchema = true`. Commit the schema JSON. No destructive fallback on upgrade. Debuggable builds keep it for downgrades only.
   - [x] Clamp both bounds in `applyHabit` and make `PetEntity.toDomain()` tolerant of out-of-range values and unknown enum names.
   - [x] Migration test harness from schema v1 (Robolectric + `MigrationTestHelper`) and a CI check for uncommitted schema changes.
4. [x] **AR-6 — Permissions & registration** 🟡
   - [x] Register passive data types per granted permission (partial degradation).
   - [x] Adopt API 36 granular health permissions (`health.READ_HEART_RATE`, `health.READ_HEALTH_DATA_IN_BACKGROUND`) plus an optional background heart-rate request.
   - Manual device verification moved to [VERIFICATION.md](VERIFICATION.md) (V1, V4, V5).
   - [x] Explicit, idempotent registration on grant and on boot (`BootCompletedReceiver` → `PassiveRegistrationWorker`). Process starts only re-register when the permission set or the boot count changed.
5. [x] **AR-7 — Layering & dependency wiring** 🟢
   - [x] Remove the `:core:health` → `:core:data` dependency (`PassiveDataDependencies` provider interface).
   - [x] Single shared dependency graph (manual `AppContainer`) for the activity, view models, service and tile. `PetDecayWorker` is left as-is for AR-5.
   - [x] Inject a `Clock` into the repository, use cases and `PetViewModel`. Engines take explicit timestamps.
6. [x] **AR-4 — Reactive surfaces** 🟡
   - [x] Request a tile update after every committed write (`NotifyingPetRepository`, wired in `AppContainer`).
   - [x] Replace `runBlocking` in `PetStatusTileService` with `serviceScope.future { }` (also fixes the `ResolvableFuture` lint errors).
   - [x] Add a subscription-scoped 60 s ticker to `GetPetStateUseCase` so decay advances on an open screen.
   - The complication provider is still planned in Phase 3 and should hook into the same refresh callback.
7. [x] **AR-5 — Repurpose or remove `PetDecayWorker`** 🟢
   - [x] Decided: remove it (DD-32). Tile refresh is handled by AR-4 and day rollover by AR-1. Critical-vital notifications (Phase 2) will get their own scheduled work when implemented.
   - [x] Removed the explicit `WAKE_LOCK` permission (WorkManager still merges it in). Cancel the legacy periodic job on upgrade.
8. [x] **AR-3 — Game-loop completeness** 🟡 *(depends on AR-8)*
   - [x] Energy restoration source: night rest (+8 %/h, 22:00–07:00 local, `NightWindow`), computed per day/night segment (DD-33).
   - [x] Pass `isNightTime` to `MoodCalculator`. The pet is `SLEEPING` throughout its night (DD-34).
   - [x] `habit_events` table (schema v2, first real migration) and 7-day consistency-based archetype selection. `IRON_BEAST` is reachable via workouts or heart rate ≥ 100 bpm (DD-35, DD-36).
   - Sleep sensing, workouts and a configurable bedtime moved to [Phase 2a follow-ups](#phase-2a-follow-ups).

### Phase 2a follow-ups
Open work that came out of the review. None of it blocks merging the remediation.

- The **device smoke test** for the remediation (onboarding, passive data, surfaces, reboot, app update, midnight) is now V1–V6 in [VERIFICATION.md](VERIFICATION.md).
- [ ] **Design calls**: settle the 🟣 "Needs your decision" items at the top of [DESIGN_DECISIONS.md](DESIGN_DECISIONS.md) (balance values, night rules, archetype thresholds, heart-rate dialogs).
- [ ] **Real sleep signal**: Health Services sleep detection (`UserActivityState.USER_ACTIVITY_ASLEEP`) layered on the night rest (DD-33).
- [ ] **Workouts**: `ExerciseClient` sessions as `HabitType.Workout`, which also strengthens the `IRON_BEAST` path (DD-36).
- [x] **Configurable bedtime** instead of the fixed 22:00–07:00 `NightWindow` (DD-33). *Done in Phase 3a (DD-48).*

---

## Phase 2b: Brand Identity & Naming
- [x] **Final Naming Decision**: the app is called **Thriveling**, a small creature that thrives when you do (DD-55). Candidates that were considered:
  - **Resona**: Resonating with your body’s biological rhythms and habits.
  - **Symbio**: Two organisms mutually flourishing in biological symbiosis.
  - **Vitalkin**: A kindred wrist companion sharing your life-energy.
  - **Paravita**: A creature living a parallel life alongside your daily routine.
  - **Vitecho**: A responsive companion where daily habits echo directly into vitals.
  - **AuraSync**: Synchronizing your aura and well-being directly with watch sensors.
- [ ] **Brand Refactor**: Replace the working title across the project. The steps are ordered so that the permanent choice, the `applicationId`, comes last.
  1. **App identity and code names** (no package change)
     - [ ] `wearApp/src/main/res/values/strings.xml` — `app_name` (`"Health Companion"` → `"Thriveling"`)
     - [ ] `wearApp/src/main/java/com/healthcompanion/wear/HealthCompanionApp.kt` — class and file → `ThrivelingApp`, plus `android:name` in `AndroidManifest.xml` and all call sites
     - [ ] `core/ui/src/main/java/com/healthcompanion/core/ui/theme/Theme.kt` — `HealthCompanionTheme` → `ThrivelingTheme`, and all call sites
     - [ ] `settings.gradle.kts` — `rootProject.name = "HealthCompanion"` → `"Thriveling"`
     - [ ] `CompanionDatabase.DATABASE_NAME` — `health_companion.db` → `companion.db`, so the file name is not tied to any app name (DD-56)
  2. **Documentation**
     - [ ] `README.md` — title heading and the working-title note
     - [ ] `docs/ARCHITECTURE.md` — `Health Companion` in the overview and prose, `HealthCompanionApp` references, the database file name
     - [ ] `docs/ROADMAP.md` — title heading (`# Health Companion: Project Roadmap`)
     - [ ] `docs/DESIGN_DECISIONS.md` and `docs/reviews/README.md` — `Health Companion` in the intros, `HealthCompanionApp` references in current entries. The dated review files are point-in-time records and keep the names they were written with.
     - [ ] `CONTRIBUTING.md` — title and working-title note
     - [ ] `NOTICE` and `SECURITY.md` — project name
  3. **CI / GitHub**
     - [ ] `.github/workflows/ci.yml` — uploaded artifact name (`wearApp-debug`)
     - [ ] Rename the GitHub repository (Settings → Repository name, e.g. `thriveling`). GitHub redirects the old URLs, but only until someone creates a new repository with the old name, so update the hardcoded URLs right after:
       - [ ] `README.md` — CI badge URL and `git clone` URL
       - [ ] `CONTRIBUTING.md` — `git clone` URL, `health-companion/` folder references and the project structure tree
       - [ ] `docs/ARCHITECTURE.md` — `health-companion/` in the source tree
       - [ ] `NOTICE` — GitHub URL
       - [ ] `.github/ISSUE_TEMPLATE/config.yml`, `.github/ISSUE_TEMPLATE/feature_request.yml`, `.github/pull_request_template.md` — links to `DunderGG/health-companion`
  4. **Final step: `applicationId` and package name.** The `applicationId` can never change after the first Play Store upload, and renaming the tile, complication and background service classes after launch would remove them from users' watches. Do this step before the first upload.
     - [ ] Decide the `applicationId` (e.g. `com.dundergg.thriveling`, or `app.thriveling` if that domain is bought)
     - [ ] `wearApp/build.gradle.kts` — `applicationId` and `namespace` (`com.healthcompanion.wear`)
     - [ ] `core/*/build.gradle.kts` — `namespace` in each module (`com.healthcompanion.core.*`)
     - [ ] All `package` and `import com.healthcompanion.*` statements, including tests, and the physical source directories (IDE refactor: *Rename Package*)
     - [ ] `wearApp/src/main/AndroidManifest.xml` — fully qualified `PassiveDataService` and `BootCompletedReceiver` names
     - [ ] `docs/VERIFICATION.md` — `adb` commands and Logcat filter using `com.healthcompanion.wear`
     - [ ] `docs/ARCHITECTURE.md` and `docs/DESIGN_DECISIONS.md` — source links and paths containing `com/healthcompanion/`. The dated review files keep their links; GitHub serves them from the commit history.

---

## Phase 2c: Pet Visual Identity & Graphics Engine Evaluation
Evaluation and prototyping phase to determine the long-term character rendering architecture for the virtual companion:
- [ ] **Path 1: AI Pixel Art / Retro Sprite Sheets**
  - Prompt AI tools (Retro Diffusion, Midjourney, DALL-E) to generate 16-bit Tamagotchi-style sprite sheets.
  - Implement a lightweight Compose frame-cycling animator (`SpriteSheetRenderer`) for idle, eating, and sleeping loops.
  - Benchmark texture memory footprint and watch battery drain on Wear OS.
- [ ] **Path 2: Rive Community Character & State Machine**
  - Integrate `rive-android` runtime into `:core:ui`.
  - Source a CC-licensed community creature (blob, animal, or robot) with pre-rigged states (`idle`, `happy`, `sad`, `eat`, `sleep`).
  - Connect `MoodCalculator` outputs and user tap events to Rive State Machine inputs.
  - Profile APK size impact (Rive C++ runtime overhead) and frame rendering performance on round displays.
- [ ] **Path 3: AI Vector Generation $\rightarrow$ Custom Rive Rigging**
  - Generate layered SVG character assets using AI vector tools (Recraft.ai / ChatGPT).
  - Import SVG into the Rive web editor, configure bone deformers and timeline animations.
  - Export custom `.riv` asset and bind into the watch app.
- [ ] **Final Graphics Engine Decision**: Choose between Procedural Vectors, Rive State Machine, or Retro Pixel Sprites based on battery consumption, APK size, and visual appeal.

---

## Phase 3: Wear OS Native Surfaces & Micro-Interactions
- [x] Interactive Wear OS Carousel Tile with direct 1-tap "+250ml Water" action button via ProtoLayout. Each tap logs once, tapping the vitals opens the app (DD-42).
- [x] Watch Face Complication Provider (`PetMoodComplicationService`): pet mood face (short text / icon) and an overall-health ring on standard watch dials, refreshed from the same `NotifyingPetRepository` callback as the tile (DD-43). The step-progress ring came later as the separate "Pet Steps" complication (Phase 3a, DD-51).
- [x] Ambient Mode support: Low-power grayscale rendering for always-on displays. Static outline pet and ring, time shown, burn-in shift, step sensor released (DD-44).
- [x] Rotary Crown Integration: the crown (or a swipe) pages between the pet and a new Vitals page with all five vitals. Zoom and crown petting were considered and dropped; petting stays a tap (DD-45).
- [x] Haptic Feedback: Custom vibration patterns on petting (purr), evolution (fanfare), and goal completion (success), where a goal is today first reaching a daily focus goal: 6,000 steps, a workout or heart rate ≥ 100, or 1,500 ml + 2 healthy meals (DD-47).

---

## Phase 3a: Settings & Daily Goals
- [x] **Settings screen**: a Settings button on a third pager page opens a settings list: daily goals for steps, water and healthy meals (steppers, crown too), bedtime (18:00–03:00 to 04:00–12:00, whole hours) and a vibration switch. The goals only drive the daily goal vibration; the archetype keeps its fixed thresholds (DD-48).
- [x] **Visible goals**: a Goals page between Vitals and Settings shows today's steps, water, healthy meals and workout against the user's goals, with bars and ✓ marks, from the same calculation as the goal vibration (DD-49).
- [x] **"Vital filled up" haptic**: a light tick-click when a vital shown on screen reaches 100 % from below while the pet UI is open, never while the pet sleeps (DD-50).
- [x] Step-progress complication: a second complication, "Pet Steps", with a ring of today's steps towards the step goal (ranged value) or the step count (short text), next to Pet Mood (DD-51).
- [x] **Start over**: a button at the end of Settings, with a confirmation dialog, replaces the pet with a new hatchling and deletes the habit history; settings are kept (DD-52).

---

## Testing & Verification

- **Unit tests**: `./gradlew test` (Windows: `.\gradlew.bat test`).
- **Emulator, device and battery checks**: see [VERIFICATION.md](VERIFICATION.md), including how to simulate Health Services sensor data on the emulator.

---

## General Improvements
>The Future of the Health Companion.

**Priority** (same colors as Phase 2a):
- 🔴 **High**: fixes a fairness, data-loss or core-loop gap, or is cheap and makes the pet feel owned. Do these first.
- 🟡 **Medium**: noticeably improves the Tamagotchi feel or daily use. Do these after the high items.
- 🟢 **Low**: nice to have, large effort, or depends on other work.
- 🟣 **Needs a decision**: a game-design call to settle before implementation.

**Suggested first batch** (🔴): rate-limit the care buttons, name your pet, evolution ceremony, decide what happens after long neglect, rest / sick-day mode, local backup, battery usage.

### Care Balance & Anti-Spam
- [ ] 🔴 **Rate-limit the care buttons**: water and food should not be spammable. Add a per-action cooldown or diminishing returns (e.g. each extra glass of water within an hour counts less).
- [ ] 🟡 **A full pet refuses**: when a vital is already full, the pet turns the food or water away (head shake) instead of the tap being logged silently. This is the classic Tamagotchi overfeeding feedback.
- [ ] 🟡 **Undo the last log**: a short undo window (snackbar or confirmation) for accidental taps on the tile or the pet screen.
- [ ] 🟢 **Treats vs. meals**: make unhealthy meals a "treat" with a real trade-off (a happiness boost but a small energy cost, overeating makes the pet sluggish) instead of just a less effective meal.
- [ ] 🟢 🟣 **Clock-change cheating**: decide how to handle the user moving the device clock forward or backward ("time travel"). Options are to ignore it, clamp negative or huge deltas, or detect it using elapsed realtime.

### Pet Life Cycle
- [ ] 🟡 **Hatching from an egg**: new pets currently start as `HATCHLING` with 100 XP, so `EvolutionStage.EGG` is never used. Start as an egg that hatches after the first steps or the first day, with a hatching animation.
- [ ] 🔴 **Name your pet**: a naming step when the pet hatches, plus a rename option in Settings. The name is currently always `"Aura"`.
- [ ] 🟢 **Age and birthdays**: show the pet's age in days (from `bornTimestamp`), and celebrate milestones such as 7, 30 and 100 days and yearly birthdays.
- [ ] 🔴 **Evolution ceremony**: a full-screen moment (animation, haptic fanfare, "Aura evolved into a Teen!") instead of a silent stage change, including an archetype reveal at `TEEN`.
- [ ] 🟡 🟣 **Sickness from neglect**: a vital stuck at 0 for several hours makes the pet sick (a visual state plus slower recovery), and it takes sustained care to recover, not a single tap. It has to stay gentle for a health app.
- [ ] 🔴 🟣 **What happens after long neglect**: the classic answer is that the pet dies. The alternatives are that it goes dormant or hibernates, runs away and comes back once the user is active again, or never reaches a failure state at all. This should be settled before sickness is built.
- [ ] 🟢 **Expanded visual evolutions**: distinct vector sprites for Egg, Hatchling, Child, Teen, Adult and Ancient Sage.
- [ ] 🟢 **Archetype transformations**: visual accessories for Swift Strider (running headband), Zen Ascetic (halo / lotus aura) and Mighty Titan (armbands).
- [ ] 🟢 **Legacy and generations**: an `ANCIENT_SAGE` can "retire", and the next egg inherits a trait or color from it. A **memorial / hall of fame** lists past pets (name, age, archetype, lifetime steps), which also gives "Start over" (DD-52) something to keep.

### Personality & Expression
- [ ] 🟡 **Idle behaviors**: random small animations while the screen is open (yawning, stretching, looking around, chasing a particle), so the pet feels alive between interactions.
- [ ] 🟢 **Tap zones and gestures**: different reactions to tapping the head, the belly or a long press (tickle, giggle, sleepy grumble if it is woken at night).
- [ ] 🟡 **Speech and thought bubbles**: short hints for what the pet wants ("💧?", "so sleepy…", "let's walk!"), which make low vitals readable without opening the Vitals page.
- [ ] 🟢 **Bond / affection**: a slow-growing relationship stat, separate from happiness, earned through consistent daily care over weeks. It unlocks small behaviors (the pet greets you, follows your finger).
- [ ] 🟢 **Daily rituals**: a morning greeting the first time the pet is seen after the night window, and a yawn and goodnight around bedtime.
- [ ] 🟢 **Time-of-day ambience**: background tint or sky that follows morning, day, evening and night, in line with the configurable bedtime.
- [ ] 🟢 **Customize appearances**: Let the user change the pet's color and appearances.
- [ ] 🟢 **Customize name font**: Let the user change the pet's name font and looks on main screen.

### Rewards & Collection
- [ ] 🟡 **Streaks**: a streak counter for days with a goal met, with forgiving "freeze" days so a single missed day doesn't reset weeks of progress.
- [ ] 🟢 **Achievements / badges**: e.g. first 10k-step day, 7-day hydration streak, first evolution, 100 pets. Shown on a trophy page.
- [ ] 🟢 **Items and inventory**: toys, food varieties and backgrounds, earned through activity (e.g. a coin per 1,000 steps). A toy gives a happiness bonus and plays its own animation.
- [ ] 🟢 **Surprise moments**: an occasional random event (the pet finds a gift, a butterfly visits) to reward opening the app without making it a slot machine.
- [ ] 🟢 **Pet journal**: a daily line from the pet's perspective ("We walked 8,214 steps together today!") and a weekly recap, which is simpler on the watch than the phone app's charts.
- [ ] 🟢 **Watch mini-game**: a rhythmic breathing exercise or a water-catch mini-game played with the rotary dial.

### Notifications & Surfaces
- [ ] 🟢 **Attention calls**: besides critical-vital alerts, the pet occasionally "calls" (at most a few times a day, never at night) when it wants to play or go for a walk. The Tamagotchi call, but rate-limited.
- [ ] 🟡 **Richer tile**: show the pet's most urgent need as an icon, and add a quick "feed" action next to "+250 ml Water".
- [ ] 🟢 **Pet watch face** (Watch Face Format): the pet lives on the watch face itself, with the time and the vitals ring. It is the most direct way to have it "always with you".
- [ ] 🟢 **Notification sound and vibration identity**: a consistent sound and vibration signature per alert type, so the user knows what the pet wants without looking.
- [ ] 🟢 **Sound effects**: subtle, retro-modern chimes on goal achievement.

### Wellbeing & Fairness
- [ ] 🔴 **Rest / sick-day mode**: pause or slow decay for a day or more when the user is ill or on holiday, so the app never punishes real-life rest.
- [ ] 🟡 🟣 **Off-wrist and charging**: decide whether decay should slow while the watch is charging or off the wrist (overnight charging currently overlaps with the night window).
- [ ] 🟡 **No guilt-tripping**: review notification and mood copy to make sure it encourages rather than shames, especially for sickness and neglect states.
- [ ] 🟡 **Timezone travel**: make the day rollover, night window and goals behave sensibly when the user crosses timezones (no double day, no missed night).

### Phone Companion App
- [ ] 🟢 **Phone app module**: an optional Android companion app (`:phoneApp`).
- [ ] 🟢 **Watch–phone sync**: two-way sync between the watch and the phone over the Wearable Data Layer API.
- [ ] 🟢 **Detailed health charts**: weekly activity trends, water intake logs and meal history on the phone.

### Accessibility & Localization
- [ ] 🟡 **TalkBack**: content descriptions for the pet's mood, vitals and goal progress on every screen, the tile and the complications.
- [ ] 🟢 **Color-blind-safe vitals**: don't rely on ring colors alone. Add icons or patterns for each vital.
- [ ] 🟢 **Localization**: move the remaining hardcoded strings (e.g. `PetArchetype` titles and descriptions) to string resources, and add at least one more language.
- [ ] 🟢 **Light Theme**: support a light theme for the app, ensuring readability and visual consistency with the dark theme.

### Performance, Reliability & Tooling
- [ ] 🔴 **Battery usage**: make sure background services and sensors are managed efficiently to minimize battery drain (see B1–B6 in [VERIFICATION.md](VERIFICATION.md)).
- [ ] 🔴 **Local backup**: Android Auto Backup rules so the pet survives a reinstall or a new watch before a cloud backup exists.
- [ ] 🟢 **Cloud backup**: back up the pet to the cloud, plus export / import of the companion.
- [ ] 🟡 **Debug menu** (debug builds only): set vitals, XP, stage and a fake clock offset to test evolution, sickness and night behavior quickly. Also a place to show the silent row repairs from DD-20.
- [ ] 🟢 **Line Implication**:  We have circle complications, but a line complication could provide a more continuous view of the pet's vitals and activity throughout the day.