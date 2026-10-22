# Verification — 22 October 2026

The initial, unchanged Android starter assembled successfully using its existing Gradle 9.5.1 / AGP 9.1.1 / Kotlin 2.4.20 setup. No original Android dependency/version failure was reproduced. The exploratory root `build` was stopped while linking iOS frameworks; it is not reported as a successful full multiplatform build.

The implemented Android app passed:

```sh
./gradlew :app:androidApp:assembleDebug \
  :app:androidApp:testDebugUnitTest \
  :app:androidApp:lintDebug \
  :app:androidApp:connectedDebugAndroidTest
node --test tests/*.test.mjs
```

- 7 host tests: coupon schedules, withholding tax, accrual boundaries, maturity behavior, month-end dates, Origin header, session cookies, rejected API paths, and server errors.
- 14 JavaScript tests: original PDF field extraction, explicit coupon schedules, OCR review flags, ambiguous allocations, malformed dates, hostile PDF trailers, equity fee totals, contribution periods, and cash injections.
- 3 device tests on Medium Phone API 36.1: Android Origin/cookie behavior, projection through the attached local engine, and a generated synthetic equity PDF through the actual bundled PDF.js parser.
- Android lint: no errors. Remaining warnings concern newer dependency versions and starter launcher icon resources.

PDF/OCR and simulation run in a restricted local WebView that must be attached to an Android window for posted bridge callbacks. The device tests use a debug-only host activity to match the app's view lifecycle.

The screenshots use synthetic investments from a temporary local API. They contain no production account data. Private sign-in, real document exports, production mutations, and scanned broker PDFs were not exercised with the owner's credentials.

The Android screens are implemented; the existing iOS/shared starter is unchanged. Owner account deletion and password-hash setup remain available in the original website. An exact feature-for-feature native copy of every advanced web table control is not claimed.
