# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

**Klavyé Kréyòl Karukera** — an intelligent keyboard for Guadeloupean Creole (kréyòl Guadeloupéen). It is an Android IME (Input Method Editor) with an iOS port in progress. The keyboard provides bilingual suggestions (Kreyòl + French) powered by a curated dictionary and n-gram model built from Creole literary texts.

## Android Build Commands

All commands run from `android_keyboard/`:

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build signed release APK (requires keystore config)
./gradlew installDebug           # Build + install on connected device
./gradlew test                   # Run all unit tests
./gradlew test --tests "com.example.kreyolkeyboard.LevenshteinDistanceTest"  # Single test class
```

**Local build gotchas:** AGP requires Java 17 (`export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`), and the checked-in `gradlew` script is corrupted (missing `eval`, passes quoted args to Gradle). Work around it with:
```bash
$JAVA_HOME/bin/java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain <task>
```
CI is unaffected (it installs Gradle 8.7 directly).

Release signing reads from `android_keyboard/gradle.properties` (local) or environment variables (`KEYSTORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Falls back to debug signing if secrets are missing. See `gradle.properties.example` for the format.

**versionCode** format: `60501` = version `6.5.1` (major × 10000 + minor × 100 + patch). minSdk 21, targetSdk 36.

## Dictionary / Data Pipeline

The JSON assets in `android_keyboard/app/src/main/assets/` are the **source of truth** used by both Android and iOS:
- `creole_dict.json` — `[word, frequency]` list (~5300 words)
- `creole_ngrams.json` — n-gram context model, ~8850 keys. Two key families in one flat object: one word (`"ka"`, from bigrams) and two words separated by a space (`"an ka"`, from trigrams). See `android_keyboard/NGRAMS.md`
- `french_simple_dict.json` — French fallback dictionary, only ~660 words. This thinness constrains both the bilingual suggestions and the spell checker (see below)

To regenerate from the Hugging Face dataset `POTOMITAN/PawolKreyol-gfc` (requires `HF_TOKEN`):
```bash
cd Dictionnaires
pip install datasets huggingface_hub
python KreyolComplet.py          # Fetches HF data, rebuilds dict + n-grams, backs up old files
```

**Never run this without a working `HF_TOKEN`.** On download failure the script silently falls back to `PawolKreyol/Textes_kreyol.json`, a local snapshot that may lag far behind the dataset, and rebuilds the dictionary from it.

Corpus word counts **replace** stored frequencies rather than adding to them, so two consecutive runs produce the same dictionary. Words absent from the corpus (hand-curated additions) are preserved, their frequency rescaled to the current corpus scale.

## Android Architecture

### IME Entry Point

`KreyolInputMethodServiceRefactored.kt` is the **only** IME service. The legacy monolithic `KreyolInputMethodService.kt` and the unused `TestInputMethodService.kt` were deleted in 10.4.2: neither was declared in the manifest, so both were dead code that still shipped in the APK and made features look implemented when they were not (`onUpdateSelection()` lived only there while the active service lacked it). Recover them from git history if ever needed.

The refactored IME coordinates four components via listener interfaces:

| Component | Responsibility |
|-----------|---------------|
| `KeyboardLayoutManager` | Creates and styles key buttons, manages shift/caps/numeric mode |
| `SuggestionEngine` | Loads dictionary + n-grams, produces ranked bilingual suggestions |
| `AccentHandler` | Long-press popup for accented characters |
| `InputProcessor` | Handles key events, backspace, word commit to `InputConnection` |

### Suggestion Pipeline (`SuggestionEngine.kt`)

1. **Prefix match** against `creole_dict.json` (Kreyòl prioritized)
2. **N-gram context** from the last two committed words, falling back to the last one when the pair is unknown
3. **Levenshtein fuzzy match** (`LevenshteinDistance.kt`) for typo tolerance
4. **Accent-tolerant match** (`AccentTolerantMatcher.kt`) — matches `e` against `é`, etc.
5. **French fallback** — only kicks in at ≥ 3 characters typed
6. **Casing preservation** — `applyCasingPattern()` mirrors the user's casing onto the suggestion

Max 3 suggestions displayed (5 internally scored: 3 Kreyòl + 2 French slots).

### Spell Checker (`KreyolSpellCheckerService.kt`)

A system `SpellCheckerService`, separate from the IME: any app's text field can query it, which is what stops Creole words from being underlined as typos. It reuses `SuggestionEngine` (`isKnownWord()` + `getSpellingSuggestions()`) rather than loading its own dictionaries.

Two things are easy to break here, both of which silently disable the service with no error anywhere:

- **Locale subtypes** (`res/xml/kreyol_spellchecker.xml`). Android picks a spell checker by matching a subtype against the *text field's* locale. Declaring only Creole locales means no match and no session is ever created. `fr` must stay declared. Diagnose with `adb shell dumpsys textservices`: empty `Spell Checker Bind Groups` means the service is selected but never instantiated.
- **`setCookieAndSequence()`** on every returned `SuggestionsInfo`. Without it the client cannot map a verdict back to the word it analysed, so nothing is ever underlined even though the service runs and logs correctly.

