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
- [x] Runtime permission flow on watch for `BODY_SENSORS` and `ACTIVITY_RECOGNITION`.
- [ ] Connect `HealthServicesManager` to live watch hardware sensors.
- [ ] Real-time step delta mapping: Convert real-world step bursts into instant companion animation reactions (e.g. running alongside user).
- [ ] Battery profiling and verification on Wear OS emulator / physical test watch.
- [ ] Local push notifications via WorkManager when hydration or hunger reaches critical thresholds.

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
  - **Kotlin source & package namespace** (affects all 31 `.kt` files — use IDE refactor: *Rename Package*)
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
    - [ ] `CONTRIBUTING.md` — title, working-title note, `git clone` URL, `health-companion/` folder references, project structure tree
    - [ ] `NOTICE` — project name and GitHub URL on lines 1 and 5
  - **CI / GitHub**
    - [ ] `.github/workflows/ci.yml` — `name:` field and the uploaded artifact name (`wearApp-debug`)
    - [ ] GitHub repository name itself (Settings → Repository name) — this automatically redirects the old URL, but update all hardcoded URLs above to match
    - [ ] README CI badge URL (`https://github.com/DunderGG/health-companion/…`)

---

## Phase 3: Wear OS Native Surfaces & Micro-Interactions
- [ ] Interactive Wear OS Carousel Tile with direct 1-tap "+250ml Water" action button via ProtoLayout.
- [ ] Watch Face Complication Provider (`PetMoodComplicationService`): Show pet mood icon or step-progress ring directly on standard watch dials.
- [ ] Ambient Mode support: Low-power grayscale rendering for always-on displays.
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

## Testing & Verification Guide

### Unit Tests
```bash
./gradlew test
```

### Wear OS Health Services Synthetic Sensor Testing
To simulate step counts and workouts on the Wear OS emulator without physical movement:
```powershell
adb shell am broadcast -a "androidx.health.services.client.action.SIMULATE_DATA"
```

