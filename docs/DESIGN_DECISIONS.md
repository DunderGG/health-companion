# Design Decisions

A log of the non-trivial design choices in Health Companion: what was chosen, what the alternatives were, and what it costs. [ARCHITECTURE.md](ARCHITECTURE.md) describes *how* the system works. This document explains *why*, and lists what to revisit. Finding IDs such as `AR-1` refer to the [reviews](reviews/README.md) that prompted a decision.

**How to use this log**
- Add an entry whenever a change involves a real choice between alternatives, a trade-off, a tunable game-balance value, or an unverified assumption.
- Never rewrite a decided entry. Replacing one means adding a new entry and setting the old entry's status to `Superseded by DD-xx`.
- Entries marked **Retroactive** document choices made before this log existed; their reasoning is reconstructed from the code and docs.
- Open questions are shown as coloured callouts (rendered on GitHub and in VS Code's Markdown preview), and marked in the index:
  - 🟣 **Your call** (`[!IMPORTANT]`): a game-design, balance or product choice that should be confirmed or changed.
  - 🟠 **Verify on device** (`[!WARNING]`): an assumption that needs an emulator or a physical watch to confirm.
- When an open question is settled, remove its callout and index marker, and note the outcome in the entry.

> [!IMPORTANT]
> **🟣 Needs your decision**
> - [DD-11](#dd-11--sensor-baselines-live-in-datastore-not-room): move sensor baselines from DataStore into Room, now that migrations exist?
> - [DD-13](#dd-13--distance-and-daily-calories-are-not-consumed): bring distance and calories back as a combined activity score?
> - [DD-14](#dd-14--floors-are-a-climbing-bonus-worth-20-steps-each): floors worth 20 steps each?
> - [DD-15](#dd-15--heart-rate-awards-are-limited-to-one-per-30-minutes): heart rate awarded at most once per 30 minutes?
> - [DD-16](#dd-16--first-reading-credits-todays-activity-so-far): credit today's steps on first install?
> - [DD-20](#dd-20--bad-rows-are-repaired-on-load-not-rejected): log silent data repairs?
> - [DD-23](#dd-23--heart-rate-is-registered-only-with-background-access): is heart rate worth two permission dialogs?
> - [DD-33](#dd-33--energy-recovers-during-a-fixed-local-night-window): fixed 22:00–07:00 bedtime, and full hunger and thirst decay at night?
> - [DD-34](#dd-34--the-pet-always-sleeps-at-night): pet always asleep at night, hiding thirst and hunger warnings?
> - [DD-36](#dd-36--archetype-from-7-day-consistency-locked-in-once-at-teen): archetype thresholds (6,000 steps; workout or heart rate ≥ 100; 1,500 ml + 2 meals; 4 of 7 days)?
> - [DD-39](#dd-39--walkrun-from-burst-cadence-with-hysteresis-a-sleeping-pet-does-not-react): live walk/run thresholds, and should moving wake a sleeping pet?
> - [DD-41](#dd-41--one-alert-per-critical-episode-at-the-mood-threshold-never-at-night): alert threshold, reminders for long episodes, quiet hours, and a quick "+250 ml" action?
> - [DD-43](#dd-43--the-complication-shows-mood-and-overall-health-not-step-progress): overall health or step progress as the complication ring, and the short mood labels?

> [!WARNING]
> **🟠 Needs verification on an emulator or watch** (step-by-step instructions: [VERIFICATION.md](VERIFICATION.md))
> - [DD-17](#dd-17--a-daily-reading-belongs-to-the-day-of-its-interval-end-minus-1-ms): which day a daily total is counted in at midnight, and after time-zone changes.
> - [DD-23](#dd-23--heart-rate-is-registered-only-with-background-access): permission dialogs on Wear OS 6 and API 33–35, and background heart-rate delivery.
> - [DD-24](#dd-24--idempotent-registration-keyed-on-permitted-sensors--boot-count): whether the passive registration survives app updates.
> - [DD-25](#dd-25--boot-re-registration-via-a-non-exported-receiver-and-workmanager): whether the boot receiver fires and passive data resumes after a reboot.
> - [DD-37](#dd-37--live-steps-come-from-the-platform-step-detector-only-while-the-screen-is-visible): whether the watch has a step detector, how quickly it reports, and the battery cost of live reactions.
> - [DD-40](#dd-40--alerts-are-scheduled-at-the-predicted-crossing-not-polled): alert timing under Doze, clearing after logging, and surviving a reboot.
> - [DD-42](#dd-42--the-tile-logs-water-in-place-through-a-loadaction-deduplicated-by-a-per-render-click-id): one tap on the tile logs water exactly once and re-renders promptly.
> - [DD-43](#dd-43--the-complication-shows-mood-and-overall-health-not-step-progress): all three complication types render and tint correctly, and the ring updates after a write.
> - [DD-44](#dd-44--the-pet-screen-stays-on-in-ambient-mode-as-a-static-outline-and-releases-the-step-sensor): ambient look and once-a-minute updates on a real always-on display, and sensor release.

---

## Index

| ID | Decision | Area | Status |
| :--- | :--- | :--- | :--- |
| [DD-01](#dd-01--standalone-wear-os-app) | Standalone Wear OS app, no phone companion | Platform | Accepted (retroactive) |
| [DD-02](#dd-02--decay-on-read-instead-of-background-ticking) | Decay-on-read instead of background ticking | Game engine | Accepted (retroactive) |
| [DD-03](#dd-03--room-as-single-source-of-truth-with-a-single-pet-row) | Room as single source of truth, single pet row | Persistence | Accepted (retroactive) |
| [DD-04](#dd-04--graceful-degradation-instead-of-a-permission-wall) | Graceful degradation instead of a permission wall | Permissions | Accepted (retroactive); open point resolved by DD-22 |
| [DD-05](#dd-05--all-pet-mutations-go-through-a-transactional-transform) | All pet mutations go through a transactional transform | Persistence | Accepted (AR-2) |
| [DD-06](#dd-06--default-pet-is-seeded-with-insert-or-ignore) | Default pet is seeded with `INSERT OR IGNORE` | Persistence | Accepted (AR-2) |
| [DD-07](#dd-07--robolectric-jvm-tests-instead-of-instrumented-tests) | Robolectric JVM tests instead of instrumented tests | Testing | Accepted (AR-2) |
| [DD-08](#dd-08--cumulative-daily-totals-are-consumed-as-deltas) | Cumulative daily totals are consumed as deltas | Sensors | Accepted (AR-1) |
| [DD-09](#dd-09--prefer-under-counting-over-double-counting) | Prefer under-counting over double counting | Sensors | Accepted (AR-1) |
| [DD-10](#dd-10--deltas-are-consumed-in-whole-units-with-the-remainder-carried-over) | Deltas consumed in whole units, remainder carried over | Sensors / balance | Accepted (AR-1) |
| [DD-11](#dd-11--sensor-baselines-live-in-datastore-not-room) | Sensor baselines live in DataStore, not Room | Persistence | Accepted (AR-1) · 🟣 your call |
| [DD-12](#dd-12--one-sensor-batch-is-one-pet-write) | One sensor batch is one pet write | Sensors | Accepted (AR-1) |
| [DD-13](#dd-13--distance-and-daily-calories-are-not-consumed) | Distance and daily calories are not consumed | Sensors / balance | Accepted (AR-1) · 🟣 your call |
| [DD-14](#dd-14--floors-are-a-climbing-bonus-worth-20-steps-each) | Floors are a climbing bonus worth 20 steps each | Balance | Accepted (AR-1) · 🟣 your call |
| [DD-15](#dd-15--heart-rate-awards-are-limited-to-one-per-30-minutes) | Heart-rate awards limited to one per 30 minutes | Balance | Accepted (AR-1) · 🟣 your call |
| [DD-16](#dd-16--first-reading-credits-todays-activity-so-far) | First reading credits today's activity so far | Balance | Accepted (AR-1) · 🟣 your call |
| [DD-17](#dd-17--a-daily-reading-belongs-to-the-day-of-its-interval-end-minus-1-ms) | A daily reading belongs to the day of its end instant − 1 ms | Sensors | Accepted (AR-1) · 🟠 verify on device |
| [DD-18](#dd-18--no-destructive-migration-on-upgrade-debug-only-destructive-downgrade) | No destructive migration on upgrade; debug-only destructive downgrade | Persistence | Accepted (AR-8) |
| [DD-19](#dd-19--committed-schemas-guarded-by-ci-rather-than-by-a-test) | Committed schemas guarded by CI rather than by a test | Persistence / CI | Accepted (AR-8) |
| [DD-20](#dd-20--bad-rows-are-repaired-on-load-not-rejected) | Bad rows are repaired on load, not rejected | Persistence | Accepted (AR-8) · 🟣 your call |
| [DD-21](#dd-21--one-shared-clamp-for-vital-values-nan-maps-to-0) | One shared clamp for vital values; `NaN` maps to 0 | Game engine | Accepted (AR-8) |
| [DD-22](#dd-22--only-activity-recognition-is-core-heart-rate-is-optional) | Only activity recognition is core; heart rate is optional | Permissions | Accepted (AR-6) |
| [DD-23](#dd-23--heart-rate-is-registered-only-with-background-access) | Heart rate is registered only with background access | Permissions | Accepted (AR-6) · 🟠 verify on device · 🟣 your call |
| [DD-24](#dd-24--idempotent-registration-keyed-on-permitted-sensors--boot-count) | Idempotent registration keyed on permitted sensors + boot count | Sensors | Accepted (AR-6) · 🟠 verify on device |
| [DD-25](#dd-25--boot-re-registration-via-a-non-exported-receiver-and-workmanager) | Boot re-registration via a non-exported receiver and WorkManager | Sensors | Accepted (AR-6) · 🟠 verify on device |
| [DD-26](#dd-26--manual-appcontainer-instead-of-a-di-framework) | Manual `AppContainer` instead of a DI framework | Architecture | Accepted (AR-7) |
| [DD-27](#dd-27--library-services-get-dependencies-through-an-application-implemented-interface) | Library services get dependencies through an Application-implemented interface | Architecture | Accepted (AR-7) |
| [DD-28](#dd-28--injected-clock-now-is-read-inside-the-transaction) | Injected `Clock`; "now" is read inside the transaction | Architecture | Accepted (AR-7) |
| [DD-29](#dd-29--surfaces-are-refreshed-by-a-repository-decorator) | Surfaces are refreshed by a repository decorator | Surfaces | Accepted (AR-4) |
| [DD-30](#dd-30--60-second-decay-ticker-only-while-collected) | 60-second decay ticker, only while collected | Game engine / UI | Accepted (AR-4) |
| [DD-31](#dd-31--tile-futures-via-kotlinx-coroutines-guava-not-horologist) | Tile futures via kotlinx-coroutines-guava, not Horologist | Surfaces | Accepted (AR-4) |
| [DD-32](#dd-32--no-periodic-background-work) | No periodic background work | Background / battery | Accepted (AR-5) |
| [DD-33](#dd-33--energy-recovers-during-a-fixed-local-night-window) | Energy recovers during a fixed local night window | Game engine / balance | Accepted (AR-3) · 🟣 your call |
| [DD-34](#dd-34--the-pet-always-sleeps-at-night) | The pet always sleeps at night | Game design | Accepted (AR-3) · 🟣 your call |
| [DD-35](#dd-35--an-append-only-habit-history-table-schema-v2) | An append-only habit history table (schema v2) | Persistence | Accepted (AR-3) |
| [DD-36](#dd-36--archetype-from-7-day-consistency-locked-in-once-at-teen) | Archetype from 7-day consistency, locked in once at TEEN | Game design / balance | Accepted (AR-3) · 🟣 your call |
| [DD-37](#dd-37--live-steps-come-from-the-platform-step-detector-only-while-the-screen-is-visible) | Live steps come from the platform step detector, only while the screen is visible | Sensors / battery | Accepted · 🟠 verify on device |
| [DD-38](#dd-38--live-steps-are-cosmetic-only) | Live steps are cosmetic only | Sensors / balance | Accepted |
| [DD-39](#dd-39--walkrun-from-burst-cadence-with-hysteresis-a-sleeping-pet-does-not-react) | Walk/run from burst cadence with hysteresis; a sleeping pet does not react | Game design / UI | Accepted · 🟣 your call |
| [DD-40](#dd-40--alerts-are-scheduled-at-the-predicted-crossing-not-polled) | Alerts are scheduled at the predicted crossing, not polled | Background / battery | Accepted · 🟠 verify on device |
| [DD-41](#dd-41--one-alert-per-critical-episode-at-the-mood-threshold-never-at-night) | One alert per critical episode, at the mood threshold, never at night | Game design / notifications | Accepted · 🟣 your call |
| [DD-42](#dd-42--the-tile-logs-water-in-place-through-a-loadaction-deduplicated-by-a-per-render-click-id) | The tile logs water in place through a `LoadAction`, deduplicated by a per-render click id | Surfaces | Accepted · 🟠 verify on device |
| [DD-43](#dd-43--the-complication-shows-mood-and-overall-health-not-step-progress) | The complication shows mood and overall health, not step progress | Surfaces | Accepted · 🟠 verify on device · 🟣 your call |
| [DD-44](#dd-44--the-pet-screen-stays-on-in-ambient-mode-as-a-static-outline-and-releases-the-step-sensor) | The pet screen stays on in ambient mode as a static outline, and releases the step sensor | Surfaces / battery | Accepted · 🟠 verify on device |
| [DD-45](#dd-45--the-crown-pages-between-the-pet-and-a-vitals-breakdown-petting-stays-a-tap) | The crown pages between the pet and a vitals breakdown; petting stays a tap | UI / input | Accepted |
| [DD-46](#dd-46--percentages-are-rounded-not-truncated-on-every-surface) | Percentages are rounded, not truncated, on every surface | UI | Accepted |

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
- **Follow-up (resolved)**: Degradation used to be all-or-nothing across permissions. Since AR-6, each permission unlocks its own sensors and only activity recognition is core (DD-22).

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
- **Status**: Accepted (AR-1).
- **Decision**: The consumed totals per data type and the last heart-rate award time are stored in the `passive_sync` Preferences DataStore via `PassiveSyncRepositoryImpl`. Concurrent batches are serialized by `DataStore.edit`.
- **Why**: A new Room table bumps the schema version. With the current destructive migration fallback, that would **delete the pet** (AR-8).
- **Trade-off**: The baseline and the pet live in different stores, so they cannot share a transaction. This is why DD-09 fixes the ordering to "consume first".

> [!IMPORTANT]
> **🟣 Your call: move the baselines into Room?** AR-8 has since added real migrations (DD-18), so this is now possible. It would let one transaction cover both the baseline and the pet, which removes the under-count-on-crash window of DD-09. It costs a schema v3 migration and moving `PassiveSyncRepositoryImpl` off DataStore.

### DD-12 — One sensor batch is one pet write
- **Status**: Accepted (AR-1).
- **Decision**: All habits derived from one Health Services batch are applied in order with `PetRepository.recordHabits(list)`, in a single transaction.
- **Why**: Fewer writes and Room invalidations (UI recompositions), and a batch is applied all-or-nothing.

### DD-13 — Distance and daily calories are not consumed
- **Status**: Accepted (AR-1).
- **Decision**: `DISTANCE_DAILY` and `CALORIES_DAILY` are no longer registered or rewarded.
- **Why**:
  - Distance is derived from the same walking as steps, so it double-rewards.
  - Daily calories include basal metabolic burn (~2,000 kcal/day), so they measure being alive, not activity. The old mapping to `Workout` also drained 10 energy per batch.
- **Consequences**: Fewer sensor wake-ups. Running and cycling without steps are under-rewarded.

> [!IMPORTANT]
> **🟣 Your call: bring them back in another form?** Running and cycling without steps are currently under-rewarded. The options are a combined activity score (e.g. active minutes), or rewarding them only through `ExerciseClient` workouts, which are planned.

### DD-14 — Floors are a climbing bonus worth 20 steps each
- **Status**: Accepted (AR-1).
- **Decision**: Each floor delta counts as **20 extra step-equivalents**, consumed in chunks of 10 floors (= 200 step-equivalents = 1 XP).
- **Why**: Climbing takes more effort than the steps it produces. It keeps the pre-existing 20-steps-per-floor ratio.
- **Code**: `IngestPassiveDataUseCase.STEPS_PER_FLOOR`.

> [!IMPORTANT]
> **🟣 Your call: is 20 steps per floor right?** It's a game-balance value. With 10-floor chunks, a typical day of 5–10 floors may not earn anything until the next day's floors add up.

### DD-15 — Heart-rate awards are limited to one per 30 minutes
- **Status**: Accepted (AR-1).
- **Decision**: A heart-rate habit (small fitness boost, 5 XP) is awarded at most once per **30 minutes**, using the latest sample in the batch. The limit is enforced atomically via `PassiveSyncRepository.tryClaimHeartRateAward`.
- **Why**: Heart-rate samples arrive in nearly every batch, and XP per batch would grow unbounded just from wearing the watch.
- **Consequences**: Heart rate adds at most ~240 XP/day. A wall-clock jump backwards resets the limit rather than blocking it.
- **Code**: `IngestPassiveDataUseCase.HEART_RATE_AWARD_INTERVAL_MS`.

> [!IMPORTANT]
> **🟣 Your call: is once per 30 minutes right?** It's a game-balance value. Up to ~240 XP a day, just from wearing the watch, is a lot next to steps (max 50 XP per batch).

### DD-16 — First reading credits today's activity so far
- **Status**: Accepted (AR-1).
- **Decision**: With no stored baseline (fresh install, or cleared app data), the first reading counts everything since midnight. For example, 8,000 steps at install gives +80 fitness and 40 XP.
- **Why**: It feels rewarding on day one, and it is a one-off.
- **Alternatives**: Start the baseline at the first reading, so nothing is credited until the user walks further.

> [!IMPORTANT]
> **🟣 Your call: keep the day-one credit?** It's a nice welcome, but clearing app data or reinstalling also re-credits the day.

### DD-17 — A daily reading belongs to the day of its interval end minus 1 ms
- **Status**: Accepted (AR-1).
- **Decision**: The local day of a `*_DAILY` data point is computed from `getEndInstant(bootInstant) − 1 ms` in the device's time zone.
- **Why**: An interval that ends exactly at midnight holds the *previous* day's total. Attributing it to the new day would credit a whole day twice.
- **Code**: `core/health/.../PassiveDataService.kt` (`latestDailyTotal`).

> [!WARNING]
> **🟠 Verify on device.** Confirm how Health Services timestamps the intervals around the daily reset, and how time-zone changes behave. Steps: [VERIFICATION.md V6](VERIFICATION.md#v6--midnight-and-time-zones-dd-17).

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
- **Status**: Accepted (AR-8).
- **Decision**: `PetEntity.toDomain()` repairs invalid data instead of throwing:
  - Vitals are clamped to `[0, 100]`, and `NaN` becomes 0.
  - Negative XP becomes 0.
  - An unknown `stage` name is re-derived from XP.
  - An unknown `archetype` name becomes `BALANCED`.

  The `Vitals` constructor keeps its `require()` range checks as an invariant for in-memory code.
- **Why**: A throwing `toDomain()` inside `getPetFlow()` crashes *every* launch, and the user cannot fix that. A slightly repaired pet is far better than a permanently crashing app. Enum fallbacks also make renaming or removing enum constants survivable.
- **Alternatives**: Throw and reset the pet (loses the pet). Throw and show an error screen (the app becomes unusable).

> [!IMPORTANT]
> **🟣 Your call: make repairs visible?** Repairs are currently silent. Consider logging or counting them once there is telemetry or a debug screen, so data bugs don't go unnoticed.

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
- **Status**: Accepted (AR-6).
- **Decision**: Heart rate is registered only if both permissions are granted:
  - the foreground permission: `BODY_SENSORS` ≤ API 35, `health.READ_HEART_RATE` ≥ 36;
  - the background permission: `BODY_SENSORS_BACKGROUND` on API 33–35, `health.READ_HEALTH_DATA_IN_BACKGROUND` on ≥ 36.

  The background permission is requested in a separate, second dialog right after the foreground grant, at most once per session.
- **Why**: Per the [Health Services permissions docs](https://developer.android.com/health-and-fitness/health-services/permissions), apps targeting API 36 must use the granular health permissions. `PassiveMonitoringClient` access to body sensors in the background needs the background permission, and Android requires background permissions to be requested after the foreground one.
- **Alternatives**:
  - Drop passive heart rate entirely (simplest, most privacy-friendly).
  - Register with the foreground permission only. Rejected: the data likely wouldn't be delivered in the background.

> [!WARNING]
> **🟠 Verify on device.** Check the dialog behaviour on Wear OS 6 and on API 33–35, and that heart-rate data is actually delivered in the background.

> [!IMPORTANT]
> **🟣 Your call: is heart rate worth two dialogs?** It only adds a small fitness and XP bonus (DD-15), and it will need Play Store health-permission declarations before release. Dropping it would simplify onboarding and privacy.

### DD-24 — Idempotent registration keyed on permitted sensors + boot count
- **Status**: Accepted (AR-6).
- **Decision**: `ensureRegistered()` stores a key of *permitted sensors + `Settings.Global.BOOT_COUNT`* after each successful registration. It skips Health Services entirely while the key is unchanged. Calls are serialized by a process-wide `Mutex`.
- **Why**: `Application.onCreate` runs on every process start, including each time Health Services wakes the app to deliver a batch. Re-registering each time costs an IPC round-trip. Including the boot count keeps the check correct across reboots even if the boot receiver never runs.
- **Consequences**: The key is based on *permitted* sensors, not the capability-filtered set. Capabilities don't change at runtime, so this saves a capability query on every start.

> [!WARNING]
> **🟠 Verify on device: does the registration survive app updates?** The assumption is that it's only lost on reboot. Clearing app data also loses it, but that clears the stored key too, so the next start re-registers. If Health Services turns out to drop registrations on app update, add the app version code to the key.

### DD-25 — Boot re-registration via a non-exported receiver and WorkManager
- **Status**: Accepted (AR-6).
- **Decision**: `BootCompletedReceiver` (`exported="false"`) enqueues a unique one-time `PassiveRegistrationWorker`, which calls `ensureRegistered(force = true)` and retries on failure.
- **Why**: This follows the [Health Services background monitoring guidance](https://developer.android.com/health-and-fitness/guides/health-services/monitor-background): registrations don't persist across reboots, and at boot Health Services may take over 10 s to respond, which exceeds a receiver's execution limit. The receiver is not exported because only the system sends `BOOT_COMPLETED`.

> [!WARNING]
> **🟠 Verify on device.** Reboot an emulator or watch and confirm that the non-exported receiver actually receives `BOOT_COMPLETED`, and that passive data arrives afterwards.

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
- **Status**: Accepted (AR-3, 2026-09-26).
- **Decision**: Inside `NightWindow.DEFAULT` (22:00–07:00 in `Clock.zone()`), energy **recovers** at +8 %/h instead of decaying at −2 %/h. `calculateDecay` splits the elapsed time into day/night segments and clamps after each one. Other vitals keep decaying at night.
- **Why**: Energy previously had no way to recover, so every pet ended up permanently `TIRED`. A deterministic, sensor-free rule:
  - works for everyone, including people who don't wear the watch at night or whose watch doesn't detect sleep;
  - is pure and testable;
  - fits decay-on-read (DD-02).

  Piecewise integration is required because the rate changes sign: a day that drains energy to 0, followed by a night, must end at the night's recovery, not at a net sum (the test documents 72 vs 52).
- **Alternatives**:
  - Health Services sleep detection (`UserActivityState.USER_ACTIVITY_ASLEEP`, needs `ACTIVITY_RECOGNITION`): the most "mirroring", but unverified on hardware and useless when the watch is charging overnight.
  - An explicit "rest" button: more UI, and it's a chore.

> [!IMPORTANT]
> **🟣 Your call: night rules.**
> - Should bedtime be user-configurable, instead of a fixed 22:00–07:00 in the device's time zone?
> - Should hydration and hunger decay more slowly at night? They currently lose −27 and −22.5 over 9 h, while the user can't log anything.
> - Should real sleep data be layered on top later, e.g. bonus XP or happiness for detected sleep?
>
> The +8 %/h recovery rate is a game-balance value.

### DD-34 — The pet always sleeps at night
- **Status**: Accepted (AR-3).
- **Decision**: `MoodCalculator` returns `SLEEPING` whenever `isNightTime` is true, regardless of vitals. Previously the rule was night *and* energy < 40, and it was never reached because `isNightTime` was never passed.
- **Why**: The pet recovers energy at night because it is asleep (DD-33). Under the old threshold rule it would wake up as soon as energy passed 40, at around 2 a.m., which is incoherent.
- **Consequences**: Thirst and hunger warnings aren't shown at night. They reappear at 07:00 if still relevant.

> [!IMPORTANT]
> **🟣 Your call: should critical needs wake the pet?** For example, show `THIRSTY` at night when hydration is critical, at the cost of the "always asleep at night" coherence.

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
- **Status**: Accepted (AR-3).
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

> [!IMPORTANT]
> **🟣 Your call: archetype thresholds.** These are provisional: 6,000 steps; a workout or heart rate ≥ 100 bpm; 1,500 ml plus 2 healthy meals; the winner needs 4 of 7 days and no tie. Tune them once real usage data exists. Also consider re-evaluating the archetype at `ADULT`.

---

## Live step reactions (Phase 2)

### DD-37 — Live steps come from the platform step detector, only while the screen is visible
- **Status**: Accepted (2026-09-26).
- **Decision**:
  - `SensorLiveStepSource` (`:core:health`) listens to `Sensor.TYPE_STEP_DETECTOR` through `SensorManager`, with `maxReportLatencyUs = 0`. When a watch has no detector, it falls back to `TYPE_STEP_COUNTER`, whose increases are spread evenly over the time since the previous reading (`StepCounterSpreader`, at most 20 steps per reading, at the real average spacing).
  - The listener is registered only while the pet screen collects `PetViewModel.uiState`. `PetScreen` now uses `collectAsStateWithLifecycle()`, so collection stops when the activity is stopped (screen off, app in the background), plus the ViewModel's existing 5-second `WhileSubscribed` grace period.
  - Without `ACTIVITY_RECOGNITION`, or without either sensor, the flow completes empty and the pet simply never walks. No new permission is needed.
- **Why**:
  - Passive Health Services data is batched by the OS and can arrive minutes late, which is useless for an "instant" reaction.
  - `MeasureClient` does not offer steps.
  - `ExerciseClient` would start a full workout session, with its own notification and battery cost, just to animate a pet.
  - The hardware step detector is a low-power sensor, and holding it only while the screen is on keeps the cost within the display's own.
- **Consequences**:
  - Switching to `collectAsStateWithLifecycle()` also stops the 60-second decay ticker (DD-30) while the activity is stopped. Previously, plain `collectAsState()` kept collecting until the activity was destroyed.
  - Reactions only exist in the app. The tile and future complications stay static.
  - Sensor timestamps (`elapsedRealtimeNanos`) are converted to wall-clock time, so the cadence tracker can use the injected `Clock`.

> [!WARNING]
> **🟠 Verify on device:** whether the target watches expose `TYPE_STEP_DETECTOR` (or only the counter), how late the first events arrive after walking starts (many detectors confirm a few steps before reporting), and whether the Wear OS emulator produces any step events. Also check the battery impact while the pet screen stays on during a walk.

### DD-38 — Live steps are cosmetic only
- **Status**: Accepted (2026-09-26).
- **Decision**: `ObservePetActivityUseCase` only produces a `PetActivity` for the UI. Live steps are never written to the pet and never award fitness or XP.
- **Why**: The same steps are counted by the passive `STEPS_DAILY` totals (DD-08). Awarding them live as well would double count, and deduplicating two sources with different latencies is fragile (DD-09: prefer under-counting over double counting).
- **Alternatives**: Award live steps and subtract them from the next passive delta. This is more immediate, but needs shared bookkeeping between a foreground and a background source, and breaks when the screen turns off mid-walk.
- **Consequences**: The vitals ring still only moves when a passive batch arrives. The pet visibly reacts right away, and the reward follows later.

### DD-39 — Walk/run from burst cadence with hysteresis; a sleeping pet does not react
- **Status**: Accepted (2026-09-26).
- **Decision**:
  - The pure `StepCadence` tracker (`:core:domain`) considers the current *burst*: steps within the last 6 s that follow the last pause longer than 2.5 s. The cadence is measured across the burst's own span, so the pet reacts after **3 steps** instead of waiting for a full window.
  - Below 3 steps, or 2.5 s after the last step: `IDLE`. Otherwise `WALKING`, or `RUNNING` once the cadence reaches **145 steps/min**. A running pet only drops back to walking below **130 steps/min** (hysteresis, so the animation doesn't flicker around one threshold).
  - Steps and a 1-second ticker are merged into a single sequential `scan`, so the pet also stops within about a second of the user.
  - `PetViewModel` shows `IDLE` whenever the mood is `SLEEPING`, so the pet stays asleep at night instead of sleepwalking (DD-34).
  - The canvas blends between states over 350 ms: a bob per step, alternating paw lifts, a 3° lean when walking and 9° when running, faster tail wagging, and speed lines when running. The gait cycle is 760 ms when walking and 420 ms when running. It is stylised and not synced to the user's actual step times.
- **Why**: Typical walking cadence is 90–130 steps/min and running is 150–180, so the thresholds sit in the gap. Burst-based measurement makes the reaction feel immediate, and the pause rule stops a single stray step from restarting the walk.
- **Alternatives**: Sync every animation bounce to a real step event. This feels more "mirrored", but event delivery is jittery and sometimes batched, so the animation would stutter.

> [!IMPORTANT]
> **🟣 Your call: live reaction tuning.**
> - Thresholds: 3 steps to react, 2.5 s pause to stop, run at ≥ 145 steps/min and back to walking below 130.
> - Should walking or running wake a sleeping pet at night (e.g. a sleepy stumble), instead of it staying asleep?
> - Should the mood change the gait, e.g. a `TIRED` pet refusing to run?

---

## Critical-vital notifications (Phase 2)

### DD-40 — Alerts are scheduled at the predicted crossing, not polled
- **Status**: Accepted (2026-09-26).
- **Decision**:
  - Hydration and hunger decay linearly (DD-02), so `VitalAlertPlanner` computes exactly when each will cross its critical threshold. A single one-time WorkManager job (`VitalAlertWorker`, unique work `CriticalVitalCheck`) is scheduled for the earliest crossing, 1 minute past it so the vital is clearly below the line.
  - The worker posts alerts for newly critical vitals, cancels alerts for recovered ones, and appends the next delayed check (`APPEND_OR_REPLACE`, so it never cancels itself while running).
  - Every committed pet write requests an immediate re-check (`REPLACE`), through the same `NotifyingPetRepository` callback that refreshes the tile (DD-29). Logging water therefore clears the thirst notification and pushes the next check out.
  - Process start and app resume call `ensureScheduled()` (`KEEP`), which leaves a pending check alone. WorkManager persists the job across reboots, so no boot hook is needed.
  - The check never writes the pet (vitals are decayed in memory), so it can't trigger itself through the write callback.
- **Why**: DD-32 removed all periodic work. A prediction gives one wake-up per actual event instead of polling every N minutes, and it stays correct because any write that changes the prediction reschedules it.
- **Alternatives**:
  - A periodic worker (at least 15 minutes): wakes the watch dozens of times a day and alerts up to 15 minutes late anyway.
  - `AlarmManager` exact alarms: precise, but need `SCHEDULE_EXACT_ALARM` (denied by default since API 33), don't survive reboot, and a pet's thirst doesn't need to-the-minute precision.
- **Consequences**:
  - WorkManager delays are inexact. Under Doze an alert can arrive later than predicted.
  - Every pet write (UI tap, sensor batch) runs one short worker. Sensor batches arrive minutes apart, so this is cheap.
  - Without notification permission the worker skips the check entirely, without recording anything, and the chain stops. Granting the permission (onboarding result) or reopening the app restarts it.

> [!WARNING]
> **🟠 Verify on device:** how late a delayed check actually runs under Doze on a real watch, that an alert appears (and is cleared after logging water), and that a scheduled check survives `adb reboot`. The quickest test is to set hydration low: log nothing for a while, or temporarily lower the threshold.

### DD-41 — One alert per critical episode, at the mood threshold, never at night
- **Status**: Accepted (2026-09-26).
- **Decision**:
  - **Threshold**: hydration or hunger below **25**, the same line where the pet turns `THIRSTY` or `HUNGRY` (`MoodCalculator.THIRSTY_BELOW` / `HUNGRY_BELOW`, now named constants shared by both).
  - **Once per episode**: an alerted vital is stored in a small `vital_alerts` DataStore file and isn't alerted again until it has recovered to at least 25. It is not re-alerted if the user just dismisses the notification.
  - **Quiet hours**: no alerts inside the pet's `NightWindow` (22:00–07:00). A vital that is, or becomes, critical at night is alerted at 07:00 if it is still critical then.
  - **Content**: one notification per vital ("Aura is thirsty" / "Aura is hungry"), channel "Pet needs" at default importance. Tapping it opens the app.
  - **Permission**: `POST_NOTIFICATIONS` (API 33+) is requested in the same onboarding request as the health permissions. It is optional and doesn't affect degraded mode.
- **Why**: Matching the mood means the notification and the pet's face always agree. One alert per episode, and silence at night, avoid a nagging watch, which is the fastest way to get notifications disabled.
- **Consequences**: Because hydration and hunger keep decaying overnight (DD-33), users who went to bed with low vitals will often get a 07:00 alert. Installs that finished onboarding before this change are never asked for the notification permission; the app is unreleased, so that only affects development builds.

> [!IMPORTANT]
> **🟣 Your call: alert rules.**
> - Alert at the mood threshold (25), or earlier/later (e.g. 20, where the happiness neglect penalty starts)?
> - A reminder if a vital stays critical for hours (e.g. once more after 4 h), or strictly once per episode?
> - Quiet hours tied to the pet's night (22:00–07:00), or separate/configurable?
> - Add a "+250 ml" action button on the thirst notification, so it can be answered without opening the app?

---

## Interactive tile (Phase 3)

### DD-42 — The tile logs water in place through a `LoadAction`, deduplicated by a per-render click id
- **Status**: Accepted (2026-09-26).
- **Decision**:
  - The tile's "+250ml Water" chip uses a `LoadAction`. A tap makes the system call `onTileRequest` again with the chip's id as `lastClickableId`. That request logs `HabitType.Hydration(250)` through `LogHabitUseCase`, the same path as the in-app token, and then renders the new hydration value.
  - `onTileRequest` now has a side effect, so each tap must log **at most once**. Every render stamps the chip with a fresh id (`log_water:<render time>`), and `TileClickLedger` stores the last handled id in a small `tile_clicks` `SharedPreferences` file. A request that repeats a handled id is ignored: a freshness refresh, the update the write itself requests (DD-29), or a second tap before the tile re-renders. The claim is committed before the water is logged, so a failed write loses one tap instead of logging twice.
  - The only feedback is the re-render: the tile now shows a hydration line next to overall health. Tapping the vitals opens the app (`LaunchAction`).
  - The layout moved from a hand-built `Column` to protolayout-material's `PrimaryLayout` and `CompactChip` (already a dependency), with a water-drop icon (`ic_tile_water_drop`, resources version `2`).
- **Why**: Logging a drink is a 3–5 second interaction. A `LoadAction` keeps it on the tile, with no app launch. Whether the renderer keeps reporting a stale `lastClickableId` on later requests is not clearly documented, and the per-render id makes the answer irrelevant.
- **Alternatives**:
  - `LaunchAction` to a trampoline activity that logs and finishes: opens a window and is slower.
  - A fixed click id plus a time window (ignore repeats within N seconds): a stale id arriving after the window, e.g. on the 10-minute refresh, would log again.
  - A DataStore file behind a `:core:domain` interface, like the other small stores: more wiring for one string that only the tile uses.
- **Consequences**:
  - A tap only takes effect once the renderer calls back, so there is no haptic or instant visual confirmation. Tapping twice quickly logs once, and a second drink needs the tile to re-render first.
  - The write triggers the usual tile update request, so a tap causes two renders in quick succession. The system coalesces them, and the second is a pure read.

> [!WARNING]
> **🟠 Verify on device:** one tap logs exactly 250 ml (check the hydration line and the in-app ring), the tile re-renders within a second or two, repeated refreshes don't log again, and tapping the vitals opens the app.

---

## Watch face complication (Phase 3)

### DD-43 — The complication shows mood and overall health, not step progress
- **Status**: Accepted (2026-09-26).
- **Decision**:
  - `PetMoodComplicationService` (a `SuspendingComplicationDataSourceService`) offers three types:
    - **Short text**: a mood face and a short mood label.
    - **Ranged value**: overall health 0–100, the same number as the in-app vitals ring, with the mood face and "72%".
    - **Monochromatic image** (`ICON` in the manifest): the mood face alone.
  - Each of the 8 moods has its own monochrome face (`ic_mood_*`, tinted by the watch face). Labels are shortened to fit a short-text slot (at most 7 characters: "Elated" for `ECSTATIC`, "Asleep" for `SLEEPING`). `MoodPresentation` maps them with an exhaustive `when`, so a new mood fails to compile until it has both.
  - Freshness works like the tile: `UPDATE_PERIOD_SECONDS = 600` for decay, and `AppContainer.requestSurfaceRefresh` calls `requestUpdateAll()` after every committed write (DD-29). Tapping it opens the app.
  - The tile and the complication read the same one-shot snapshot, `GetPetStateUseCase.current()`, so both apply the same decay and mood rules.
- **Why**: The roadmap offered "mood icon or step-progress ring". A step ring needs a daily step goal, and the game has none: steps feed fitness and XP without a target. Overall health is already the app's headline number and already has a 0–100 range.
- **Alternatives**:
  - Step progress towards a new daily goal: needs a goal decision and today's step total (which currently lives only in the sensor baselines).
  - `SMALL_IMAGE` with the full-colour pet: richer, but it can't be tinted, has no ambient version, and many watch faces only accept monochrome icons.
  - A complication timeline predicting mood changes: decay is linear, so it could be computed ahead of time, but the 10-minute refresh is simpler and accurate enough.
- **Consequences**:
  - A mood change caused only by decay (e.g. falling asleep at 22:00) can show up to about 10 minutes late, or later when the system stretches update periods to save battery.
  - Every pet write now also asks for a complication update. The call does nothing when the complication isn't on a watch face.

> [!IMPORTANT]
> **🟣 Your call: complication content.**
> - Keep overall health as the ring, or add a daily step goal and offer a step-progress ring (as a second complication, or instead)?
> - Short labels "Elated" and "Asleep" instead of "Ecstatic" and "Sleeping"?

> [!WARNING]
> **🟠 Verify on device:** all three types render on a watch face (with icons tinted, and in ambient mode), logging water updates the ring within a few seconds, and tapping it opens the app.

---

## Ambient mode (Phase 3)

### DD-44 — The pet screen stays on in ambient mode as a static outline, and releases the step sensor
- **Status**: Accepted (2026-09-26).
- **Decision**:
  - `MainActivity` registers `AmbientLifecycleObserver` (`androidx.wear:wear` 1.4.0, a new dependency). When the watch dims and always-on is enabled, the app stays on screen instead of returning to the watch face.
  - **Look**: in ambient mode the pet screen shows the time (plain text: Material's `TimeText` draws a filled pill behind the time even in ambient), the pet's name, and a static outline pet in its current mood (ears, eyes, mouth, and the Zzz when asleep). The vitals ring becomes thin outline arcs without background tracks, inset so the time fits along the top. Everything is grey (`AmbientGray`) on black, or pure white on low-bit displays, where grey could be quantized to black. Buttons, the sensor chip, the stage line, gradients, cheeks and particles are hidden.
  - **No animation**: the ambient pet creates no infinite transitions, so nothing redraws between updates.
  - **Burn-in**: when `burnInProtectionRequired`, the whole screen moves by up to 4 dp per axis on each once-a-minute update, walking a fixed 8-step loop around the centre (`BurnInShift`).
  - **Sensors and refresh**: `PetViewModel.setAmbient(true)` swaps the live step flow for a constant `IDLE` (`flatMapLatest`), which unregisters the step listener. `onUpdateAmbient` feeds `GetPetStateUseCase.execute(refresh = …)`, so decay and mood are re-evaluated every minute even if the CPU slept through the ticker's `delay`.
  - The onboarding and loading screens are not adapted. They are shown only briefly, on first launch.
- **Why**: Always-on is the Wear OS way to keep a glanceable app visible. The guidelines ask for mostly black pixels, no animation, and a burn-in shift. Releasing the step sensor keeps DD-37's rule (live steps only while the user can interact) in spirit, since live reactions aren't drawn in ambient anyway.
- **Alternatives**:
  - Do nothing, and let the system show the watch face when the screen dims: the simplest and cheapest option, but the pet disappears the moment the wrist drops.
  - Horologist's ambient helpers: another dependency family for what one observer does.
  - A separate ambient screen: duplicates the layout. Adding `displayMode` to the existing components keeps one layout.
  - A per-mood bitmap for ambient: needs 8 more assets. The outline reuses the procedural pet's own shapes.
- **Consequences**:
  - Low-bit displays still get anti-aliased edges, which the display quantizes. Compose's draw calls don't expose turning anti-aliasing off.
  - Ambient support depends on the system's `WAKE_LOCK` handling. The permission is present in the merged manifest (added by WorkManager) but not declared by the app itself since DD-32.
  - Entering ambient hides the buttons, so the pet moves slightly upwards in the layout.

> [!WARNING]
> **🟠 Verify on device:**
> - With always-on enabled, dimming the screen keeps the pet screen in the ambient look, and a tap returns to the interactive one.
> - The activity stays resumed, so the once-a-minute updates are actually drawn: the time and ring advance, and the shift moves on burn-in devices.
> - The step sensor is released while ambient.
> - Ambient still engages if WorkManager's `WAKE_LOCK` ever disappears from the merged manifest.

---

## Rotary crown (Phase 3)

### DD-45 — The crown pages between the pet and a vitals breakdown; petting stays a tap
- **Status**: Accepted (2026-09-26), chosen by the project owner from four options.
- **Decision**:
  - The pet screen is now page 1 of a two-page `VerticalPagerScaffold` (`PetPager`). Page 2 (`VitalsScreen`) lists all five vitals with a whole-number percentage and a bar in the ring's colours, under "Aura · 92%" (overall health).
  - Turning the crown snaps between the pages, with haptic ticks. This is the scaffold's default rotary behaviour (`PagerDefaults.snapRotaryScrollableBehavior`), so no rotary code of our own is needed. Swiping does the same, and a vertical page indicator shows the position.
  - The Vitals page is a fixed column, not a scrolling list, so the crown never has two jobs on one page.
  - Happiness appears for the first time: it has no arc on the ring.
  - In ambient mode the ambient pet is shown whichever page was open, and the page is restored afterwards (DD-44).
- **Why**: Paging is the standard Wear OS use of the crown and is discoverable. The roadmap's three ideas (zoom, vitals breakdown, petting) compete for one input, and a vitals breakdown is the most useful.
- **Alternatives**:
  - Crown petting (strokes with haptic ticks), alone or mixed with paging: playful, but a slow turn meaning "stroke" and a fast turn meaning "scroll" is ambiguous and hard to tune.
  - Crown zoom on the pet: the least useful of the three.
- **Consequences**:
  - Zooming and crown petting are dropped from the roadmap item. Petting stays a tap on the pet.
  - The vitals rows are sized for the 5-row layout. A sixth row (e.g. XP or level) would need a scrolling list, and with it a rotary scroll inside the page.

---

## Display details

### DD-46 — Percentages are rounded, not truncated, on every surface
- **Status**: Accepted (2026-09-26).
- **Decision**: The app's Vitals page, the tile and the complication all show vitals and overall health through one helper, `Float.toDisplayPercent()` (in `:wearApp`). It rounds to the nearest whole number, clamps to `0..100`, and shows `NaN` as 0.
- **Why**: Decay starts the moment a value is written. A vital just filled to 100 is 99.99 a few seconds later, and truncation showed 99 % right after the user topped it up (seen on the emulator, V10). Using one helper also means the surfaces can't disagree by 1.
- **Alternatives**: Keep truncating and special-case values close to 100. That's more code for the same result.
- **Consequences**: A value of 99.5 or more shows as 100 % although it isn't quite full. The ring and the bars still draw the exact fraction.
