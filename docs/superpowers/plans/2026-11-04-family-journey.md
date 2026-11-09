# Family Journey Implementation Plan

**Goal:** Add family journey capture and an interactive map/places mockup to the current 도착노트 app.

**Architecture:** Pure Kotlin journey models/reducer, SharedPreferences persistence sharing consent deletion, a background Kakao search adapter, and a separate Compose screen integrated with MainActivity. Reuse CarSignals and Voice.

**Tech Stack:** Kotlin, Compose Canvas/Material3, Pleos FusedLocation, HttpURLConnection, JUnit4.

## Chunk 1: Capture and nearby search

- [x] Add JourneyTest.kt covering deduplicated stops, timing, fresh/invalid coordinates, lifecycle route breaks and completion.
- [x] Implement Journey.kt and JourneyStore.kt; no automatic business-trip merging changes.
- [x] Add place-response tests and KakaoPlaces.kt; optional local key, worker execution, bounded timeouts, handled errors.

## Chunk 2: Integrated screen and voice

- [x] Add JourneyScreen.kt: responsive route overview, numbered stop selection, trip controls, timeline, saved places, note controls, sample mode, clear loading/empty/error states.
- [x] Connect MainActivity navigation, persisted journeys, parked-only mutation guards, GPS/gear signals and voice notes.
- [x] Protect queued callbacks after collection stops. Update consent and clear all in-memory journey state on withdrawal.

## Chunk 3: Verify and document

- [x] Run `sh ./gradlew testDebugUnitTest assembleDebug lintDebug` with JDK17 and Android SDK.
- [x] Review changes for consent, lifecycle, sample/live isolation and compatibility with the existing logbook.
- [x] Document launch instructions, mockup limitations and optional Kakao key in README.

No publish, deployment, API-key registration, or commits are needed for this local mockup.

## Verification results

- `testDebugUnitTest assembleDebug lintDebug`: BUILD SUCCESSFUL (33 tests, zero failures, zero lint errors).
- Reviewed by a separate reviewer; gear-unknown handling, complete history access and address fallback corrected; re-review found no blockers.
- Installed and launched in the existing Pleos Connect v2.1.0 AVD. Screenshot saved in `docs/mockups/family-journey.png`.
- SDK found at `~/Library/Android/sdk`; Android Studio bundles Java25. Used temporary Temurin17 in `/private/tmp/jdk-17.0.21+9/Contents/Home`; Gradle installed missing platform35/build-tools35 under the existing SDK.
- No real Kakao API key/account was configured, so external search and production authentication were not tested.
- Android UIAutomator selected the adjacent driving panel rather than this multi-window app; screenshot inspection used for layout verification.
