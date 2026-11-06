# Journey phone viewing and Excel separation

**Goal:** Separate phone viewing from CSV export, reuse the existing Vercel host, and offer Kakao Maps links for recorded stops.

**Approved direction:** The user requested separate QR and Excel functions and authorized implementation plus a scannable hosted test. Place links follow Kakao's documented map/search URLs. No recommendation or navigation engine is added.

**Architecture:** Reuse the immutable journey snapshot and bounded fragment links. The view page renders visit cards; the CSV page retains table/download behavior. Both share the existing decoder. Coordinates are recorded parking positions, not verified POI entrances; links label that distinction. No trip data is uploaded.

- [x] Add failing Kotlin tests for distinct view/CSV URLs preserving the same visits; add JS map-link tests for coordinates, missing GPS, encoding and no location.
- [x] Split Compose actions/dialogs and create the phone visit-card page with Kakao links. Keep CSV table/download on its own page.
- [x] Run Android tests/build/lint, Node tests, and mobile browser checks including a real app QR and CSV download.
- [ ] Publish only viewer files to existing Git-connected Vercel hosting; preserve existing monthly page and unrelated local changes.
- [ ] Install the verified APK, open the family demo with hosted URLs and leave a usable QR visible. Document exact deployment and test limits.

## Current verification and limits

- Independent review found preview/voice state mismatch and cached GPS reversing daily rollover; both fixed and re-reviewed.
- Android tests/build/lint pass; web tests: 8 pass. Phone user confirmed visit cards, CSV coordinate fields and direct Kakao app coordinate opening.
- Web-only commit `07dd7b8` pushed from a separate checkout. Vercel returned `Deployment was blocked`; production journey page remains 404. Later cache/import handling and direct-app links remain local changes. LAN viewer is used for real QR tests.
- Voice PCM was recognized by actual STT, and Mac microphone receives real speech. Initial word loss and limited phrase matching remain unresolved. User explicitly paused voice fixes to prioritize functional tests.
- GPS simulation passed: saved route grew from 107 to 112 points, distance from 0 to 1.169 km, stops from 4 to 5. Last stop saved at approximately 37.456, 127.1575. This verifies emulator capture, persistence and rendering, not real driving or road matching.
- User found overlapping markers (1-4 at one coordinate) made favorite #3 appear under #4. User rejected the initial joined-number labels. Replaced them with one actual visit number and its own star (e.g. `3 ★`), prioritizing the selected visit, otherwise a favorite. A separate same-location count button opens a visit chooser. List selection and marker selection share the stop ID. Groups merge across overlapping members so changing representatives cannot reintroduce collisions. Visual check showed right row #3 selected with map marker `3 ★`; final build/reinstall verification follows.
