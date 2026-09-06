# Architecture & System Design

## Overview
**Health Companion** is a standalone Wear OS virtual pet app inspired by the classic Tamagotchi toy, reimagined for modern smartwatches. The companion's growth, energy, and happiness directly mirror the user's real-world health habits—including physical activity, step goals, hydration, nutrition, and rest.

---

## 1. System Interaction Flow

```plantuml
@startuml System_Interaction_Flow
!theme plain
skinparam componentStyle rectangle
skinparam roundCorner 8

package "Real-World Health Activities" as RealWorld {
    [Steps & Walking\n(Health Services)] as Steps
    [Active Workouts\n(ExerciseClient)] as Workouts
    [Hydration Log\n(+250ml quick-tap)] as Water
    [Nutrition Log\n(Healthy Meal / Snack)] as Food
    [Sleep & Rest\n(Passive Monitoring)] as Sleep
}

package "Tamagotchi Game Engine" as PetEngine {
    [Time-Delta Decay Engine] as Decay
    [Vitals State\n(Energy, Hunger, Hydration, Fitness, Mood)] as Vitals
    [Evolution & Archetype Rules] as Evo
}

package "Wear OS User Surfaces" as WearSurfaces {
    [Main Wear Compose App\n(Interactions, Petting, Stats)] as MainApp
    [Wear OS Quick Tile\n(1-Swipe Status & Water Log)] as Tile
    [Watch Face Complication\n(Pet Mood Icon / Meter)] as Complication
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
| **Health & Sensors** | Health Services for Wear OS (`androidx.health:health-services-client`) | Passive step counting via `PassiveMonitoringClient` and `PassiveListenerService`. |
| **Glance Surfaces** | AndroidX Wear Tiles & ProtoLayout | Instant-access carousel card with 1-tap micro-interactions. |
| **Watch Face Integration** | AndroidX WatchFace Complications | Live mood and vital progress complications on third-party watch faces. |
| **Architecture Pattern** | Clean Architecture + MVI (UDF) | Unidirectional Data Flow with immutable `StateFlow<PetUiState>`. |
| **Persistence** | Jetpack Room SQLite Database | Offline-first local storage for pet vitals, health logs, and history. |
| **Preferences** | Jetpack DataStore Preferences | Lightweight key-value storage for user settings and daily goals. |
| **Background Processing**| Jetpack WorkManager (`work-runtime-ktx`) | Battery-efficient periodic maintenance and midnight daily resets. |

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
coreHealth --> coreDomain : Dispatches Habit Events
coreHealth --> coreData : Records Sensor Updates
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
   - Wear OS surfaces: `PetStatusTileService` (Carousel Tile) and Watch Face Complications.

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
   - Use cases: `GetPetStateUseCase`, `LogHabitUseCase`, `CalculateDecayUseCase`.

4. **[`:core:data`](../core/data)**:
   - Offline-first persistence via Jetpack Room (`CompanionDatabase`, `PetDao`, `PetEntity`).
   - `PetRepositoryImpl`: Coordinates between SQLite database and domain engine.
   - `PetDecayWorker`: Background WorkManager worker for periodic health maintenance.

5. **[`:core:health`](../core/health)**:
   - Wraps Wear OS **Health Services API** (`androidx.health:health-services-client`).
   - `PassiveMonitoringClient`: Subscribes to daily step counts and passive goals without active battery drain.
   - `PassiveDataService`: Listens for OS-batched step updates and applies them to the companion.

6. **[`:core:model`](../core/model)**:
   - Pure domain models (`Pet`, `Vitals`, `Mood`, `HabitType`, `EvolutionStage`, `PetArchetype`). Zero Android UI dependencies.

---

## 4. Health Mirroring Game Engine

### Mathematical Time-Delta Decay
To prevent battery drain on Wear OS watches (~300–400 mAh), the game does **not** rely on continuous background ticking. Instead, decay is calculated mathematically on demand:

$$\text{decay} = \frac{\text{currentTime} - \text{lastUpdatedTimestamp}}{3600000} \times \text{decayRatePerHour}$$

| Vital | Range | Hourly Decay | Real-World Restoration Trigger | Effect on Companion |
| :--- | :--- | :--- | :--- | :--- |
| **Fitness / Vitality** | 0–100 | $1.5\% / \text{hr}$ | Step tracking via `PassiveMonitoringClient` & active workouts | High fitness triggers athletic evolutions and energetic animations |
| **Hydration** | 0–100 | $3.0\% / \text{hr}$ | $+250\text{ml}$ quick tap on watch / Tile | Thirsty pet appears droopy; sends gentle haptic reminder |
| **Hunger / Nutrition**| 0–100 | $2.5\% / \text{hr}$ | Healthy Meal ($+30\%$) / Snack ($+20\%$) | Starving pet refuses to play; well-fed pet smiles and dances |
| **Energy** | 0–100 | $2.0\% / \text{hr}$ | Night sleep and rest periods | Sleepy pet yawns and sleeps when watch is in ambient mode |
| **Happiness**| 0–100 | $2.0\% / \text{hr}$ | Weighted vitals + direct petting/rotary play | Drops if any vital < 20; unlocks tricks, dialogue bubbles, XP |

### Evolution & Archetypes
- **Evolution Stages**: `EGG` (Lv 0) $\rightarrow$ `HATCHLING` (Lv 1, 100 XP) $\rightarrow$ `CHILD` (Lv 2, 300 XP) $\rightarrow$ `TEEN` (Lv 3, 750 XP) $\rightarrow$ `ADULT` (Lv 4, 1500 XP) $\rightarrow$ `ANCIENT_SAGE` (Lv 5, 3000 XP).
- **Habit-Based Archetypes**: At `TEEN` stage, habits determine the pet's persona:
  - *Swift Strider (Cardio)*: High step count consistency.
  - *Zen Ascetic (Mindful)*: High sleep quality and perfect hydration.
  - *Mighty Titan (Strength)*: High workout frequency.
  - *Balanced Soul (Harmony)*: Balanced habits across all vitals.

---

## 5. Wear OS Performance & Battery Best Practices
- **Passive Health Services**: Using `PassiveMonitoringClient` delegates sensor polling to the OS hardware hub, consuming near-zero extra battery.
- **Pure Vector UI**: All companion graphics are drawn via hardware-accelerated Compose Canvas paths, eliminating large bitmap assets from memory.
- **Ambient Mode Compatible**: Pure black OLED backgrounds (`#0A0E14`) maximize battery preservation.
- **Micro-Interactions**: Wear OS users interact in 3-to-5-second bursts. The Carousel Tile and 1-tap quick action buttons allow logging without deep navigation.
