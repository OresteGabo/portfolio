# Gabo investment portfolio — Kotlin Multiplatform

Android and iPhone investment journal built with shared Compose Multiplatform screens and Material 3. It connects to the existing Gabo bond app at `https://bonds.orestegabo.dev`. The personal website and CV are excluded.

## Run

For Android, open this folder in Android Studio, sync Gradle, select `app.androidApp`, and run on Android 7.0 / API 24 or newer. For iPhone, open `app/iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme. Both hosts call the same shared `App()`; the old starter screen has been removed.

```sh
./gradlew :app:androidApp:assembleDebug
./gradlew :core:jvmTest :core:iosSimulatorArm64Test
./gradlew :app:shared:testAndroidHostTest :app:shared:iosSimulatorArm64Test
./gradlew :app:androidApp:lintDebug :app:androidApp:connectedDebugAndroidTest
node --test tests/*.test.mjs
```

Boot an iPhone simulator before running shared iOS tests; they use native Vision services. Select a specific booted device with `-PiosTestDevice=<UDID>`.

APK: `app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

The project uses Gradle **9.1.0**, Android Gradle Plugin **9.0.0**, Kotlin **2.3.21**, Compose Multiplatform **1.11.1**, Material 3 **1.11.0-alpha07**, and Lifecycle **2.10.0**. AGP is pinned to the maximum version supported by the installed Android Studio. Compose 1.12.1 requires AGP 9.1 and SDK 37, and Lifecycle 2.11.0 has the same requirements. Compose, Lifecycle, and the compile/target SDK were aligned to the compatible SDK 36 baseline. See the [AGP 9.0 compatibility requirements](https://developer.android.com/build/releases/agp-9-0-0-release-notes).

Kotlin 2.3.21 also avoids Kotlin 2.4 metadata errors in AGP 9.0’s bundled D8 compiler. Use the wrapper and configured JDK 21 toolchain. `local.properties` must point at an Android SDK with API 36 installed. The earlier command-line build passed with AGP 9.1.1, but that did not verify Android Studio compatibility. A root `build` also compiles and links iOS targets and is much heavier than building Android alone.

## Native screens

- **Portfolio:** authenticated bond and share purchases, separate currency totals, net coupon income, scheduled past coupons, accrued income, search, status filters, bond details, coupon schedule, add/edit/delete bonds, and add shares.
- **PDF import:** select a BNR result or RSE equity purchase note, extract text/OCR locally, review warnings and every field, then explicitly save through the original API. PDFs are limited to 10 MB and 10 pages, matching the web importer.
- **Calendar:** the web app's complete published FY 2026/2027 issuance data, status and date-based alerts, recorded applications, and upcoming portfolio coupons. This schedule is bundled, just as it is hardcoded in the web app; it does not automatically acquire later BNR calendars.
- **Documents:** authenticated library, search/category filters, full supplied facts/charges/flow/sections, and authenticated PDF export through the native document picker. Owner-only visibility follows the existing API.
- **Stocks:** the same ten-company watchlist, source freshness/status, active holding valuations, purchase-cost comparison, saved daily history, and bond market results ranked by the API strategy score.
- **Simulator:** original web calculation engine, contribution periods, one-time cash injections, allowed tenors, tenor coupon assumptions, fill and reinvestment rates, purchase charges, optional active RWF holdings, milestones, chart, and monthly results.
- **Learn:** original web education text rendered into native reading blocks with search.
- **Account:** server selection, sign-in, invitation-token registration, sign-out, owner account summaries and invitation management. Password setup and the full website remain accessible through explicit browser links; the browser has a separate session.

All product screens, navigation, ViewModel state, API rules, JSON records, date/coupon calculations, formatting, and content are shared. Android's activity and iOS's SwiftUI wrapper only host `App()`. Native file pickers, PDF export, URL opening, sharing, preferences and HTTP engines are implemented behind platform interfaces. See [the architecture map](docs/ARCHITECTURE.md) for the exact boundaries.

The original calculation and purchase-parsing rules are bundled once for both platforms. Android uses a restricted local WebView with PDF.js/Tesseract; iOS uses JavaScriptCore with native PDFKit extraction and Vision OCR. The visible import review and simulator are the same shared Compose screens. The large Android OCR runtime is excluded from the iPhone app.

## API and privacy

The client uses the existing `/api/bonds/` endpoints, sends the selected server's `Origin` header for writes, and retains the server's HttpOnly session cookie in a shared in-memory Ktor cookie jar. The Darwin transport disables native cookie persistence and caching. Passwords, investment records, PDFs, invitations, and session cookies are not persisted by the app. Sign in again after the process exits. Only the server origin is saved. Android backup is disabled. Exported PDFs are saved only where the user explicitly chooses.

The default server requires HTTPS. Debug builds allow HTTP for local development; release builds do not. For an Android emulator and a local web server use `http://10.0.2.2:3000` in Account. For the iPhone simulator use `http://localhost:3000`. Physical phones need a reachable HTTPS server (or a local development host in a debug build). For your web app's host-based route rewriting, configure a host reachable from the device and set `BONDS_APP_ORIGIN` consistently on that server. The selected origin must match the server's origin check. API errors appear visibly; the app never supplies fabricated holdings or market prices.

No backend or private web files were modified or copied. The app has no access to credentials in the web project's `.data` or environment files.

## Refresh copied public logic

The source snapshot is in `web-logic/source`. To refresh from the original web repository (with its TypeScript/PDF dependencies installed):

```sh
node scripts/sync-web-logic.mjs /path/to/gabo
node scripts/copy-education.mjs /path/to/gabo
```

The first script regenerates the shared JavaScript modules/bundle and the Android-only PDF/OCR assets. `node scripts/bundle-shared-logic.mjs /path/to/gabo` can rebuild the portable bundle from the checked-in source snapshot without refreshing that snapshot. The second renders the original education JSX and regenerates the native `education.json` text snapshot. Third-party PDF/OCR licenses are bundled beside those assets. These Android assets add size to the APK so OCR works without downloading models. iOS uses the system Vision framework instead.

## Verification limits

Shared tests run on JVM, Android, and iOS and cover API origin/cookie behavior, server errors, coupon boundaries, response limits, session expiry, account isolation and duplicate-action prevention. Native adapter tests cover Android browser extraction and iOS PDFKit/Vision plus JavaScriptCore. JavaScript parity tests compare the portable native bundle with the original modules. Emulator tests use synthetic records, never production account credentials. Real account login, private document downloads, broker-specific PDF scans, and production data changes require your account for end-to-end confirmation.

The Android APK and Xcode iPhone application are built from the same shared source. Current checks and runtime previews are recorded in [verification notes](docs/VERIFICATION.md).
