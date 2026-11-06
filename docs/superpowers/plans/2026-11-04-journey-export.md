# Journey CSV Export Implementation Plan

**Goal:** Export stored family-trip visits as Excel-compatible CSV inside the current app.

**Architecture:** A Kotlin export codec creates CSV and bounded compressed QR links. A static journey viewer consumes the same schema and produces the same CSV. A Compose dialog exposes QR pages and Android's document picker with consent/gear guards.

**Tech Stack:** Kotlin, JUnit, Compose, Android Activity Result API, plain JavaScript and Node tests.

## Chunk 1: Export codec

- [x] Add JourneyExportTest.kt for CSV correctness, open/interrupted visits, JSON round trip, sample labeling, and split QR losslessness/ZXing encoding. Run red.
- [x] Implement JourneyExport.kt with an immutable export snapshot, CSV and bounded QR chunks. Run green.

## Chunk 2: Existing-app and viewer integration

- [x] Add viewer/journey-export.mjs and Node tests for schema validation, CSV quoting and parity.
- [x] Add viewer/journey.html with mobile table, sample/partial status, CSV download and invalid-link handling.
- [x] Add CSV/QR dialog to JourneyScreen and guarded CreateDocument flow in MainActivity. Do not deploy remotely.

## Chunk 3: Verify and hand over

- [x] Run Android testDebugUnitTest assembleDebug lintDebug; run Node tests and a real generated-link browser download check.
- [x] Obtain read-only review per requesting-code-review skill and address concrete findings.
- [x] Update README with usage, fields, local viewer URL and deployment boundary. Leave local changes uncommitted.

## Verification results

- Android: `testDebugUnitTest assembleDebug lintDebug` successful, 38 tests / 0 failures, 0 lint errors.
- Web: `node --test viewer/journey-export.test.mjs` successful, 5 tests.
- Real Kotlin snapshot generated a compressed URL and CSV. A local mobile Chromium session rendered 3 visits and downloaded a byte-identical CSV, including BOM, Korean, multiline fields, quotes, negative coordinates and neutralized formulas. Invalid fragments show an error; request paths contain no trip fragment.
- High-compression 1,001-stop regression failed before the fix and passed after applying per-page decoded-size/count limits. All visit numbers preserved.
- Review requested cancellation of active writes, invalidation on non-P/unknown gear and count/decoded-size link bounds. Fixed and re-reviewed: approved. Existing monthly viewer untouched.
- Existing Pleos AVD resolves CREATE_DOCUMENT to `com.android.car.frameworkpackagestubs/.Stubs$DocumentsUIStubWithResult`, not a real picker. Added intent visibility and an explicit phone-QR fallback. Real provider file writing needs verification on a device with a document picker.
- Static phone page has NOT been deployed. Local testing used 127.0.0.1:8766. Hosted QR usage requires publishing journey.html and journey-export.mjs to the existing viewer hosting.

- Installed the final debug APK into the Pleos AVD and confirmed the new CSV button renders and stays disabled while gear is unknown. Final dialog interaction could not be completed: UIAutomator exited137 and the emulator subsequently showed its boot animation. Do not claim end-to-end native picker/QR dialog verification. Mobile browser download verification completed independently.