Because `fr` is declared, this service replaces the system one for **all** French text, on a ~660-word French dictionary. It therefore only flags a word when a plausible correction exists; widening `french_simple_dict.json` is what would let that restriction be lifted.

Android allows a single spell checker system-wide and no app can select itself. The user must pick it in **Settings › System › Keyboard › Spell checker** (under *Keyboard*, not *Languages*), so the app cannot rely on it being active. Three things verified on an emulator by walking the real UI:

- Android shows a deterrent confirmation dialog first, warning that the spell checker "can collect all the text you type, including personal data like passwords and credit card numbers". Any onboarding that guides users here has to prepare them for it.
- A master switch, *Use spell checker*, sits above the picker. When it is off, nothing is checked at all and the chosen service is never called, with no other symptom.
- **Reinstalling the app leaves the system binding stale** (`dumpsys textservices` shows the bind group with `mSpellChecker=null`), and the service stays silent until a reboot. Worth re-testing after a Play Store update before concluding the checker is broken.

### Gamification (`gamification/` package)

- `CreoleDictionaryWithUsage` — plain class over a `JSONObject` persisted to `filesDir`, tracks per-word usage counts. `getWordUsageCount()`/`incrementWordUsage()` are `synchronized`: the suggestion engine reads them from a background thread while the IME writes on the main thread
- `WordUsageStats` — per-word stats with 7 mastery levels: Pipirit → Potomitan
- `VocabularyStatsActivity` — displays dashboard with progress per level
- `WordCommitListener` interface — `KreyolInputMethodServiceRefactored` implements this to log each committed word

### Games (`wordscramble/`, `wordsearch/`, `mokarenaj/` packages)

Three vocabulary mini-games accessible from `SettingsActivity` (`mokarenaj` is a Creole Wordle). They pull words directly from the loaded dictionary. No separate data source.

## iOS Port (lives on the `ios/port` branch)

The iOS Swift/SwiftUI port is **not on `main`** — its sources, `project.yml`, and `ios-build.yml` workflow exist only on the `ios/port` branch. On `main`, `ios/` contains only signing materials (CSR, distribution key). Check out or merge from `ios/port` before doing iOS work.

The port uses **XcodeGen** (`project.yml`) and requires a Mac with Xcode 15:

```bash
cd ios
xcodegen generate               # Creates KreyolKeyb.xcodeproj from project.yml
```

The iOS project references the shared JSON assets directly from `android_keyboard/app/src/main/assets/` — do not duplicate them. The Xcode project is gitignored; regenerate it with `xcodegen generate` before building.

The structure mirrors Android: `Core/SuggestionEngine.swift`, `Core/LevenshteinDistance.swift`, `Core/AccentTolerantMatcher.swift`, `Gamification/`, `Games/`, `Views/ContentView.swift` (≈ `SettingsActivity`), and `KeyboardExtension/KeyboardViewController.swift` (≈ the IME service).

**Phase 1** (Swift source files) is complete. **Phase 2** (wiring the `KeyboardViewController` with actual key views, accent popups, and App Group sharing) is not yet implemented.

## CI/CD

- **`build-apk.yml`** — triggers on push/PR to `main` when `android_keyboard/**` or `.github/workflows/**` change, or on `v*` tags. Runs the Python dictionary pipeline first (needs `HF_TOKEN` secret), then builds and signs the APK. Creates a GitHub Release on tags. Its paths filter also covers `Dictionnaires/**`; note the workflow regenerates the dictionary on every build **without committing it back**, so the shipped APK is built from a freshly regenerated dictionary rather than the committed one.
- **`ios-build.yml`** (on `ios/port` branch only) — triggers on push to `ios/port` when `ios/` changes. Runs on `macos-14` (Xcode 15, Apple Silicon). Requires secrets: `DIST_CERT_BASE64`, `DIST_CERT_PASSWORD`, `PROVISIONING_PROFILE_BASE64`, `DEVELOPMENT_TEAM`, `APPLE_ID`, `APP_SPECIFIC_PASSWORD`.

## Legacy / Auxiliary Directories

- `clavier_creole/` — abandoned Flutter prototype (`lib/main.dart`). Do not develop here, **but do not assume its `assets/` are dead either**: `KreyolComplet.py` reads its previous dictionary from `clavier_creole/assets/` and writes the regenerated files to both there and `android_keyboard/`. The two copies must stay in sync.
- `PawolKreyol/` — raw Creole corpus texts (`Textes_kreyol.json`/`.xlsx`) feeding the HF dataset.
- `docs/` — GitHub Pages site (privacy policy, beta onboarding, feedback form).
- `KreyolKeybPlayStore/`, `Screenshots/`, `Logos/` — store listing and branding assets.
