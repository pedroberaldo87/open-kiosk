# Contributing to Open Kiosk

Thanks for your interest in contributing! This is a small project, so the process is straightforward.

## How to Contribute

1. Fork the repository
2. Create a branch from `main` (`git checkout -b my-feature`)
3. Make your changes
4. Open a Pull Request against `main`

For large changes or new features, please open an issue first to discuss the approach.

## Development Setup

1. Clone your fork
2. Open in Android Studio (Hedgehog 2023.1.1 or newer)
3. Let Gradle sync complete
4. Build: `./gradlew assembleDebug`

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config in local.properties)
./gradlew assembleRelease

# Lint check
./gradlew lintDebug
```

## Code Style

- Follow the [Kotlin official style guide](https://kotlinlang.org/docs/coding-conventions.html)
- Follow Jetpack Compose conventions for UI code
- Use Hilt for dependency injection (annotate with `@Singleton`, `@HiltViewModel`, `@AndroidEntryPoint`)
- Commit messages: imperative mood, short first line (e.g., "Add playlist shuffle support")

## Internationalization

All user-facing strings must go in resource files — no hardcoded strings in Kotlin UI code.

- English: `res/values/strings.xml`
- Portuguese: `res/values-pt/strings.xml`

## Before Submitting

- Run `./gradlew lintDebug` and fix any issues
- Test on a real Android device — the emulator is insufficient for kiosk mode, camera-based motion detection, and sensor features
- Make sure the app builds cleanly with `./gradlew assembleDebug`

## License

By contributing, you agree that your contributions will be licensed under the [GNU General Public License v3.0](LICENSE).
