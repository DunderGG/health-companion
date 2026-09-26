# Architecture & System Design

## Overview
**Health Companion** is a standalone Wear OS virtual pet app inspired by the classic Tamagotchi toy, reimagined for modern smartwatches. The companion's growth, energy, and happiness directly mirror the user's real-world health habits—including physical activity, step goals, hydration, nutrition, and rest.

> **Document status (2026-09-26):** This document describes both the implemented system and the target design. Items marked *(planned)* do not exist in code yet. Known deviations between the design and the current implementation are tracked as numbered review findings (**AR-1 … AR-8**) in [§9 Architecture Review Findings](#9-architecture-review-findings-2026-09-26) and scheduled in [ROADMAP.md → Phase 2a](ROADMAP.md#phase-2a-architecture-review-remediation). The reasoning behind non-trivial design choices, their trade-offs and open questions is logged separately in [DESIGN_DECISIONS.md](DESIGN_DECISIONS.md) (**DD-xx**).

---

## 1. System Interaction Flow

```plantuml
@startuml System_Interaction_Flow
!theme plain
skinparam componentStyle rectangle
skinparam roundCorner 8

package "Real-World Health Activities" as RealWorld {
    [Steps & Walking\n(Health Services)] as Steps
    [Active Workouts\n(ExerciseClient)\n(planned)] as Workouts
    [Hydration Log\n(+250ml quick-tap)] as Water
    [Nutrition Log\n(Healthy Meal / Snack)] as Food
    [Sleep & Rest\n(Passive Monitoring)\n(planned)] as Sleep
}

package "Game Engine" as PetEngine {
    [Time-Delta Decay Engine] as Decay
    [Vitals State\n(Energy, Hunger, Hydration, Fitness, Mood)] as Vitals
    [Evolution & Archetype Rules] as Evo
}

package "Wear OS User Surfaces" as WearSurfaces {
    [Main Wear Compose App\n(Interactions, Petting, Stats)] as MainApp
    [Wear OS Quick Tile\n(1-Swipe Status;\nWater Log planned)] as Tile
    [Watch Face Complication\n(Pet Mood Icon / Meter)\n(planned)] as Complication
}

Steps --> Vitals : Boosts Fitness & XP
Workouts --> Vitals : Major Fitness & Energy Burn
Water --> Vitals : Restores Hydration
Food --> Vitals : Restores Hunger & Nutrition
Sleep --> Vitals : Recharges Energy
Decay --> Vitals : Fair Passive Drain

Vitals --> Evo : Feeds Level & Traits
Vitals --> MainApp : Renders Live Screen
Vitals --> Tile : Updates Glance State
Vitals --> Complication : Updates Watch Dial
@enduml
```

---

## 2. Technology Stack & Frameworks

| Layer / Component | Technology | Rationale & Capabilities |
| :--- | :--- | :--- |
| **Target Platform** | Wear OS 3.0+ (API 30–36) | Standalone wearable app (`com.google.android.wearable.standalone = true`). |
| **UI Framework** | Jetpack Compose for Wear OS (`compose-material3`, `compose-foundation`) | Hardware-accelerated, declarative UI optimized for circular displays. |
| **Wear Utilities** | Horologist (`horologist-compose-layout`) | Rotary crown input, ambient mode scaffolds, and volume/haptics. |
| **Health & Sensors** | Health Services for Wear OS (`androidx.health:health-services-client`) | Capability-aware passive monitoring via `PassiveMonitoringClient`: steps, floors, and heart rate. |
| **Glance Surfaces** | AndroidX Wear Tiles & ProtoLayout | Instant-access carousel card with 1-tap micro-interactions. |
| **Watch Face Integration** | AndroidX WatchFace Complications | *(planned — dependency declared, no data source service yet)* Live mood and vital progress complications on third-party watch faces. |
| **Architecture Pattern** | Clean Architecture + MVI (UDF) | Unidirectional Data Flow with immutable `StateFlow<PetUiState>`. |
| **Persistence** | Jetpack Room SQLite Database | Offline-first local storage for pet vitals, health logs, and history. |
| **Preferences** | Jetpack DataStore Preferences | *(planned — dependency declared, not yet used)* Lightweight key-value storage for user settings and daily goals. |
| **Background Processing**| Jetpack WorkManager (`work-runtime-ktx`) | One-time re-registration of passive monitoring after boot (`PassiveRegistrationWorker`). No periodic work: decay is computed on read (AR-5). |

---

## 3. Architectural Pattern: Clean Architecture & MVI (UDF)

The project follows Modern Android Architecture with strict separation of concerns across a multi-module setup:

```plantuml
@startuml Module_Architecture
!theme plain
skinparam componentStyle rectangle
skinparam roundCorner 8

package "Wear OS Application" {
    [:wearApp] as wearApp
}

package "Presentation & Hardware Services" {
    [:core:ui] as coreUi
    [:core:health] as coreHealth
}

package "Domain Logic & Persistence" {
    [:core:domain] as coreDomain
    [:core:data] as coreData
}

package "Core Domain Models" {
    [:core:model] as coreModel
}

wearApp --> coreUi : Compose Screens & Components
wearApp --> coreHealth : Passive Monitoring
wearApp --> coreDomain : Use Cases
wearApp --> coreData : Database & Background Workers
wearApp --> coreModel : Domain Entities

coreUi --> coreModel : Renders Vitals & Mood
coreHealth --> coreDomain : IngestPassiveDataUseCase
coreHealth --> coreModel : Model Types

coreData --> coreDomain : Implements Repositories
coreData --> coreModel : Persists Entities

coreDomain --> coreModel : Evaluates Game Rules
@enduml
```

### Module Responsibilities

1. **[`:wearApp`](../wearApp)**:
   - Standalone Wear OS application entry point (`com.google.android.wearable.standalone = true`).
   - UI orchestration, Wear Navigation, ViewModel bindings.
   - Composition root: `AppContainer` builds the single dependency graph (clock, database, repositories, use cases, `HealthServicesManager`). `HealthCompanionApp` owns it and implements `PassiveDataDependencies` for `:core:health`.
   - Wear OS surfaces: `PetStatusTileService` (Carousel Tile) and Watch Face Complications *(planned)*.

2. **[`:core:ui`](../core/ui)**:
   - Modern vector-based companion renderer (`ModernPetCanvas`).
   - Dynamic animations: breathing physics, eye blinking, mood facial expressions (Happy, Ecstatic, Hungry, Thirsty, Tired, Sleeping, Grumpy).
   - Circular multi-vital progress ring (`VitalsRing`) designed for round watch dials.
   - Wear Material3 Theme & Color tokens.

3. **[`:core:domain`](../core/domain)**:
   - Pure Kotlin business logic and game mechanics.
   - `PetDecayEngine`: Mathematical timestamp-delta decay calculation.
   - `MoodCalculator`: Evaluates mood states dynamically based on vitals.
   - `EvolutionEngine`: Experience thresholds and archetype branching.
   - `DailyTotalTracker`: Converts cumulative daily sensor totals into apply-once deltas.
   - Use cases: `GetPetStateUseCase`, `LogHabitUseCase`, `IngestPassiveDataUseCase`.
   - Repository interfaces: `PetRepository`, `PassiveSyncRepository`.

4. **[`:core:data`](../core/data)**:
   - Offline-first persistence via Jetpack Room (`CompanionDatabase`, `PetDao`, `PetEntity`).
   - `PetRepositoryImpl`: Coordinates between SQLite database and domain engine.
   - `PassiveSyncRepositoryImpl`: DataStore-backed bookkeeping of consumed sensor totals and heart-rate award timing.

5. **[`:core:health`](../core/health)**:
   - Wraps Wear OS **Health Services API** (`androidx.health:health-services-client`).
   - `HealthServicesManager`: Queries device capabilities via `getCapabilitiesAsync()` and registers a `PassiveListenerService` for the intersection of desired and supported data types (`STEPS_DAILY`, `FLOORS_DAILY`, `HEART_RATE_BPM`). Gracefully skips unsupported sensors.
   - `PassiveDataService`: Receives OS-batched sensor data, extracts the latest daily totals (`IntervalDataType`, tagged with their local day) and the latest heart-rate sample (`SampleDataType`) into a `PassiveDataBatch`, and hands it to `IngestPassiveDataUseCase`.
   - Depends only on `:core:domain`. `PassiveDataService` obtains `IngestPassiveDataUseCase` through the `PassiveDataDependencies` interface, which the `Application` implements.

6. **[`:core:model`](../core/model)**:
   - Pure domain models (`Pet`, `Vitals`, `Mood`, `HabitType`, `EvolutionStage`, `PetArchetype`). Zero Android UI dependencies.

### Directory & Source Tree

```
health-companion/
├── build.gradle.kts                      # Root build configuration
├── settings.gradle.kts                   # Multi-module settings
├── gradle/libs.versions.toml             # Version catalog (Compose, Wear, Health, etc.)
│
├── docs/                                 # Project documentation & design specs
│   ├── ARCHITECTURE.md                   # System architecture & PlantUML diagrams
│   ├── DESIGN_DECISIONS.md               # Decision log (DD-xx): rationale, trade-offs, open questions
│   └── ROADMAP.md                        # Phased timeline & testing guide
│
├── wearApp/                              # Wear OS Application Module
│   ├── src/main/AndroidManifest.xml      # Standalone watch app configuration
│   └── src/main/java/com/healthcompanion/wear/
│       ├── HealthCompanionApp.kt         # Application: owns AppContainer, WorkManager scheduling
│       ├── AppContainer.kt               # Composition root (manual DI)
│       ├── MainActivity.kt               # Main Wear ComponentActivity
│       ├── presentation/pet/             # PetScreen, PetViewModel, PetUiState
│       └── tiles/PetStatusTileService.kt # Wear OS Carousel Tile
│
├── core/
│   ├── model/                            # Pure domain models (Pet, Vitals, Mood, Habits)
│   ├── domain/                           # Pure Kotlin game engine & use cases
│   │   ├── engine/PetDecayEngine.kt      # Mathematical decay & habit application
│   │   ├── engine/MoodCalculator.kt      # Dynamic mood evaluation
│   │   └── engine/EvolutionEngine.kt     # Evolution thresholds & archetypes
│   ├── data/                             # Persistence & Repository layer
│   │   ├── db/CompanionDatabase.kt       # Room Database
│   │   ├── repository/PetRepositoryImpl.kt
│   ├── health/                           # Wear OS Health Services integration
│   │   ├── HealthServicesManager.kt      # PassiveMonitoringClient wrapper
│   │   └── PassiveDataService.kt         # PassiveListenerService: parses sensor batches
│   └── ui/                               # Wear OS Compose UI Components
│       ├── components/ModernPetCanvas.kt # Dynamic vector companion
│       ├── components/VitalsRing.kt      # Circular multi-vital progress arcs
│       └── theme/Theme.kt                # Wear Material3 Dark OLED Palette
```

---

## 4. Component Signal & Interaction Flow (Sequence Diagrams)

To keep the runtime architecture digestible and modular, the end-to-end happy-case lifecycle is decomposed into three distinct sequence flows:
1. **Phase 1: App Startup & Reactive State Observation** (screen wake, decay-on-read, and reactive Compose binding).
2. **Phase 2: User Micro-Interaction** (1-tap quick action, atomic game engine habit integration, and Room SQLite invalidation).
3. **Phase 3: Passive Hardware Sensor Ingestion** (Health Services batched sensor events, conversion to domain habits, and persistence).

---

### 4.1 Phase 1: App Startup & Reactive State Observation

This sequence details what occurs when the user opens the application or the watch screen wakes from ambient mode.

```plantuml
@startuml Phase1_App_Startup_And_Observation
!theme plain
autonumber
skinparam roundCorner 8
skinparam sequenceMessageAlign center

actor "User" as user

box "Presentation (:wearApp & :core:ui)" #F4F6F9
    participant "PetScreen\n(Compose)" as UI
    participant "PetViewModel" as VM
end box

box "Domain Layer (:core:domain)" #EDF7ED
    participant "GetPetStateUseCase" as GetUseCase
    participant "PetDecayEngine" as DecayEngine
    participant "MoodCalculator" as MoodCalc
end box

box "Data & Persistence (:core:data)" #FFF8E1
    participant "PetRepositoryImpl" as Repo
    participant "PetDao\n(Room SQLite)" as DAO
end box

user -> UI : Opens App / Screen Wakes
activate UI
UI -> VM : Observes uiState (Flow via collectAsState)
activate VM

VM -> GetUseCase : execute()
activate GetUseCase
GetUseCase -> Repo : getPetFlow()
activate Repo
Repo -> DAO : getPetFlow() [Room Table Observer]
activate DAO
DAO --> Repo : emits PetEntity (Flow)
deactivate DAO
Repo --> GetUseCase : maps entity to Pet domain model
deactivate Repo

GetUseCase -> DecayEngine : calculateDecay(vitals, now)
activate DecayEngine
DecayEngine --> GetUseCase : returns decayed Vitals (Δt)
deactivate DecayEngine

GetUseCase -> MoodCalc : calculateMood(decayedVitals)
activate MoodCalc
MoodCalc --> GetUseCase : returns Mood (e.g. CONTENT)
deactivate MoodCalc

GetUseCase --> VM : emits PetWithMood(pet, mood)
deactivate GetUseCase

VM --> UI : emits PetUiState.Success(pet, mood)
UI -> user : Renders VitalsRing & Animated ModernPetCanvas
deactivate VM
deactivate UI
@enduml
```

#### Phase 1 Architectural Description
- **Decay-on-Read Pattern**: Rather than running continuous 1-second background ticking loops that would rapidly deplete the watch battery (~300–400 mAh), the database stores vitals as they were at the last write timestamp. When the UI screen wakes and observes the state, `GetPetStateUseCase` lazily evaluates elapsed time decay via `PetDecayEngine.calculateDecay(vitals, now)` based on $\Delta t = \text{currentTimeMillis} - \text{lastUpdatedTimestamp}$.
- **Reactive Stream Composition**:
  1. `PetScreen` subscribes to `PetViewModel.uiState` using Compose's `collectAsState()` delegate.
  2. `PetViewModel` combines the cold stream from `GetPetStateUseCase.execute()` with local petting state into a hot `StateFlow<PetUiState>` using `.stateIn(SharingStarted.WhileSubscribed(5000))`.
  3. `PetDao.getPetFlow()` establishes an SQLite table observer via Room.
  4. `MoodCalculator.calculateMood()` evaluates prioritized rules against the decayed vitals to determine the companion's expression (e.g., Happy, Content, Thirsty, Hungry).
  5. `PetScreen` recomposes the hardware-accelerated `ModernPetCanvas` and `VitalsRing` in the active mood state.
- **Live decay while open**: `GetPetStateUseCase` combines the Room stream with a 60-second ticker, so the displayed vitals keep decaying on an open screen even when nothing is written. The ticker only runs while the ViewModel collects (`WhileSubscribed(5000)`), so it costs nothing when the screen is gone.
- ⚠ **Known gap (AR-3)**: `MoodCalculator` is always called with `isNightTime = false`, so `Mood.SLEEPING` is never produced.

---

### 4.2 Phase 2: User Micro-Interaction (Quick Log Hydration)

This sequence illustrates a typical user micro-interaction: tapping a 1-tap quick action button (e.g., logging +250ml of water).

```plantuml
@startuml Phase2_User_Micro_Interaction
!theme plain
autonumber
skinparam roundCorner 8
skinparam sequenceMessageAlign center

actor "User" as user

box "Presentation (:wearApp & :core:ui)" #F4F6F9
    participant "PetScreen\n(Compose)" as UI
    participant "PetViewModel" as VM
end box

box "Domain Layer (:core:domain)" #EDF7ED
    participant "LogHabitUseCase" as LogUseCase
    participant "GetPetStateUseCase" as GetUseCase
    participant "PetDecayEngine" as DecayEngine
    participant "MoodCalculator" as MoodCalc
    participant "EvolutionEngine" as EvoEngine
end box

box "Data & Persistence (:core:data)" #FFF8E1
    participant "PetRepositoryImpl" as Repo
    participant "PetDao\n(Room SQLite)" as DAO
end box

user -> UI : Taps WaterActionToken (+250ml)
activate UI
UI -> VM : logWater(250)
activate VM

VM -> LogUseCase : execute(HabitType.Hydration(250))
activate LogUseCase

LogUseCase -> Repo : recordHabit(HabitType.Hydration(250))
activate Repo

Repo -> Repo : getPet()
Repo -> DecayEngine : applyHabit(vitals, Hydration(250), now)
activate DecayEngine
note over DecayEngine
  1. Evaluates Δt decay to now
  2. Applies hydration boost (+20%)
  3. Awards XP (+15)
end note
DecayEngine --> Repo : Pair(updatedVitals, xpEarned)
deactivate DecayEngine

Repo -> EvoEngine : checkEvolution(petWithNewVitals, xpEarned)
activate EvoEngine
EvoEngine --> Repo : evolvedPet (stage, archetype, xp)
deactivate EvoEngine

Repo -> DAO : insertOrUpdate(PetEntity.fromDomain(evolvedPet))
activate DAO
DAO --> Repo : rowId (committed to SQLite)
deactivate DAO
Repo --> LogUseCase : returns evolvedPet
deactivate Repo
LogUseCase --> VM : habit logged
deactivate LogUseCase

== Reactive Recomposition Loop ==

DAO --> Repo : Room table invalidation triggers getPetFlow()
activate Repo
Repo --> GetUseCase : emits updated Pet
deactivate Repo
activate GetUseCase
GetUseCase -> DecayEngine : calculateDecay(vitals, now)
GetUseCase -> MoodCalc : calculateMood(vitals)
activate MoodCalc
MoodCalc --> GetUseCase : returns Mood (e.g. HAPPY)
deactivate MoodCalc
GetUseCase --> VM : emits PetWithMood
deactivate GetUseCase

VM --> UI : emits updated PetUiState.Success
UI -> user : Recomposes VitalsRing & PetCanvas (happy smile, sparkles)
deactivate VM
deactivate UI
@enduml
```

#### Phase 2 Architectural Description
- **Wearable Micro-Interactions**: Wear OS interactions typically last only 3–5 seconds. Tapping `WaterActionToken` immediately triggers tactile haptic feedback on the wrist via `LocalHapticFeedback` and fires a non-blocking asynchronous coroutine in `viewModelScope`.
- **Atomic Engine Mutation & Progression**:
  1. `PetRepositoryImpl.recordHabit()` first calls `PetDecayEngine.applyHabit()`, which brings vitals up to the current millisecond before applying the habit boost (clamping between `0.0` and `100.0`) and computing earned XP.
  2. `EvolutionEngine.checkEvolution()` checks whether the new XP total crosses stage milestones (e.g., Egg $\rightarrow$ Hatchling $\rightarrow$ Child $\rightarrow$ Teen) and determines archetype specializations (e.g., `Swift Strider`, `Zen Ascetic`).
  3. The evolved pet is flattened into a `PetEntity` and persisted via `PetDao.insertOrUpdate()` with `OnConflictStrategy.REPLACE`.
  - **Atomicity**: `recordHabit()` delegates to `PetRepository.updatePet { transform }`, which runs the read, the engine evaluation, and the write inside a single Room `withTransaction { }`. Concurrent writers (UI taps, `PassiveDataService`) are serialized and never overwrite each other's updates.
- **Automatic Reactive Loop Closure**: The write operation does not require manual event dispatching to the UI. Instead, Room's built-in SQLite table invalidation mechanism triggers `PetDao.getPetFlow()`. The fresh entity is automatically emitted through `GetPetStateUseCase`, transformed into a new `PetUiState.Success`, and rendered on the watch face with updated vitals and expressions.

---

### 4.3 Phase 3: Passive Hardware Sensor Ingestion

This sequence illustrates background health tracking via Wear OS Health Services when the user walks or exercises.

```plantuml
@startuml Phase3_Passive_Sensor_Event
!theme plain
autonumber
skinparam roundCorner 8
skinparam sequenceMessageAlign center

actor "Watch Sensors\n(Health Services)" as sensors

box "Health Integration (:core:health)" #EDE7F6
    participant "PassiveDataService" as PassiveService
end box

box "Domain Layer (:core:domain)" #EDF7ED
    participant "IngestPassiveDataUseCase" as Ingest
    participant "PetDecayEngine" as DecayEngine
    participant "EvolutionEngine" as EvoEngine
    participant "GetPetStateUseCase" as GetUseCase
end box

box "Data & Persistence (:core:data)" #FFF8E1
    participant "PassiveSyncRepositoryImpl\n(DataStore)" as Sync
    participant "PetRepositoryImpl" as Repo
    participant "PetDao\n(Room SQLite)" as DAO
end box

box "Presentation (:wearApp & :core:ui)" #F4F6F9
    participant "PetViewModel" as VM
    participant "PetScreen\n(Compose)" as UI
end box

sensors -> PassiveService : onNewDataPointsReceived(dataPoints)
activate PassiveService
note over PassiveService
  Runs in CoroutineScope(SupervisorJob + Dispatchers.IO)
  Builds PassiveDataBatch: latest STEPS_DAILY / FLOORS_DAILY
  total (+ local day) and latest HEART_RATE_BPM sample
end note

PassiveService -> Ingest : execute(batch)
activate Ingest
Ingest -> Sync : consumeDailyTotal("steps_daily", day, total, 200)
activate Sync
note over Sync
  DailyTotalTracker: new delta since
  last consumed total (atomic edit)
end note
Sync --> Ingest : consumed steps (e.g. 400)
deactivate Sync
Ingest -> Sync : consumeDailyTotal("floors_daily", ...) / tryClaimHeartRateAward(...)
Ingest -> Repo : recordHabits([Steps(400), HeartRate(72)])
activate Repo
Repo -> DecayEngine : applyHabit(...) per habit
Repo -> EvoEngine : checkEvolution(...) per habit
Repo -> DAO : insertOrUpdate(PetEntity) [one transaction]
activate DAO
DAO --> Repo : committed
deactivate DAO
deactivate Repo
deactivate Ingest
deactivate PassiveService

== Reactive UI & Surface Update ==

DAO --> Repo : Room emits updated PetEntity over getPetFlow()
activate Repo
Repo --> GetUseCase : emits Pet
deactivate Repo
activate GetUseCase
GetUseCase --> VM : emits PetWithMood
activate VM
VM --> UI : emits updated PetUiState.Success
activate UI
UI -> UI : Increments fitness ring arc in real-time
deactivate UI
deactivate VM
deactivate GetUseCase
@enduml
```

#### Phase 3 Architectural Description
- **Hardware-Hub Passive Monitoring**: By registering a `PassiveListenerService` via `PassiveMonitoringClient`, the companion app offloads sensor polling (accelerometer, step detector, PPG heart rate) to the dedicated low-power sensor hardware hub. The application CPU is only woken when the OS delivers batched sensor data.
- **Cumulative Totals → Deltas**: `*_DAILY` data types report the *running total since local midnight*, and Health Services may deliver the same total more than once. `IngestPassiveDataUseCase` therefore never applies a total directly. It asks `PassiveSyncRepository.consumeDailyTotal()` for the part not yet applied. The pure `DailyTotalTracker` rules are:
  - A new local day (or first-ever reading) counts everything since midnight. A reading from an earlier day is ignored.
  - On the same day only the increase over the already-consumed total counts. A lower total (usually an out-of-order batch) is ignored.
  - Deltas are consumed in whole units (200 steps = 1 XP unit, 10 floors). The remainder carries over, so small batches never lose progress to integer rounding.
  - Baselines live in DataStore (`passive_sync`), not Room (see DD-11). `DataStore.edit` serializes concurrent batches. Deltas are consumed *before* the pet is updated: a crash in between under-counts rather than double counts.
- **Biometric to Companion Mapping**:
  - `DataType.STEPS_DAILY` delta $\rightarrow$ `HabitType.Steps` (restores fitness vital and awards XP proportional to step volume).
  - `DataType.FLOORS_DAILY` delta $\rightarrow$ `HabitType.Steps` bonus (20 step equivalents per floor, for climbing effort).
  - `DataType.HEART_RATE_BPM` latest sample $\rightarrow$ `HabitType.HeartRate` (resting vs active cardio zones), awarded **at most once per 30 minutes**.
  - `DISTANCE_DAILY` and `CALORIES_DAILY` are **not** consumed. Distance comes from the same walking as steps, and daily calories include basal burn, so neither reflects extra activity.
  - All habits from one batch are applied with `PetRepository.recordHabits()` in a single transaction.
- **Unified Single Source of Truth**: Because `PassiveDataService` writes directly to Room SQLite via `PetRepositoryImpl`, the active `PetScreen` automatically receives the updated vitals through its database observation stream.
  - **Pull-based surfaces**: Tiles cannot observe the `Flow`. `AppContainer` therefore wraps the repository in `NotifyingPetRepository`, which calls `TileService.getUpdater(context).requestUpdate(PetStatusTileService::class.java)` after every committed write, from the UI or the sensors. The tile also declares a 10-minute freshness interval so decay shows even without writes. Complications will hook into the same callback once a provider exists.

---

### 4.4 Additional Sequence Diagrams for Future Documentation

To keep the primary diagrams manageable and focused on the core runtime loop, the following sequence diagrams represent other operational scenarios that can be added as dedicated reference flows:

1. **Runtime Permission Flow & Graceful Degradation**:
   - `MainActivity` $\rightarrow$ `PermissionViewModel.checkPermissions()` $\rightarrow$ `PermissionScreen` onboarding $\rightarrow$ system permission dialog $\rightarrow$ user denial $\rightarrow$ `PermissionState.Denied` $\rightarrow$ degraded `PetScreen` displaying the `⚠ Enable sensors` chip $\rightarrow$ deep-linking to system Settings and recovery on `ON_RESUME`.
2. **Boot Re-registration (`PassiveRegistrationWorker`)**:
   - `BOOT_COMPLETED` $ightarrow$ `BootCompletedReceiver` $ightarrow$ unique one-time `PassiveRegistrationWorker` $ightarrow$ `HealthServicesManager.ensureRegistered(force = true)` $ightarrow$ `Result.retry()` on failure.
3. **Carousel Tile Request & Rendering (`PetStatusTileService`)**:
   - User swipes to Tile on watch face $\rightarrow$ system invokes `TileService.onTileRequest()` $\rightarrow$ coroutine `future { }` query to `PetRepository` (no blocking) $\rightarrow$ ProtoLayout element tree assembly $\rightarrow$ 10-minute cache freshness declaration $\rightarrow$ `ListenableFuture<Tile>` delivery.
4. **Milestone Evolution & Archetype Specialization**:
   - Cumulative XP crosses stage threshold (e.g. 750 XP for `TEEN`) $\rightarrow$ `EvolutionEngine.checkEvolution()` inspects dominant vitals (e.g. `fitness > 80f`) $\rightarrow$ locks in specialized persona (`CARDIO_RUNNER`) $\rightarrow$ triggers celebratory haptic pattern and visual evolution feedback.
5. **Sensor Capability Negotiation & Hardware Fallback**:
   - `HealthServicesManager.tryRegisterPassiveDataService()` queries `passiveMonitoringClient.getCapabilitiesAsync()` $\rightarrow$ computes intersection with `desiredDataTypes` $\rightarrow$ registers listener only for hardware-supported data types, gracefully skipping missing sensors (e.g. watches without PPG heart rate hardware).

---

## 5. Persistence & Local Storage Architecture (Room & SQLite)

### 5.1 Architectural Overview & Offline-First Strategy

On Wear OS smartwatches, network connectivity is intermittent—wearers leave their phones behind during workouts, Wi-Fi radios sleep to preserve the ~300–400 mAh battery, and cellular (LTE) hardware is either absent or power-prohibitive. Consequently, **Health Companion** employs an **offline-first local persistence architecture**:

1. **Single Source of Truth**: The local SQLite database (`health_companion.db`) is the authoritative source for companion state, vitals, XP, and habit records. No surface or component maintains a diverging in-memory state.
2. **Reactive Observation**: In-app UI screens (`PetScreen`) subscribe directly to the database via reactive Kotlin `Flow` streams. Any write to the database (whether initiated by a button tap or background sensor event) immediately and automatically updates them. System surfaces (Tiles, Complications) are pull-based and are asked to refresh after every committed write by the `NotifyingPetRepository` decorator.
3. **Microscopic Disk Footprint**: Companion state is stored in a normalized, compact table (`pets`). A single primary record (`id = "companion_primary"`) occupies less than 4 KB of flash storage, ensuring sub-millisecond query latency and zero disk pressure on wearable NAND storage.

---

### 5.2 Core Persistence Concepts: Kotlin/Android vs. C++

For developers transitioning from C++, Android's persistence terminology maps directly to familiar native database concepts:

| Term | Android / Kotlin Definition | C++ Equivalent / Native Parallel | Role in Health Companion |
| :--- | :--- | :--- | :--- |
| **SQLite** | An embedded, serverless, transactional SQL engine bundled in the Android OS userland (written in pure C). Operates directly on a local binary file on the device filesystem. | Linking `sqlite3.c` / `libsqlite3.so` directly into a C++ process and calling the raw C API (`sqlite3_open()`, `sqlite3_step()`). | Underpins all persistent storage. The database file is located at `/data/data/com.healthcompanion.wear/databases/health_companion.db`. |
| **ORM** *(Object-Relational Mapping)* | An architectural technique that automatically bridges the impedance mismatch between relational tables (flat scalar columns: `REAL`, `INTEGER`, `TEXT`) and object-oriented memory graphs (nested domain classes, value objects, and enum types). | C++ compile-time ORM libraries such as [`sqlite_orm`](https://github.com/fnc12/sqlite_orm) or [ODB](https://www.codesynthesis.com/products/odb/), or manual struct serialization mapping. | Eliminates manual `Cursor` indexing (e.g. `cursor.getFloat(4)`). Translates relational rows directly to/from `PetEntity`. |
| **Room** | Google's official Android persistence library built on SQLite. It provides compile-time SQL verification, Kotlin Symbol Processing (KSP) code generation, schema migration management, and native Coroutine/Flow streaming. | A compile-time code-generation tool (like Protobuf / FlatBuffers compilers or C++ template metaprogramming) that verifies SQL queries during compilation, generates prepared statements, and handles object deserialization with zero reflection. | Acts as the persistence abstraction layer in `:core:data`. Generates concrete SQLite implementations for DAOs at build time. |
| **DAO** *(Data Access Object)* | An architectural design pattern isolating database interactions behind an abstract interface. The developer declares queries and mutations using annotations (`@Query`, `@Insert`, `@Update`), and the framework generates the underlying SQL execution code. | An abstract C++ interface (`class IPetDao { virtual PetEntity getPet() = 0; ... }`) whose concrete implementation is generated by a preprocessor or code generator. | `PetDao` interface defines all SQL CRUD operations, completely decoupling persistence mechanisms from domain business logic. |

---

### 5.3 The Room Architectural Triad

Room organizes local persistence into three tightly coupled architectural components:

```plantuml
@startuml Room_Persistence_Architecture
!theme plain
allowmixing
skinparam componentStyle rectangle
skinparam roundCorner 8

package "Domain Layer (:core:model & :core:domain)" {
    class Pet <<Domain Model>> {
        +id: String
        +name: String
        +stage: EvolutionStage
        +archetype: PetArchetype
        +vitals: Vitals
        +experiencePoints: Int
        +bornTimestamp: Long
    }
    class Vitals <<Value Object>> {
        +energy: Float
        +hunger: Float
        +hydration: Float
        +fitness: Float
        +happiness: Float
        +lastUpdatedTimestamp: Long
    }
    Pet *-- Vitals
    interface PetRepository
}

package "Persistence Layer (:core:data)" {
    class PetRepositoryImpl
    interface PetDao <<@Dao>> {
        +getPetFlow(petId): Flow<PetEntity?>
        +getPet(petId): PetEntity?
        +insertOrUpdate(entity): Long
        +insertIfAbsent(entity): Long
        +update(entity): Int
    }
    class PetEntity <<@Entity(tableName = "pets")>> {
        +id: String [PK]
        +name: String
        +stage: String
        +archetype: String
        +energy: Float
        +hunger: Float
        +hydration: Float
        +fitness: Float
        +happiness: Float
        +lastUpdatedTimestamp: Long
        +experiencePoints: Int
        +bornTimestamp: Long
        --
        +toDomain(): Pet
        +{static} fromDomain(pet): PetEntity
    }
    abstract class CompanionDatabase <<@Database>> {
        +{abstract} petDao(): PetDao
        +{static} getInstance(context): CompanionDatabase
    }
}

database "SQLite Flash Storage\n(health_companion.db)" as SQLite

PetRepository <|.. PetRepositoryImpl : implements
PetRepositoryImpl --> PetDao : queries / writes
PetRepositoryImpl --> CompanionDatabase : withTransaction { }
PetRepositoryImpl ..> PetEntity : maps to/from domain
PetRepositoryImpl --> Pet : streams Flow<Pet>
PetDao ..> PetEntity : returns / receives
CompanionDatabase --> PetDao : provides factory
CompanionDatabase --> SQLite : connection pooling & migrations
PetDao --> SQLite : executes prepared SQL on "pets" table
@enduml
```

#### 1. The Entity: [`PetEntity.kt`](../core/data/src/main/java/com/healthcompanion/core/data/db/entity/PetEntity.kt)
An `@Entity` class represents a table schema in SQLite. 
- **Flattening Invariant**: The domain model `Pet` contains a nested `Vitals` value object and typed Kotlin enums (`EvolutionStage`, `PetArchetype`). Relational tables cannot natively store nested objects, so `PetEntity` flattens these fields into scalar columns:
  - `vitals.energy` $\rightarrow$ `energy: Float` (`REAL`)
  - `vitals.lastUpdatedTimestamp` $\rightarrow$ `lastUpdatedTimestamp: Long` (`INTEGER`)
  - `stage: EvolutionStage` $\rightarrow$ `stage: String` (`TEXT`)
- **Domain Purity Boundary**: `PetEntity` implements `toDomain(): Pet` and `companion object fromDomain(pet: Pet): PetEntity`. This ensures the domain layer (`:core:model`, `:core:domain`) remains 100% pure Kotlin with zero annotations or imports from Android or Room.

#### 2. The DAO: [`PetDao.kt`](../core/data/src/main/java/com/healthcompanion/core/data/db/dao/PetDao.kt)
The `@Dao` interface declares SQL operations:
- **Reactive Streaming (`getPetFlow`)**:
  ```kotlin
  @Query("SELECT * FROM pets WHERE id = :petId LIMIT 1")
  fun getPetFlow(petId: String = "companion_primary"): Flow<PetEntity?>
  ```
  Returns a cold `Flow`. When collected, Room executes the query on `Dispatchers.IO` and registers an internal table observer.
- **One-Shot Query (`getPet`)**:
  ```kotlin
  @Query("SELECT * FROM pets WHERE id = :petId LIMIT 1")
  suspend fun getPet(petId: String = "companion_primary"): PetEntity?
  ```
  Suspends the calling coroutine until the query completes asynchronously on background I/O.
- **Upsert Mutation (`insertOrUpdate`)**:
  ```kotlin
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdate(pet: PetEntity): Long
  ```
  Executes an atomic SQLite `INSERT OR REPLACE INTO pets` statement, returning the inserted row ID.
- **Seed Mutation (`insertIfAbsent`)**:
  ```kotlin
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertIfAbsent(pet: PetEntity): Long
  ```
  Used only to seed the default companion. `INSERT OR IGNORE` guarantees seeding can never overwrite an existing pet.

> **Transactions**: DAO calls are individually atomic, but a read followed by a write is not. `PetRepositoryImpl` therefore performs every mutation through `updatePet { transform }`, which wraps the read → transform → write sequence in `CompanionDatabase.withTransaction { }`. Never read the pet and write it back outside that method.

#### 3. The Database Provider: [`CompanionDatabase.kt`](../core/data/src/main/java/com/healthcompanion/core/data/db/CompanionDatabase.kt)
Extends `RoomDatabase`, serving as the connection pool manager and factory for DAOs:
- **Thread-Safe Singleton**: Implements the classic double-checked locking idiom with an `@Volatile private var INSTANCE` field (equivalent to C++ memory fences / `std::atomic` and `std::call_once`):
  ```kotlin
  fun getInstance(context: Context): CompanionDatabase {
      return INSTANCE ?: synchronized(this) {
          INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
      }
  }
  ```
- **Context Leak Prevention**: Always invokes `context.applicationContext` so that short-lived Wear activities (`MainActivity`) are never retained in static memory when closed or rotated.

#### 4. Schema Versioning & Migrations
Losing the pet is the worst possible failure for a virtual-pet app, so the schema is managed explicitly:
- **Exported schemas**: `exportSchema = true`. KSP writes `core/data/schemas/<db class>/<version>.json`, and these files are committed.
- **No destructive fallback on upgrade**: every upgrade must go through [`ALL_MIGRATIONS`](../core/data/src/main/java/com/healthcompanion/core/data/db/migrations/Migrations.kt). A missing migration crashes on open rather than silently deleting the pet. Debuggable builds only fall back destructively on a *downgrade* (installing an older branch build).
- **Drift guard in CI**: the build re-exports the current schema JSON. CI fails if `core/data/schemas/` differs from the committed files, which catches an entity changed without a version bump and migration.
- **Migration tests**: `CompanionDatabaseMigrationTest` (Robolectric + `MigrationTestHelper`) creates a database from the exported JSON and opens it with the current entities and migrations. The schema JSON is added to the *unit-test* assets only and is not shipped in the APK.
- **Tolerant loading**: `PetEntity.toDomain()` never throws on a bad row. It clamps vitals (including `NaN`), clamps negative XP, re-derives an unknown stage from XP, and falls back to `BALANCED` for an unknown archetype. `PetDecayEngine.applyHabit` clamps both bounds via the shared `Float.toVitalRange()`.

**Changing the schema** (checklist, also in `Migrations.kt`):
1. Bump `CompanionDatabase.VERSION` and build. Room exports the new `<version>.json`.
2. Add a `Migration(old, new)` (or `@AutoMigration`) to `ALL_MIGRATIONS`.
3. Add a migration test from the previous version with representative data.
4. Commit the new schema JSON together with the change.

---

### 5.4 Reactive Invalidation Tracker: How Room Powers the UI

The reactive UI update loop in Health Companion works through Room's built-in **`InvalidationTracker`**:

```
[Write Operation]
Sensor Event / User Tap
       │
       ▼
PetDao.insertOrUpdate(entity) ──► SQLite Engine executes UPDATE
                                          │
                                          ▼
                               Room InvalidationTracker
                               (Detects table modification)
                                          │
                                          ▼
                               Re-runs registered SELECT query
                                          │
                                          ▼
PetDao.getPetFlow() ─────────────► Emits fresh PetEntity down Flow
                                          │
                                          ▼
PetRepositoryImpl.getPetFlow() ──► entity.toDomain()
                                          │
                                          ▼
GetPetStateUseCase.execute() ────► Applies Δt decay + calculates Mood
                                          │
                                          ▼
PetViewModel.uiState ────────────► Emits PetUiState.Success
                                          │
                                          ▼
Compose PetScreen ───────────────► Recomposes ModernPetCanvas & VitalsRing
```

#### Under the Hood (C++ Parallel):
1. When `getPetFlow()` is first collected, Room registers a SQLite update hook (similar to `sqlite3_update_hook()` in native C/C++).
2. When any thread modifies the `pets` table (via `insertOrUpdate()` or `update()`), SQLite notifies Room's invalidation tracker.
3. Room's tracker batches table changes and schedules a re-query on a background I/O dispatcher thread.
4. The fresh query result is emitted into the coroutine channel/Flow.
5. Subscribers receive the updated data automatically without requiring manual pub/sub event buses, BroadcastReceivers, or callback registration.

---

### 5.5 Table Schema & Type Mapping

| Domain Field (`Pet`) | Domain Type | Entity Field (`PetEntity`) | SQLite Column | SQLite Affinity | Nullable | Description |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `String` | `id` | `id` | `TEXT` | No (PK) | Companion identifier (`"companion_primary"`). |
| `name` | `String` | `name` | `name` | `TEXT` | No | User-assigned companion name (e.g. "Kairo"). |
| `stage` | `EvolutionStage` | `stage` | `stage` | `TEXT` | No | Enum name (`EGG`, `HATCHLING`, `CHILD`, etc.). |
| `archetype` | `PetArchetype` | `archetype` | `archetype` | `TEXT` | No | Enum name (`BALANCED`, `SWIFT_STRIDER`, etc.). |
| `vitals.energy` | `Float` | `energy` | `energy` | `REAL` | No | Energy level `[0.0, 100.0]`. |
| `vitals.hunger` | `Float` | `hunger` | `hunger` | `REAL` | No | Hunger level `[0.0, 100.0]`. |
| `vitals.hydration` | `Float` | `hydration` | `hydration` | `REAL` | No | Hydration level `[0.0, 100.0]`. |
| `vitals.fitness` | `Float` | `fitness` | `fitness` | `REAL` | No | Fitness level `[0.0, 100.0]`. |
| `vitals.happiness` | `Float` | `happiness` | `happiness` | `REAL` | No | Happiness level `[0.0, 100.0]`. |
| `vitals.lastUpdatedTimestamp` | `Long` | `lastUpdatedTimestamp` | `lastUpdatedTimestamp` | `INTEGER` | No | Epoch millisecond timestamp of last write. |
| `experiencePoints` | `Int` | `experiencePoints` | `experiencePoints` | `INTEGER` | No | Cumulative XP earned towards evolution. |
| `bornTimestamp` | `Long` | `bornTimestamp` | `bornTimestamp` | `INTEGER` | No | Epoch millisecond timestamp of pet birth. |

---

## 6. Health Mirroring Game Engine

### Mathematical Time-Delta Decay
To prevent battery drain on Wear OS watches (~300–400 mAh), the game does **not** rely on continuous background ticking. Instead, decay is calculated mathematically on demand:

$$\text{decay} = \frac{\text{currentTime} - \text{lastUpdatedTimestamp}}{3600000} \times \text{decayRatePerHour}$$

| Vital | Range | Hourly Decay | Real-World Restoration Trigger | Effect on Companion |
| :--- | :--- | :--- | :--- | :--- |
| **Fitness / Vitality** | 0–100 | $1.5\% / \text{hr}$ | Step and floor deltas, and rate-limited heart rate via `PassiveMonitoringClient`; active workouts *(planned)* | High fitness triggers athletic evolutions and energetic animations |
| **Hydration** | 0–100 | $3.0\% / \text{hr}$ | $+250\text{ml}$ quick tap on watch / Tile | Thirsty pet appears droopy; sends gentle haptic reminder |
| **Hunger / Nutrition**| 0–100 | $2.5\% / \text{hr}$ | Healthy Meal ($+30\%$) / Snack ($+20\%$) | Starving pet refuses to play; well-fed pet smiles and dances |
| **Energy** | 0–100 | $2.0\% / \text{hr}$ | Night sleep and rest periods *(planned — no restoration source exists yet, see AR-3)* | Sleepy pet yawns and sleeps when watch is in ambient mode |
| **Happiness**| 0–100 | $2.0\% / \text{hr}$ | Weighted vitals + direct petting/rotary play | Drops if any vital < 20; unlocks tricks, dialogue bubbles, XP |

### Evolution & Archetypes
- **Evolution Stages**: `EGG` (Lv 0) $\rightarrow$ `HATCHLING` (Lv 1, 100 XP) $\rightarrow$ `CHILD` (Lv 2, 300 XP) $\rightarrow$ `TEEN` (Lv 3, 750 XP) $\rightarrow$ `ADULT` (Lv 4, 1500 XP) $\rightarrow$ `ANCIENT_SAGE` (Lv 5, 3000 XP).
- **Habit-Based Archetypes**: At `TEEN` stage, habits determine the pet's persona:
  - *Swift Strider (Cardio)*: High step count consistency.
  - *Zen Ascetic (Mindful)*: High sleep quality and perfect hydration.
  - *Mighty Titan (Strength)*: High workout frequency.
  - *Balanced Soul (Harmony)*: Balanced habits across all vitals.
- ⚠ **Current implementation (AR-3)**: `EvolutionEngine` assigns the archetype once, from a snapshot of vitals at the moment `TEEN` is reached, not from habit consistency. `IRON_BEAST` (Mighty Titan) is never assigned. Consistency-based archetypes require a persisted habit event log, which does not exist yet.

---

## 7. Runtime Permissions & Degraded Mode

### Required Permissions

Runtime permissions are resolved per API level in [`HealthPermissions.kt`](../core/health/src/main/java/com/healthcompanion/core/health/HealthPermissions.kt). Only `ACTIVITY_RECOGNITION` is *core*; heart rate is optional.

| Permission | API levels | Protection Level | Unlocks | Role |
| :--- | :--- | :--- | :--- | :--- |
| `ACTIVITY_RECOGNITION` | all | **Dangerous** | `STEPS_DAILY`, `FLOORS_DAILY` | **Core**. Without it the app runs in degraded mode |
| `BODY_SENSORS` | ≤ 35 (`maxSdkVersion`) | **Dangerous** | `HEART_RATE_BPM` (foreground) | Optional |
| `health.READ_HEART_RATE` | ≥ 36 (Wear OS 6) | **Dangerous** | `HEART_RATE_BPM` (foreground) | Optional. Replaces `BODY_SENSORS` for apps targeting API 36 |
| `BODY_SENSORS_BACKGROUND` | 33–35 (`maxSdkVersion`) | **Dangerous** | Passive heart rate in the background | Optional. Requested separately after the foreground grant |
| `health.READ_HEALTH_DATA_IN_BACKGROUND` | ≥ 36 | **Dangerous** | Passive heart rate in the background | Optional. Requested separately after the foreground grant |
| `WAKE_LOCK` | all | Normal | Not declared by the app. Merged in from WorkManager's own manifest (used for boot re-registration) | — |
| `RECEIVE_BOOT_COMPLETED` | all | Normal | `BootCompletedReceiver` re-registers passive monitoring after reboot | — |
| `VIBRATE` | all | Normal | Haptic feedback on petting, evolution, and goal completion | — |

Heart rate is registered only when **both** its foreground and (API 33+) background permission are granted, because passive delivery happens in the background.

### Why Runtime Permissions Are Needed

On Android 6.0+ (API 23+), `BODY_SENSORS` and `ACTIVITY_RECOGNITION` are classified as **dangerous permissions** — the OS will not grant them automatically at install time. The app must explicitly request them at runtime via a system dialog, and the user can deny or revoke them at any time through system Settings.

On Wear OS specifically:
- The system permission dialog is shown directly on the watch (no phone companion involved, since this is a standalone app).
- After the user taps **"Deny"** twice for the same permission, the system sets a **"Don't ask again"** flag. Subsequent calls to `requestPermissions()` will return an immediate denial without showing a dialog. The only recovery path is for the user to manually toggle the permission in **Settings → Apps → Health Companion → Permissions**.
- `shouldShowRequestPermissionRationale()` returns `false` in two cases: (1) the permission has never been requested, and (2) the user selected "Don't ask again." We distinguish these by tracking whether a request has been launched.

### Design Philosophy: Graceful Degradation

The app follows a **degraded mode** strategy rather than blocking the user behind a permission wall:

1. **First launch**: A lightweight `PermissionScreen` explains why sensor access is needed and presents an "Allow" button. It requests `ACTIVITY_RECOGNITION` and the foreground heart-rate permission together. If heart rate was granted on API 33+, a second system dialog asks for background heart-rate access, once per session. These are the only times the app proactively interrupts the user.
2. **Activity permission granted**: The app enters **full mode**. Steps and floors are registered. Heart rate is registered too if it is fully permitted, but it is optional and missing heart rate never shows the chip.
3. **Activity permission denied**: The app enters **degraded mode**. `PetScreen` renders normally with all manual features working (water, meals, petting), but a subtle `⚠ Enable sensors` chip appears above the companion name. Tapping it opens the app's system settings. Heart rate can still be registered on its own if granted.
4. **Permissions changed in Settings**: On `ON_RESUME`, `MainActivity` re-checks permissions and re-syncs the registration, which is a no-op if nothing changed.

This ensures the app is **always usable** — the virtual pet can still be fed, hydrated, and petted without sensor data. Sensor permissions enhance the experience but are not a hard gate.

### Passive Registration Lifecycle

Health Services **forgets passive registrations on reboot**, and registering is an IPC call that may be slow at boot. `HealthServicesManager.ensureRegistered()` is therefore the single, idempotent entry point:

- **What is registered**: `PassiveSensorPlanner.permittedSensors()` (from the current grants), intersected with device capabilities. If nothing is permitted, the listener is cleared.
- **Idempotency**: the last successful registration is stored (`passive_registration` SharedPreferences) as a key made of the permitted sensors plus `Settings.Global.BOOT_COUNT`. If the key is unchanged, the call returns immediately without contacting Health Services. A new boot or a permission change yields a new key. A process-wide `Mutex` serializes concurrent callers.
- **Callers**:
  - `HealthCompanionApp.onCreate()` on every process start (cheap).
  - `PermissionViewModel` after permission results and on resume.
  - `BootCompletedReceiver` → `PassiveRegistrationWorker` (WorkManager, `force = true`), as the Health Services docs recommend, because registration at boot can exceed a receiver's time limit.

### Implementation Architecture

```
MainActivity (ON_RESUME)
  └── PermissionViewModel
        ├── checkPermissions() ←── HealthPermissions.hasCorePermission(context)
        ├── onPermissionResult(grants)
        │     ├── activity granted → PermissionState.Granted
        │     └── activity denied  → PermissionState.Denied (degraded mode)
        ├── consumeBackgroundHeartRateRequest() → optional 2nd dialog (API 33+)
        ├── every check/result → HealthServicesManager.ensureRegistered()
        └── permissionState: StateFlow<PermissionState>
              └── observed by MainActivity setContent { when(permState) { ... } }

BOOT_COMPLETED → BootCompletedReceiver → PassiveRegistrationWorker → ensureRegistered(force = true)
```

| File | Responsibility |
| :--- | :--- |
| [`HealthPermissions.kt`](../core/health/src/main/java/com/healthcompanion/core/health/HealthPermissions.kt) | Per-API-level permission names, core/heart-rate checks, permitted sensors |
| [`PassiveSensor.kt`](../core/health/src/main/java/com/healthcompanion/core/health/PassiveSensor.kt) | Pure sensor ↔ permission rules and registration key (`PassiveSensorPlanner`) |
| [`HealthServicesManager.kt`](../core/health/src/main/java/com/healthcompanion/core/health/HealthServicesManager.kt) | Idempotent `ensureRegistered()` against Health Services |
| [`PassiveRegistrationWorker.kt`](../core/health/src/main/java/com/healthcompanion/core/health/PassiveRegistrationWorker.kt) | Boot receiver and WorkManager re-registration job |
| [`PermissionState.kt`](../wearApp/src/main/java/com/healthcompanion/wear/presentation/permission/PermissionState.kt) | Sealed interface: `Checking`, `Required`, `Granted`, `Denied` |
| [`PermissionViewModel.kt`](../wearApp/src/main/java/com/healthcompanion/wear/presentation/permission/PermissionViewModel.kt) | Orchestrates permission checks, result callbacks, and Health Services registration |
| [`PermissionScreen.kt`](../wearApp/src/main/java/com/healthcompanion/wear/presentation/permission/PermissionScreen.kt) | Compact onboarding UI with sensor icon, explanation, and "Allow" button |
| [`MainActivity.kt`](../wearApp/src/main/java/com/healthcompanion/wear/MainActivity.kt) | Hosts the foreground (`RequestMultiplePermissions`) and background heart-rate (`RequestPermission`) launchers, and routes between screens |

---

## 8. Wear OS Performance & Battery Best Practices
- **Passive Health Services**: Using `PassiveMonitoringClient` delegates sensor polling to the OS hardware hub, consuming near-zero extra battery.
- **Pure Vector UI**: All companion graphics are drawn via hardware-accelerated Compose Canvas paths, eliminating large bitmap assets from memory.
- **Ambient Mode Compatible**: Pure black OLED backgrounds (`#0A0E14`) maximize battery preservation.
- **Micro-Interactions**: Wear OS users interact in 3-to-5-second bursts. The Carousel Tile and 1-tap quick action buttons allow logging without deep navigation.

---

## 9. Architecture Review Findings (2026-09-26)

A review of the implementation against this document. The overall structure is sound: layered modules, a pure-Kotlin domain, Room as the single source of truth, and decay-on-read instead of background ticking. The findings below are correctness bugs or gaps between the design and the code. Each finding has an ID so it can be tracked, fixed, and closed one at a time. The recommended order of work is in [ROADMAP.md → Phase 2a](ROADMAP.md#phase-2a-architecture-review-remediation).

| ID | Severity | Summary |
| :--- | :--- | :--- |
| AR-1 | ✅ Resolved | Cumulative daily sensor totals are applied as deltas |
| AR-2 | ✅ Resolved | Pet updates are non-atomic read-modify-write (lost updates) |
| AR-3 | 🟠 High | Some vitals cannot recover; sleep, night mood, and consistency-based archetypes are unimplemented |
| AR-4 | ✅ Resolved | Tiles, complications, and the open screen do not update reactively |
| AR-5 | ✅ Resolved | `PetDecayWorker` is redundant under decay-on-read |
| AR-6 | ✅ Resolved | Permission degradation is all-or-nothing; API 36 permission model; implicit boot re-registration |
| AR-7 | ✅ Resolved | Layering violation (`:core:health` → `:core:data`) and fragmented dependency wiring |
| AR-8 | ✅ Resolved | Destructive migrations can delete the pet; invalid rows crash the app |

### AR-1 — Cumulative daily totals treated as deltas ✅ Resolved
- **Resolution**: `PassiveDataService` now only parses batches. `IngestPassiveDataUseCase` consumes deltas via the pure `DailyTotalTracker` (DataStore-backed `PassiveSyncRepositoryImpl`) and applies all resulting habits in one `recordHabits()` transaction. Out-of-order and same-day decreasing totals are ignored rather than re-anchored. Distance and daily calories are no longer registered. Heart-rate awards are limited to one per 30 minutes. See [§4.3](#43-phase-3-passive-hardware-sensor-ingestion). Covered by `DailyTotalTrackerTest`, `IngestPassiveDataUseCaseTest` and `PassiveSyncRepositoryImplTest`.
- **Where**: [`PassiveDataService.kt`](../core/health/src/main/java/com/healthcompanion/core/health/PassiveDataService.kt), `PetDecayEngine.applyHabit()`.
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

### AR-3 — Unrecoverable vitals and unimplemented game inputs
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
- **Where**: [`PetStatusTileService.kt`](../wearApp/src/main/java/com/healthcompanion/wear/tiles/PetStatusTileService.kt), `GetPetStateUseCase`.
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

  See [§7](#7-runtime-permissions--degraded-mode) and DD-22 to DD-25. ⚠ The permission dialogs and the boot re-registration are unverified on a Wear OS 6 image or device.
- **Where**: [`HealthPermissions.kt`](../core/health/src/main/java/com/healthcompanion/core/health/HealthPermissions.kt), [`HealthServicesManager.kt`](../core/health/src/main/java/com/healthcompanion/core/health/HealthServicesManager.kt), `HealthCompanionApp.onCreate()`, `AndroidManifest.xml`.
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
- **Resolution**: Schema export is on and `1.json` is committed. The destructive fallback is removed for upgrades (debuggable builds keep it for downgrades only). Migrations are registered in `ALL_MIGRATIONS`. CI fails on uncommitted schema changes. `toDomain()` is tolerant of bad rows, and `applyHabit` clamps both bounds. See [§5.3 Schema Versioning & Migrations](#4-schema-versioning--migrations) and DD-18 to DD-21. Covered by `CompanionDatabaseMigrationTest`, `PetEntityTest` and `PetDecayEngineTest`.
- **Where**: [`CompanionDatabase.kt`](../core/data/src/main/java/com/healthcompanion/core/data/db/CompanionDatabase.kt), [`Vitals.kt`](../core/model/src/main/java/com/healthcompanion/core/model/Vitals.kt), `PetEntity.toDomain()`.
- **Problems**:
  - With `exportSchema = false` and `fallbackToDestructiveMigration(dropAllTables = true)`, any schema change deletes the pet.
  - `Vitals` uses `require()` to validate ranges, and `toDomain()` calls it with no guard. A single out-of-range value (e.g. a negative habit amount, since `applyHabit` boosts are only clamped at the top) throws inside `getPetFlow()` and crashes the app every time the pet is loaded.
- **Target design**:
  - Enable `exportSchema = true`, commit the schemas, and write explicit `Migration`s. Keep destructive fallback for debug builds at most.
  - Clamp both bounds in `applyHabit` (`coerceIn(0f, 100f)`) and have `toDomain()` coerce values so they load safely rather than throwing.
