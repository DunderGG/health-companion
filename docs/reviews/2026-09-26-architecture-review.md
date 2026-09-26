# Architecture Review — 2026-09-26

- **Scope**: The whole implementation (all modules), compared with [ARCHITECTURE.md](../ARCHITECTURE.md) as it stood on 2026-09-26.
- **Outcome**: 8 findings (AR-1 … AR-8), all resolved on the `architecture-review-remediation` branch. The fixes were scheduled in [ROADMAP.md → Phase 2a](../ROADMAP.md#phase-2a-architecture-review-remediation).
- **Related**: The reasoning behind the fixes is logged as DD-05 to DD-36 in [DESIGN_DECISIONS.md](../DESIGN_DECISIONS.md).

## Summary

The overall structure is sound: layered modules, a pure-Kotlin domain, Room as the single source of truth, and decay-on-read instead of background ticking. The findings are correctness bugs or gaps between the design and the code. Each has an ID so it can be tracked, fixed, and closed one at a time.

Each finding keeps its original problem statement (what was wrong at review time) together with its resolution, so the history stays readable after the code has moved on.

## Findings

| ID | Severity | Status | Summary |
| :--- | :--- | :--- | :--- |
| AR-1 | 🔴 Critical | ✅ Resolved | Cumulative daily sensor totals are applied as deltas |
| AR-2 | 🔴 Critical | ✅ Resolved | Pet updates are non-atomic read-modify-write (lost updates) |
| AR-3 | 🟠 High | ✅ Resolved | Some vitals cannot recover; sleep, night mood, and consistency-based archetypes are unimplemented |
| AR-4 | 🟠 High | ✅ Resolved | Tiles, complications, and the open screen do not update reactively |
| AR-5 | 🟡 Medium | ✅ Resolved | `PetDecayWorker` is redundant under decay-on-read |
| AR-6 | 🟠 High | ✅ Resolved | Permission degradation is all-or-nothing; API 36 permission model; implicit boot re-registration |
| AR-7 | 🟡 Medium | ✅ Resolved | Layering violation (`:core:health` → `:core:data`) and fragmented dependency wiring |
| AR-8 | 🟠 High | ✅ Resolved | Destructive migrations can delete the pet; invalid rows crash the app |

### AR-1 — Cumulative daily totals treated as deltas ✅ Resolved
- **Resolution**: `PassiveDataService` now only parses batches. `IngestPassiveDataUseCase` consumes deltas via the pure `DailyTotalTracker` (DataStore-backed `PassiveSyncRepositoryImpl`) and applies all resulting habits in one `recordHabits()` transaction. Out-of-order and same-day decreasing totals are ignored rather than re-anchored. Distance and daily calories are no longer registered. Heart-rate awards are limited to one per 30 minutes. See [ARCHITECTURE.md §4.3](../ARCHITECTURE.md#43-phase-3-passive-hardware-sensor-ingestion). Covered by `DailyTotalTrackerTest`, `IngestPassiveDataUseCaseTest` and `PassiveSyncRepositoryImplTest`.
- **Where**: [`PassiveDataService.kt`](../../core/health/src/main/java/com/healthcompanion/core/health/PassiveDataService.kt), `PetDecayEngine.applyHabit()`.
- **Problem**: `STEPS_DAILY`, `CALORIES_DAILY`, `DISTANCE_DAILY` and `FLOORS_DAILY` report the running total since local midnight. Each batch passes that total to `recordHabit()`, so the whole day's activity is re-applied on every batch.
- **Impact**: At 8,000 steps, every batch adds +80 fitness and awards XP again. `CALORIES_DAILY` includes basal burn (~2,000 kcal), so each batch is logged as a zero-minute workout worth ~40 XP that also **drains 10 energy**. Distance largely double-counts steps. Net effect: fitness is pinned at 100, XP grows without bound, energy trends to 0, and the pet is stuck in `TIRED`. Heart-rate samples also award 5 XP per batch, which inflates XP further.
- **Target design**:
  - Persist the last-seen total per data type, together with the day it belongs to.
  - Apply only `max(0, newTotal − lastTotal)`. Reset the baseline when the day rolls over.
  - Drop distance and floors as reward sources, or fold them into a single activity score.
  - Model calories as active burn only (or drop them). Never map them to `Workout` energy drain.
  - Rate-limit or cap the heart-rate XP awarded per hour.
- **Tests**: unit tests for delta computation, midnight reset, out-of-order batches, and a total that decreases.

### AR-2 — Non-atomic pet updates ✅ Resolved
- **Resolution**: `PetRepository.updatePet(pet)` (blind overwrite) was replaced by `updatePet { transform }`, which runs inside `withTransaction { }`. `recordHabit()`, `CalculateDecayUseCase`, and `PetDecayWorker` all go through it. Default-pet seeding uses `insertIfAbsent` (`INSERT OR IGNORE`). `PetRepositoryImplTest` (Robolectric, in-memory Room) covers concurrent writers. Before the fix, 50 concurrent hydration logs persisted only 2.
- **Where**: `PetRepositoryImpl.recordHabit()`, `PetRepositoryImpl.getPetFlow()` (default-pet insert), `PetDecayWorker.doWork()`, `CalculateDecayUseCase`.
- **Problem**: Each writer does `getPet()` → compute → `insertOrUpdate()` with no transaction around it. Writers run concurrently: UI taps, one coroutine per sensor batch (up to 5 sequential writes each), and the worker. Each entry point builds its own `PetRepositoryImpl`, so an in-memory `Mutex` would not protect them either.
- **Impact**: Updates are silently lost (e.g. a water tap is overwritten by a concurrent step batch).
- **Target design**: Wrap the read-modify-write in `RoomDatabase.withTransaction { }` (or a `@Transaction` DAO method). Collapse each sensor batch into a single transactional write.

### AR-3 — Unrecoverable vitals and unimplemented game inputs ✅ Resolved
- **Resolution**:
  - Energy recovers during the pet's night (`NightWindow`, 22:00–07:00 local), integrated per day/night segment.
  - The pet is `SLEEPING` throughout the night (`isNightTime` is now passed).
  - A new `habit_events` table (schema v2 via `@AutoMigration(1, 2)`, the first real migration) records every applied habit.
  - `ArchetypeSelector` picks the archetype from 7-day consistency at the `TEEN` transition, so `IRON_BEAST` is now reachable.
  - Still open: Health Services sleep detection (`UserActivityState.USER_ACTIVITY_ASLEEP`) and `ExerciseClient` workouts.

  See DD-33 to DD-36.
- **Where**: `PetDecayEngine`, `MoodCalculator`, `EvolutionEngine`, `GetPetStateUseCase`.
- **Problems**:
  - **Energy** only ever decreases (decay, workouts, unhealthy meals). Nothing restores it, because there is no sleep source.
  - `isNightTime` is never passed, so `Mood.SLEEPING` never occurs.
  - The archetype is chosen from a snapshot of vitals at the `TEEN` transition rather than from habit consistency. `IRON_BEAST` is unreachable. With the pre-AR-1 inflated fitness, almost every pet became `CARDIO_RUNNER`; `fitness > 80` at the `TEEN` transition still dominates.
  - `ExerciseClient` workouts and sleep monitoring do not exist.
- **Target design**:
  - Add an energy restoration source: a sleep heuristic (e.g. inactivity plus night window), an explicit "rest" action, or Health Services sleep data where available.
  - Supply `isNightTime` from a clock abstraction.
  - Introduce a **habit event log table** (`habit_events`: type, amount, timestamp) so archetypes, streaks, and history can be derived from consistency. Requires AR-8 first.

### AR-4 — Surfaces are not reactive ✅ Resolved
- **Resolution**: Every committed write requests a tile update via the `NotifyingPetRepository` decorator wired in `AppContainer`. `PetStatusTileService` builds tiles in `serviceScope.future { }` (kotlinx-coroutines-guava) instead of `runBlocking` plus the restricted `ResolvableFuture`, which also clears the 6 lint errors. `GetPetStateUseCase` re-evaluates decay every 60 s while collected. The complication provider is still *planned*; when it is added, it should hook into the same refresh callback. See DD-29 to DD-31.
- **Where**: [`PetStatusTileService.kt`](../../wearApp/src/main/java/com/healthcompanion/wear/tiles/PetStatusTileService.kt), `GetPetStateUseCase`.
- **Problems**:
  - Tiles are pull-based. The tile renders a snapshot that stays cached for 10 minutes and is never asked to refresh after writes. `onTileRequest()` also blocks the main thread with `runBlocking`.
  - There is no complication data source service.
  - `GetPetStateUseCase` recalculates decay only when Room emits, so vitals on an open screen are frozen until the next write.
- **Target design**:
  - After each committed write, call `TileService.getUpdater(context).requestUpdate(PetStatusTileService::class.java)` (and the complication equivalent once one exists).
  - Replace `runBlocking` with a coroutine-based tile service (e.g. Horologist `SuspendingTileService`).
  - Combine the pet Flow with a subscription-scoped ticker (~60 s) so decay advances while the screen is visible.

### AR-5 — `PetDecayWorker` is redundant ✅ Resolved
- **Resolution**: Removed `PetDecayWorker` and `CalculateDecayUseCase` (used only by the worker), plus the `:core:data` WorkManager dependency and the explicit `WAKE_LOCK` permission. Its would-be jobs are covered elsewhere: decay-on-read (DD-02), tile refresh on write (AR-4), lazy day rollover (AR-1). `HealthCompanionApp` cancels the legacy `PetPeriodicDecayWork` job on upgraded installs. Critical-vital notifications (Phase 2) remain open and would need their own scheduled work. See DD-32.
- **Where**: `PetDecayWorker.kt` (now removed), `HealthCompanionApp.schedulePeriodicDecay()`.
- **Problem**: Under decay-on-read, persisting a decayed snapshot every 2 hours changes no user-visible result. It only adds another writer (safe since AR-2, but pointless) and triggers an extra Room invalidation.
- **Target design**: Give it a real job or remove it. Useful jobs: refreshing tiles and complications (AR-4), pruning stale sensor baselines (AR-1 handles day rollover lazily on the next reading), and critical-vital notifications (Phase 2). If it is removed, drop the explicit `WAKE_LOCK` permission as well.

### AR-6 — Permissions and Health Services registration ✅ Resolved
- **Resolution**:
  - Sensors are registered per granted permission (`PassiveSensorPlanner`). Only `ACTIVITY_RECOGNITION` decides between degraded and full mode.
  - Uses the API 36 health permissions (`health.READ_HEART_RATE`, `health.READ_HEALTH_DATA_IN_BACKGROUND`), with legacy `BODY_SENSORS*` capped at `maxSdkVersion="35"`.
  - Heart rate requires background access and is requested in a separate, optional second dialog.
  - Registration goes through the idempotent `ensureRegistered()`, keyed on permitted sensors and boot count, with an explicit `BootCompletedReceiver` → `PassiveRegistrationWorker`.

  See [ARCHITECTURE.md §7](../ARCHITECTURE.md#7-runtime-permissions--degraded-mode) and DD-22 to DD-25. ⚠ The permission dialogs and the boot re-registration are unverified on a Wear OS 6 image or device.
- **Where**: [`HealthPermissions.kt`](../../core/health/src/main/java/com/healthcompanion/core/health/HealthPermissions.kt), [`HealthServicesManager.kt`](../../core/health/src/main/java/com/healthcompanion/core/health/HealthServicesManager.kt), `HealthCompanionApp.onCreate()`, `AndroidManifest.xml`.
- **Problems**:
  - `hasPermissions()` requires *all* permissions. Partial grants register nothing.
  - The project targets API 36, where `BODY_SENSORS` is superseded by granular health permissions (`android.permission.health.READ_HEART_RATE`, etc.). Passive/background heart-rate requirements are unverified.
  - Passive listener registration runs on *every* process start via `Application.onCreate()`. Re-registration after reboot works only because WorkManager happens to start the process. No explicit boot receiver or re-registration worker exists.
- **Target design**:
  - Map each data type to its required permission and register the intersection of *supported* and *permitted* types.
  - Adopt the API 36 health permissions and verify the passive heart-rate requirements on a Wear OS 6 image.
  - Move registration into an explicit, idempotent path: on permission grant, on boot (via a receiver or WorkManager), and on app start only if not already registered.

### AR-7 — Layering and dependency wiring ✅ Resolved
- **Resolution**: `:core:health` now depends only on `:core:domain` and `:core:model`, and gets its use case via `PassiveDataDependencies`. `AppContainer` (manual DI, lazy members) is the single graph used by the activity, view models, tile and service; the static `HealthCompanionApp.instance` is removed. A domain `Clock` is injected into `PetRepositoryImpl`, the use cases and `PetViewModel`, and the engines no longer default to the system time. See DD-26 to DD-28.
- **Where**: `:core:health` build file, `PassiveDataService`, `PetDecayWorker`, `PetStatusTileService`, `HealthCompanionApp`.
- **Problems**:
  - `:core:health` depends on `:core:data` and instantiates `PetRepositoryImpl` directly, bypassing the domain layer.
  - Dependencies are wired partly through a static service locator (`HealthCompanionApp.instance`) and partly by building ad-hoc instances in each Android component.
  - Engines read `System.currentTimeMillis()` directly.
- **Target design**:
  - `:core:health` depends only on `:core:domain` (`PetRepository` / `LogHabitUseCase`).
  - Provide a single dependency graph (a manual `AppContainer`, or Hilt) shared by the activity, services, tile, and workers.
  - Inject a `Clock` into the engines and use cases.

### AR-8 — Data durability ✅ Resolved
- **Resolution**: Schema export is on and `1.json` is committed. The destructive fallback is removed for upgrades (debuggable builds keep it for downgrades only). Migrations are registered in `ALL_MIGRATIONS`. CI fails on uncommitted schema changes. `toDomain()` is tolerant of bad rows, and `applyHabit` clamps both bounds. See [ARCHITECTURE.md §5.3 Schema Versioning & Migrations](../ARCHITECTURE.md#4-schema-versioning--migrations) and DD-18 to DD-21. Covered by `CompanionDatabaseMigrationTest`, `PetEntityTest` and `PetDecayEngineTest`.
- **Where**: [`CompanionDatabase.kt`](../../core/data/src/main/java/com/healthcompanion/core/data/db/CompanionDatabase.kt), [`Vitals.kt`](../../core/model/src/main/java/com/healthcompanion/core/model/Vitals.kt), `PetEntity.toDomain()`.
- **Problems**:
  - With `exportSchema = false` and `fallbackToDestructiveMigration(dropAllTables = true)`, any schema change deletes the pet.
  - `Vitals` uses `require()` to validate ranges, and `toDomain()` calls it with no guard. A single out-of-range value (e.g. a negative habit amount, since `applyHabit` boosts are only clamped at the top) throws inside `getPetFlow()` and crashes the app every time the pet is loaded.
- **Target design**:
  - Enable `exportSchema = true`, commit the schemas, and write explicit `Migration`s. Keep destructive fallback for debug builds at most.
  - Clamp both bounds in `applyHabit` (`coerceIn(0f, 100f)`) and have `toDomain()` coerce values so they load safely rather than throwing.
