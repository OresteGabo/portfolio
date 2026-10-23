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

## Native branding and navigation follow-up

The shared header now uses the original web Gabo mark, with Material icons for account/theme actions and a standard app bar/back button. The four bottom destinations use labeled Material icons with selected states. Secondary screens hide the root navigation bar. Heading sizes and sign-in spacing are compact, and colors follow the cream/olive logo. Android adaptive/legacy and iOS launcher artwork use the same mark; sources and regeneration instructions are in `branding/README.md`.

Android debug assembly, lint and shared host tests passed; physical-iPhone Kotlin compilation and the Xcode simulator build passed. The [signed-in iPhone preview](screenshots/ios-material-navigation.png) uses a temporary local API with synthetic holdings. The preview process was restarted without its temporary server override and the mock server stopped afterward.


## Account, PDF entry, Documents and Stocks follow-up

- Account is the fifth bottom tab. It contains sign-out, persisted System/Light/Dark appearance, privacy and owner tools. Server connection controls are absent from the end-user UI.
- The portfolio's accessible Add purchase action opens a PDF-first screen, with manual bond/share entry as a fallback. The native picker reads the PDF and opens the automatically filled form with warnings and missing fields. iOS now completes picker dismissal before resuming the shared review flow, fixing a crash observed during the picker-to-review accessibility transition.
- Documents use expandable list tiles with a right-side download action. Full document details remain available after expansion.
- Stocks automatically load actual saved history for the selected ticker/range and show a chart above a compact watchlist. Missing history is shown explicitly without fabricated chart points.

Android debug assembly/lint, shared Android host tests, all 8 shared/native iOS tests, physical-iPhone Kotlin compilation, and the full Xcode simulator build passed. Simulator UI checks verified Account/theme persistence/sign-out, native picker selection into a filled purchase review, expandable document rows, and changing the stock shown in the chart. The PDF, account, documents and market prices in these checks were synthetic. No purchase was saved to a production account.

Previews: [Account](screenshots/ios-account-settings.png), [PDF review](screenshots/ios-pdf-review.png), [Documents](screenshots/ios-documents-expandable.png), [Stocks](screenshots/ios-stock-charts.png).


## Secure saved sign-in follow-up

- Added opt-in password/session persistence, a locked startup screen, Account controls, background locking and native privacy covers. Native vaults use Keychain user-presence protection on iOS and auth-per-use Keystore/AES-GCM on Android. No real password or production account was used.
- 10 shared security tests run on Android host and iOS: no private requests before unlock, cancelled authentication, expiry renewal, invalid-password cleanup, pending-action cancellation/background lock, offline logout cleanup, password fallback, a completely new ViewModel restoring a remembered login, corrupt-data error sanitization, and incorrect confirmation preserving the current account.
- Two additional core API tests verify cookie snapshot round-trip without extending expiry, origin binding, and rejecting expired stored cookies. The core suite now has 15 tests on JVM, Android host, and iOS.
- Android debug assembly/lint, physical-iPhone Kotlin compilation, and the signed Xcode simulator app build passed. The 3 Android API/PDF integration tests passed on each of the two running emulators. The Android launcher activity started successfully after the test runner's uninstall cleanup.
- An isolated iPhone 16 Pro / iOS 18.6 simulator, containing only a synthetic localhost account, verified actual system authentication prompts, successful simulated Face ID, session restoration after app termination, expired-server-session renewal without typing the password, background locking, and restoration after a complete simulator shutdown/boot using the OS device-authentication fallback. App preferences were checked and contained neither the synthetic password nor its session token. Physical Secure Enclave/biometric hardware is not emulated, so on-device hardware behavior remains a physical-device check.

Keychain testing requires a signed simulator app. The unsigned UI-only build reported secure-storage failure; the ad-hoc-signed build works. Use normal Xcode Run, or for simulator testing:

```sh
xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.6' \
  CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- build
```

The backend was not modified or deployed. The inspected web code limits ordinary sessions to 12 hours and file-owner sessions to one hour; secure mobile sign-in respects server validity and can renew through the existing login endpoint after device authentication. Network access is still needed to retrieve private portfolio data.

