# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An Android IME (soft keyboard) for **Luxembourgish (Lëtzebuergesch)** with dictionary + N-gram word prediction, plus the Python pipeline that regenerates its dictionary assets from a Hugging Face corpus.

### Critical naming caveat

The app was forked from a Guadeloupean Creole keyboard ("Potomitan Kreyòl") and **was never renamed internally**. The product is Luxembourgish, but:

- Package / applicationId is `com.example.kreyolkeyboard`, directory `clavier_creole/`, classes `KreyolInputMethodServiceRefactored`, `CreoleDictionaryWithUsage`, `KreyolSpellCheckerService`.
- `Constants.kt` still says `APP_NAME = "Potomitan Kreyòl Keyboard"`, `APP_VERSION = "5.3.0"` — **stale, not the real version**. The real version is `versionName`/`versionCode` in `android_keyboard/app/build.gradle`.
- `res/xml/method.xml` declares `languageTag="fr-GP"` (Guadeloupe), not `lb`.
- Several docs (`CONTRIBUTING.md`, `CHANGELOG.md` older entries, GitHub Actions job names and release notes) still talk about Créole.
- Comments and log messages are in French throughout.

Do not "fix" these names casually — `applicationId` is tied to the Play Store listing and the signing keystore. Treat Kreyòl/Creole identifiers as historical aliases for the Luxembourgish feature they now implement.

## Commands

All Gradle commands run from `android_keyboard/`:

```bash
cd android_keyboard
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # release APK (signed if keystore props present)
./gradlew bundleRelease        # AAB for Play Store
./gradlew installDebug         # install on connected device/emulator
./gradlew clean
```

Outputs are renamed by `applicationVariants.configureEach` to `Letzebuergesch_Clavier_v<version>_<buildType>_<yyyy-MM-dd>.apk` under `app/build/outputs/`.

Requires JDK 17 (both `sourceCompatibility` and `jvmTarget` are 17), Gradle 8.7, AGP 8.6.0, Kotlin 1.9.25, compileSdk/targetSdk 35, minSdk 21.

`local.properties` holds a **Windows** SDK path (`C:\Users\...\Android\Sdk`). Building from WSL/Linux requires pointing `sdk.dir` (or `ANDROID_HOME`) at a Linux SDK.

### Dictionary pipeline

```bash
cd Dictionnaires
python LuxembourgishComplet.py   # regenerates Luxembourgish dict + ngrams
python KreyolComplet.py          # legacy Creole pipeline
```

`LuxembourgishComplet.py` pulls `Akabi/Luxemburgish_Press_Conferences_Gov` from Hugging Face (needs `HF_TOKEN` or `HF_TOKEN_read_write` in `.env`; falls back to `luxemburgish_data/transcriptions.json`) and writes **directly into the Android assets**: `../android_keyboard/app/src/main/assets/luxemburgish_dict.json` and `..._ngrams.json`, with timestamped copies in `Dictionnaires/backups/`. Note the `Dictionnaires/README.md` still documents the older `clavier_creole/assets/` output paths — the script paths above are authoritative.

### Tests

**There is no automated test infrastructure** — no `src/test/` or `src/androidTest/`, no test dependencies. `AccentTolerantMatchingTest.kt` lives in `src/main` and is a runtime self-test invoked from `runAccentTolerantTests()` in the IME service; its results go to logcat only (tag `AccentTolerantTest` / `LuxemburgIME`). Verification is manual on device — see `android_keyboard/GUIDE_TEST_MANUEL.md`.

To watch the keyboard at runtime: `adb logcat -s LuxemburgIME SuggestionEngine InputProcessor KeyboardLayoutManager AccentTolerantTest`.

## Architecture

### Two IME services — only one is live

`AndroidManifest.xml` registers **`KreyolInputMethodServiceRefactored`** as the IME. `KreyolInputMethodService.kt` (~1500 lines) is the pre-refactor monolith, unreferenced and effectively dead code — editing it changes nothing at runtime. Always work in the Refactored service.

### Component decomposition

The Refactored service implements four listener interfaces and owns four collaborators; all cross-component communication goes through those callbacks, not direct calls back into the service:

