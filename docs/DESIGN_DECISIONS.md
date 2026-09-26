# Design Decisions

A log of the non-trivial design choices in Health Companion: what was chosen, what the alternatives were, and what it costs. [ARCHITECTURE.md](ARCHITECTURE.md) describes *how* the system works. This document explains *why*, and lists what to revisit.

**How to use this log**
- Add an entry whenever a change involves a real choice between alternatives, a trade-off, a tunable game-balance value, or an unverified assumption.
- Never rewrite a decided entry. Replacing one means adding a new entry and setting the old entry's status to `Superseded by DD-xx`.
- Entries marked **Retroactive** document choices made before this log existed; their reasoning is reconstructed from the code and docs.
- ⚠ marks an **open question**: something unverified or deliberately provisional.

---

## Index

| ID | Decision | Area | Status |
| :--- | :--- | :--- | :--- |
| [DD-01](#dd-01--standalone-wear-os-app) | Standalone Wear OS app, no phone companion | Platform | Accepted (retroactive) |
| [DD-02](#dd-02--decay-on-read-instead-of-background-ticking) | Decay-on-read instead of background ticking | Game engine | Accepted (retroactive) |
| [DD-03](#dd-03--room-as-single-source-of-truth-with-a-single-pet-row) | Room as single source of truth, single pet row | Persistence | Accepted (retroactive) |
| [DD-04](#dd-04--graceful-degradation-instead-of-a-permission-wall) | Graceful degradation instead of a permission wall | Permissions | Accepted (retroactive) ⚠ |
| [DD-05](#dd-05--all-pet-mutations-go-through-a-transactional-transform) | All pet mutations go through a transactional transform | Persistence | Accepted (AR-2) |
| [DD-06](#dd-06--default-pet-is-seeded-with-insert-or-ignore) | Default pet is seeded with `INSERT OR IGNORE` | Persistence | Accepted (AR-2) |
| [DD-07](#dd-07--robolectric-jvm-tests-instead-of-instrumented-tests) | Robolectric JVM tests instead of instrumented tests | Testing | Accepted (AR-2) |
| [DD-08](#dd-08--cumulative-daily-totals-are-consumed-as-deltas) | Cumulative daily totals are consumed as deltas | Sensors | Accepted (AR-1) |
| [DD-09](#dd-09--prefer-under-counting-over-double-counting) | Prefer under-counting over double counting | Sensors | Accepted (AR-1) |
| [DD-10](#dd-10--deltas-are-consumed-in-whole-units-with-the-remainder-carried-over) | Deltas consumed in whole units, remainder carried over | Sensors / balance | Accepted (AR-1) |
| [DD-11](#dd-11--sensor-baselines-live-in-datastore-not-room) | Sensor baselines live in DataStore, not Room | Persistence | Accepted (AR-1), revisit now possible ⚠ |
| [DD-12](#dd-12--one-sensor-batch-is-one-pet-write) | One sensor batch is one pet write | Sensors | Accepted (AR-1) |
| [DD-13](#dd-13--distance-and-daily-calories-are-not-consumed) | Distance and daily calories are not consumed | Sensors / balance | Accepted (AR-1) ⚠ |
| [DD-14](#dd-14--floors-are-a-climbing-bonus-worth-20-steps-each) | Floors are a climbing bonus worth 20 steps each | Balance | Accepted (AR-1) ⚠ |
| [DD-15](#dd-15--heart-rate-awards-are-limited-to-one-per-30-minutes) | Heart-rate awards limited to one per 30 minutes | Balance | Accepted (AR-1) ⚠ |
| [DD-16](#dd-16--first-reading-credits-todays-activity-so-far) | First reading credits today's activity so far | Balance | Accepted (AR-1) ⚠ |
| [DD-17](#dd-17--a-daily-reading-belongs-to-the-day-of-its-interval-end-minus-1-ms) | A daily reading belongs to the day of its end instant − 1 ms | Sensors | Accepted (AR-1), unverified ⚠ |
| [DD-18](#dd-18--no-destructive-migration-on-upgrade-debug-only-destructive-downgrade) | No destructive migration on upgrade; debug-only destructive downgrade | Persistence | Accepted (AR-8) |
| [DD-19](#dd-19--committed-schemas-guarded-by-ci-rather-than-by-a-test) | Committed schemas guarded by CI rather than by a test | Persistence / CI | Accepted (AR-8) |
| [DD-20](#dd-20--bad-rows-are-repaired-on-load-not-rejected) | Bad rows are repaired on load, not rejected | Persistence | Accepted (AR-8) ⚠ |
| [DD-21](#dd-21--one-shared-clamp-for-vital-values-nan-maps-to-0) | One shared clamp for vital values; `NaN` maps to 0 | Game engine | Accepted (AR-8) |
| [DD-22](#dd-22--only-activity-recognition-is-core-heart-rate-is-optional) | Only activity recognition is core; heart rate is optional | Permissions | Accepted (AR-6) |
| [DD-23](#dd-23--heart-rate-is-registered-only-with-background-access) | Heart rate is registered only with background access | Permissions | Accepted (AR-6), unverified ⚠ |
| [DD-24](#dd-24--idempotent-registration-keyed-on-permitted-sensors--boot-count) | Idempotent registration keyed on permitted sensors + boot count | Sensors | Accepted (AR-6) ⚠ |
| [DD-25](#dd-25--boot-re-registration-via-a-non-exported-receiver-and-workmanager) | Boot re-registration via a non-exported receiver and WorkManager | Sensors | Accepted (AR-6), unverified ⚠ |
| [DD-26](#dd-26--manual-appcontainer-instead-of-a-di-framework) | Manual `AppContainer` instead of a DI framework | Architecture | Accepted (AR-7) |
| [DD-27](#dd-27--library-services-get-dependencies-through-an-application-implemented-interface) | Library services get dependencies through an Application-implemented interface | Architecture | Accepted (AR-7) |
| [DD-28](#dd-28--injected-clock-now-is-read-inside-the-transaction) | Injected `Clock`; "now" is read inside the transaction | Architecture | Accepted (AR-7) |
| [DD-29](#dd-29--surfaces-are-refreshed-by-a-repository-decorator) | Surfaces are refreshed by a repository decorator | Surfaces | Accepted (AR-4) |
| [DD-30](#dd-30--60-second-decay-ticker-only-while-collected) | 60-second decay ticker, only while collected | Game engine / UI | Accepted (AR-4) |
| [DD-31](#dd-31--tile-futures-via-kotlinx-coroutines-guava-not-horologist) | Tile futures via kotlinx-coroutines-guava, not Horologist | Surfaces | Accepted (AR-4) |
| [DD-32](#dd-32--no-periodic-background-work) | No periodic background work | Background / battery | Accepted (AR-5) |
| [DD-33](#dd-33--energy-recovers-during-a-fixed-local-night-window) | Energy recovers during a fixed local night window | Game engine / balance | Accepted (AR-3) ⚠ |
| [DD-34](#dd-34--the-pet-always-sleeps-at-night) | The pet always sleeps at night | Game design | Accepted (AR-3) ⚠ |
| [DD-35](#dd-35--an-append-only-habit-history-table-schema-v2) | An append-only habit history table (schema v2) | Persistence | Accepted (AR-3) |
| [DD-36](#dd-36--archetype-from-7-day-consistency-locked-in-once-at-teen) | Archetype from 7-day consistency, locked in once at TEEN | Game design / balance | Accepted (AR-3) ⚠ |

---

## Foundations (retroactive)

### DD-01 — Standalone Wear OS app
- **Status**: Accepted (retroactive).
- **Decision**: The app runs entirely on the watch (`com.google.android.wearable.standalone = true`). There is no phone app and no Data Layer sync.
- **Why**: Users often leave the phone behind during workouts. A standalone app is simpler to build and ship.
- **Alternatives**: Phone + watch pair with Data Layer sync (planned as optional Phase 5).
- **Consequences**: All state, logic, and history live on the watch. No backup or export until Phase 5.

### DD-02 — Decay-on-read instead of background ticking
- **Status**: Accepted (retroactive).
- **Decision**: Vitals are stored with a `lastUpdatedTimestamp`. Decay is computed from elapsed time whenever the state is read or written (`PetDecayEngine.calculateDecay`), not by a periodic timer.
- **Why**: A ticking loop would wake the CPU constantly and drain a ~300–400 mAh battery.
- **Alternatives**: Periodic background ticks. Alarm-based updates.
- **Consequences**: Anything that shows vitals must compute decay itself. An open screen does not advance until it re-reads (AR-4). The periodic `PetDecayWorker` is largely redundant (AR-5).
- **Code**: `core/domain/.../engine/PetDecayEngine.kt`, `GetPetStateUseCase`.

### DD-03 — Room as single source of truth with a single pet row
- **Status**: Accepted (retroactive).
- **Decision**: One SQLite row (`id = "companion_primary"`) holds the whole pet. UI observes it via Room `Flow`.
- **Why**: Simple and offline-first. Room invalidation gives reactive UI for free.
- **Alternatives**: In-memory state with periodic saves. Multiple pets.
- **Consequences**: Multiple pets would need a schema change. Pull-based surfaces (tiles, complications) do not benefit from `Flow` and must be refreshed explicitly (AR-4).

### DD-04 — Graceful degradation instead of a permission wall
- **Status**: Accepted (retroactive).
- **Decision**: If sensor permissions are denied, the app still works with manual logging and shows an "Enable sensors" chip, rather than blocking.
- **Why**: The pet should always be usable. Sensor data enhances it but is not required.
- ⚠ **Open**: Degradation is currently all-or-nothing across permissions (AR-6).

---

## Persistence & concurrency (AR-2)

### DD-05 — All pet mutations go through a transactional transform
- **Status**: Accepted (AR-2, 2026-09-26).
- **Decision**: `PetRepository.updatePet { transform }` is the only way to change the pet. It reads, transforms and writes inside one Room `withTransaction`. The blind `updatePet(pet)` overwrite was removed so it cannot be misused.
- **Why**: Several writers run concurrently (UI, sensor batches, worker). Separate read and write calls lost 48 of 50 concurrent updates in testing.
- **Alternatives**:
  - An in-process `Mutex`: rejected, because each component built its own repository instance, so there was no shared lock.
  - `@Transaction` DAO methods: equivalent, but they put game logic in the DAO.
  - Optimistic concurrency with a version column: requires a schema change.
- **Consequences**: `transform` must be a pure, non-suspending function. It may be called while other writers wait, so it must be fast.
- **Code**: `core/data/.../repository/PetRepositoryImpl.kt`.

### DD-06 — Default pet is seeded with `INSERT OR IGNORE`
- **Status**: Accepted (AR-2).
- **Decision**: Creating the default pet uses `PetDao.insertIfAbsent` (`OnConflictStrategy.IGNORE`). It never uses `REPLACE`.
- **Why**: The `Flow` could see an empty table while another writer was creating and updating the pet. Seeding with `REPLACE` would then wipe that update.
- **Consequences**: Seeding is idempotent and safe to call from anywhere.

### DD-07 — Robolectric JVM tests instead of instrumented tests
- **Status**: Accepted (AR-2).
- **Decision**: Tests that exercise Room run under Robolectric in `./gradlew test`. They are not instrumented `androidTest`s.
- **Why**: No emulator is needed. They run in the existing CI job, and they catch concurrency bugs against a real SQLite database.
- **Alternatives**: Instrumented tests. They would need an emulator in CI, which is slow and fragile.
- **Consequences**: Robolectric's SQLite is close to the device's, but not identical. On-device behaviour (Health Services, tiles) still needs manual verification.

---

## Passive sensor ingestion (AR-1)

### DD-08 — Cumulative daily totals are consumed as deltas
- **Status**: Accepted (AR-1, 2026-09-26).
- **Decision**: `*_DAILY` readings are never applied directly. The pure `DailyTotalTracker` compares each reading with the amount already consumed that day and returns only the new part:
  - A new local day (or no baseline yet) counts everything since midnight.
  - A reading from an earlier day is ignored.
  - On the same day, only the increase over the consumed total counts.
- **Why**: Health Services reports running totals and may redeliver them. Applying totals re-applied the whole day on every batch.
- **Code**: `core/domain/.../engine/DailyTotalTracker.kt`, `IngestPassiveDataUseCase`.

### DD-09 — Prefer under-counting over double counting
- **Status**: Accepted (AR-1).
- **Decision**: Whenever the ingestion pipeline is uncertain, it drops activity rather than risk applying it twice:
  1. **Lower total on the same day** (usually an out-of-order batch): ignored, and the baseline is kept, not re-anchored.
  2. **Ordering**: the delta is consumed (baseline saved) *before* the pet is updated. A crash in between loses that delta instead of re-applying it later.
- **Why**: Double counting compounds (inflated XP, pinned fitness) and cannot be undone. A missed chunk of steps is barely noticeable. The first version re-anchored on lower totals, and a concurrency test showed that this double counts (5,000 → 3,000 → 5,000 applied 2,000 twice).
- **Consequences**: After a genuine mid-day sensor counter reset, no steps are counted until the total passes the old baseline.
- **Alternatives**: Re-anchor on decrease (rejected, see above). Store baseline and pet in one transaction (see DD-11).

### DD-10 — Deltas are consumed in whole units with the remainder carried over
- **Status**: Accepted (AR-1). Game-balance values are tunable.
- **Decision**: Steps are consumed in chunks of **200** (the engine's XP unit, 1 XP per 200 steps) and floors in chunks of **10**. Anything smaller stays unconsumed until the next reading.
- **Why**: `applyHabit` computes XP with integer division. Passive batches often contain fewer than 200 steps, so they would earn 0 XP forever.
- **Alternatives**: Fractional XP (a model change). A separate stored remainder (more state, same effect).
- **Consequences**: Fitness updates in steps of 200 walking steps (+2 fitness) rather than continuously.
- **Code**: `IngestPassiveDataUseCase.STEP_GRANULARITY`, `FLOOR_GRANULARITY`.

### DD-11 — Sensor baselines live in DataStore, not Room
- **Status**: Accepted (AR-1). ⚠ Revisit: now possible, since AR-8 landed real migrations (DD-18).
- **Decision**: The consumed totals per data type and the last heart-rate award time are stored in the `passive_sync` Preferences DataStore via `PassiveSyncRepositoryImpl`. Concurrent batches are serialized by `DataStore.edit`.
- **Why**: A new Room table bumps the schema version. With the current destructive migration fallback, that would **delete the pet** (AR-8).
- **Trade-off**: The baseline and the pet live in different stores, so they cannot share a transaction. This is why DD-09 fixes the ordering to "consume first".
- ⚠ **Revisit**: Once AR-8 adds real migrations, moving the baselines into Room would let one transaction cover both. That removes the under-count-on-crash window.

### DD-12 — One sensor batch is one pet write
- **Status**: Accepted (AR-1).
- **Decision**: All habits derived from one Health Services batch are applied in order with `PetRepository.recordHabits(list)`, in a single transaction.
- **Why**: Fewer writes and Room invalidations (UI recompositions), and a batch is applied all-or-nothing.

### DD-13 — Distance and daily calories are not consumed
- **Status**: Accepted (AR-1). ⚠ Game design open.
- **Decision**: `DISTANCE_DAILY` and `CALORIES_DAILY` are no longer registered or rewarded.
- **Why**:
  - Distance is derived from the same walking as steps, so it double-rewards.
  - Daily calories include basal metabolic burn (~2,000 kcal/day), so they measure being alive, not activity. The old mapping to `Workout` also drained 10 energy per batch.
- **Consequences**: Fewer sensor wake-ups. Running and cycling without steps are under-rewarded.
- ⚠ **Open**: Reintroduce as a combined activity score or through `ExerciseClient` workouts (AR-3).

### DD-14 — Floors are a climbing bonus worth 20 steps each
- **Status**: Accepted (AR-1). ⚠ Game-balance value.
- **Decision**: Each floor delta counts as **20 extra step-equivalents**, consumed in chunks of 10 floors (= 200 step-equivalents = 1 XP).
- **Why**: Climbing takes more effort than the steps it produces. It keeps the pre-existing 20-steps-per-floor ratio.
- **Code**: `IngestPassiveDataUseCase.STEPS_PER_FLOOR`.

### DD-15 — Heart-rate awards are limited to one per 30 minutes
- **Status**: Accepted (AR-1). ⚠ Game-balance value.
- **Decision**: A heart-rate habit (small fitness boost, 5 XP) is awarded at most once per **30 minutes**, using the latest sample in the batch. The limit is enforced atomically via `PassiveSyncRepository.tryClaimHeartRateAward`.
- **Why**: Heart-rate samples arrive in nearly every batch, and XP per batch would grow unbounded just from wearing the watch.
- **Consequences**: Heart rate adds at most ~240 XP/day. A wall-clock jump backwards resets the limit rather than blocking it.
- **Code**: `IngestPassiveDataUseCase.HEART_RATE_AWARD_INTERVAL_MS`.

### DD-16 — First reading credits today's activity so far
- **Status**: Accepted (AR-1). ⚠ Game-balance question.
- **Decision**: With no stored baseline (fresh install, or cleared app data), the first reading counts everything since midnight. For example, 8,000 steps at install gives +80 fitness and 40 XP.
- **Why**: It feels rewarding on day one, and it is a one-off.
- **Alternatives**: Start the baseline at the first reading, so nothing is credited until the user walks further.

### DD-17 — A daily reading belongs to the day of its interval end minus 1 ms
- **Status**: Accepted (AR-1). ⚠ **Unverified on hardware.**
- **Decision**: The local day of a `*_DAILY` data point is computed from `getEndInstant(bootInstant) − 1 ms` in the device's time zone.
- **Why**: An interval that ends exactly at midnight holds the *previous* day's total. Attributing it to the new day would credit a whole day twice.
- ⚠ **Open**: Confirm how Health Services timestamps the intervals around the daily reset, and how time-zone changes behave, on an emulator with synthetic data or a physical watch.
- **Code**: `core/health/.../PassiveDataService.kt` (`latestDailyTotal`).

---

## Data durability (AR-8)

### DD-18 — No destructive migration on upgrade; debug-only destructive downgrade
- **Status**: Accepted (AR-8, 2026-09-26).
- **Decision**: No build uses `fallbackToDestructiveMigration`. Every upgrade must have a migration in `ALL_MIGRATIONS`; a missing one crashes on open. Only debuggable builds (detected at runtime via `ApplicationInfo.FLAG_DEBUGGABLE`, since library modules have no `BuildConfig`) use `fallbackToDestructiveMigrationOnDowngrade`.
- **Why**: Losing the pet is the worst failure this app can have, and a crash is visible and fixable. Downgrades happen routinely when switching branches during development, but never in normal release use.
- **Alternatives**:
  - Destructive fallback in debug builds for upgrades too. Rejected: it would hide missing migrations until release.
  - Destructive fallback everywhere (the previous behaviour).
- **Consequences**: A developer who forgets a migration sees a crash immediately. Release users are never silently wiped.
- **Code**: `core/data/.../db/CompanionDatabase.kt`, `db/migrations/Migrations.kt`.

### DD-19 — Committed schemas guarded by CI rather than by a test
- **Status**: Accepted (AR-8).
- **Decision**: Room schema JSON is exported to `core/data/schemas/` and committed. CI fails if the build changes anything in that folder. `CompanionDatabaseMigrationTest` (Robolectric + `MigrationTestHelper`) covers migrations and data survival.
- **Why**: KSP re-exports the *current* version's JSON on every build, so a test alone would compare the entities with a file regenerated from those same entities. It cannot notice "entity changed without a version bump". Comparing against git can.
- **Alternatives**: The Room Gradle plugin (`androidx.room`, `schemaDirectory`). Not adopted, to avoid another plugin, and its host-test asset wiring was unverified. A KSP argument plus the AGP `hostTests` assets API works.
- **Consequences**: A local build may show a modified schema JSON in `git status`, which is the intended signal. Schema files are unit-test assets only and are not shipped in the APK (verified). The CI check was verified by temporarily adding a column: it flagged `1.json`.
- **Gotcha**: After *reverting* an entity change locally, Gradle can restore the KSP task from cache without re-exporting, which leaves a stale schema JSON behind. Run `./gradlew :core:data:kspDebugKotlin --rerun` to regenerate it. CI runs from a clean checkout and is not affected.
- **Code**: `core/data/build.gradle.kts`, `.github/workflows/ci.yml`.

### DD-20 — Bad rows are repaired on load, not rejected
- **Status**: Accepted (AR-8). ⚠ Silent repair.
- **Decision**: `PetEntity.toDomain()` repairs invalid data instead of throwing:
  - Vitals are clamped to `[0, 100]`, and `NaN` becomes 0.
  - Negative XP becomes 0.
  - An unknown `stage` name is re-derived from XP.
  - An unknown `archetype` name becomes `BALANCED`.

  The `Vitals` constructor keeps its `require()` range checks as an invariant for in-memory code.
- **Why**: A throwing `toDomain()` inside `getPetFlow()` crashes *every* launch, and the user cannot fix that. A slightly repaired pet is far better than a permanently crashing app. Enum fallbacks also make renaming or removing enum constants survivable.
- **Alternatives**: Throw and reset the pet (loses the pet). Throw and show an error screen (the app becomes unusable).
- ⚠ **Open**: Repairs are silent. Consider logging or counting them once there is telemetry or a debug screen, so data bugs don't go unnoticed.

### DD-21 — One shared clamp for vital values; `NaN` maps to 0
- **Status**: Accepted (AR-8).
- **Decision**: `Float.toVitalRange()` in `:core:model` clamps to `[0, 100]` and maps `NaN` to 0. It is used for every computed or loaded vital: all `applyHabit` results and all of `toDomain()`.
- **Why**: Previously boosts were clamped only at the top. A negative habit amount could go below 0 and trip the `Vitals` invariant. A single helper keeps the rule in one place.
- **Consequences**: Nonsensical inputs (e.g. negative millilitres) are absorbed silently rather than rejected. Validating inputs at the boundary (UI, sensor parsing) remains the caller's job.

---

## Permissions & registration (AR-6)

### DD-22 — Only activity recognition is core; heart rate is optional
- **Status**: Accepted (AR-6, 2026-09-26).
- **Decision**: `ACTIVITY_RECOGNITION` alone decides between full mode and degraded mode (the "Enable sensors" chip). Heart-rate permissions are optional. Missing them never shows the chip, and each permission unlocks only its own sensors.
- **Why**: Steps drive the core loop, while heart rate is a small bonus (DD-15). A permanent warning chip for a declined optional permission would nag users who made a deliberate privacy choice. Before AR-6, denying heart rate also disabled step tracking.
- **Alternatives**: Treat every permission as required (the old behaviour). Add a separate "partial" state with its own UI (more UI for little gain).

### DD-23 — Heart rate is registered only with background access
- **Status**: Accepted (AR-6). ⚠ Unverified on hardware.
- **Decision**: Heart rate is registered only if both permissions are granted:
  - the foreground permission: `BODY_SENSORS` ≤ API 35, `health.READ_HEART_RATE` ≥ 36;
  - the background permission: `BODY_SENSORS_BACKGROUND` on API 33–35, `health.READ_HEALTH_DATA_IN_BACKGROUND` on ≥ 36.

  The background permission is requested in a separate, second dialog right after the foreground grant, at most once per session.
- **Why**: Per the [Health Services permissions docs](https://developer.android.com/health-and-fitness/health-services/permissions), apps targeting API 36 must use the granular health permissions. `PassiveMonitoringClient` access to body sensors in the background needs the background permission, and Android requires background permissions to be requested after the foreground one.
- **Alternatives**:
  - Drop passive heart rate entirely (simplest, most privacy-friendly).
  - Register with the foreground permission only. Rejected: the data likely wouldn't be delivered in the background.
- ⚠ **Open**:
  - Verify the dialog behaviour on Wear OS 6 and API 33–35.
  - Play Store health-permission declarations will be needed before release.
  - Reconsider whether heart rate is worth two dialogs.

### DD-24 — Idempotent registration keyed on permitted sensors + boot count
- **Status**: Accepted (AR-6). ⚠ Assumption about Health Services state.
- **Decision**: `ensureRegistered()` stores a key of *permitted sensors + `Settings.Global.BOOT_COUNT`* after each successful registration. It skips Health Services entirely while the key is unchanged. Calls are serialized by a process-wide `Mutex`.
- **Why**: `Application.onCreate` runs on every process start, including each time Health Services wakes the app to deliver a batch. Re-registering each time costs an IPC round-trip. Including the boot count keeps the check correct across reboots even if the boot receiver never runs.
- **Consequences**: The key is based on *permitted* sensors, not the capability-filtered set. Capabilities don't change at runtime, so this saves a capability query on every start.
- ⚠ **Assumption**: The registration survives app updates and is only lost on reboot. It is also lost if the user clears app data, but that clears the stored key too, so the next start re-registers. If Health Services turns out to drop registrations on app update, add the app version code to the key.

### DD-25 — Boot re-registration via a non-exported receiver and WorkManager
- **Status**: Accepted (AR-6). ⚠ Unverified on hardware.
- **Decision**: `BootCompletedReceiver` (`exported="false"`) enqueues a unique one-time `PassiveRegistrationWorker`, which calls `ensureRegistered(force = true)` and retries on failure.
- **Why**: This follows the [Health Services background monitoring guidance](https://developer.android.com/health-and-fitness/guides/health-services/monitor-background): registrations don't persist across reboots, and at boot Health Services may take over 10 s to respond, which exceeds a receiver's execution limit. The receiver is not exported because only the system sends `BOOT_COMPLETED`.
- ⚠ **Open**: Verify on an emulator or watch that the non-exported receiver actually receives `BOOT_COMPLETED` and that the passive data arrives afterwards.

---

## Layering & dependency wiring (AR-7)

### DD-26 — Manual `AppContainer` instead of a DI framework
- **Status**: Accepted (AR-7, 2026-09-26).
- **Decision**: A hand-written `AppContainer` in `:wearApp`, owned by `HealthCompanionApp`, builds the whole graph with `by lazy` members. There is no Hilt or Koin.
- **Why**: The graph is small (about ten objects), and adding Hilt would bring a Gradle plugin, annotation processing and build time. Lazy members keep cold starts cheap when the process is only woken to deliver a sensor batch or render a tile.
- **Alternatives**: Hilt (worth adopting if the graph grows or needs per-screen scopes). Koin.
- **Consequences**: New dependencies are added by hand in one place. `PetDecayWorker` still constructs its own graph (removed in AR-5).

### DD-27 — Library services get dependencies through an Application-implemented interface
- **Status**: Accepted (AR-7).
- **Decision**: `:core:health` declares `PassiveDataDependencies`, which the app's `Application` implements. `PassiveDataService` casts `application` to that interface to get `IngestPassiveDataUseCase`.
- **Why**: The service is instantiated by the Android system, so there is no constructor injection. Declaring the interface in the library lets `:core:health` depend only on the domain layer, without knowing about `:wearApp` or `:core:data`.
- **Consequences**: A host `Application` that doesn't implement the interface crashes when a batch is delivered. That failure is loud, and the batch is dropped and logged.

### DD-28 — Injected `Clock`; "now" is read inside the transaction
- **Status**: Accepted (AR-7).
- **Decision**:
  - A domain `fun interface Clock` (with `Clock.SYSTEM`) is injected into `PetRepositoryImpl`, the use cases and `PetViewModel`.
  - `PetDecayEngine` functions take an explicit timestamp and no longer default to `System.currentTimeMillis()`.
  - Repository mutations read the clock *inside* the transaction.
  - The `Pet` and `Vitals` model constructors keep their wall-clock defaults, for convenience in tests and previews. Production code passes explicit times.
- **Why**: Tests become deterministic, and there is a single source of time. Reading the time before waiting for the transaction could produce a timestamp older than one a concurrent writer just stored, which would re-apply a few milliseconds of decay.
- **Consequences**: Wall-clock changes (manual time or time-zone changes) still affect decay. That is inherent to decay-on-read (DD-02).

---

## Reactive surfaces (AR-4)

### DD-29 — Surfaces are refreshed by a repository decorator
- **Status**: Accepted (AR-4, 2026-09-26).
- **Decision**: `NotifyingPetRepository` (in `:core:domain`) wraps the real repository and runs a callback after every *successful* mutation. `AppContainer` uses that callback to request a tile update. Reads never notify.
- **Why**: Tiles are pull-based and cannot observe Room. The decorator gives every writer (UI, passive sensors) the refresh without any of them knowing about tiles, and it keeps `:core:data` free of Wear surface APIs.
- **Alternatives**:
  - Call `requestUpdate` at each write site. That is easy to forget.
  - Have an app-scoped collector observe the `Flow`. The process is often not alive, so it would miss writes.
  - Refresh from a periodic worker. That lags and wakes the CPU for no reason.
- **Consequences**:
  - A writer that bypasses the container (e.g. `PetDecayWorker`, removed in AR-5) doesn't refresh the tile.
  - The system throttles frequent `requestUpdate` calls, so a burst of sensor batches is coalesced.

### DD-30 — 60-second decay ticker, only while collected
- **Status**: Accepted (AR-4). Tunable.
- **Decision**: `GetPetStateUseCase` combines the Room stream with a ticker (default 60 s) and re-evaluates decay on every tick. The ticker only runs while the stream is collected (`WhileSubscribed(5000)` in `PetViewModel`).
- **Why**: The fastest vital drops 3 points per hour, i.e. 0.05 per minute. A 1-minute cadence is visually smooth enough and costs one recomposition per minute, and only while the screen is visible.
- **Alternatives**: Tick every second (wasteful). Compute decay in the UI layer (duplicates domain logic).
- **Consequences**: In ambient mode the screen may stay subscribed and recompose once per minute, which matches the ambient update cadence.

### DD-31 — Tile futures via kotlinx-coroutines-guava, not Horologist
- **Status**: Accepted (AR-4).
- **Decision**: `PetStatusTileService` returns `serviceScope.future { ... }` (`kotlinx.coroutines.guava.future`) from `onTileRequest` and `onTileResourcesRequest`. The scope is cancelled in `onDestroy`.
- **Why**: It removes `runBlocking` (which blocked the binder thread) and the restricted `ResolvableFuture` API (6 lint errors). The library is already in the version catalog, and no new dependency family is needed.
- **Alternatives**: Horologist `SuspendingTileService`. It is cleaner if more tiles or Horologist layouts are adopted later, but it adds a dependency today.

---

## Background work (AR-5)

### DD-32 — No periodic background work
- **Status**: Accepted (AR-5, 2026-09-26).
- **Decision**:
  - Removed `PetDecayWorker` (2-hour periodic decay snapshot) and `CalculateDecayUseCase`, which only it used.
  - `HealthCompanionApp` calls `WorkManager.cancelUniqueWork("PetPeriodicDecayWork")` on every start, so upgraded installs drop the persisted job instead of failing to instantiate a deleted class.
  - The only remaining WorkManager use is the one-time boot re-registration (DD-25).
- **Why**: Under decay-on-read (DD-02), a stored snapshot changes nothing the user sees. The jobs the worker could have taken on are handled better elsewhere: tile refresh on every write (DD-29), day rollover on the next sensor reading (DD-08). Every periodic wake-up costs battery.
- **Alternatives**: Repurpose the worker for tile refresh, baseline pruning, or notifications. Tile refresh and pruning aren't needed, and notifications are a separate feature.
- **Consequences**:
  - Critical-vital notifications (ROADMAP Phase 2) will need their own scheduling. Consider exact alarms or a worker that runs only when a vital is predicted to cross a threshold, computed from the decay rates.
  - The `cancelUniqueWork` call can be removed once no installs from before AR-5 remain.
  - `WAKE_LOCK` is no longer declared by the app, but WorkManager's manifest still merges it in.

---

## Game-loop completeness (AR-3)

### DD-33 — Energy recovers during a fixed local night window
- **Status**: Accepted (AR-3, 2026-09-26). ⚠ Game-balance values; real sleep sensing is open.
- **Decision**: Inside `NightWindow.DEFAULT` (22:00–07:00 in `Clock.zone()`), energy **recovers** at +8 %/h instead of decaying at −2 %/h. `calculateDecay` splits the elapsed time into day/night segments and clamps after each one. Other vitals keep decaying at night.
- **Why**: Energy previously had no way to recover, so every pet ended up permanently `TIRED`. A deterministic, sensor-free rule:
  - works for everyone, including people who don't wear the watch at night or whose watch doesn't detect sleep;
  - is pure and testable;
  - fits decay-on-read (DD-02).

  Piecewise integration is required because the rate changes sign: a day that drains energy to 0, followed by a night, must end at the night's recovery, not at a net sum (the test documents 72 vs 52).
- **Alternatives**:
  - Health Services sleep detection (`UserActivityState.USER_ACTIVITY_ASLEEP`, needs `ACTIVITY_RECOGNITION`): the most "mirroring", but unverified on hardware and useless when the watch is charging overnight.
  - An explicit "rest" button: more UI, and it's a chore.
- ⚠ **Open**:
  - A user-configurable bedtime, instead of a fixed 22–07 in the device zone.
  - Whether hydration and hunger should decay more slowly at night (−27 and −22.5 over 9 h, while the user can't log anything).
  - Layering real sleep data on top, e.g. bonus XP or happiness for detected sleep.

### DD-34 — The pet always sleeps at night
- **Status**: Accepted (AR-3). ⚠ Game design.
- **Decision**: `MoodCalculator` returns `SLEEPING` whenever `isNightTime` is true, regardless of vitals. Previously the rule was night *and* energy < 40, and it was never reached because `isNightTime` was never passed.
- **Why**: The pet recovers energy at night because it is asleep (DD-33). Under the old threshold rule it would wake up as soon as energy passed 40, at around 2 a.m., which is incoherent.
- **Consequences**: Thirst and hunger warnings aren't shown at night. They reappear at 07:00 if still relevant.

### DD-35 — An append-only habit history table (schema v2)
- **Status**: Accepted (AR-3).
- **Decision**:
  - A new `habit_events` table (`id`, `type`, `amount`, `detail?`, indexed `timestampMillis`) is added via `@AutoMigration(from = 1, to = 2)`, the first real schema migration, exercising the AR-8 infrastructure.
  - `recordHabits()` appends the applied habits in the same transaction as the pet update and prunes rows older than **30 days**.
  - Habits are flattened into a stable type name plus up to two numbers. Unknown type names are skipped on load.
- **Why**: Consistency-based features (archetypes, streaks, future history charts) need history. A snapshot of vitals can't express "5 of the last 7 days".
- **Alternatives**:
  - Per-day aggregate rows: smaller, but lose detail and complicate edge cases like midnight.
  - JSON blobs: not queryable.
  - Keep history in DataStore: no range queries, and no shared transaction with the pet.
- **Consequences**: The table stays small (a few hundred rows over 30 days). `updatePet { }` writes (non-habit transforms) are not recorded. Longer retention would be needed for any future long-term charts.

### DD-36 — Archetype from 7-day consistency, locked in once at TEEN
- **Status**: Accepted (AR-3). ⚠ Game-balance thresholds.
- **Decision**:
  - When the pet *first* reaches `TEEN` while still `BALANCED` (`EvolutionEngine.reachesSpecialization`), `ArchetypeSelector` scores the last 7 local days. Each day can qualify for:
    - **cardio**: ≥ 6,000 steps, floor bonus steps included;
    - **strength**: a workout, or a heart-rate reading ≥ 100 bpm;
    - **zen**: ≥ 1,500 ml water and ≥ 2 healthy meals.
  - The focus area with the most qualifying days wins if it has ≥ 4 days and no tie. Otherwise the pet stays `BALANCED`.
  - `checkEvolution` itself no longer changes the archetype, and the archetype never changes after this moment.
- **Why**: The design promises "consistency", not a lucky snapshot. The old vitals rule made nearly every pet `CARDIO_RUNNER`, and `IRON_BEAST` was unreachable.
- **Consequences**:
  - Until `ExerciseClient` workouts exist, the strength path relies on sparse heart-rate awards (≤ 1 per 30 min, DD-15).
  - Pets that were already `TEEN`+ and `BALANCED` before this change stay `BALANCED`. The old code would have kept re-evaluating them.
  - Zen no longer considers sleep, because there is no real sleep signal yet (DD-33).
- ⚠ **Open**: Tune the thresholds once real usage data exists. Consider re-evaluating the archetype at `ADULT`.
