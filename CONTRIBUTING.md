# Contributing to Pulse

First off, thank you for taking the time to contribute! Pulse is an open-source Android system monitor and benchmarking tool, and we appreciate help from the community.

Please follow these guidelines to make contribution as smooth as possible.

---

## Code of Conduct
By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

---

## How Can I Contribute?

### 1. Reporting Bugs
* Check the [Issue Tracker](https://github.com/iabhnv19/Pulse/issues) to see if the bug has already been reported.
* If not, create a new issue using the **Bug Report** template. Provide clear steps to reproduce, device model, Android version, and logs if applicable.

### 2. Suggesting Enhancements
* Use the **Feature Request** template to propose new features, UI improvements, or additional telemetry metrics.

### 3. Pull Requests
1. Fork the repository and clone your fork.
2. Create a feature branch: `git checkout -b feature/AmazingFeature`.
3. Make your changes adhering to Kotlin and Jetpack Compose coding standards.
4. Run tests and build locally (`./gradlew assembleDebug`) to ensure no regressions.
5. Commit your changes with clear commit messages.
6. Push to your branch and open a Pull Request against the `main` branch.

---

## Development Setup
1. Open the project in **Android Studio** (Ladybug or newer recommended).
2. Sync Gradle (`File > Sync Project with Gradle Files`).
3. Run the app on an emulator or physical Android device (API 24+).