| Component | Responsibility |
|---|---|
| `KeyboardLayoutManager` | Builds the key grid **programmatically** (no XML inflation for the keyboard), styles buttons, tracks shift/capslock/numeric mode, space-bar long-press. Layout is AZERTY-derived with `é`/`ë` on the bottom row. |
| `InputProcessor` | Interprets key presses against the `InputConnection`: backspace, enter (respects `IME_FLAG_NO_ENTER_ACTION` and `TYPE_TEXT_FLAG_MULTI_LINE`), capitalization, current-word tracking, word-commit events. |
| `SuggestionEngine` | Loads `luxemburgish_dict.json` + `luxemburgish_ngrams.json` from assets, runs prefix and N-gram lookups on coroutines, emits both plain and `BilingualSuggestion` results. Modes: `DICTIONARY` (while typing), `CONTEXTUAL` (N-gram, after space), `MIXED`. |
| `AccentHandler` | Long-press accent popup driven by a static `accentMap` (`a` → à á â ä ã å, etc.). |

Supporting: `AccentTolerantMatcher` (diacritic-insensitive lookup so typing `letzebuergesch` matches `lëtzebuergesch`), `FrenchDictionary` + `BilingualSuggestion` (optional French fallback from `french_simple_dict.json`, gated to words of 3+ letters so the primary language wins).

`res/layout/keyboard.xml` exists but the live keyboard is built in code; the XML layouts that matter are `activity_main.xml` and `activity_vocabulary_stats.xml`.

### Asset data formats

Both Luxembourgish assets are flat JSON objects, not the metadata-wrapped shape used by `french_simple_dict.json`:

- `luxemburgish_dict.json` — `{"word": corpusFrequency}`
- `luxemburgish_ngrams.json` — `{"word1 word2": count}` (bigrams keyed by space-joined context)

The CI workflow hard-fails if either file is missing and reports `jq 'length'` counts into the release notes, so keep both as top-level JSON objects.

### Gamification / usage tracking

`InputProcessor` fires `WordCommitListener` on each committed word → `CreoleDictionaryWithUsage` merges a per-word `user_count` onto the shipped corpus frequency and persists `luxemburgish_dict_with_usage.json` in `filesDir` (never assets). By design only words already in the dictionary are counted — passwords and personal strings are never recorded, and nothing leaves the device. `SettingsActivity` (batched writes via a scheduled executor) and `VocabularyStatsActivity` read that file to render level/progress stats. Preserve that filtering when touching this path; it is the basis of the published privacy policy in `docs/privacy/`.

### Samsung / low-end device handling

The service carries deliberate workarounds: a lifecycle-scoped `serviceScope` (`SupervisorJob`) to fix memory leaks observed on Samsung A21s, a periodic memory-monitoring job, `isLowEndDevice()` gating on `isLowRamDevice`/`memoryClass <= 256`, `abiFilters` limited to `armeabi-v7a`/`arm64-v8a`, and multidex + `largeHeap`. Don't strip these as dead weight.

## Release & signing

Release builds read `KEYSTORE_FILE` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` from `gradle.properties` first, then environment variables, and **silently fall back to debug signing** if any is missing (the Gradle output prints which one). A "successful" release APK can therefore be debug-signed — check the build log line before shipping.

Locally: copy `gradle.properties.template` → `gradle.properties` and fill in the values (`gradle.properties` is gitignored; never commit real values). In CI the keystore comes from the `KEYSTORE_BASE64` secret, decoded into `android_keyboard/potomitan-keystore.jks`.

`.github/workflows/build-apk.yml` runs on pushes to `main` touching `android_keyboard/**`: verify dictionary assets → build debug/release APK and AAB in parallel → validate → and, **only on a `v*` tag**, create a GitHub Release with all four artifacts. Cutting a release therefore means bumping `versionCode`/`versionName` in `app/build.gradle`, then pushing a `v*.*.*` tag. (Note the workflow's `paths` filter says `Dictionnaries/**` — a typo; the real directory is `Dictionnaires/`.)

## Repo layout beyond the app

- `android_keyboard/` — the Android project (the only buildable app).
- `Dictionnaires/` — Python corpus pipelines, backups, corpus data.
- `clavier_creole/` — vestigial Flutter `main.dart` + a stale copy of the dictionary assets; not part of any build.
- `PawolKreyol/`, `KreyolKeybPlayStore/`, `Logos/`, `Screenshots/` — Creole-era corpus, Play Store listing texts, and image assets.
- `docs/`, `github-pages-privacy/` — two copies of the privacy policy served via GitHub Pages; keep them in sync when the tracking behavior changes.
