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
├── wearApp/          # Wear OS application module (entry point, AppContainer, tile)
├── core/model/       # Pure domain models (no Android dependencies)
├── core/domain/      # Game engine, use cases, repository interfaces, Clock
├── core/data/        # Room database + schemas, DataStore sensor sync, repository implementations
├── core/health/      # Wear OS Health Services integration, permissions, boot re-registration (WorkManager)
└── core/ui/          # Compose UI components and theme
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full system design and module dependency graph. The reasons behind non-trivial choices are logged in [docs/DESIGN_DECISIONS.md](docs/DESIGN_DECISIONS.md), and past reviews live in [docs/reviews/](docs/reviews/README.md).

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

All tests run on the JVM, with no emulator needed:

```bash
# macOS / Linux
./gradlew test

# Windows
.\gradlew.bat test
```

- **`:core:domain`**: game engine (decay, night rest, mood, archetypes, daily-total deltas) and use cases, with fake repositories and clocks.
- **`:core:data`**: Room and DataStore integration tests under Robolectric (concurrent writes, migrations, bad-row repair).
- **`:core:health`**: sensor/permission planning rules.
- **`:wearApp`**: the tile's click-deduplication rule, the ambient burn-in shift, the pet screen's ambient behaviour (sensor release, once-a-minute refresh), and the vitals breakdown.

### Changing the Database Schema

Room schemas are exported to `core/data/schemas/` and committed. CI fails if a build changes them, because an entity changed without a version bump would put users' pets at risk. To change an entity:

1. Bump `CompanionDatabase.VERSION` and build. Room exports the new `<version>.json`.
2. Add a migration (an `@AutoMigration` on `@Database`, or a `Migration` in `ALL_MIGRATIONS`).
3. Add a case to `CompanionDatabaseMigrationTest`, and commit the new schema JSON with the change.

See the checklist in [docs/ARCHITECTURE.md §5.3](docs/ARCHITECTURE.md#4-schema-versioning--migrations).

### Simulating Sensor Data on the Emulator

To test passive tracking on the Wear OS emulator without physical movement, simulate Health Services data as described in [docs/VERIFICATION.md §1.1](docs/VERIFICATION.md#11-synthetic-health-services-data). The same guide lists the manual checks to run on an emulator or watch, and how to profile battery use.

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
   If you made a non-trivial design choice (a real alternative, a trade-off, a game-balance value, or an unverified assumption), add an entry to [docs/DESIGN_DECISIONS.md](docs/DESIGN_DECISIONS.md).

4. **Open a pull request** against `main` with a clear description of what changed and why.

---

## Code Style & Conventions

- Kotlin code style is set to `official` (enforced via `kotlin.code.style=official` in `gradle.properties`).
- Format your code with Android Studio's built-in formatter (**Code → Reformat Code**) before committing.
- Follow the existing Clean Architecture module boundaries — domain logic belongs in `:core:domain`, not in `:wearApp`.

### License Header

Every new Kotlin (`.kt`) or Java (`.java`) source file must begin with the project's SPDX-compliant copyright header (build scripts such as `.kts` and configuration files are excluded):

```kotlin
// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0
```

> [!TIP]
> **Android Studio Automation**: You can configure Android Studio to automatically prepend this header to every newly created file:
> 1. Open **Settings / Preferences** (`Ctrl + Alt + S` on Windows/Linux or `Cmd + ,` on macOS).
> 2. Navigate to **Editor → File and Code Templates → Includes → File Header**.
> 3. Paste:
>    ```kotlin
>    // Copyright ${YEAR} DunderGG
>    // SPDX-License-Identifier: Apache-2.0
>    ```

---

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
