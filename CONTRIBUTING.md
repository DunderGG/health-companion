# Contributing to Health Companion

Thank you for your interest in contributing! This document covers everything you need to get the project building locally and submit a pull request.

> [!NOTE]
> **Health Companion** is currently a working title. A dedicated milestone (**Phase 2b**) in the [Project Roadmap](docs/ROADMAP.md) is reserved for the final brand name decision. Contributions are welcome under the current name — a rename refactor will be a tracked milestone task.

---

## Prerequisites

| Tool | Version |
|---|---|
| **Android Studio** | Ladybug (2024.2+) or Meerkat (2024.3+) |
| **JDK** | OpenJDK 21 |
| **Android SDK** | API Level 36 (compileSdk) with Platform-Tools |
| **Target Device** | Wear OS 3.0+ (API 30+) emulator or physical smartwatch |

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/<your-username>/health-companion.git
cd health-companion
```

### 2. Configure your local Android SDK path

Copy the provided example file and edit it with your local SDK path:

```bash
# macOS / Linux
cp local.properties.example local.properties

# Windows (PowerShell)
Copy-Item local.properties.example local.properties
```

Then open `local.properties` and set `sdk.dir` to your local Android SDK location. See [`local.properties.example`](local.properties.example) for platform-specific examples.

> [!IMPORTANT]
> `local.properties` is excluded from version control. **Never commit it.** It contains your machine-specific paths.

### 3. Open in Android Studio

Open the root `health-companion/` folder in Android Studio. Gradle will sync the multi-module project automatically.

---

## Project Structure

```
health-companion/
├── wearApp/          # Wear OS application module (entry point)
├── core/model/       # Pure domain models (no Android dependencies)
├── core/domain/      # Game engine, use cases, repository interfaces
├── core/data/        # Room database, repository implementations, WorkManager
├── core/health/      # Wear OS Health Services integration
└── core/ui/          # Compose UI components and theme
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full system design and module dependency graph.

---

## Building

### Debug APK

```bash
# macOS / Linux
./gradlew :wearApp:assembleDebug

# Windows
.\gradlew.bat :wearApp:assembleDebug
```

Output: `wearApp/build/outputs/apk/debug/wearApp-debug.apk`

---

## Running Tests

The domain layer has a unit test suite covering decay math and mood evaluation:

```bash
# macOS / Linux
./gradlew test

# Windows
.\gradlew.bat test
```

### Simulating Sensor Data on the Emulator

To test passive step count tracking on the Wear OS emulator without physical movement:

```bash
adb shell am broadcast -a "androidx.health.services.client.action.SIMULATE_DATA"
```

---

## Submitting a Pull Request

1. **Fork** the repository and create a feature branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes.** Keep commits focused and atomic.

3. **Run the tests** and ensure they pass before opening a PR:
   ```bash
   ./gradlew test
   ```

4. **Open a pull request** against `main` with a clear description of what changed and why.

---

## Code Style

- Kotlin code style is set to `official` (enforced via `kotlin.code.style=official` in `gradle.properties`).
- Format your code with Android Studio's built-in formatter (**Code → Reformat Code**) before committing.
- Follow the existing Clean Architecture module boundaries — domain logic belongs in `:core:domain`, not in `:wearApp`.

---

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).

