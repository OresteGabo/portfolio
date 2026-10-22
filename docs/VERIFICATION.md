# Verification — 23 October 2026

## Build compatibility

The project uses Gradle 9.1.0, AGP 9.0.0, Kotlin 2.3.21, Compose Multiplatform 1.11.1, Material 3 1.11.0-alpha07, Lifecycle 2.10.0, and compile/target SDK 36. This resolves the installed Android Studio's AGP compatibility error without bypassing dependency metadata checks. Android Studio should resync with **Sync Project with Gradle Files**.

## Shared application

Android and iPhone now host the same Compose `App()`, screens, navigation and lifecycle ViewModel. API/session handling, JSON contracts, coupon dates, formatting and content are shared. File dialogs, sharing, preferences, HTTP transports, PDF extraction and OCR have native adapters. See [ARCHITECTURE.md](ARCHITECTURE.md).

The following checks passed during the refactor:

- 13 core tests on JVM, Android host and iOS simulator: coupon schedules/tax/accrual/maturity, JSON records, Origin/cookies, session clearing, redirects, API errors, origin/path policy and oversized response rejection.
- 5 shared presentation tests on Android host and iOS simulator: formatting/PDF envelope validation, expired-session cleanup, account isolation on server changes, and duplicate-action prevention.
- 16 JavaScript tests, including native-bundle parity with the original projection and purchase-parsing modules.
- 3 Android integration tests on each of Pixel 9 Pro API 36 and Medium Phone API 36.1: actual Android HTTP cookies/Origin, local engine projection, and synthetic equity PDF extraction through bundled PDF.js.
- 3 native iOS tests: JavaScriptCore projection, PDFKit text-PDF purchase extraction, and Vision scanned-PDF OCR feeding the same shared purchase parser. Fixtures are generated synthetic documents.
- Android debug APK assembly and lint (no errors); server tests; Kotlin compilation for physical iPhone arm64.
- Xcode Debug simulator app build and installation on iPhone 16 Pro / iOS 18.6. A [runtime screenshot](screenshots/ios-shared-sign-in.png) confirms the shared portfolio sign-in screen replaces the Click-me/Hello-Compose starter.

The iOS app tests require a booted simulator. Kotlin's default `simctl --standalone` mode returned empty Vision observations for the same fixture that passed in a normal simulator process. The shared test task therefore disables standalone mode and selects `booted`; use `-PiosTestDevice=<UDID>` to select a specific booted simulator. All 8 shared/native iOS tests pass with this configuration.

Commands:

```sh
./gradlew :core:jvmTest :core:testAndroidHostTest :core:iosSimulatorArm64Test \
  :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test \
  :app:shared:compileKotlinIosArm64 \
  :app:androidApp:assembleDebug :app:androidApp:lintDebug :server:test
./gradlew :app:androidApp:connectedDebugAndroidTest
node --test tests/*.test.mjs
xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.6' \
  CODE_SIGNING_ALLOWED=NO build
```

## Limits

Screenshots and import tests use synthetic records, not private account data. Real account login, private document downloads, broker-specific scanned PDFs, and production mutations were not exercised with the owner's credentials. Physical-device signing and deployment remain dependent on the existing Xcode signing setup. Owner account deletion and password-hash setup remain available on the original website; exact parity with every advanced web table control is not claimed.
