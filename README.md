# Struct Capital

Android and iPhone investment journal built with shared Compose Multiplatform screens and Material 3. The official public website is `https://structcapital.net` (Struct Capital, by GABO). API traffic currently connects to the existing service at `https://bonds.orestegabo.dev` until the Vercel domain migration is verified. See [domain setup and cutover](docs/DOMAINS.md). The personal website and CV are excluded.

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

- **Portfolio:** authenticated bond and share purchases, separate currency totals, net coupon income, scheduled past coupons, accrued income, status filters, bond details, coupon schedule, add/edit/delete bonds, and add shares.
- **Add purchase → PDF import:** a prominent portfolio action opens PDF import first. Select a BNR result or RSE equity purchase note, extract text/OCR locally, automatically fill the bond/share form, review warnings and every field, then explicitly save through the original API. PDFs are limited to 10 MB and 10 pages, matching the web importer.
- **BNR issuance reports:** upload a summary market results PDF from Documents or Add purchase. Review bond terms, auction totals, and every explicit coupon date, then save the report before any personal confirmation. The saved issue appears among pending bond choices and overlays matching calendar entries. Repeated uploads reuse one shared issuance record per security code/settlement date, even if the PDF bytes differ; conflicting terms return a review error. This requires the Spring API’s issuance support and Flyway V3/V4 migrations. Personal confirmations remain separate private documents.
- **Calendar:** a Monday-first month grid with selectable dates, event markers, month navigation and Today. Filter issuances or personal payments, then expand an event for dates, recorded applications, and expected coupon/principal amounts. Uses the complete published FY 2026/2027 issuance data and active portfolio purchases. A Today status card shows currently scheduled subscriptions across all months, with green open, amber closing-today, upcoming and closed badges. Status follows Rwanda time, updates while the calendar is visible and refreshes on return to the app; the selected date does not change the current status. Closing-day labels ask users to confirm the broker deadline because the schedule provides dates only. The published schedule is bundled; it does not automatically acquire later BNR calendars.
- **Documents:** compact, expandable list tiles with search/category filters and a download icon on the right. Expanded rows retain all supplied facts/charges/flow/sections; authenticated PDF export uses the native document picker. Owner-only visibility follows the existing API.
- **Stocks:** a chart-first screen with stock selection, month/year/all ranges, automatic saved-price history loading, a compact ten-company watchlist, source freshness, holding valuations and bond prices. Charts only use actual saved observations.
- **Simulator:** original web calculation engine, contribution periods, one-time cash injections, allowed tenors, tenor coupon assumptions, fill and reinvestment rates, purchase charges, optional active RWF holdings, milestones, chart, and monthly results.
- **Learn:** original web education text rendered into native reading blocks with search.
- **About:** a native page with the theme-aware Struct Capital mark, GABO attribution, a short app overview, feature summaries, website and contact links, and legal/software credits. Available from Settings and the legal hub, including before sign-in.
- **Account tab:** grouped settings for Appearance, Security, Privacy, Tools, Links, Administration (owner only), and Session. Includes secure saved sign-in, device unlock, a theme selector, native simulator/education, relocated bond search, explicit browser links, account summaries and invitation management. Sign-in and invitation-token registration are shown before entering the app. Password setup and the full website remain accessible through explicit browser links; the browser has a separate session.

All product screens, navigation, ViewModel state, API rules, JSON records, date/coupon calculations, formatting, and content are shared. Android's activity and iOS's SwiftUI wrapper only host `App()`. Native file pickers, PDF export, URL opening, sharing, preferences and HTTP engines are implemented behind platform interfaces. See [the architecture map](docs/ARCHITECTURE.md) for the exact boundaries.

The original calculation and purchase-parsing rules are bundled once for both platforms. Android uses a restricted local WebView with PDF.js/Tesseract; iOS uses JavaScriptCore with native PDFKit extraction and Vision OCR. The visible import review and simulator are the same shared Compose screens. The large Android OCR runtime is excluded from the iPhone app.

## API and privacy

The client uses the existing `/api/bonds/` endpoints and an in-memory Ktor cookie jar. Native HTTP cookie persistence and caching remain disabled. **Secure sign-in is opt-in:** select “Save sign-in” when logging in, or enable **Account → Secure sign-in** and confirm your password. It saves the password and an origin-bound session snapshot in the platform's protected store, then requires device authentication before restoring them after a process restart. When the server session expires, a device-authenticated unlock can sign in again using the saved password. Server expiry and credential revocation are respected; the app does not extend an expired cookie locally.

