# Health Companion (Wear OS)

A health-mirroring virtual pet companion for Wear OS smartwatches, inspired by the classic Tamagotchi toy. Your companion's vitals (Energy, Hydration, Nutrition, Fitness, and Happiness) directly reflect your real-world habits.

---

## Key Features
- **Health-Mirroring Game Mechanics**: Taking steps boosts the pet's fitness and evolution XP; drinking water restores hydration; logging meals keeps hunger at bay.
- **Battery-Friendly Time-Delta Decay**: Employs mathematical timestamp-delta decay rather than continuous CPU wakeups.
- **Wear OS Health Services**: Passively monitors daily step counts and workout goals via `PassiveMonitoringClient`.
- **Modern Vector-Native Companion**: Hardware-accelerated dynamic vector rendering with breathing bounce, blinking eyes, blushing cheeks, and mood expressions.
- **Circular Wear OS UI**: Circular multi-vital progress ring (`VitalsRing`) and micro-interaction buttons optimized for round smartwatches.
- **Wear OS Carousel Tile**: Swipe from your watch face to instantly glance at your companion's status.
- **Clean Multi-Module Architecture**: Decoupled domain engine, data layer, health client, and UI modules.

---

## Project Structure

```
health-companion/
├── build.gradle.kts                      # Root build configuration
├── settings.gradle.kts                   # Multi-module settings
├── gradle/libs.versions.toml             # Version catalog (Compose, Wear, Health, etc.)
│
├── docs/                                 # Project documentation & design specs
│   ├── ARCHITECTURE.md                   # System architecture & PlantUML diagrams
│   └── ROADMAP.md                        # Phased timeline & testing guide
│
├── wearApp/                              # Wear OS Application Module
│   ├── src/main/AndroidManifest.xml      # Standalone watch app configuration
│   └── src/main/java/com/healthcompanion/wear/
│       ├── HealthCompanionApp.kt         # Application setup & WorkManager scheduling
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
│   │   └── workers/PetDecayWorker.kt     # Periodic background maintenance
│   ├── health/                           # Wear OS Health Services integration
│   │   ├── HealthServicesManager.kt      # PassiveMonitoringClient wrapper
│   │   └── PassiveDataService.kt         # PassiveListenerService for step counting
│   └── ui/                               # Wear OS Compose UI Components
│       ├── components/ModernPetCanvas.kt # Dynamic vector companion
│       ├── components/VitalsRing.kt      # Circular multi-vital progress arcs
│       └── theme/Theme.kt                # Wear Material3 Dark OLED Palette
```

---

## Build & Run Instructions

### Prerequisites
- **Android Studio**: Ladybug (2024.2+) or Meerkat (2024.3+)
- **JDK**: OpenJDK 21
- **Android SDK**: API Level 36 (compileSdk) with Android SDK Platform-Tools
- **Target Device**: Wear OS 3.0+ (API 30+) emulator or physical smartwatch

### Running Unit Tests
Execute unit tests for the domain game loop and decay calculation:
```bash
# macOS / Linux
./gradlew test

# Windows
.\gradlew.bat test
```

### Building the Wear OS Debug APK
```bash
# macOS / Linux
./gradlew :wearApp:assembleDebug

# Windows
.\gradlew.bat :wearApp:assembleDebug
```
The output APK is generated at `wearApp/build/outputs/apk/debug/wearApp-debug.apk`.

### Testing Sensor Data on Emulator
To test passive step count tracking on the Wear OS emulator without physical movement, use ADB to broadcast synthetic sensor updates:
```bash
adb shell am broadcast -a "androidx.health.services.client.action.SIMULATE_DATA"
```

---

## Project Naming & Roadmap

> [!NOTE]
> **Health Companion** is currently a working title. A dedicated milestone (**Phase 2b**) in the [Project Roadmap](docs/ROADMAP.md) is open for selecting the permanent brand name. Candidate names include **Resona**, **Symbio**, **Vitalkin**, **Paravita**, **Vitecho**, and **AuraSync**.

For complete details on system design, mathematical formulas, and the phased development plan:
- 📖 [System Architecture & PlantUML Specifications](docs/ARCHITECTURE.md)
- 🗺️ [Phased Project Roadmap](docs/ROADMAP.md)

---

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.