[Unlock screen preview](screenshots/ios-secure-unlock.png).


## Device authentication and portfolio consistency follow-up

The overview previously summed only bond face values while the purchase history also displayed equities. It now shows invested cash from one shared portfolio snapshot, with bond and share costs broken out per currency. Stocks derives its positions from that same snapshot and active-share/date filter. Successful writes close the editor before refreshing; an unsuccessful refresh keeps the previous complete snapshot and explicitly confirms that the purchase was saved, preventing a misleading resubmission.

Five new common regressions cover BK-only/multiple-lot totals, mixed currencies and bond cash cost, inactive/future/invalid-date shares, a saved BK purchase appearing in the overview and Stocks, and a successful write followed by a failing refresh and read-only retry. All 20 Android host tests and 23 iOS simulator tests pass, alongside Android assembly/lint, physical-iPhone Kotlin compilation, and the signed Xcode simulator build. An iPhone simulator UI check with a temporary synthetic API showed RWF 36,500 in both the overview card and the BOK holding cost in Stocks, including after changing tabs. No real purchase was changed or resubmitted.

Android now uses device-neutral unlock wording and supports PIN/pattern/password-only phones, including an OS Keyguard confirmation fallback for API 24–29. Those older versions use a five-second Keystore authorization window; Android 11+ retains auth-per-operation crypto and strong biometric/device-credential selection. Existing older biometric-only records remain compatible; disabling and re-enabling secure sign-in adopts the new policy. iOS uses passcode wording when biometrics cannot be evaluated. Builds verify both platform adapters; physical iris/fingerprint scanners and the older Android screen-lock flow were not exercised on hardware.


## Calendar redesign follow-up

Replaced the long issuance-card feed with a shared monthly calendar: Monday-first date grid, previous/next month, Today, muted adjacent-month dates, accessible date/event descriptions, and distinct issuance/coupon/maturity markers. The selected-day agenda uses compact expandable event tiles. Filters switch between all events, issuances, and personal payments; empty dates offer the next matching event. Published date details, recorded applications, and schedule/source notes remain accessible.

Five new common date tests cover leap-day/year-boundary grids, end-of-month navigation, issuance milestones and the open-book window, active-purchase coupon/principal events, and invalid-date handling. All 25 Android host tests and 28 iOS simulator tests pass. Android assembly/lint, physical-iPhone Kotlin compilation, and the signed iOS simulator build pass. iPhone simulator checks used the published issuance data plus a synthetic bond payment to verify month navigation, payment filtering, selection and expandable event state. No production records were modified.

The final compact filter bar and calendar were also checked in the signed simulator app. Selecting My payments displayed the synthetic RWF 4,750 expected coupon and hid issuance markers; the calendar information dialog showed the BNR schedule coverage and payment-estimate notes. The simulator was restarted against its normal server and the temporary API stopped afterward.

[Monthly calendar preview](screenshots/ios-month-calendar.png).


## Apple Passwords follow-up

The iOS app previously had only app-vault secure sign-in and Compose semantics; it lacked associated-domain signing and explicit UIKit username/new-password input traits. Added native credential traits, an Apple Passwords save offer after accepted manual login/registration, and an authenticated-only Android AutoFill commit. Save failure/decline does not reject the login. The iOS entitlement and stable bundle ID match the prepared website's public AASA endpoint; the bonds subdomain rewrite excludes that endpoint.

Four new regressions verify accepted/rejected credential-save ordering, safe handling of password-manager errors, and exact HTTPS production-origin binding. All 29 Android host and 32 iOS simulator tests pass, along with Android assembly/lint and physical-iPhone Kotlin compilation. Web TypeScript checking passes; a direct route/proxy check confirms HTTP 200 JSON, exact app identity, no association redirect/rewrite on three hosts, and preservation of the portfolio rewrite.

The live association URL returned 404 during implementation. The two web changes are prepared locally and require publication. No actual password was sent to Apple Passwords and no iCloud credential was created, changed or removed during testing. End-to-end sharing/AutoFill still requires the published website association and a matching device provisioning profile; the simulator build cannot validate that production trust relationship.

