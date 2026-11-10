# Kakao maps and account login (checked 2026-11-04)

## Map display and nearby search

Kakao Local category search accepts GPS coordinates, radius and category. It authenticates the application with a REST API key, not an end-user login token. The prototype calls this API only after the driver selects nearby search while P is confirmed. No API key is needed for the isolated sample journey.

Official docs: https://developers.kakao.com/docs/ko/local/dev-guide

## Can the car sign in using a QR code?

Kakao staff confirmed on 2025-10-02 that web REST authorization can request `prompt=qr_login`. The Android SDK itself does not expose an option to display a QR login UI. This parameter is described in the staff reply but is absent from the main REST reference, so verify it on the Pleos browser before relying on it for production.

- Staff response: https://devtalk.kakao.com/t/sdk-qr/146595
- OAuth flow: https://developers.kakao.com/docs/ko/kakaologin/rest-api

Two possible implementations:

1. Display Kakao's hosted authorization page on the car using a supported browser surface, request QR authentication, and handle the configured OAuth redirect. This needs platform/browser validation and registered Kakao app credentials.
2. Display our own short-lived pairing link as a QR on the car. The phone opens our HTTPS page and completes normal Kakao Login. Our backend validates state, exchanges the authorization code, and binds the confirmed user to the waiting car session. The car receives only our application session through an authenticated, expiring, single-use pairing flow. Show a matching code on phone and car and ask the user to confirm the intended vehicle. Never put access tokens or a client secret in a QR.

Option 2 is our proposed architecture, not a Kakao-provided device authorization API. It requires a backend, registered redirect URI, logout/account-switch handling for shared vehicles, and a working network. It does not require a separate phone app. The current local prototype implements neither production authentication nor a simulated successful login.

## Kakao Login is not KakaoMap favorites sync

Signing into Route94 with a Kakao account identifies a Route94 user. It does not sign an embedded map SDK into the KakaoMap consumer application or grant access to that application's saved places. Kakao staff confirmed on 2026-10-06 that there is no API/alternative integration for importing KakaoMap favorite groups; a shared group URL may only lead users to KakaoMap.

Staff response: https://devtalk.kakao.com/t/url/151956

The mockup therefore keeps journey favorites inside Route94. Cross-device syncing of our own favorites can be added behind our own user accounts, independently of KakaoMap's favorites. Account sign-in is optional for the local map/search functionality.

## Build and prototype boundaries

Configure `kakaoRestApiKey` as an untracked user Gradle property or `ORG_GRADLE_PROJECT_kakaoRestApiKey` environment variable. Rebuild the app. The prototype embeds this app key in the debug build; use an appropriate key-management/network design before public distribution. Never commit a key or client secret.

The route panel is a local geographic plot of collected coordinates, not Kakao map tiles or a calculated driving route. It works offline. A full basemap SDK integration remains separate from nearby REST search. Sample places are fictional. API results are shown for user confirmation and do not prove the user visited a specific business. Confirm the provider's permitted retention/display terms before distributing a service that stores returned place metadata.

Pleos supplies vehicle coordinates; it does not expose the family's movements after they leave the car. Stops show parking candidates, which the driver can name, favorite or annotate.
