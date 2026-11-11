# Sudoku for Android

A standalone, offline native Android app. Open this project in Android Studio.
There is no in-app account, advertising, analytics, or network permission.
Optional offline curated collections supplement an unlimited generated supply.

**AI disclosure:** This app, including its code and artwork, was generated with AI
under human direction. Third-party components retain their own attribution and
licenses.

## Download

Download **Sudoku-1.0.apk** from the
[latest GitHub Release](https://github.com/mikeliddle/Sudoku/releases/latest).
It is a signed sideload build for Android 10 and newer; no app store or account
is needed. Open the downloaded APK to install, allowing installation from that
specific browser/file manager only if Android requests it. Do not bypass
Play Protect or other unsafe-app warnings.

The release includes a SHA-256 checksum. APKs and signing keys are not checked
into the repository. The official release cannot update a differently signed
development installation; preserve any development saves before switching.

## Play

The launcher icon is a pumpkin-orange adaptive icon with an upright warm-white
9x9 Sudoku grid, heavier 3x3 dividers, and nine nonconflicting givens.

The app opens on the difficulty-selection menu. **Resume** continues your unfinished
saved game; selecting a new difficulty asks before replacing it. **Settings**
contains Auto notes, Show mistakes, and Red, Orange, Yellow, Green, Blue, Pink,
Purple, and Brown color swatches in a compact two-row grid, with an outlined
selected swatch and accessible color names. Accents have accessible light/dark variants,
independent of System/Light/Dark appearance. Existing Forest, Ocean, and Orchid
preferences map to Green, Blue, and Purple without changing saved games or records.
Dark Red and Brown use deeper, saturated button fills with light labels.
Dark Orange uses vivid pumpkin-orange fills with dark labels. Each has coordinated
readable accents for board entries, notes, and outlines. Swatch fills keep their
own hue regardless of the selected accent; only the selection outline changes.

Select a digit in the bottom number bank to highlight matching entries and arm it.
Matching visible notes get an accent-colored circle around that individual digit,
including manually added notes; suppressed candidates stay hidden. Switching or
clearing the bank selection updates these highlights without changing notes.
Tap editable squares to enter that digit repeatedly. Alternatively, select a square
first, then tap a bank digit to enter it once (or toggle its note in Notes mode).
A number-first placement never selects the target square or its houses, so the
next bank tap switches the digit rather than overwriting that square.
Square-first one-shot entry clears both square selection and armed input. With no square
selected, another bank digit switches the armed digit, and the same digit cancels.
Tap outside the board and input controls to stop number entry.
The **Notes** button deliberately keeps the armed digit while switching modes;
switching modes never writes into the selected square. Selecting a square
highlights its row, column, and box. Fixed clues cannot be changed.
Tapping a bank digit clears the previous square/house highlight; matching-digit
highlights remain only when number-first input is armed.
Tapping outside the board clears square selection
and armed input; Notes remains an input-mode control rather than an outside tap.

The small number under each bank digit is `max(0, 9 - occurrences on the board)`,
including clues. Notes do not count as entries. A digit with zero remaining is
disabled and automatically deselected; erasing or undoing an entry makes it
available again. Number entry never opens the software keyboard.
Placing the last occurrence also clears the square, house, and matching-digit
highlights; ordinary number-first placements keep the digit armed for repeated entry.
Successful number placements give a brief accent pulse and digit-settling animation,
without blocking input. This feedback is skipped when Android disables animations.

- **Notes** changes square taps into manual pencil-note toggles.
- **Auto notes** resets all annotations to legal candidates when turned on.
  To reset manual changes, switch it off and on again. Switching it off keeps
  the current notes, so you can continue working manually.
  Its preference is saved separately for Easy, Medium and Hard; all are available in
  Settings. Existing saved games seed their own difficulty's preference without
  resetting manual notes; other difficulties inherit the previous global default.
- Peer constraints always hide impossible notes without deleting your decisions.
  Replacing, clearing, or undoing an entry restores exactly the previously kept
  candidates that become possible, including the entry square's own notes.
  Multiple simultaneous blockers must all be removed before a note reappears.
- **Show mistakes** compares entries with the unique solution. Off by default;
  no penalties. Its setting is preserved with the game.
- Duplicate numbers in a row, column, or box are always blocked, independently
  of Show mistakes. The attempted square briefly flashes red; the rejected move
  leaves numbers, notes, undo history, counts, and the armed digit unchanged.
  Locally legal values are not rejected just for differing from the solution.
- **Undo** restores placements, erasures, manual notes, and auto-note resets.

The bottom controls contain only accessible Undo/Erase icon buttons, Notes, and the nine digits with
their remaining counts; instructional and input-status text is omitted.
Undo, Erase, and Notes have consistent 16dp internal padding and 56dp-high touch
targets, with extra width for the Notes label. Erase uses a trash-can icon but
still clears only the selected editable cell; it never deletes the game.
Difficulty and elapsed time share a centered header, without a filled-cell count.
Custom buttons consistently use flat rounded surfaces with native ripple feedback;
accent outlines separate secondary actions from the surrounding canvas, with
stronger selected outlines and muted completed-digit surfaces. Native elevation
animators are disabled to avoid clipped, inconsistent shadows.

## Puzzle generation and difficulty

Every generated puzzle starts with a randomized backtracking solution.
One shuffled greedy pass tries removing each of the 81 givens, keeping a removal
only when a separate solution-counting search establishes exactly one solution.
This common candidate pool has no difficulty filter or clue-count target.
The requested tier is selected by rejection sampling from that pool, using these
fixed-point human-technique rules:

| Mode | Acceptance contract |
|---|---|
| Easy | Solved using hidden singles alone: for a digit, exactly one legal empty square remains in a row, column or 3x3 box, after excluding all filled peers. Repeat full number-by-number passes until solved or a pass places nothing. |
| Medium | Hidden singles alone stall. Restart from the original givens and repeatedly apply both hidden singles and naked singles (a square with only one legal candidate). These two rules together solve the puzzle. |
| Hard | Both rules together reach a fixed point with empty squares remaining. The puzzle still has exactly one solution, independently established by search. |

Candidates are recomputed from filled peers only. No locked candidates, subsets,
X-wings, guessing, arbitrary hidden-single step limits or clue-count thresholds
participate in these ratings. A pass processes digits 1-9 across all rows,
columns and boxes, placing immediately; the combined solver then scans all
cells for naked singles. Both repeat through a terminal no-change pass.
Hard no longer promises completion by a particular advanced logical solver:
it means precisely that these two simple rules do not finish.

These are operational rules, not universal human difficulty ratings.
Generation remains bounded at 180 candidates, cancellable and off the UI thread;
failure reports a retryable error rather than substituting another tier.
Legacy `FOCUSED`/`MEDIUM`/`HARD` save IDs, saved games, curated categories and
human votes are unchanged. Only fresh generated puzzles use the new acceptance
rules; imported curated categories remain the curator's choice.

### Reproducible 100-puzzle experiment

Run `.\gradlew.bat --no-daemon classificationBatch` to write
`dist\puzzle-classification-20261111\puzzles.csv`, `traces.csv` and `summary.json`.
The fixed sample uses one `java.util.Random(seed)` per candidate, seeds
**2026111100-2026111199**, with the common removal process above: no tier
requests, quotas, duplicate replacement or tuning after viewing the split.
All 100 exact-givens SHA-256 identities are distinct. These are greedy
irreducible puzzles (no retained given can be removed alone), not uniformly
sampled Sudoku puzzles or globally minimum-clue puzzles.

| Classification | Count / percent | Starting clues | Hidden placements | Naked placements |
|---|---|---|---|---|
| Easy | 21 / 21% | 23-26 | 55-58 | 0 |
| Medium | 23 / 23% | 23-27 | 32-47 | 8-25 |
| Hard | 56 / 56% | 22-26 | 1-29 | 0-13 |

Placement ranges use the hidden-only trace for Easy and the combined trace
for Medium/Hard. Hard leaves **23-57 empty squares**; Easy and Medium leave zero.
Pass counts, including the terminal no-change pass, are respectively 5-13,
4-11 and 2-6. The initial local run took **1.307 seconds** including generation,
classification, independent verification and CSV writing; timing is
machine/JIT-dependent and not a phone performance claim. The split describes
only this sampler and seed range, not natural prevalence.

`puzzles.csv` contains index, seed, identity, all 81 original givens (`0` means
empty), category, clue count, uniqueness result, both solvers' placement/pass/
residual counts and timings. Treat givens as strings when opening CSV in a
spreadsheet so leading zeros are preserved. `traces.csv` records every placement
in both stages. Cell indices are zero-based row-major; houses 0-8 are rows,
9-17 columns, 18-26 boxes; house `-1` denotes a naked single.
Each trace is independently replayed using separate peer arithmetic, and a
snapshot-wave solver (rather than immediate placement) must reach the same
fixed point. A second independent search proves uniqueness for every puzzle.
The batch fails on a duplicate, disagreement or invalid inference rather than
quietly dropping/replacing a puzzle. Results stay outside the APK.

## Persistence and interface

The current puzzle, solution, entries, underlying note decisions, toggles, selected
cell, armed digit, play time, and last 500 undoable actions are saved atomically in
app-private storage. Rotation and activity/process recreation restore the game.
Starting a replacement game asks for confirmation; only one current game is kept.
Uninstalling or clearing app storage removes it.

Time runs only during play, pausing in the menu, settings, and background.
A solved board celebrates with animated digits and confetti, followed by elapsed
time, offered difficulty, optional **Classify puzzle**, and **Done**, which returns to the main menu.
Classification records how the puzzle felt: Easy, Medium or Hard. It never
changes the game's offered difficulty, timer or personal-best category.
The system's
reduced-motion preference skips the animation. Resume is only offered for unfinished
games; the menu offers simply **Easy**, **Medium** and **Hard**, without difficulty explanations,
help or past-result entries, alongside Settings.
Each difficulty shows its personal-best completed solve time when one exists.
Only a correctly completed puzzle can set a record, using active play time;
slower solves do not replace it. Records survive new games and settings changes.
An existing completed saved game can seed its difficulty's record.
Settings uses a borderless gear icon; gameplay returns to the menu with a
borderless three-line icon. Both retain accessible labels and touch feedback.
Settings closes with a borderless X icon; per-difficulty switches are labeled
**Auto notes: Easy**, **Auto notes: Medium** and **Auto notes: Hard**.
Legacy `FOCUSED` (Easy) and `HARD` save/preference identifiers remain unchanged.
Medium uses `MEDIUM` and inherits the old global auto-notes default on first use;
existing per-difficulty preferences, game state and best times are preserved.

Settings offers a saved **System / Light / Dark** appearance choice, independently
of the accent color. System is the default and follows device changes; explicit
Light/Dark affects only Sudoku, including dialogs. Changing appearance preserves
the current game and returns to Settings after recreation.
Portrait puts the board above the controls;
landscape puts the board alongside them, with the bank below the controls. Both
layouts account for system bars and cutouts. The board exposes 81 individually
described accessibility nodes, including row, column, value, fixed-clue status,
visible notes, and optional mistake status. A physical keyboard can navigate with
arrows and enter digits; it is not required.

## Offline classification and curation

Complete a puzzle correctly, then choose **Classify puzzle** on the win screen.
The choice is saved immediately and can be changed on that screen before **Done**.
Skipping classification still records completion. There is one local record per
exact set of givens, not a cross-user vote total. Records contain only the puzzle,
the difficulty first offered to this player, and their optional classification:
no device identity, account, solve time or gameplay annotations.

For each difficulty, new games use the first unseen curated puzzle in collection
order. Only valid completion consumes it; selection, abandonment and Resume do not.
After that tier's collection is exhausted, the same locally certified generator
supplies new games. Completion identities persist independently of the collection,
across restart and re-import, including puzzles previously completed as generated
games. New unseen imports become eligible immediately. A completed legacy current
save seeds this history; earlier completions cannot be reconstructed from best times.

### Export → curate on PC → import

1. Open **Settings → Export puzzle records**. Choose a local folder in Android's
   document picker. Copy `sudoku-puzzles.json` to your PC using USB file transfer
   or another transfer method you choose. Export never replaces the unfinished game.
2. In the JSON file, select records from `votes`. Copy each chosen record's `id`
   and `givens` into `puzzles`, with the curator's chosen `difficulty`. Usually this
   comes from `classification`, but the curator decides. Keep `votes` empty in the
   distribution file. Merge submissions by `id`, resolving disagreements yourself:
   identical puzzles should appear once, assigned to one difficulty.
3. Copy the curated JSON back to the device. Choose
   **Settings → Import curated collection**, then select it. The app checks every
   record off the UI thread, validates unique solvability, and atomically adds the
   entire collection or rejects it without changing existing records or the current
   game. Import reports additions, identical duplicates and ignored vote records.

This PowerShell example selects all classified local records; edit the resulting
`puzzles` list to select a smaller collection or change curator classifications:

```powershell
$records = Get-Content -Raw .\sudoku-puzzles.json | ConvertFrom-Json
$selected = @($records.votes | Where-Object { $null -ne $_.classification } |
    ForEach-Object {
        [ordered]@{ id = $_.id; givens = $_.givens; difficulty = $_.classification }
    })
[ordered]@{
    format = 'quietgrid-sudoku'
    version = 1
    puzzles = $selected
    votes = @()
} | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 .\curated.json
```

Imports are additive, not collection replacement. Identical rows are deduplicated.
Conflicting difficulties for an existing ID or within a file reject the whole
import; this version does not remove or reclassify already imported catalog entries.
Resolve conflicts against your existing catalog before importing.
Vote records in a file are validated but **never** imported as local votes or
completion history. Merely importing someone else's completion must not consume
their puzzles for you.

### Version 1 interchange contract

Use UTF-8 JSON (an initial BOM is accepted). All four top-level fields are required,
and unknown fields are rejected. A complete valid collection example:

```json
{
  "format": "quietgrid-sudoku",
  "version": 1,
  "puzzles": [
    {
      "id": "19b928a88dcad4ac3117b40c27e2fc5cab100e991ff23d7dcd70f84c68baaaa5",
      "givens": "530070000600195000098000060800060003400803001700020006060000280000419005000080079",
      "difficulty": "Easy"
    }
  ],
  "votes": []
}
```

`givens` is exactly 81 ASCII digits, row-major, with `0` for an empty square.
`id` is lowercase SHA-256 of those exact 81 ASCII bytes, with no newline or prefix.
Do not hash the solution, player entries, JSON text or a transformed equivalent
puzzle. Preserve exported IDs when curation does not change the givens.
`difficulty` must be exactly `Easy`, `Medium` or `Hard`.

A vote has exactly `id`, `givens`, `offeredDifficulty` (the same three labels) and
`classification` (one label or JSON `null`). A catalog entry has exactly `id`,
`givens` and `difficulty`. Human classification and catalog assignment are **not**
algorithmic certification: a unique puzzle may be curated into any tier even if
the logical solver rates it differently or cannot solve it without search.
Generated puzzles always satisfy their algorithmic acceptance contracts.

Imports are limited to 2 MiB, 2,000 catalog rows and 2,000 informational vote rows;
the stored curated collection is capped at 2,000 puzzles. Validation is cancellable,
with a 200,000-search-node limit per uniqueness check. Unsupported versions,
malformed UTF-8/JSON, duplicate keys, bad IDs, invalid/filled/nonunique/unsolvable
grids, unknown classifications and conflicting entries produce explicit errors.
Very expensive unique grids can be rejected by the validation budget.
Local completion history is not truncated to the import limits. Large lifetime
exports can exceed them: curate a bounded subset and remove `votes` before import.
Export includes all local records and the existing collection; it is not a
save-game or completion-history backup/restore facility.

Cancelling or interrupting import leaves local data unchanged. Android document
providers do not offer universally atomic export: a failed/interrupted export
may leave a partial destination file; delete that file and export again.
Cloud-backed document providers may transfer a chosen file under their own
policies; choose local device storage to keep the entire workflow offline.

## Build

Requirements: Java 17, Android SDK platform 36, build-tools 36.0.0. The installed
app supports Android 10 (API 29) and newer. This project uses Java and native
Android Views, AGP 8.13.0, and a checked-in official Gradle 8.13 wrapper whose
distribution is SHA-256 pinned. It has no third-party app dependencies.
Build-time dependency artifacts are SHA-256 pinned in
`gradle\verification-metadata.xml`; Gradle verifies them in strict mode.
See `SECURITY.md` before updating dependencies or adding automation.
The verification baseline was produced on Windows. If another platform needs
an unlisted native build-tool artifact, review its upstream origin and checksum
before adding it; do not disable verification.

In PowerShell, set Java 17 and your Android SDK paths, for example:

```powershell
$env:JAVA_HOME = 'C:\Path\To\Java17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat --no-daemon engineTest :app:assembleDebug :app:lintDebug
```

In Android Studio, choose your installed Java 17 as the **Gradle JDK**
(Settings > Build, Execution, Deployment > Build Tools > Gradle); the Studio
bundled Java 25 is newer than this pinned Gradle version supports.
`local.properties` contains this machine's SDK path and is ignored by git.

Development APK: `app\build\outputs\apk\debug\app-debug.apk`.
This is a debug-signed development APK, not a Play Store release.
It retains the production application ID. Do not install it over an existing
phone app to try curation without explicit authorization and a save-preservation
plan; a compatible debug installation could be replaced. Building does not
install anything. Release version/signing configuration is unchanged by this feature.

## Public sideload releases

No app store is required: publish the release APK on a website or a repository's
release page so people can download and install it themselves. This does not add
internet access to the app. Android 10 or newer is required.

Create your private signing key once on Windows, then build:

```powershell
.\scripts\New-ReleaseSigning.ps1
.\gradlew.bat --no-daemon engineTest :app:assembleRelease :app:lintRelease
```

The signed, non-debuggable APK is
`app\build\outputs\apk\release\app-release.apk`.
Release builds require the private signing properties and keystore;
they do not fall back to a debug key. A source recipient must create their own
key rather than receive the publisher's private key.

On Windows these are stored outside the project at
`%LOCALAPPDATA%\QuietGrid\Signing`. `SUDOKU_SIGNING_DIR` can override that path.
On other systems the default is `~/.quietgrid-signing`; create an equivalent
private `signing.properties` containing `storeFile`, `storePassword`, `keyAlias`,
and `keyPassword`, with the keystore in the same directory.

**Privately back up the entire signing directory and keep it secret.**
Future compatible updates require the same key, application ID, and an increased
`versionCode` in `app\build.gradle`. Losing the key prevents signing compatible
updates. A build signed by another publisher cannot update this publisher's app.
The public release uses a different certificate from the development APK and
cannot directly update an existing debug installation. Do not uninstall a
development installation just to test this release if it contains saves you need.

For a public source archive, include only source, resources, build scripts,
the Gradle wrapper, scripts, and documentation/license files. Do not ZIP the
whole working folder: `.gitignore` does not filter ZIP contents. Exclude
`.signing`, `local.properties`, `.gradle`, `.idea`, all build directories,
and private keys/passwords. Do not distribute the instrumentation test APK.

On the downloading device, open the APK and, if required, allow app installation
from that specific browser/file manager in Android's settings. Turn that
permission off afterward if desired. Do not bypass Play Protect or other
unsafe-app warnings. Publish a SHA-256 checksum alongside the APK.

## License and privacy

Original project content is MIT-licensed; see `LICENSE`.
Third-party licensing and icon attribution are in `THIRD_PARTY_NOTICES.md` and
`licenses\APACHE-2.0.txt`. These notices, the MIT license, and `PRIVACY.md` are
also embedded in every APK under `assets/legal`.

The app collects/transmits no data and has no network permission. It keeps game
state, best times, and preferences locally. See `PRIVACY.md` for the complete
statement. A download host's privacy policy applies separately to APK downloads.

```powershell
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb -d install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb -d shell am start -W -n com.quietgrid.sudoku/.MainActivity
```

## Verification

`engineTest` is a dependency-free JVM suite covering note provenance, overlapping
blockers, replacement/clearing/undo, manual edits and reset, immutable clues,
remaining-count clamping, exhausted-digit disarming/re-enabling, armed input and
notes-mode transitions, persistence/corrupt saves/history bounds,
and 36 reproducibly seeded unique puzzles across all three difficulty contracts.
The curation suite exercises exact identity, classification persistence, old
SUD2/SUD3 saves, UTF-8 and JSON validation, unique/unsolvable/ambiguous grids,
atomic conflict rejection, export/import separation, curated-first selection,
completion-only consumption, generated fallback and unseen entries after re-import.

Device tests exercise the real native controls, digit switching/cancelling,
outside taps, given-cell behavior, note editing, exhausted digits, mode switching,
menu/resume timing, settings/colors, win flow, both layouts, rotation, recreation,
and generation on hardware. Swatch checks cover every accent-selection transition
in both appearances, including native drawable colors and rendered pixels.
Instrumentation targets the separate `com.quietgrid.sudoku.validation` package
by default, labeled **Sudoku validation**, never the player installation. Fixtures
replace only that validation installation's game, settings and puzzle library,
including atomic-file recovery artifacts. A curation device test covers native
classification, offered-difficulty best-time preservation, Medium preferences,
curated selection and restart/Resume consumption. Each test normally
restores them in teardown, but a process crash can prevent that cleanup; keep a
durable backup if you choose to retain anything in the validation installation.
Do not interact with it while tests are running.
The document-picker test creates actual JSON documents in Downloads, verifies
their exported bytes, imports/re-imports them through Android's native picker,
and checks atomic rejection of malformed, unsupported, nonunique and conflicting
collections without changing the unfinished game's saved bytes. Its uniquely
named document fixtures are deleted afterward. The native curation lifecycle
test covers all tiers, abandonment/Resume, completion-only consumption, Medium
generation after exhaustion, and newly imported unseen entries taking priority.
New UI assertions use `runTestOnUiThread` so failures propagate to instrumentation
rather than crashing the app's main thread.

The optional `testSystemThemeChangesPreserveGame` check temporarily requests system light/dark mode and restores the original
setting. On devices that lock that system setting, tests explicitly report the
limitation and validate configuration-specific board rendering and contrast instead;
they do not claim a live system-theme transition occurred. The command below
excludes that check, leaving OS settings unchanged.

```powershell
.\gradlew.bat --no-daemon :app:assembleValidation :app:assembleValidationAndroidTest
& $adb -s <authorized-serial> install -r .\app\build\outputs\apk\validation\app-validation.apk
& $adb -s <authorized-serial> install -r .\app\build\outputs\apk\androidTest\validation\app-validation-androidTest.apk
$methods = [regex]::Matches((Get-Content .\app\src\androidTest\java\com\quietgrid\sudoku\DeviceTests.java -Raw), 'public void (test\w+)\(') |
    ForEach-Object { $_.Groups[1].Value } |
    Where-Object { $_ -ne 'testSystemThemeChangesPreserveGame' }
$tests = ($methods | ForEach-Object { "com.quietgrid.sudoku.DeviceTests#$_" }) -join ','
$result = & $adb -s <authorized-serial> shell am instrument -w -r -e class $tests com.quietgrid.sudoku.validation.test/android.test.InstrumentationTestRunner
$result
if (($result -join "`n") -notmatch "OK \($($methods.Count) tests?\)") {
    throw 'Native instrumentation did not explicitly pass every requested test.'
}
```

Do not treat adb's exit code alone as success: instrumentation can exit zero
while reporting failed assertions. Require the explicit `OK (N tests)` result.
Successful device tests save `sudoku-menu.png`, `sudoku-settings.png`,
`sudoku-portrait.png`, `sudoku-landscape.png`, and `sudoku-win.png` under the app's
external files directory. No emulator or USB-driver installation is necessary
for an already authorized USB-connected device. The verified device identified
itself as STK-LX3 running Android 10.