The final signed Xcode simulator build also passes. Its simulated entitlement payload (embedded in the executable’s `__TEXT` section) contains the exact application identity and `webcredentials:bonds.orestegabo.dev`. The app was installed/launched normally without test credentials. Website publication approval remains pending.


## Sectioned settings, Portfolio cleanup and scanned-table import

Account now opens a Settings page with grouped list rows: Appearance, Security, Privacy, Tools, Links, Administration (owner only), and Session. Theme opens a radio-selection dialog; device sign-in uses a single accessible switch row; private details are in dialogs. Owner account/invitation controls and bond search use sheets. Native simulator, education and bond search are reachable through Tools. Browser links use external-link icons. Sign-out confirms removal of the app's remembered sign-in.

Portfolio opens directly on the investment summary; removed its introductory slogan, simulator/learn buttons and search field. Status filtering and the existing investment data remain. iPhone simulator checks with synthetic owner records verified the grouped screen, Dark/Follow device selection, and Account → native simulator → Back. Appearance was restored to Follow device after testing.

The supplied private bond PDF is a one-page scan without extractable text. Apple Vision returned label columns before their values, so the original line-based parser rejected the result. The iOS OCR adapter now reconstructs physical rows from normalized text boxes before handing text to the same shared purchase parser. JavaScript error handling exposes only typed, actionable import errors, never document text or raw dependency errors.

The exact supplied PDF passed a temporary local iOS engine test: OCR was used, all required fields were recovered, and 14 coupon dates were parsed. That private-file test was removed immediately afterward. The source PDF was never copied into the repository or uploaded; no purchase was saved. Two permanent geometry tests and two native generated-PDF/error tests cover the regression using synthetic data. All 31 Android host and 36 iOS simulator tests pass; Android assembly/lint, physical-iPhone Kotlin compilation, and the signed Xcode simulator build pass.

Previews: [Settings sections](screenshots/ios-settings-sections.png), [Portfolio](screenshots/ios-portfolio-clean.png).

The approved Apple Passwords website change is committed locally as `204a611`, but push failed because Git is not authenticated. The GitHub integration also denied both Git-data and contents writes with 403. No browser commit was completed; the live association endpoint was rechecked and still returns 404. Publication requires authenticated Git write access, not another approval.

## Illustrated empty states

Replaced text-only empty states across Portfolio, Documents, Calendar, stock history, bond prices, bond search, owner accounts and simulator results with a shared Compose component. It uses themed vector watermarks, short labels and context-specific actions. Decorative artwork is excluded from accessibility descriptions. Loading indicators remain distinct from empty results. Empty portfolios have one Add purchase action; shares-only portfolios omit empty bond sections; document filters appear only when documents exist.

Verified Android debug assembly, Android lint, iPhone ARM64 Kotlin compilation and a signed Xcode simulator build. Inspected Portfolio, Documents, stock history and Calendar with empty synthetic records on iPhone 16 Pro; checked Documents in both light and dark themes. Confirmed Add purchase opens the import flow. Restored Follow device appearance and normal server configuration afterward. No real records were modified.

Previews: [Empty portfolio](screenshots/ios-empty-portfolio.png), [Empty documents](screenshots/ios-empty-documents.png), [Dark documents](screenshots/ios-empty-documents-dark.png).

## Per-section empty investments

Portfolio now always displays Bonds and Shares sections, each with its own compact illustrated empty card and Add purchase action when that category has no records. This replaces the single whole-portfolio empty card and keeps the bond state visible for shares-only portfolios. Stocks displays an empty active-holdings card. Learning now distinguishes no lessons from no search matches, with a Clear search action. Empty bond-price lists hide the unused search field.

Android assembly/lint and iPhone ARM64 Kotlin compilation pass. The signed simulator build passed for the Portfolio/Stocks changes; visually verified both a fully empty portfolio and a shares-only portfolio with synthetic data. Previews: [Empty sections](screenshots/ios-empty-portfolio.png), [Shares without bonds](screenshots/ios-no-bonds-with-shares.png). Restored the normal server after verification; no real records changed.

