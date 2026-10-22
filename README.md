# Gabo investment portfolio

Android investment journal built with Jetpack Compose and Material 3. It connects to the existing Gabo bond app at `https://bonds.orestegabo.dev`. The personal website and CV are excluded.

## Run

Open this folder in Android Studio, sync Gradle, select `app.androidApp`, and run on Android 7.0 / API 24 or newer.

```sh
./gradlew :app:androidApp:assembleDebug
./gradlew :app:androidApp:testDebugUnitTest :app:androidApp:lintDebug
node --test tests/*.test.mjs
```

APK: `app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

The checked-in wrapper is Gradle 9.5.1, Android Gradle Plugin 9.1.1, Kotlin 2.4.20, and Compose Multiplatform 1.12.1. The initial Android debug build succeeded with those versions; a Gradle-version failure was not reproduced. Use the wrapper and the configured JDK 21 toolchain. `local.properties` must point at an installed Android SDK with API 37. A root `build` also compiles and links iOS targets and is much heavier than building Android alone.

## Native screens

- **Portfolio:** authenticated bond and share purchases, separate currency totals, net coupon income, scheduled past coupons, accrued income, search, status filters, bond details, coupon schedule, add/edit/delete bonds, and add shares.
- **PDF import:** select a BNR result or RSE equity purchase note, extract text/OCR locally, review warnings and every field, then explicitly save through the original API. PDFs are limited to 10 MB and 10 pages, matching the web importer.
- **Calendar:** the web app's complete published FY 2026/2027 issuance data, status and date-based alerts, recorded applications, and upcoming portfolio coupons. This schedule is bundled, just as it is hardcoded in the web app; it does not automatically acquire later BNR calendars.
- **Documents:** authenticated library, search/category filters, full supplied facts/charges/flow/sections, and authenticated PDF export through Android's document picker. Owner-only visibility follows the existing API.
- **Stocks:** the same ten-company watchlist, source freshness/status, active holding valuations, purchase-cost comparison, saved daily history, and bond market results ranked by the API strategy score.
- **Simulator:** original web calculation engine, contribution periods, one-time cash injections, allowed tenors, tenor coupon assumptions, fill and reinvestment rates, purchase charges, optional active RWF holdings, milestones, chart, and monthly results.
- **Learn:** original web education text rendered into native reading blocks with search.
- **Account:** server selection, sign-in, invitation-token registration, sign-out, owner account summaries and invitation management. Password setup and the full website remain accessible through explicit browser links; the browser has a separate session.

All visible product screens are Compose. A restricted local WebView executes the original TypeScript-derived calculation/PDF/OCR modules without showing web pages. Its resource loader only serves bundled assets from a fixed virtual HTTPS origin and blocks external resources. This preserves the original numerical and PDF rules while keeping the UI native. Android is implemented in `app/androidApp`; the existing shared/iOS starter is not a port of these screens.

## API and privacy

The client uses the existing `/api/bonds/` endpoints, sends the selected server's `Origin` header for writes, and retains the server's HttpOnly session cookie in an in-memory cookie jar. Passwords, investment records, PDFs, invitations, and session cookies are not persisted by the app. Sign in again after the process exits. Only the server origin is saved. Android backup is disabled. Exported PDFs are saved only where the user explicitly chooses.

The default server requires HTTPS. Debug builds allow HTTP for local development; release builds do not. For an emulator and a local web server use `http://10.0.2.2:3000` in Account. For your web app's host-based route rewriting, configure a host reachable from the device and set `BONDS_APP_ORIGIN` consistently on that server. The selected origin must match the server's origin check. API errors appear visibly; the app never supplies fabricated holdings or market prices.

No backend or private web files were modified or copied. The app has no access to credentials in the web project's `.data` or environment files.

## Refresh copied public logic

The source snapshot is in `web-logic/source`. To refresh from the original web repository (with its TypeScript/PDF dependencies installed):

```sh
node scripts/sync-web-logic.mjs /path/to/gabo
node scripts/copy-education.mjs /path/to/gabo
```

The first script regenerates the bundled JavaScript and PDF/OCR assets. The second renders the original education JSX and regenerates the native `education.json` text snapshot. Third-party PDF/OCR licenses are bundled beside those assets. These assets add size to the APK so OCR works without downloading models.

## Verification limits

Unit and local-engine tests cover API origin/cookie behavior, server errors, coupon boundaries, original PDF parsing rules, and contribution calculations. Emulator tests use synthetic records, never production account credentials. Real account login, private document downloads, broker-specific PDF scans, and production data changes require your account for end-to-end confirmation.

Latest verification: **24 tests passed** (7 host, 14 JavaScript, 3 emulator), Android APK assembled, and lint completed without errors. See [verification notes](docs/VERIFICATION.md) and [synthetic portfolio preview](docs/screenshots/portfolio-synthetic.png).
