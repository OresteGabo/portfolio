# Testing the standalone Spring API

The production default remains `https://bonds.orestegabo.dev`. Local API selection is an explicit **Debug-only build override**. It does not write over the saved server preference or reuse a production-origin session/password vault.

## Local backend

Start the separate `gabo-portfolio-api` project with profile `local`, its private `.env` configuration and its PostgreSQL container. Check `/actuator/health` on port 8080. Set a real owner email in `BONDS_ADMIN_EMAIL`; the template's `you@example.com` is only an example. Sign in using the original password used to generate `BONDS_ADMIN_PASSWORD_HASH`.

For a physical phone, use the Mac's Bonjour hostname, for example `http://your-mac.local:8080` (find the name with `scutil --get LocalHostName`). The phone must reach the Mac on the local network. Add this exact origin to the backend's private `GABO_ALLOWED_ORIGINS` and restart the backend. Include `http://localhost:8080` as well for terminal checks. On iOS, allow the local-network permission if prompted.

## Android

```sh
./gradlew :app:androidApp:assembleDebug \
  -PdevelopmentApiOrigin=http://your-mac.local:8080
```

Install the debug APK with `adb install -r` to preserve data. The existing Debug manifest permits local HTTP; Release has neither that exception nor a development origin. For the emulator, `http://10.0.2.2:8080` is another option; add that exact origin to the backend. For a USB phone, `adb reverse tcp:8080 tcp:8080` permits a build targeting `http://localhost:8080`; that forwarding lasts only while the connection is active.

## iPhone

```sh
xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'platform=iOS,id=YOUR_DEVICE_UDID' \
  GABO_DEVELOPMENT_API_ORIGIN=http://your-mac.local:8080 build
```

The build setting is included in Info.plist and read only by a Kotlin Debug binary. Existing ATS local-network support covers `.local` development hosts; no global ATS exception is added. Install over the existing app to preserve its settings. Sign in to the local account separately; Apple website-password association remains with the production website.

In Xcode, the same setting can be supplied as a user-defined Build Setting for the Debug configuration. Leave the shared Config.xcconfig default empty. Build again without the override to return to the normal saved/default connection. Release ignores a supplied development origin.

## Verification

Shared unit tests verify release/default selection. `:core:verifyLocalSpringApi` is an opt-in live smoke check using the same Kotlin HTTP client as the app. It requires `GABO_VERIFICATION_SESSION_FILE` pointing to a private temporary JSON file outside Git with `origin` and an origin-bound `session` snapshot from the native secure-session format. The check verifies authentication, bond/share/document loading, overview principal against full history, every PDF download, and logout. It does not create investments or modify financial records. Use only a short-lived local session and remove the file afterwards; never pass a password or cookie as a Gradle command-line property.

The backend's `python3 scripts/check-login.py` is the interactive login check when a session fixture is not available. It reads the configured email and prompts for the original password without printing it.

The Mac and Spring process must stay running while a phone uses the local API. This does not deploy Spring, change the live website, or redirect production traffic.
