# Shibboleth Final Review Fix Report

## Status

`READY TO COMMIT`

All four final-review findings were addressed in one change wave on
`feature/shibboleth-share`. The required focused unit test and Kotlin compile commands pass.
No public codec API, dependency, build configuration, or unrelated source file changed.

## Context

- Worktree: `C:\Users\SKYBBK\AppData\Local\Temp\opencode\legado-shibboleth-share`
- Branch: `feature/shibboleth-share`
- Starting HEAD: `27b807b819d3d7e46f3fb5373dde55a98a3bfd97`
- Planned commit subject: `fix(share): address final token review`
- Approved design read first:
  `C:\Users\SKYBBK\Documents\soft\legado\docs\superpowers\specs\2026-07-15-shibboleth-share-design.md`
- Compatibility donor inspected:
  `b09bff9ea8ac84f812a5bf1801f252e7d7d40cfd`

## Changes

### Exact suffix validation

`Shibboleth.parse` now requires `Sigma^` to start immediately after `¥`. It no longer
accepts arbitrary suffix text merely because a later caret exists. Focused cases cover
missing, altered, truncated, and reordered suffix text. Existing valid sibling fixtures
still decode successfully.

### Delayed clipboard import

`MainActivity.importShibbolethFromClipboard` now returns before reading the clipboard
unless the activity lifecycle is at least `RESUMED` and FragmentManager state has not
been saved. The valid branch exhaustively maps all seven enum values to their existing
dialogs, calls `showDialogFragment` first, and clears the clipboard only after that call
returns successfully. Invalid and expired tokens retain their existing toast and
non-clearing behavior.

### Lowercase encoder scheme

`Shibboleth.canEncode` now requires the exact lowercase `https://` prefix in addition to
structural URL validation. Uppercase and mixed-case HTTPS forms are rejected by both
`canEncode` and `encode`. Parsing remains case-insensitive for valid raw HTTP(S) URLs.

### Donor compatibility fixtures

The test suite now contains six complete hard-coded token fixtures for `sy`, `dy`, `zd`,
`jh`, `ml`, and `ld`, using URL
`https://example.com/path/file.json?key=4%2F5`, seed `1789344000000`, and expiry
`1800000000000`. Each exact encoded envelope is asserted and each fixture is decoded.
`rw` is intentionally absent because it is this fork's extension, not sibling-compatible.

## TDD Evidence

### Baseline

Before editing, the required focused command passed with the existing 14 tests:

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.utils.ShibbolethTest" --max-workers=1 --no-parallel
```

Result: `BUILD SUCCESSFUL in 28s`.

### RED

Tests were added before production changes. The first run executed 16 tests and failed
exactly the three new behavior groups:

```text
ShibbolethTest > fixed donor fixture encodes and decodes all sibling compatible types FAILED
ShibbolethTest > encoder accepts only valid https URLs FAILED
ShibbolethTest > suffix must be exact literal immediately after yen marker FAILED
16 tests completed, 3 failed
BUILD FAILED in 33s
```

The fixture sentinel failure exposed the seeded donor-compatible URL body:

```text
#L:example店🛜1刚path钢file店串?key=🕓拜2F五
```

That value follows the donor commit's ordered mappings and `kotlin.random.Random(time)`
algorithm and was then pinned in six complete literal envelopes.

### GREEN

After the minimal codec and import-flow changes, the same required focused command passed:

```text
BUILD SUCCESSFUL in 1m 34s
60 actionable tasks: 7 executed, 53 up-to-date
```

All 16 `ShibbolethTest` tests passed with zero failures.

## Required Verification

### Focused unit tests

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.utils.ShibbolethTest" --max-workers=1 --no-parallel
```

Result: passed, `BUILD SUCCESSFUL in 1m 34s`, 16 tests and 0 failures.

### Kotlin compile

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --max-workers=1 --no-parallel
```

Result: passed, `BUILD SUCCESSFUL in 27s`, 27 tasks up-to-date.

### Git inspection

Commands inspected before commit:

```powershell
git status --short --branch
git diff --check
git diff -- app/src/main/java/io/legado/app/utils/Shibboleth.kt app/src/test/java/io/legado/app/utils/ShibbolethTest.kt app/src/main/java/io/legado/app/ui/main/MainActivity.kt
git log --oneline -10
```

`git diff --check` exited successfully. Git emitted only existing working-tree line-ending
notices that LF will be replaced by CRLF when Git next touches the three Kotlin files.

## Files

- `app/src/main/java/io/legado/app/utils/Shibboleth.kt`
- `app/src/main/java/io/legado/app/ui/main/MainActivity.kt`
- `app/src/test/java/io/legado/app/utils/ShibbolethTest.kt`
- `.superpowers/sdd/final-fix-report.md`

## Self-Review

- Confirmed suffix validation checks the full literal at the exact post-`¥` offset.
- Confirmed lowercase mapping and encoder eligibility now agree.
- Confirmed raw URL parser scheme checks remain case-insensitive.
- Confirmed all six donor-compatible codes have complete exact fixtures and `rw` does not.
- Confirmed all seven import enum branches remain exhaustive with no fallback route.
- Confirmed clipboard access occurs only after both lifecycle guards.
- Confirmed clipboard clearing occurs after successful dialog submission and cannot run if
  `showDialogFragment` throws.
- Confirmed invalid and expired branches do not clear clipboard.
- Confirmed no public codec declarations or signatures changed.
- Confirmed no unrelated files are part of the source/test diff.

## Concerns

- Gradle reports existing restricted native-access and deprecated-feature warnings. Neither
  command reported a Kotlin compilation warning or test failure.
- The lifecycle guard intentionally skips the one-shot delayed import if the activity is not
  resumed when the callback fires; this follows the review requirement and avoids clipboard
  reads or FragmentManager transactions from an unsafe lifecycle state.