On iPhone, the Keychain item uses a non-migrating, passcode-bound accessibility class and a user-presence access control. Face ID, Touch ID or the device passcode authenticates access. Android uses an authenticated Keystore key that wraps a random data-encryption key; the saved record uses AES-GCM. API 30+ uses an auth-per-operation `BiometricPrompt.CryptoObject`, supporting strong fingerprint, face or iris authentication and the device PIN/pattern/password. API 24–29 uses native screen-lock confirmation and a five-second Keystore authorization window because these versions cannot combine strong biometrics and device credentials in a crypto prompt. Existing biometric-only saved records remain readable; re-enable secure sign-in to switch them to the screen-lock policy. Hardware must be configured before enabling secure sign-in. The operating system decides whether a passcode is required after a restart or biometric lockout.

Leaving the app locks a remembered account, drops the live API session and private records, and invalidates native authentication material. Native privacy covers hide the app during inactivity. Pending OS authentication and document-picker flows are allowed to finish rather than being interrupted by their own lifecycle transitions. Sign-out removes the saved sign-in even if the logout network call fails; turning the option off removes the saved record while keeping the current in-memory session. If the saved password stops working, it is forgotten and the password sign-in screen is shown.

Apple Passwords website sharing is a separate opt-in build capability: when enabled with a compatible provisioning profile, successful manual sign-in/registration offers to save the website login in Apple Passwords. It is disabled by default for Personal Team signing; the app’s protected remembered sign-in and native AutoFill field hints remain available. iOS fields have explicit username/password/new-password input traits for AutoFill. Android commits its AutoFill form after accepted authentication. A password-manager decline or save failure does not reject a valid login, and signing out does not delete the Apple Passwords entry. Website association and matching device signing are required; see [Apple Passwords setup](docs/APPLE_PASSWORDS.md).

Without secure sign-in, credentials and cookies stay in memory and another process requires manual sign-in. Investment records and invitations are not saved as offline account data. Preferences contain connection configuration, theme, and a non-secret saved-sign-in marker, never the saved password or token. Connection details are not shown in the end-user interface. Android backup is disabled. Exported PDFs are saved only where the user explicitly chooses.

The app connects to the configured production API automatically, without exposing a server editor. The default server requires HTTPS; release builds reject HTTP. Internal debug configuration still supports local emulator development and tests. API errors appear visibly; the app never supplies fabricated holdings or market prices.

BNR issuance imports also require the updated separate Spring backend; see [issuance import setup](docs/BOND_ISSUANCE_IMPORT.md). Private web files and credentials were not copied into the app.

## Refresh copied public logic

The source snapshot is in `web-logic/source`. To refresh from the original web repository (with its TypeScript/PDF dependencies installed):

```sh
node scripts/sync-web-logic.mjs /path/to/gabo
node scripts/copy-education.mjs /path/to/gabo
```

The first script regenerates the shared JavaScript modules/bundle and the Android-only PDF/OCR assets. `node scripts/bundle-shared-logic.mjs /path/to/gabo` can rebuild the portable bundle from the checked-in source snapshot without refreshing that snapshot. The second renders the original education JSX and regenerates the native `education.json` text snapshot. Third-party PDF/OCR licenses are bundled beside those assets. These Android assets add size to the APK so OCR works without downloading models. iOS uses the system Vision framework instead.

## Verification limits

Shared tests run on JVM, Android, and iOS and cover API origin/cookie behavior, server errors, coupon boundaries, response limits, session expiry, account isolation and duplicate-action prevention. Native adapter tests cover Android browser extraction and iOS PDFKit/Vision plus JavaScriptCore. JavaScript parity tests compare the portable native bundle with the original modules. Emulator tests use synthetic records, never production account credentials. Secure sign-in tests include process/device restart, system authentication, session renewal, cancellation, corrupt saved data, and local logout cleanup using synthetic accounts. Physical biometric hardware and real account credentials were not used. Real account login, private document downloads, broker-specific PDF scans, and production changes still require account-specific confirmation.

The Android APK and Xcode iPhone application are built from the same shared source. Current checks and runtime previews are recorded in [verification notes](docs/VERIFICATION.md).

Branded support, privacy and security contacts are documented in [email setup](docs/EMAIL.md); their source values live in `docs/legal/content.json`.

Documents also lists virtual Struct Capital annual reports. They are generated locally on download, with a PDF cover and brand badge; no report/document row is stored in the backend. Completed years cover 1 January–31 December and the current year is explicitly year to date. Transactions are restricted to the period; earlier positions are carried into the closing portfolio estimate. Only official share prices dated within the reporting year are used, and missing prices/disposal history leave the complete valuation unavailable. Current saved quantities/statuses are disclosed as a reconstruction rather than a frozen historical statement.

The first-run introduction presents four native, swipeable pages: portfolio overview, PDF import/review, calendar reminders and annual reports. It reuses the supplied theme-specific illustration assets, supports English/French, offers Skip and a direct sign-in shortcut, and remembers completion per device. Existing authenticated/locked sessions are preserved. Replay it from Settings → About → Replay introduction. Programmatic page transitions respect reduced motion; content scrolls independently of the fixed navigation controls on small screens or at larger text sizes.
