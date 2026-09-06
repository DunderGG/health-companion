# Health Companion (Wear OS)

A health-mirroring virtual pet companion for Wear OS smartwatches, inspired by the classic Tamagotchi toy. Your companion's vitals (Energy, Hydration, Nutrition, Fitness, and Happiness) directly reflect your real-world habits.

---

## ✨ Key Features
- **Health-Mirroring Game Mechanics**: Taking steps boosts the pet's fitness and evolution XP; drinking water restores hydration; logging meals keeps hunger at bay.
- **Battery-Friendly Time-Delta Decay**: Employs mathematical timestamp-delta decay rather than continuous CPU wakeups.
- **Wear OS Health Services**: Passively monitors daily step counts and workout goals via `PassiveMonitoringClient`.
- **Modern Vector-Native Companion**: Hardware-accelerated dynamic vector rendering with breathing bounce, blinking eyes, blushing cheeks, and mood expressions.
- **Circular Wear OS UI**: Circular multi-vital progress ring (`VitalsRing`) and micro-interaction buttons optimized for round smartwatches.
- **Wear OS Carousel Tile**: Swipe from your watch face to instantly glance at your companion's status.

---

## 🧰 Tech Stack
- **Platform**: Standalone Wear OS 3.0+ (API 30–36)
- **UI & Rendering**: Jetpack Compose for Wear OS, Wear Material 3, dynamic Compose Canvas
- **Health & Sensors**: AndroidX Health Services (`PassiveMonitoringClient`)
- **Architecture**: Multi-module Clean Architecture + Unidirectional Data Flow (MVI)
- **Persistence & Background**: Jetpack Room SQLite, DataStore Preferences, WorkManager

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Ladybug (2024.2+) or Meerkat (2024.3+)
- **JDK**: OpenJDK 21
- **Android SDK**: API Level 36 (`compileSdk 36`) with Android SDK Platform-Tools
- **Target Device**: Wear OS 3.0+ (API 30+) emulator (round display recommended) or physical smartwatch

### 1. Clone & Open
```bash
git clone https://github.com/DunderGG/health-companion.git
```
Open the repository in **Android Studio**. Gradle will automatically sync dependencies.

### 2. Run on an Emulator
1. Start a **Wear OS emulator** (API 30+ / Wear OS 3.0+) via Android Studio's **Device Manager**.
2. Select the **`wearApp`** run configuration in the top toolbar.
3. Click **Run** (`Shift + F10`).

Alternatively, assemble the debug APK via the command line:
```bash
# macOS / Linux
./gradlew :wearApp:assembleDebug

# Windows
.\gradlew.bat :wearApp:assembleDebug
```
The output APK is generated at `wearApp/build/outputs/apk/debug/wearApp-debug.apk`.

### 3. Install on a Physical Smartwatch (Wireless Debugging)
Most Wear OS smartwatches (Pixel Watch, Galaxy Watch, etc.) connect for development via Wi-Fi:

1. **Enable Developer Options**: On your watch, navigate to **Settings → System → About → Versions** and tap **Build number** 7 times.
2. **Enable Wireless ADB**: Go to **Settings → Developer Options**, and turn on **ADB debugging** and **Wireless debugging**.
3. **Pair & Connect** (ensure your computer and watch share the same Wi-Fi network):
   - Under *Wireless debugging*, tap **Pair new device** to view the IP, port, and pairing code:
     ```bash
     adb pair <watch-ip>:<pairing-port>
     adb connect <watch-ip>:<connect-port>
     ```
4. **Deploy**:
   - Select your watch from Android Studio's device selector dropdown and click **Run** (`Shift + F10`), or:
   - Install the built APK directly via ADB:
     ```bash
     adb install -r wearApp/build/outputs/apk/debug/wearApp-debug.apk
     ```

### 4. Run Unit Tests
Execute the test suite covering mathematical decay, habit logging, and mood calculations:
```bash
# macOS / Linux
./gradlew test

# Windows
.\gradlew.bat test
```

### 5. Simulating Sensor Steps on Emulator
Because emulators do not physically walk, use ADB to broadcast synthetic Wear OS Health Services sensor events:
```bash
adb shell am broadcast -a "androidx.health.services.client.action.SIMULATE_DATA"
```

---

## 📚 Documentation

Detailed architectural specifications and development plans are maintained in the [`docs/`](docs/) directory:

- 📖 **[System Architecture](docs/ARCHITECTURE.md)** — PlantUML system flows, module responsibilities, mathematical decay formulas, and source tree.
- 🗺️ **[Project Roadmap](docs/ROADMAP.md)** — 5-phase development roadmap, emulator synthetic sensor guide, and the **Phase 2b** community naming survey (*Resona*, *Symbio*, *Vitalkin*, *Paravita*, *Vitecho*, *AuraSync*).
- 🤝 **[Contributing Guidelines](CONTRIBUTING.md)** — Environment setup, code style, required license headers, and PR workflow.

---

## 🤝 Contributing

Contributions, bug reports, and ideas are welcome! Please review [CONTRIBUTING.md](CONTRIBUTING.md) for developer setup, code style standards, and the required copyright header for new source files.

---

## 👤 Author

**David Bennehag** - [@DunderGG](https://github.com/DunderGG) - [dunder.gg](https://dunder.gg)

---

## 📄 License

Copyright 2026 DunderGG

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

See [LICENSE](LICENSE) for the full license terms and [NOTICE](NOTICE) for project attributions.
