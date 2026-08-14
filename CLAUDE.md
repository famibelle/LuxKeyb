# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Lëtzebuergesch Clavier** — an Android IME (soft keyboard) for Luxembourgish, with dictionary + n-gram prediction, Levenshtein typo tolerance, a system spell checker, emoji panel, vocabulary games and a progression system. Plus the Python pipeline that regenerates its dictionary from a Hugging Face corpus.

## Shared history with KreyolKeyb — read this before any big change

This repo and [`famibelle/KreyolKeyb`](https://github.com/famibelle/KreyolKeyb) (Klavyé Kréyòl Karukéra, a Guadeloupean Creole keyboard) **share the same git root** and the same codebase. LuxKeyb forked at `4517522` ("Version 6.1.8"), diverged for 39 commits, then re-merged KreyolKeyb's `v10.9.2`.

Consequences that matter:

- **Syncing future upstream work is a `git merge`, not a port.** The merge base is now recent:
  ```bash
  git remote add kreyol https://github.com/famibelle/KreyolKeyb.git   # once
  git fetch kreyol --tags
  git merge v10.10.0                                                  # or whichever tag
  ```
  Expect conflicts only where both sides edited the same lines. The standing resolution policy: **take upstream for engine/UI code, keep ours for identity** (`strings.xml`, launcher icons, `LuxLevels.kt`, `accentMap`, keyboard rows, asset filenames, `applicationId`).
- **Keep divergence minimal on purpose.** Restructuring shared files (renaming classes, centralising constants) makes every future merge more expensive. Prefer in-place edits that keep upstream's shape.
- Upstream's `CLAUDE.md` documents the shared architecture in more depth and is worth reading when touching the engine.

### Naming caveat

The Creole ancestry is still visible in identifiers and is **deliberate**:

- Kotlin package / Gradle `namespace` is `com.example.kreyolkeyboard`; classes are `KreyolInputMethodServiceRefactored`, `KreyolSpellCheckerService`, `CreoleDictionaryWithUsage`.
- `applicationId` is **`com.potomitan.luxkeyboard`** — different from the namespace. Every adb command needs both halves:
  ```
  com.potomitan.luxkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored
  ```
- Directories `clavier_creole/`, `PawolKreyol/`, `KreyolKeybPlayStore/` are inherited.

Renaming the package would break the shared merge base for no user-visible gain. Treat Kreyòl/Creole identifiers as historical aliases.

## Commands

All Gradle commands run from `android_keyboard/`:

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (signed only if keystore props present)
./gradlew bundleRelease          # AAB for Play Store
./gradlew installDebug           # install on device/emulator
./gradlew testDebugUnitTest      # the whole test suite (120 tests)
./gradlew testDebugUnitTest --tests "com.example.kreyolkeyboard.LevenshteinDistanceTest"
```

Requires **JDK 17** and Android SDK 36. AGP 9.3.1, Gradle 9.6.1, compileSdk/targetSdk 36, minSdk 21. AGP 9 compiles Kotlin natively — there is **no `org.jetbrains.kotlin.android` plugin and no `kotlinOptions` block**; `jvmTarget` comes from `compileOptions.targetCompatibility`. APK/AAB names are set through `androidComponents { onVariants }` (the old `applicationVariants.configureEach` API is gone).

`android_keyboard/local.properties` is gitignored and per-machine. On WSL/Linux it must point at a Linux SDK (or be absent so `ANDROID_HOME` is used); a Windows `sdk.dir` will fail the build.

`versionCode` format: `100902` = `10.9.2` (major × 10000 + minor × 100 + patch).

### Dictionary pipeline

```bash
cd Dictionnaires
pip install datasets huggingface_hub
python LuxembourgishComplet.py     # needs HF_TOKEN
```

Pulls `POTOMITAN/luxembourgish-corpus` (from the `Texte` column) and writes **directly into the Android assets**, with timestamped backups in `Dictionnaires/backups/`.

**Never run this without a valid `HF_TOKEN`.** On failure the script silently falls back to a local corpus of a few dozen lines and rebuilds the dictionary from it. CI guards against this with a minimum word count; locally, check the printed count.

## Asset schema — the contract the engine depends on

Both files live in `android_keyboard/app/src/main/assets/`:

- `luxemburgish_dict.json` — **array of pairs**, `[["an", 2090], ...]`, sorted by descending frequency. **Not an object.** An object parses to a `JSONException` at load time and silently disables every suggestion while remaining valid, non-empty JSON — this is exactly what broke upstream's v10.2.6.
- `luxemburgish_ngrams.json` — `{context: [{"word": ..., "probability": ...}]}` with **two key families in one flat object**: one word (`"der"`, from bigrams) and two words (`"an der"`, from trigrams). `SuggestionEngine.resolveNgramContext()` tries the two-word key first and falls back to the one-word key. Emitting only the one-word family makes contextual prediction inert without failing anything visibly.
- `french_simple_dict.json` — French fallback, metadata-wrapped (different shape, inherited).

CI enforces all three properties (array type, ≥ 3000 words, non-zero two-word keys) before building. `NgramContextTest` covers the second family.

## Architecture

### IME entry point

`KreyolInputMethodServiceRefactored.kt` is the **only** IME service. It coordinates four components through listener interfaces:

| Component | Responsibility |
|---|---|
| `KeyboardLayoutManager` | Builds the key grid **programmatically** (no XML for the keyboard), vector icons for ⇧/⌫/⏎, emoji mode, corner hints, space-bar long-press |
| `SuggestionEngine` | Loads dictionary + n-grams, ranks bilingual suggestions |
| `AccentHandler` | Long-press popup, driven by `accentMap`; also serves emoji skin tones |
| `InputProcessor` | Key events, emoji-aware backspace, cursor-word tracking, word commit |

### Suggestion pipeline (`SuggestionEngine.kt`)

Prefix match → n-gram context (two words, then one) → Levenshtein fuzzy match → accent-tolerant match → French fallback (only from 3 characters) → casing preserved via `applyCasingPattern()`. Max 3 shown, 5 scored.

`calculateDictionaryScore()` constants are **calibrated to corpus frequency distribution**. Luxembourgish frequencies are much flatter than Creole's (max 2090 here vs 15519 upstream); re-check these after any big corpus change.

### Keyboard layout and accents — data-driven, not translated

Row 4 is `["123", ",", "é", "ä", " ", "ë", ".", "EMOJI", "⏎"]`. The three dedicated diacritic keys and the `accentMap` ordering come from actual counts over `luxemburgish_dict.json`:

```
é 6347/933 · ë 2877/355 · ä 2129/403 · ü 97/25 · è 97/47 · à 37/3 · ô 29/3 · ê 22/7 · ö 12/4
```

Upstream gives the fourth slot to a dedicated `-` key (21.7 % of Creole words contain one); in Luxembourgish that figure is 2.8 %, so `ä` takes the slot and `-` lives under long-press on `.`. Creole GEREC digraphs (`ch`, `dj`, `ng`, `ny`, `gn`, `gy`, `tj`) are removed. **If you regenerate the corpus, re-run these counts before changing the layout.**

### Spell checker (`KreyolSpellCheckerService.kt`)

A system `SpellCheckerService`, separate from the IME, reusing `SuggestionEngine`. Two things silently disable it:

- **Locale subtypes** (`res/xml/kreyol_spellchecker.xml`). Android matches subtypes against the *text field's* locale. We declare `lb` **and `fr`** — `lb` alone almost never matches, so no session is ever created. Diagnose with `adb shell dumpsys textservices`; empty `Spell Checker Bind Groups` means selected but never instantiated. `de` is deliberately **not** declared: German is widespread in Luxembourg and replacing its system checker with a 6k-word Luxembourgish dictionary would cost more than it gains.
- **`setCookieAndSequence()`** on every returned `SuggestionsInfo`, without which nothing is ever underlined.

Because `fr` is declared, this replaces the system French checker. That is why `shouldFlagAsTypo()` only flags an unknown word when a plausible correction exists.

The user must select it in **Settings › System › Keyboard › Spell checker** (under *Keyboard*, not *Languages*). Reinstalling leaves the binding stale until reboot.

### Gamification and games

`InputProcessor` fires `WordCommitListener` → `CreoleDictionaryWithUsage` merges a per-word `user_count` into `luxemburgish_dict_with_usage.json` in `filesDir`. **Only words already in the dictionary are counted** — passwords and personal strings are never recorded, and `isSensitiveInput()` gates the whole path. This is the basis of the published privacy policy; preserve it.

`LuxLevels.kt` holds the eight-rung ladder (Ufänker → Sproochenmeeschter) with no `Context` dependency, so both the IME and `SettingsActivity` can use it and it stays JVM-testable.

Games live in `wordsearch/`, `wordscramble/`, `wuertriet/` (Wuertsich, Wuertmix, Wuertriet). They pull words from the loaded dictionary. `WordSearchGenerator.ALPHABET` must include `ÄËÉÖÜ` or accented filler letters give the answers away.

### Tests

`app/src/test/` only — no `androidTest/`, so `testDebugUnitTest` is the whole suite. They work because of `testOptions { unitTests.returnDefaultValues = true }` (stubs `android.util.Log`) and the real `org.json:json` on the test classpath.

`LevenshteinDictionaryTest` and `NgramContextTest` read the real assets from `src/main/assets/`. **`LevenshteinDictionaryTest` still falls back to an inline sample when the file is missing rather than failing** — inherited behaviour worth tightening, since it is how a broken dictionary can pass a green suite.

## CI/CD and release

`.github/workflows/build-apk.yml`: regenerate dictionary (needs `HF_TOKEN` secret) → **unit tests** → build debug/release APK + AAB in parallel → validate → create GitHub Release on `v*` tags.

- The dictionary artifact is **handed between jobs via `upload-artifact`**. Without it each build job re-checkouts and silently uses the *committed* dictionary instead of the regenerated one; `needs:` only orders jobs, it does not share files.
- Release is blocked unless the top `## [x.y.z]` entry of `android_keyboard/CHANGELOG.md` matches the pushed tag.
- Release signing reads `KEYSTORE_FILE` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` from `gradle.properties` then env, and **falls back to debug signing with only a `println`** if any is missing. Check the build log before shipping a "successful" release APK.
- `update-download-stats.yml.disabled` / `update-exclusive-features.yml.disabled` are Play-Store-driven crons, disabled until the app is actually published. Drop the suffix to re-enable.

Release flow: feature commits → one `chore(release): version X.Y.Z` touching `CHANGELOG.md` + `app/build.gradle` → push tag `vX.Y.Z`.

## Known gaps

- `HF_TOKEN` is **not** configured as a GitHub secret here, so the CI dictionary job will fail until it is added.
- The **9 guide screenshots** (`res/drawable-nodpi/guide_screenshot_*.png`) and the onboarding previews still show the Creole keyboard; they need recapturing on an emulator.
- `docs/` is a large inherited marketing site (tracts, posters, simulator, press kit) still entirely in Creole. Only `docs/privacy/` and `docs/README.md` have been localised.
- `android_keyboard/keystore-config.txt` was removed from tracking, but its plaintext password remains in the git history of both repos, which are public. The signing key should be considered compromised and rotated before any Play release.

## Legacy directories

- `clavier_creole/` — abandoned Flutter prototype. Its `assets/` held the good Luxembourgish corpus for a while; nothing reads it now.
- `PawolKreyol/`, `KreyolKeybPlayStore/`, `Screenshots/` — Creole-era corpus, store listing and branding.
- `Dictionnaires/KreyolComplet.py` — the Creole pipeline, kept for reference; `LuxembourgishComplet.py` is the one this app uses.
