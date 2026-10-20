# CCloud Cinematic — Progress Log

> Resume point for any agent/session. Read this first, then `CINEMATIC_IMPLEMENTATION_PLAN.md`.

## Status snapshot
| Phase | State |
|---|---|
| A — Inventory, baseline, architecture | ✅ Complete |
| B — Foundation & design system | ✅ Complete (adaptive scaffold rework deferred to C) |
| C — Home + adaptive navigation | 🟡 In progress (Home live as start destination; adaptive nav/RTL/UI tests pending) |
| D — TMDB + rich details | ⬜ |
| E — Explore/search/recommendations | ⬜ |
| F — Player polish, TV hardening, docs | ⬜ |

- Branch: `feature/cinematic-discovery`
- Baseline commit (master): `1d04f6e`
- Phase A commit: `aadbc82`

## Environment (this machine) — required to build
1. JDK 17 (system default JDK 27 is too new for Gradle 8.11.1):
   `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
2. Google Maven (`dl.google.com`) returns 404 from this network → use the opt-in mirror init script:
   `./gradlew --init-script scripts/dev/mirror-init.gradle.kts <tasks>`
3. Android SDK Build-Tools 35.0.0 were missing and the Google SDK host is blocked; installed from
   `mirrors.cloud.tencent.com/AndroidSDK/build-tools_r35_macosx.zip` into `$ANDROID_HOME/build-tools/35.0.0`.
No project Gradle/AGP/SDK versions were changed.

## Baseline (master @ 1d04f6e, before any app change)
- `testDebugUnitTest assembleDebug lintDebug` → **BUILD SUCCESSFUL** (3m41s). 0 unit tests existed. 21 Kotlin compiler warnings.
- Lint: 10 errors + 55 warnings suppressed by existing `lint-baseline.xml`; no new issues.
- APKs: per-ABI + universal debug APKs in `app/build/outputs/apk/debug/`.
- No device/emulator validation performed (none attached).

## Completed
### Phase A
- Audit of primary repo + 3 reference repos (cloned to `/tmp/ccref`, commits: vhdmsm `1d5a600`, tizenos `78f14eb`, 30nama-apps `1485327`; all MIT).
- `docs/CINEMATIC_IMPLEMENTATION_PLAN.md`: findings, decision matrix, Mermaid architecture, feature matrix, defaults.
- `scripts/dev/mirror-init.gradle.kts` (env-only).

### Phase B (so far)
- `core/model/ContentModels.kt` — `ContentType`, `DataSource`, `CatalogRef`, `Rating` (kind + reporter), `Availability` (sealed; only `Playable` is playable), `ContentIdentity` (provenance + mapping version).
- `core/model/CatalogMappers.kt` — `Movie`/`Series`/`FavoriteItem` → `CatalogTitle`; existing favorites read as-is (no migration needed).
- `core/model/LoadState.kt` — `LoadState` (Loading/Empty/Ready/Partial/Failed), `FeatureFlags` (TMDB only with user token).
- `core/matching/ContentMatcher.kt` — normalizer (Persian/Arabic folding, digits, ZWNJ, diacritics, articles) + deterministic scored matcher (IMDb-first, type gating, remake rejection, ambiguity margin, threshold 0.95, `ALGORITHM_VERSION=1`).
- `core/playback/PlaybackProgress.kt` — resume/finished rules, fraction, keys (local-only).
- `core/network/RetryPolicy.kt` — `FailureKind`, classifier, jittered exponential backoff, Retry-After (seconds/date; over-cap ⇒ no retry).
- `core/designsystem/CinematicTokens.kt`, `CinematicComponents.kt` — tokens, `RatingPill`, `AvailabilityBadge`, `EmptyState`, `ErrorState`, `LoadingSkeleton`, focus-aware `PosterCard`.
- `res/values/strings_cinematic.xml` + `res/values-fa/strings_cinematic.xml`.
- Tests: `ContentMatcherTest`, `DomainModelsTest` (+ `PlaybackProgressTest`, `RetryPolicyTest`), `ProgressStoreTest`.
- `core/playback/ProgressStore.kt` — bounded local progress store behind `KeyValueStore` (SharedPreferences impl). Not yet wired into the player.

### Phase B verification (2026-10-20)
- `testDebugUnitTest assembleDebug lintDebug` → BUILD SUCCESSFUL; **39 unit tests, 0 failures** (JVM only).
- Lint caught `java.time` (API 26) in `RetryPolicy` → rewritten with `SimpleDateFormat` (minSdk 24 safe).
- Remaining lint: 21 errors outside new code, all pre-existing legacy: 20× `UnsafeOptInUsageError` in `VideoPlayerActivity.kt`, 1× `NetworkSecurityConfig`. (Baseline lint error count was not captured separately — treat these as pre-existing legacy issues, not regressions; none touch `core/`.)
- No device/emulator run. Playback/favorites code paths are unchanged (no legacy Kotlin file modified).

### Phase B finish + Phase C start (session 2)
- **Player resume (local):** `VideoPlayerActivity` gains `EXTRA_MOVIE_ID` + `startWithMovieInfo()`; saves progress on `onPause`/`onDestroy`, seeks once on first READY (or immediately if already READY). Episodes keyed by series/season/episode. All guarded by try/catch; legacy `start()` unchanged. `SingleMovieScreen` now calls `startWithMovieInfo`.
- `core/home/HomeAssembler.kt` (+`HomeAssemblerTest`, 7 tests): rails, dedupe/cap 20, empty-rail omission, hero selection (backdrop + browsable), partial-failure map.
- `ui/home/HomeViewModel.kt`: 4 parallel first-page catalog requests (new/top-IMDb movies & series, server-side sorts), Continue Watching from `ProgressStore` + cached title files, My List from existing `favorites.json`. `LoadState` Ready/Partial/Failed/Empty.
- `ui/home/HomeScreen.kt`: capped 16:9 hero (≤360dp phone / 420dp TV) with scrims, Play only when real catalog source exists, Details; rails of `PosterCard`; landscape `ProgressCard` with real progress; skeleton; error with Retry; partial notice; refreshes local rails on resume.
- Navigation: `AppScreens.Home` (route `home`) added first in bottom bar/sidebar; Splash → Home. Movies tab unchanged.
- Verification: `testDebugUnitTest assembleDebug lintDebug` → SUCCESS, **46 tests, 0 failures**; lint 21 errors = same legacy set. **Not device-tested.**

## Decisions
- The app's catalog is **server-hi-speed-iran.info**, not 30nama; there is no login/session/subscription in this codebase. `SubscriptionRequired` state exists for future use but nothing emits it today.
- Existing screens untouched in Phase B; new code lives under `com.pira.ccloud.core`.
- `supportsRtl` stays `false` until Phase C verifies existing screens under RTL (flipping it now would mirror untested legacy layouts).
- Retry policy is not yet wired into `BaseRepository` (wiring = behavior change; do with repository fixture tests in Phase B-end/C).

## Known defects / risks (pre-existing)
- Hardcoded catalog API key in `BaseRepository` (extractable; not treated as secret).
- `usesCleartextTraffic="true"` globally.
- Raw exception messages reach UI from repositories.
- No resume-position persistence in `VideoPlayerActivity`.

## Next actions
1. Manual check on phone + TV emulator: Home loads, hero Play starts playback, back returns to Home, resume works after reopening a movie, D-pad traverses hero→rails.
2. Phase C remainder: Compose UI tests for Home states (fake ViewModel input via `HomeAssembler`); TV focus restoration (save focused key per rail); tablet/landscape densities; bottom-bar inset padding check with 6 tabs; enable `supportsRtl` after RTL review of legacy screens + Persian legacy strings.
3. Wire `RetryPolicy` into `BaseRepository` with fixture tests (Phase C/D boundary).
4. Phase D: optional TMDB provider (user token, TTL cache from tizen `tmdb.js` values), details enrichment.

Resume command:
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew --init-script scripts/dev/mirror-init.gradle.kts testDebugUnitTest assembleDebug lintDebug
```