## Subscription date ranges

Calendar highlights actual issuance book-opening through book-closing dates with a continuous booking-style band and solid endpoints. A compact card shows opening and closing dates with “Subscriptions” between them; tapping it selects the opening day. Focus follows the current, next, or latest subscription window in the displayed month. Today/selected dates outside a window use outlines so the two range endpoints stay distinct. Settlement and private payment dates remain standalone events; no payment intervals are invented. Trailing weeks outside the current month are omitted.

Added shared tests for inclusive boundaries, single-day windows, invalid/reversed dates, cross-month/year windows, month membership, and automatic range focus. All 33 Android host and 38 iOS tests passed. Final Android assembly/lint, iPhone ARM64 Kotlin compilation and signed Xcode simulator build passed. Visually checked the October range and its tap action on iPhone 16 Pro using synthetic sign-in; no real records changed. Preview: [Subscription range calendar](screenshots/ios-subscription-range-calendar.png).

## Monetary summary and expandable purchases

Portfolio’s summary now contains only total cash cost, bond principal, RSE equity cost and saved annual net coupon, grouped by currency. Removed the separate income panels and record counts. Annual coupon is derived in `PortfolioSnapshot` from saved terms of current active, unmatured bonds. Shared cost/term renderers keep the overview, inline disclosure and management page consistent.

Bond and share purchases now use compact list tiles with inline animated disclosure of saved terms, dates, references, money details, notes and source links. Bond disclosures also show coupon tracking/schedule and retain access to management (edit/delete). Empty investment cards have no purchase action; the floating Add purchase button remains visible even for completely empty portfolios.

Android assembly/lint, iPhone ARM64 Kotlin compilation and the signed Xcode simulator build pass. All 34 Android host and 39 iOS tests pass, including a new annual-coupon regression covering account currencies, status, maturity and future purchases. Verified summary amounts (synthetic RWF 100,000 principal + 36,500 equity cost = 136,500 total; 9,500 annual net coupon), both expansion/collapse actions and exactly one purchase action for a fully empty portfolio on iPhone 16 Pro. Restored the normal server after preview; no real records were modified.

Preview: [Money summary and purchase tiles](screenshots/ios-portfolio-money-tiles.png).

## Kigali photo dashboard

Adapted the supplied visual reference into shared Compose UI: a photo-backed Portfolio header, a clipped Canvas motif inspired by imigongo lines, gold/olive money card, cash-allocation donut and horizontal insight cards. The cards show cash allocation, coupon income/accrual and the next expected payment, using the existing snapshot and calendar calculations with currencies kept separate. Previous/next buttons supplement native horizontal scrolling. All/Shares/Bonds filters lead into the existing expandable purchase tiles. Add purchase remains floating and there is no duplicated profile badge.

Bundled optimized copies of the supplied Kigali photo and BNR/BK/I&M/Bralirwa/Cimerwa/KCB marks; source notes are in `ASSETS.md`. Portfolio rows and Stocks watchlists use the shared logo resolver, with text fallbacks for unsupported symbols. iOS SwiftUI observes the existing theme preference so native status-bar contrast follows light/dark selection.

Final Android debug assembly, Android lint, iPhone ARM64 Kotlin compilation and signed Xcode simulator build passed. Visually inspected light and dark dashboards on iPhone 16 Pro with synthetic records and verified the carousel buttons navigate through all three cards. Confirmed the synthetic amounts remain RWF 136,500 total, 100,000 principal, 36,500 equity cost and 9,500 annual net coupon; the next expected coupon is RWF 4,750. Native drag/scroll injection did not move the simulator during automation; carousel navigation was verified through its accessible buttons. Restored Follow device appearance and normal server configuration afterward. No real records were modified.

Previews: [Light dashboard](screenshots/ios-dashboard-light.png), [Dark dashboard](screenshots/ios-dashboard-dark.png).

## Equal-height insight cards

