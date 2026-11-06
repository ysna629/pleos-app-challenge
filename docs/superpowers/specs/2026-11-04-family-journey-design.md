# Family journeys inside 도착노트

The user approved adding a family-trip utility to the existing application after discussing automatic parking capture, GPS-based Kakao place suggestions, voice memories, and a trip-map recap. Deliver an interactive Compose mockup in the current package; no separate application.

## Experience

Add 여행 기록 to the existing top navigation and a home entry card. A journey has a title, start/end times, sampled coordinates, and parking stops. Starting and ending, place selection, favorites, and note editing require a confirmed P gear. Read-only history remains available. A sample journey can be previewed without altering live history, location or the business logbook; sample labels must be explicit.

The screen has a route overview with numbered selectable stops and a visit timeline/details panel. Render the recorded coordinates locally as a route overview, explicitly distinguish it from a street-map basemap. Highlight saved places. Provide an empty state, no-location state, missing-key message, loading/failure state for nearby search, and completed journey history. A sample illustrates several family stops with fictional names and notes.

## Data and lifecycle

Keep journey state in a pure Kotlin reducer, and persist it in the existing `trips` preferences so consent withdrawal deletes everything. An explicit active journey gates GPS recording. Use coordinate validity and freshness checks, cap stored route samples, and close/reopen stops from P transitions without duplicating repeated callbacks. Store an address-only stop when no fresh fix is available; never use stale coordinates to search. Do not pretend to record while the activity is destroyed. Persisted active journeys resume on reopening, with a route break so missing data is not drawn as observed movement.

Expose location updates from CarSignals and feed them on the main thread. Reject queued vehicle/location/network/voice callbacks after withdrawal or activity destruction. A family note uses the existing Voice object and state machine, and driving cancels it. Existing business trip collection continues independently.

## Nearby places

Add a small Kakao REST client using HttpURLConnection on a worker thread. Search food, cafe, shopping and tourism categories near a selected real stop, deduplicate and sort by distance. Configure the key through a local Gradle property/environment variable, never commit a key. No configured key means no network call and a clear coordinate-only fallback. Only explicit nearby-search actions transmit stop coordinates to Kakao. Sample suggestions are fictional and never presented as API results. Place confirmation is manual; GPS alone is not proof of a visit.

## Privacy and verification

Bump consent text/version for stored journey traces and explicit external search. User-initiated journey changes and voice input require P, including when gear is unknown. Consented automatic GPS capture and gear-transition bookkeeping operate during the active journey, including while driving. Test reducer behavior for duplicate parking callbacks, route sampling/freshness, stop duration, consent clearing integration, journey completion, and business command precedence. Test real response parsing independently of network. Run Android unit tests, assembleDebug and lint when the toolchain permits. Document demo entry, key configuration, limitations and verification results.
