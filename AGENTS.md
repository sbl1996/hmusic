# Repository Guidelines

## Project Structure & Module Organization
`hmusic` is a single-module Android app. Main code lives under `app/src/main/java/com/hastur/hmusic`, split by concern:

- `ui/` for Compose screens, view models, and theme files
- `data/` for Room entities, DAO, database, and repository code
- `player/` for playback service and player state management
- `sync/` for S3-compatible backup and restore integration

Resources and manifest files are in `app/src/main/res` and `app/src/main/AndroidManifest.xml`. Local and instrumented tests belong in `app/src/test` and `app/src/androidTest`. Dependency versions are centralized in `gradle/libs.versions.toml`.

## Build, Test, and Development Commands
- `cp .env.example .env` initializes local config for the secrets plugin.
- `./gradlew assembleDebug` builds the debug APK.
- `./gradlew test` runs JVM unit tests, including Robolectric-based tests.
- `./gradlew connectedAndroidTest` runs device or emulator instrumentation tests.
- `./gradlew lint` runs Android lint checks.

Use Android Studio for interactive debugging, previews, and emulator runs against the `app` module.

## Coding Style & Naming Conventions
Use Kotlin with 4-space indentation and standard IDE formatting. Keep package names lowercase and match the existing structure under `com.hastur.hmusic`. Use `PascalCase` for classes and composables, `camelCase` for methods and properties, and clear suffixes such as `*Screen`, `*ViewModel`, `*Repository`, `*Dao`, and `*Entity`.

Prefer small feature-focused files except where the existing UI is already consolidated, such as `ui/MusicPlayerScreen.kt`. Keep new Compose state hoisted where practical and place persistence or network code outside composables.

## Testing Guidelines
Add JVM tests in `app/src/test` for repository, sync, and database behavior. Add UI or integration coverage in `app/src/androidTest`. Name tests after the target class, for example `MusicRepositoryTest.kt` or `MusicPlayerScreenTest.kt`.

Robolectric and Roborazzi are available, so prefer fast local tests for UI logic and screenshot-sensitive changes when editing Compose screens.

## Commit & Pull Request Guidelines
Use short, imperative commit subjects, preferably following Conventional Commits.

Pull requests should include a concise summary, test notes, linked issue or task if applicable, and screenshots for visible UI changes. Call out `.env` or signing-related changes explicitly.