All three carousel cards now use one explicit 236 dp frame height, scaled by the system text-size factor. Replaced content-dependent minimum heights with weighted internal layouts, preserving equal top/bottom edges for allocation, income, payment and the no-payment variant. Each card has a theme-specific gradient, subtle radial glow, low-contrast line artwork and a tinted accent palette. Income uses two stacked icon/amount rows with a divider; the payment card uses a date badge and flowing background lines. No financial calculations changed.

Android assembly/lint, iPhone ARM64 Kotlin compilation and signed Xcode simulator build passed. Inspected the light allocation card and both dark income/payment cards on iPhone 16 Pro with synthetic records; verified matching card bounds and carousel navigation. Restored Follow device appearance and normal server configuration. Previews: [Light allocation](screenshots/ios-insights-light.png), [Dark income](screenshots/ios-insights-income-dark.png), [Dark payment](screenshots/ios-insights-payment-dark.png).

## Estimated net worth and cash-cost allocation

The main card now shows estimated net worth (tracked bond principal plus current estimated share value) and RSE equity value. Total cash cost moved to the allocation card footer and its donut remains a cash-cost allocation. The main card exposes price date/cached status/cost fallback and a valuation-basis dialog; purchase rows explicitly identify their cost figures. Stocks uses the same shared valuation helper for current values and gains.

Six new shared regressions cover changing prices/multiple lots, bond principal versus purchase fees, exclusion of annual income, missing quotes, invalid/future prices, inactive/future holdings, currency isolation, automatic quote loading and preserving data/cached quotes on refresh failures. The initial malformed-price fixture incorrectly attempted to construct non-finite JSON numbers; corrected it to use malformed API string values. All 40 Android host and 45 iOS tests then passed. Final Android assembly/lint and iPhone ARM64 Kotlin compilation passed; the signed Xcode simulator build passed for the valuation screen before the final per-row “Cost” label adjustment.

Verified on iPhone 16 Pro with synthetic records: a BOK quote change from RWF 400 to 500 changes share value from 40,000 to 50,000 and total net worth from 140,000 to 150,000, while allocation cash cost stays 136,500. A zero quote produces an explicit purchase-cost fallback. Verified the valuation-basis dialog. No real purchases or quotes were modified; restored the normal server after preview. [Net-worth preview](screenshots/ios-estimated-net-worth.png).

## Compact currency-aware hero

Removed the closing-price status/date line from the visible hero, moved that information into the valuation-basis dialog, and reduced padding/spacing. A single currency badge near the heading scopes all four amount-only figures. Screen-reader descriptions retain full currency-qualified values. Each currency keeps its own card.

Android assembly/lint, iPhone ARM64 Kotlin compilation and signed Xcode simulator build passed. Inspected the shortened card on iPhone 16 Pro with synthetic data and confirmed price details remain available through the info button. Restored the normal server after preview. [Compact hero](screenshots/ios-compact-net-worth.png).

## Swipeable combined calendar

Removed All events/Issuances/My payments tabs. Issuance windows and personal payments are now shown together, with the existing category dots and legend. The calendar surface handles horizontal drag gestures: left advances one month and right goes back one month after a directional distance threshold; cancellation resets the gesture. Vertical scrolling remains with the outer list. Month-body transitions slide in the corresponding direction, while tapping dates within a month does not animate the grid. Existing previous/next arrows and Today remain accessible; source information moved beside the agenda heading.

Android assembly/lint, iPhone ARM64 Kotlin compilation and signed Xcode simulator build passed. Verified the tab-free layout, combined settlement/coupon date, next-month range changes, day selection and Today on iPhone 16 Pro with synthetic records. Native automated drag injection produced a date tap instead of a drag; physical swipe behavior could not be exercised through that automation interface. Restored the normal server after verification. [Calendar without tabs](screenshots/ios-calendar-no-tabs.png).

## Recent investments and on-demand history

Portfolio now previews the past 12 months with a maximum of 12 purchases per investment type. See all opens a separate full-history screen. Shared regressions cover date boundaries, future records, preview limits, aggregate valuation/count preservation, lazy full-history fetching, repeat visits, sync invalidation and compatibility with older servers. All 44 Android host and 49 iOS simulator tests pass. Android assembly/lint, iPhone ARM64 compilation and the signed Xcode simulator build pass.

