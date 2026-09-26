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
3. [x] **AR-8 — Data durability** 🟠 *(must land before any Room schema change)*
   - [x] `exportSchema = true`. Commit the schema JSON. No destructive fallback on upgrade. Debuggable builds keep it for downgrades only.
   - [x] Clamp both bounds in `applyHabit` and make `PetEntity.toDomain()` tolerant of out-of-range values and unknown enum names.
   - [x] Migration test harness from schema v1 (Robolectric + `MigrationTestHelper`) and a CI check for uncommitted schema changes.
4. [x] **AR-6 — Permissions & registration** 🟠
   - [x] Register passive data types per granted permission (partial degradation).
   - [x] Adopt API 36 granular health permissions (`health.READ_HEART_RATE`, `health.READ_HEALTH_DATA_IN_BACKGROUND`) plus an optional background heart-rate request.
   - Manual device verification moved to [VERIFICATION.md](VERIFICATION.md) (V1, V4, V5).
   - [x] Explicit, idempotent registration on grant and on boot (`BootCompletedReceiver` → `PassiveRegistrationWorker`). Process starts only re-register when the permission set or the boot count changed.
5. [x] **AR-7 — Layering & dependency wiring** 🟡
   - [x] Remove the `:core:health` → `:core:data` dependency (`PassiveDataDependencies` provider interface).
   - [x] Single shared dependency graph (manual `AppContainer`) for the activity, view models, service and tile. `PetDecayWorker` is left as-is for AR-5.
   - [x] Inject a `Clock` into the repository, use cases and `PetViewModel`. Engines take explicit timestamps.
6. [x] **AR-4 — Reactive surfaces** 🟠
   - [x] Request a tile update after every committed write (`NotifyingPetRepository`, wired in `AppContainer`).
   - [x] Replace `runBlocking` in `PetStatusTileService` with `serviceScope.future { }` (also fixes the `ResolvableFuture` lint errors).
   - [x] Add a subscription-scoped 60 s ticker to `GetPetStateUseCase` so decay advances on an open screen.
   - The complication provider is still planned in Phase 3 and should hook into the same refresh callback.
7. [x] **AR-5 — Repurpose or remove `PetDecayWorker`** 🟡
   - [x] Decided: remove it (DD-32). Tile refresh is handled by AR-4 and day rollover by AR-1. Critical-vital notifications (Phase 2) will get their own scheduled work when implemented.
   - [x] Removed the explicit `WAKE_LOCK` permission (WorkManager still merges it in). Cancel the legacy periodic job on upgrade.
8. [x] **AR-3 — Game-loop completeness** 🟠 *(depends on AR-8)*
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
- [ ] **Configurable bedtime** instead of the fixed 22:00–07:00 `NightWindow` (DD-33).

---

## Phase 2b: Brand Identity & Naming
- [ ] **Final Naming Decision**: Choose official app & companion name from curated candidates:
  - **Resona**: Resonating with your body’s biological rhythms and habits.
  - **Symbio**: Two organisms mutually flourishing in biological symbiosis.
  - **Vitalkin**: A kindred wrist companion sharing your life-energy.
  - **Paravita**: A creature living a parallel life alongside your daily routine.
  - **Vitecho**: A responsive companion where daily habits echo directly into vitals.
  - **AuraSync**: Synchronizing your aura and well-being directly with watch sensors.
- [ ] **Brand Refactor**: Update all references to the working title across the project:
  - **App identity**
    - [ ] `wearApp/src/main/res/values/strings.xml` — `app_name` string value (`"Health Companion"`)
    - [ ] `wearApp/build.gradle.kts` — `namespace` and `applicationId` (`com.healthcompanion.wear`)
    - [ ] `wearApp/src/main/AndroidManifest.xml` — `android:name=".HealthCompanionApp"` (if the Application class is renamed)
  - **Kotlin source & package namespace** (affects all `.kt` files, including tests — use IDE refactor: *Rename Package*)
    - [ ] All `package com.healthcompanion.*` declarations
    - [ ] All `import com.healthcompanion.*` statements
    - [ ] `core/*/build.gradle.kts` — `namespace` in each module (`com.healthcompanion.core.*`)
    - [ ] `wearApp/src/main/java/com/healthcompanion/wear/HealthCompanionApp.kt` — class name and file
    - [ ] `core/ui/src/main/java/com/healthcompanion/core/ui/theme/Theme.kt` — `HealthCompanionTheme` function name and all call sites
    - [ ] Physical source directory tree (`src/main/java/com/healthcompanion/…`) — renamed automatically by IDE package refactor
  - **Build configuration**
    - [ ] `settings.gradle.kts` — `rootProject.name = "HealthCompanion"`
  - **Documentation**
    - [ ] `README.md` — title heading, CI badge URL, and `git clone` URL
    - [ ] `docs/ARCHITECTURE.md` — `Health Companion` in overview, `HealthCompanionApp.kt` reference, package path in module diagram, `com/healthcompanion/wear/` source tree
    - [ ] `docs/ROADMAP.md` — title heading (`# Health Companion: Project Roadmap`) and this checklist itself
    - [ ] `docs/DESIGN_DECISIONS.md` and `docs/reviews/*.md` — `Health Companion` in the intros, and package paths such as `core/data/.../repository/`
    - [ ] `CONTRIBUTING.md` — title, working-title note, `git clone` URL, `health-companion/` folder references, project structure tree
    - [ ] `NOTICE` — project name and GitHub URL on lines 1 and 5
  - **CI / GitHub**
    - [ ] `.github/workflows/ci.yml` — `name:` field and the uploaded artifact name (`wearApp-debug`)
    - [ ] GitHub repository name itself (Settings → Repository name) — this automatically redirects the old URL, but update all hardcoded URLs above to match
    - [ ] README CI badge URL (`https://github.com/DunderGG/health-companion/…`)

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
- [x] Watch Face Complication Provider (`PetMoodComplicationService`): pet mood face (short text / icon) and an overall-health ring on standard watch dials, refreshed from the same `NotifyingPetRepository` callback as the tile (DD-43). A step-progress ring needs a daily step goal first (DD-43, your call).
- [x] Ambient Mode support: Low-power grayscale rendering for always-on displays. Static outline pet and ring, time shown, burn-in shift, step sensor released (DD-44).
- [ ] Rotary Crown Integration: Use watch crown for smooth zooming, inspecting vitals breakdown, and interactive petting.
- [ ] Haptic Feedback: Custom vibration patterns on petting, evolution, and goal completion.

---

## Phase 4: Gamification, Evolution & Mini-Games
- [ ] Expanded visual evolutions: Distinct vector sprites for Egg, Hatchling, Child, Teen, Adult, and Ancient Sage.
- [ ] Archetype transformations: Visual accessories for Swift Strider (running headband), Zen Ascetic (halo/lotus aura), and Mighty Titan (armbands).
- [ ] Watch mini-game: Rhythmic breathing exercise / water catch mini-game using the rotary dial.
- [ ] Sound effects: Subtle, retro-modern chimes on goal achievement.

---

## Phase 5: Ecosystem & Companion Mobile App (Optional)
- [ ] Android companion phone app module (`:phoneApp`).
- [ ] Wearable Data Layer API sync: Two-way sync between watch and phone.
- [ ] Detailed health charts: Weekly activity trends, water intake logs, and meal history.
- [ ] Cloud backup and companion export/import.

---

## Testing & Verification

- **Unit tests**: `./gradlew test` (Windows: `.\gradlew.bat test`).
- **Emulator, device and battery checks**: see [VERIFICATION.md](VERIFICATION.md), including how to simulate Health Services sensor data on the emulator.