The companion web overview API passes six tests and TypeScript checking, including account isolation and a 530-lot fixture reduced to 13 transferred records without changing totals. Publication was handed to the user after GitHub integration writes were denied and Git had no saved HTTPS credential. The prepared local web branch `codex/mobile-portfolio-overview` contains only the three API files and excludes the older Apple Passwords commit. Transfer reduction requires this API to be deployed; until then, the app uses its compatible full-response fallback.

## Combined coupon insight — 24 October 2026

Merged coupon income and next payment into one fixed-height carousel card per currency. Annual net and accrued amounts sit above an actionable next-coupon row, with a date badge and chevron. Only coupon events contribute to the next coupon amount; same-day coupons in that currency are combined. Calendar opens at the exact coupon date, selecting its day and agenda.

Android assembly/lint, physical-iPhone Kotlin compilation and the signed Xcode simulator build pass. Checked the combined card with synthetic records on iPhone 16 Pro and tapped a 23 November 2026 coupon: Calendar displayed November 2026 with that day selected and its expected coupon in the agenda. Restored the normal server and stopped temporary servers afterward. Preview: [Combined coupon card](screenshots/ios-combined-coupon-card.png).

## Chart designs with synthetic prices — 24 October 2026

Replaced the bare price polyline with shared area, line and bar views, labeled scales, dashed grid lines, current/selected values, period change and a tappable observation cursor. Bar views start at zero. Stocks uses saved observation dates and the simulator reuses the chart component for projections. Tightened the Stocks controls so the complete graph fits the phone viewport. The designs take inspiration from the Kotlin Kandy and Lets-Plot examples; no Lets-Plot dependency was added because its Compose frontend lists Desktop, Android and WasmJS, without iOS.

Android assembly/lint, physical-iPhone Kotlin compilation and the signed Xcode simulator build pass. A temporary local API returned 40 fictitious, varied closing prices for the screenshots, marked DEMO DATA. Verified all three style switches and a tap selecting 18 September at RWF 324. Returned the app to its normal API and stopped the temporary server; no production records were written. These checks cover the populated case; the existing sparse-history empty state remains in place.

Previews: [Area](screenshots/ios-chart-area-demo.png), [Line with cursor](screenshots/ios-chart-line-demo.png), [Bars](screenshots/ios-chart-bars-demo.png).

## Invited-account self-deletion — 24 October 2026

As requested, this phase covers invited database accounts only. Settings → Privacy → Delete account requires password confirmation and explains permanent deletion plus the separate status of downloaded files and system password-manager entries. Main owner and environment-managed friend accounts retain their existing behavior. The client shows this option for invited UUIDv4 accounts; the server independently checks the session, account type, database identity and current password. No caller-supplied victim ID or email is used.

The companion DELETE `/api/bonds/auth/account` endpoint checks Origin and a bounded JSON body, limits password attempts, deletes the account, bond/share purchases, all sessions and its email-bound invitation, and expires the cookie. Registered invited accounts currently have no server-hosted PDFs: the existing document library is owner-only and remains outside this phase. On confirmed success, the app removes secure sign-in, cookies and its private snapshot. Failed or unconfirmed deletion keeps the account visible.

All 46 Android host and 51 iOS simulator tests pass, including deletion success, saved-sign-in removal and failure preservation. Android assembly/lint, physical-iPhone Kotlin compilation and the signed Xcode simulator build pass. Seven web deletion tests plus six overview regressions and TypeScript checking pass. Web tests use mocked storage, including an isolated local purge that preserves another user's files and sessions; a real production database purge was not executed.

Inspected the confirmation and Cancel action on iPhone 16 Pro with a synthetic invited account. No real account was deleted. Restored the normal server afterward. Preview: [Delete confirmation](screenshots/ios-delete-account-confirmation.png). The three web files are prepared on local branch `codex/invited-account-deletion` in the web repository for user publication; this new endpoint must be deployed before the app feature works in production. Apple guidance: https://developer.apple.com/support/offering-account-deletion-in-your-app/. This scoped phase does not assert deletion coverage for deployment-managed accounts.
