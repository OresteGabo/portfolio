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

## Settings appearance and chart preference — 24 October 2026

Renamed the bottom destination to Settings with a gear icon. Replaced the appearance dialog with selectable System/Light/Dark preview cards. Moved Area/Line/Bars into Settings → Appearance → Chart style. The shared chart reads one composition preference, persisted with SharedPreferences on Android and NSUserDefaults on iOS, and no longer shows style controls on Stocks or simulator charts.

Android assembly/lint, physical-iPhone Kotlin compilation and signed Xcode simulator build pass. On iPhone 16 Pro, selected Dark and Bars, verified the Stocks graph had no style buttons, restarted and checked both values persisted. Checked Light and restored System plus Area afterward. Preview: [Appearance settings](screenshots/ios-settings-appearance-cards.png).

## Pending bonds and confirmation import — 24 October 2026

The clarified workflow requires no application file. Add purchase → Add pending bond asks only for a published bond and the amount applied. Dates come from the calendar; records use submitted status. Shared tiles use a muted grey surface, faded issuer mark and explicit Pending label. Pending disclosures show applied amount and expected dates, hiding unconfirmed price, coupon and schedule. Pending records do not enter active net worth, cash allocation, coupon estimates or payment events. Required legacy storage price/coupon defaults are provisional, documented in record notes and not shown as executed terms.

Pending purchase details offer BNR PDF confirmation. Confirmation review is read-only: missing or inconsistent figures block saving instead of asking for manual completion. The Add purchase manual-entry options are removed. Bond import copy says BNR PDFs only; existing RSE share-note import remains available. Explicit-target confirmation checks currency, settlement, maturity, optional ISIN and allocation not exceeding the application. General imports load full purchase history on demand and require a choice when pending matches exist. A confirmation updates the selected record ID through the existing PUT endpoint, using the actual allocation and price, including partial allocations.

Five new shared regressions cover pending input, invalid amounts/dates, exclusion from totals/events, partial-allocation matching, rejecting unreadable/inconsistent PDFs and replacement of the same record through the model. All 51 Android host and 56 iOS simulator tests pass. Final Android assembly/lint, physical-iPhone Kotlin compilation and signed Xcode simulator build pass. On iPhone 16 Pro, created a disposable in-memory RWF 1,000,000 pending record without a file, inspected its grey history tile and BNR confirmation action, and checked the exact resulting payload against the real web validator. This UI check did not submit a confirmation PDF; the record replacement and partial allocation were tested through the mocked API. No real portfolio was modified.

Previews: [No-file entry](screenshots/ios-pending-bond-entry.png), [Pending tile](screenshots/ios-pending-bond-tile.png). Existing purchases endpoints support this flow; no additional backend deployment is required for pending purchases.

## Hero behind the status bar — 24 October 2026

Portfolio draws its photograph from the top window edge, including behind the iPhone Dynamic Island/status bar. The shared hero offsets its logo and controls by the top safe inset. A pinned, theme-aware gradient keeps the clock and system icons readable above the photo and scrolling content. Home progress/notices render below the header within the hero, while other screens retain standard Scaffold insets. Android system-bar icon contrast follows the app theme; iOS continues using SwiftUI's existing appearance preference.

Android assembly/lint, physical-iPhone Kotlin compilation and signed Xcode simulator build pass. Inspected the complete iPhone 16 Pro hero in light and dark themes using synthetic records, including a refresh notice beneath the safe header. Checked Settings navigation, restored System appearance, returned to the normal API and stopped the preview server. No new data tests were added for this visual-only change. Physical scroll gestures were not exercised in this check; the status veil is attached to the fixed root content.

Previews: [Light](screenshots/ios-hero-status-bar-light.png), [Dark](screenshots/ios-hero-status-bar-dark.png).

## Calendar pending purchases — 24 October 2026

- Android assembly/lint, physical-iPhone Kotlin compilation, and signed Xcode simulator build passed.
- All 52 shared Android host and 57 iOS simulator tests passed. Coverage includes pending exclusion from active totals, partial allocation matching, rejecting a different issue, preserving the RWF 11,000 commission, and a PUT to the same pending ID producing one active record with actual allocated value and cash cost.
- With a disposable localhost API, iPhone simulator Calendar navigation to November selected the November 5-year issuance automatically. Entering RWF 1,000,000 showed commission RWF 11,000 and total RWF 1,011,000; saving returned to November. The emitted submitted record passed the existing website `parsePurchase` validator and retained the November settlement and RWF 11,000 fee. No production purchases were changed.
- Preview: [Calendar pending purchase](screenshots/ios-calendar-pending-entry.png). PDF matching/update was verified through shared tests; the native picker was not re-exercised in this follow-up.

## Accepted documents and missing statement records — 24 October 2026

- Add purchase now shows the three accepted PDF categories. BK Capital statements reconcile saved transactions and create persistent missing-document reminders for unmatched purchases; these are outside all investment totals.
- All 55 shared Android host tests and 61 iOS simulator tests passed, including native PDFKit statement recognition, confirmation matching, wrong-contract rejection, server reminder persistence, exclusion from totals and logout cleanup. Android assembly/lint, physical-iPhone Kotlin compilation and the signed Xcode simulator build passed.
- All 20 app JavaScript parsing/calculation tests passed, including wrapped statement contract references, keeping deposits/fees out of purchases, rejecting malformed dates/amounts, portable engine parity and the BNR OCR July label fix.
- Local inspection of all three supplied examples stayed on-device. Native PDFKit text extraction recognized the share contract with no missing fields and the statement with four purchase entries. The scanned BNR example was rendered and read with native Vision; its 14 coupon dates and required fields were retained after handling OCR capital-I confusion in July. Private source files and intermediate text/images were not added to either repository.
- Isolated website tests verify durable reminders, repeated statement imports, owner/member isolation, matching on actual execution cost, submitted records staying unresolved, later confirmation clearing a reminder, and account deletion cleanup. Production purchases were not created or changed. Website schema/API deployment remains necessary.
- Simulator UI verification used a disposable localhost API with synthetic records. The accepted-document notice renders correctly; tapping a muted missing-document tile opens the native picker directly, and cancelling preserves the reminder. Previews: [Accepted PDFs](screenshots/ios-accepted-purchase-documents.png), [Missing confirmation](screenshots/ios-missing-confirmation.png). The real supplied statement was not submitted to a production API.

Final website validation for this follow-up passed all 52 targeted PDF/account/import/storage tests, TypeScript checking, ESLint on the changed API/auth/parser files, and whitespace checks. The website deployment checklist is saved locally at `/Users/muhirwagabooreste/WebstormProjects/gabo/STATEMENT_IMPORT.md`.

## Direct confirmation uploads — 24 October 2026

Pending Upload confirmation now invokes the native picker directly from Portfolio or investment history. A separate Details action preserves management/deletion access. Statement reminders share the same direct flow, replacing the intermediary missing-confirmation page shown in the earlier screenshot. The existing purchase Details page retains its own directly functioning upload button.

Android assembly/lint, physical-iPhone Kotlin compilation, shared Android/iOS tests, and signed Xcode simulator build passed. New shared tests verify one picker/parse operation produces a review, cancellation never parses or opens review, and incorrect document kinds release progress without proceeding. On iPhone simulator with a disposable local pending purchase, tapping Upload confirmation opened the native picker while remaining in All investments; cancelling returned to the same expanded purchase with its filters intact. No production purchase was uploaded or changed.

## Visible PDF processing — 24 October 2026

- Added a shared Reading PDF dialog with Opening file, Extracting data and Checking details stages, native page/OCR feedback, and cancellation. Native selection callbacks occur before reading bytes on both Android and iOS.
- Android assembly/lint, physical-iPhone Kotlin compilation, shared Android/iOS tests and signed Xcode simulator build passed. New lifecycle tests cover progress during a suspended provider read, actual OCR messages, blocking repeated uploads, clearing the dialog before showing an extraction error, and cancelling without proceeding to review or mutating purchases.
- On iPhone simulator, choosing the previously provided BNR scan from a temporary Files folder immediately showed the progress dialog with Opening file completed and Extracting data active, including Recognizing page 1 on this device. Processing then closed the dialog and showed the expected mismatch review for the synthetic October pending purchase. Confirm remained disabled; no purchase was saved. The test used a disposable localhost API and the temporary simulator copy was removed afterward. No original source PDF or production purchase was modified.

## Readable loading and notice expiry — 24 October 2026

The PDF indicator is now a compact spinner with the current status and a one-second minimum visible duration for fast success/error paths. Cancellation remains immediate. Shared tests cover a fast successful parse staying in loading before review, an error staying in loading before its notice, repeated taps being ignored and cancellation clearing state. Success notices now expire after three seconds and errors after five seconds; repeated identical notices receive a fresh timeout.

Final Android assembly/lint, shared Android host and iOS simulator tests, and physical-iPhone Kotlin compilation passed. An initial iOS test run reported no booted simulator; rerunning on the booted existing iPhone 16 Pro passed. The extra Xcode/UI check originally blocked by approval-service usage limits was not rerun for this follow-up; no new visual verification is claimed. No production records were changed.

## Store confirmation PDFs in Documents — 24 October 2026

Android assembly/lint, physical-iPhone Kotlin compilation, 62 Android shared tests and 68 iOS shared tests passed. The new shared test verifies exact PDF bytes sent before the pending purchase PUT, no confirmation after upload failure, retry after a purchase-write failure reusing the uploaded document, Documents refresh, and private import cleanup after completion.

All 21 targeted website upload, account deletion, owner-storage and statement tests passed in isolated PGlite databases. Upload tests verify unchanged download bytes, hidden partial uploads, multi-part retries, hash deduplication, account isolation, origin/session enforcement, bounded malformed-input rejection and account deletion cleanup. Website TypeScript checking and ESLint on changed document modules/routes passed. No production records or PDFs were uploaded; the website deployment remains manual, with its checklist at /Users/muhirwagabooreste/WebstormProjects/gabo/DOCUMENT_UPLOADS.md. Simulator was restored to its original shut-down state. No new UI screenshot is claimed.

## Automatic device authentication — 24 October 2026

Android assembly/lint, physical-iPhone Kotlin compilation, 65 Android shared tests and 71 iOS shared tests passed. New tests cover automatic cold-start authentication queued during initialization, returning from background without a tap, ignoring duplicate resume events, cancellation without prompt loops, authentication-sheet lifecycle events, manual retry and skipping authentication when no sign-in is saved. Existing background privacy and session renewal tests still pass. The simulator was returned to its original shut-down state. Native biometric UI was not manually exercised in this follow-up.

## Pull-to-refresh, monthly agenda and investment targets — 24 October 2026

Final Android assembly/lint, physical-iPhone Kotlin compilation, 72 Android shared tests and 78 iOS shared tests passed. New cases cover a fast refresh holding loading before success, failure instead of a checkmark, duplicate-pull suppression, snapshot preservation, all-month agenda dates, excluding repetitive interior subscription days, target validation, per-account/server persistence and deletion cleanup, complete contribution aggregates, purchased versus pending cash costs, sold/matured records, foreign currencies and future purchases. Website TypeScript checking, ESLint on mobile-overview.ts and all 29 targeted overview/upload/account/owner-storage/statement tests passed. Signed Xcode simulator builds passed for the updated shared UI.

UI inspection used a disposable localhost API and synthetic data. Calendar showed October's four events and its dated agenda even with an empty October 24 selected; the floating button opened a pending bond preselected to October's issuance without an intermediary page. Settings defaulted to RWF 100,000, accepted the 300k preset, and updated its recent-month cost/progress view after Check progress. Previews: [Month agenda](screenshots/ios-month-agenda.png), [Target settings](screenshots/ios-monthly-target-settings.png). No production purchases were changed.

Native simulator automation could activate accessibility controls but did not deliver working drags or scrolls on either Calendar or Portfolio, so no visual claim is made about the pull animation; its timing, completion/error state and duplicate prevention are covered by shared tests. The temporary API was stopped, its origin was passed only as launch arguments, and the simulator was restored to its initial shut-down state. The website monthly-aggregate addition remains a manual deployment; device-local targets and explicit full-history calculation work before that update.

## Financial number privacy — 24 October 2026

Android assembly/lint, physical-iPhone Kotlin compilation, 76 Android shared tests, 82 iOS shared tests and the signed Xcode simulator build passed. New tests cover fixed masks for currency, negative/large values, rates and share quantities; readable navigation dates; account-reference masking; unchanged formatting/calculation data; preference persistence; and toggling without extra portfolio requests.

Native iPhone simulator verification used only synthetic amounts on a disposable localhost API. The eye toggle masked the hero and target card, removed target percentage semantics, and hid share quantities/costs in history and expanded share details. Accessibility inspection returned Financial numbers hidden rather than original values. After terminating/reopening, the app still had hidden numbers and the allocation chart exposed only Financial chart hidden; toggling again restored amounts and proportions. Preview: [Numbers hidden](screenshots/ios-numbers-hidden.png). Original visible-number preference was restored, the test app/API were stopped, and the simulator returned to its initial shut-down state. No production account data was changed; this feature needs an app rebuild, with no server deployment.

## Initial persistent-masking capture protection — 24 October 2026

Historical validation of the first implementation; the best-effort iPhone secure-container implementation below supersedes its always-masked policy.

Android assembly/lint, physical-iPhone Kotlin compilation, 79 Android shared tests, 85 iOS shared tests and signed Xcode simulator builds passed. Shared policy tests verify protection defaults before data loads, eye-toggle bypass prevention on iPhone, Settings-only override and persistence, preservation of manual hiding, and background window protection independent of a foreground override.

A new Android instrumentation test ran successfully on an isolated disposable Android 16 emulator. Real UiAutomation screenshots of a synthetic magenta window were black with protection enabled and magenta after the explicit override. The test also verified re-protection while inactive, foreground restoration of the override, re-enabling protection, and saved permission on a fresh Activity. Its local API served only an unauthenticated test response. An earlier run on a read-only existing emulator failed installation due insufficient storage; it did not change the saved emulator. The successful fresh device is removed after testing.

Native iPhone simulator inspection used a disposable localhost API with synthetic financial data. The initial Portfolio was masked with protection enabled; the eye action displayed Settings guidance without revealing values. Settings showed the default enabled capture switch, its honest persistent-masking explanation, and the disabled forced Hide numbers switch. No iPhone screenshot-blocking API is claimed: screenshot notifications arrive after capture, so the default protection keeps values masked while enabled. Actual opt-out semantics were covered by shared tests; the iPhone UI protection switch was left enabled. No production data was read or changed, and both temporary test environments were cleaned up.

Primary references: https://developer.apple.com/documentation/uikit/uiapplication/userdidtakescreenshotnotification, https://developer.apple.com/forums/thread/824922, https://developer.android.com/security/fraud-prevention/activities.

### Shared contract price history (24 October 2026)

- 83 shared Android host tests passed, including I&M at 90 on 22 July followed by a published 115 close, source precedence, date/range filtering, missing quotes and contract price ranges.
- Android debug assembly/lint and shared iPhone device compilation passed. No iPhone runtime or visual chart verification was performed for this change.
- Companion website: 21 market/history tests passed; the extended contract test also verified long-history endpoints/extrema. Tests used isolated PGlite storage and synthetic multi-account records. TypeScript and targeted ESLint passed.
- Existing confirmed contract records contribute via an anonymous database projection; official closing-price storage and current valuation remain unchanged. The API must be deployed separately; no production data was changed or website files published.

### Swipeable company charts (24 October 2026)

Android debug build/lint and shared iPhone device compilation passed after introducing the company chart pager. All 85 shared Android host tests passed. Native swipe interaction was not exercised on a simulator/device in this change.

### Holdings returns (24 October 2026)

Holdings now show company logos, market value, signed gain/loss amounts and a green/red percentage badge (neutral for unchanged/unpriced holdings). Return uses the shared valuation's combined purchase cost including fees, rather than a daily price change. Missing quotes and zero cost produce no invented percentage. All values continue through FinancialText masking. All 86 shared Android host tests, Android build/lint and shared iPhone device compilation passed; no native visual verification was performed for this card redesign.

## Best-effort visible-number iPhone capture shielding — 24 October 2026

Supersedes the initial always-masked iPhone policy above. The native Swift host now wraps the shared Compose controller in the secure text-layout canvas, retaining its UITextField owner. Numbers can be shown with capture protection enabled. An unrecognized/missing canvas falls back to shared masking; active scene capture also forces masking. Manual number privacy and the Settings-only capture override remain separate.

Android assembly/lint, shared physical-iPhone Kotlin compilation and the signed Xcode simulator app build passed. All 86 Android shared tests and 93 iPhone simulator tests passed. A new iPhone test verifies that native readiness and recording state change an existing model immediately, manual hiding survives recording, eye toggles cannot bypass forced masking, explicit opt-out is honored and unavailable shielding fails closed.

Native UI validation used fictitious holdings on a localhost-only API, with protection explicitly on through temporary launch arguments. iOS 18.6 (iPhone 16 Pro) and iOS 26.2 (iPhone 17 Pro) both showed RWF 46,000 normally; Settings reported protection on, the secure canvas available and manual Hide numbers off. Navigation/touch and accessibility remained functional. No real account was accessed or modified, and the UI capture-protection toggle was not disabled. The local server and simulator apps were stopped and both simulators shut down after inspection.

These checks verify container installation, UI functionality and masking policy, not pixel exclusion from real device captures. Physical-iPhone screenshots and recordings must be checked before relying on this best-effort workaround. It shields the Compose app view as a whole, not external system pickers/apps or OS status bars. Apple does not document secure text canvas embedding as screenshot protection and its behavior may change: https://developer.apple.com/forums/thread/824922.

### English/French localization (2026-10-24)

- Shared language tests cover system/region fallback, saved selection, switching, message arguments, French number/date formatting, decimal keyboard input, and financial masking with narrow no-break spaces. Android shared suite: 92 passed. iPhone simulator shared suite: 99 passed.
- Android debug assembly, Android lint, iOS device Kotlin compilation and the native Xcode simulator build passed.
- On iPhone 16 Pro / iOS 18.6, using fictitious data from a localhost fixture: verified the French portfolio and calendar layouts, Settings → Appearance → Language, immediate English/French switching, and retention of the selected language after terminating and relaunching the app. No production account was used.
- Checked the shared catalog for duplicate keys and coverage of all 251 bundled education blocks. Original PDF contents, company names and API identifiers stay unchanged.

## Capture-only masked iPhone backdrop — 25 October 2026

The native capture host now preserves a redacted UIKit image beneath the secure live Compose content. Simulator checks with fictitious holdings showed visible live amounts with manual Hide numbers off, while the debug-only backing-image inspection showed masked amounts, a hidden allocation chart, and normal navigation/Settings. The backing image continued to follow navigation and model updates. No screenshot-export action was added.

A signed build was installed on the connected iPhone 12 Pro running iOS 18.7.1. Xcode’s real-device screenshot at 05:19 showed the portfolio interface, masked financial values and the visible-numbers eye state, rather than a blank screen. The test used temporary launch arguments and read-only fictitious portfolio data. Evidence: `docs/screenshots/iphone-capture-redaction.png` (ignored artifact). The user subsequently tested a physical-device screen recording with Hide numbers off and confirmed that numbers were masked as requested. The temporary test connection was cleared by relaunching without overrides.

Final validation passed: 92 Android tests, 100 iOS tests, Android debug build and lint, and a signed physical-iPhone build.

Privacy regressions cover layer redaction/restore, immutable captured pixels, secure-host readiness, recording without forcing the manual visibility preference, and opt-out/fallback behavior. Android’s existing screenshot blocking remains in place. The UIKit secure-canvas behavior remains undocumented and should be checked after iOS updates.

## Android masked screenshot fallback — 25 October 2026

The user chose to allow Android screenshots with financial values masked on screen whenever capture protection is enabled. Foreground FLAG_SECURE blocking is removed after Compose initializes; background windows remain secure for recents privacy. Protection enforces the shared mask independently of the manual Hide numbers preference, including numeric inputs and charts. Disabling protection restores manual visibility control. The Settings explanation is updated in English and French. The iPhone capture-only implementation is unchanged.

Validation: Android debug build, lint and all 92 shared Android host tests passed. The updated native instrumentation test passed on the connected BRP-NX1M running Android 15: the system screenshot contained the foreground test content with either protection preference, while pausing the Activity restored FLAG_SECURE and resuming cleared it. The test restored the saved connection and protection preferences.

A subsequent temporary read-only instrumentation probe checked whether the internal `SurfaceControl.Transaction.setSkipScreenshot(SurfaceControl, boolean)` method could support an iPhone-like layer exclusion workaround. On this Android 15 phone, normal app reflection returned `NoSuchMethodException`. This rules out direct access on the tested device; it does not establish that every private/native workaround is impossible. The probe did not launch an Activity or change account/capture settings, and its source and installed test package were removed afterward. No capture-only Android workaround was added to production.

## Experimental Android capture-only rendering — 25 October 2026

After explicit user authorization to try private/native code, a debug-only HiddenApiBypass 6.1 experiment successfully invoked setSkipScreenshot on the app’s own SurfaceView. On the connected BRP-NX1M running Android 15, a red live test surface remained visible to PixelCopy while a system screenshot showed its blue backdrop. No root access or system configuration change was used. The preliminary experiment Activity was removed after this proof.

The app now uses one excluded live-image surface above the original Compose view, with the shared redaction registry producing an immutable masked image for the ordinary window. Initial financial text remains masked until the private exclusion transaction commits and the first protected frame renders successfully. Native rendering and the bypass dependency are debug-only; release builds retain the supported on-screen masking fallback.

The financial-text device regression verified visible digits in the live surface and a masked system screenshot matching explicitly hidden UI. Turning protection off captured the digits normally. A two-second OS screen recording of the synthetic financial screen was decoded with AVFoundation: all seven frames in the final recording contained the compact financial mask instead of the long visible number. This is real-device synthetic evidence, not a claim that all Android manufacturers support the private operation.

Final validation passed: all 92 shared Android host tests; both connected-device privacy regressions, including touch routing, amount updates, manual masking, opt-out/re-enable, and background window flags; debug and release builds; and Android lint. The device regression waits for the expected protected frame rather than assuming a fixed redraw delay. Synthetic evidence is saved in ignored `docs/screenshots/portfolio-android-capture-live.png`, `portfolio-android-capture-masked.png`, and `portfolio-android-recording-frame.png`. The experimental debug build remains installed; the test package and device-side synthetic artifacts were removed, and the normal app was reopened. Saved connection and privacy settings were restored by the tests.

## Authentication grace and short-lived data cache — 25 October 2026

The shared model retains its unlocked session and data through background interruptions shorter than 30 seconds. It still covers inactive UI immediately and locks/purges private state after that deadline, with a monotonic foreground check for suspended iOS timers. Cold launches retain mandatory authentication. Finishing saved-sign-in setup while already backgrounded honors the original deadline.

Portfolio and market data use a one-minute in-memory freshness window; explicit refreshes and writes force network updates, and account/session clearing removes cache markers. Regression tests assert that three screenshot-style interruptions cause no additional vault reads or API requests, long backgrounding relocks, suspended deadlines cannot bypass authentication, and cache expiry/refresh/writes fetch again.

Validation: all 97 Android shared tests and 105 iOS shared tests passed; Android debug build and lint passed. The final signed iPhone build also passed. Updated debug builds were installed and reopened normally on the connected iPhone and Android phone, with saved settings preserved. The test simulator was shut down afterward. No server changes or persistent private-data cache were introduced.

## User-facing exception messages — 25 October 2026

Audit found direct exception-message rendering in the shared action dispatcher (including initial session checking before login), both purchase form failure handlers, and PDF review empty states. The UI displays model errors through its red Notice and Settings text; those surfaces now receive only approved copy through the shared error setter. This identifies a route that can explain the reported red startup text; the exact original failure was not captured. Stock-history failures already used static retry copy. Native/raw parser progress is now filtered before display too.

Regression tests inject a synthetic native/transport diagnostic with a private URL/token marker, malformed successful JSON/HTML and an HTTP server failure into startup before login. They also check direct error assignments, PDF/validation failures, progress text, rate limiting, incorrect credentials, translated fallback copy and preservation of saved-but-refresh-failed warnings. Technical diagnostic markers never reach display state.

Final validation: 102 Android shared tests and 110 iOS shared tests passed (212 total), alongside Android debug build/lint and the signed iPhone build. Both device updates were installed and reopened using their normal saved settings. The iOS test simulator was shut down afterward. A final source audit found no remaining direct exception-message rendering in the shared screens; only the reviewed message boundary inspects failure messages.

## Automatic monthly target card — 25 October 2026

Removed the manual Check progress actions from the Portfolio insight card and Settings breakdown. Model loading now fills in missing monthly totals automatically on older compact APIs, while modern summaries use no full-history request. Both views use the same model readiness and progress function. Pull-to-refresh and post-save synchronization update the totals; ordinary cached reads do not repeat the work.

Regression coverage checks initial readiness, authoritative compact totals without history downloads, pull-refresh and post-save updates, compatibility fallback with fee-inclusive cash cost, compact preview preservation, cache reuse, optional summary failure/recovery and authentication expiry.

Validation passed: 104 Android shared tests and 112 iOS shared tests (216 total), Android debug build/lint, and signed iPhone build. Updated apps were installed and reopened normally on both connected phones. The iOS test simulator was shut down afterward.

### Shake number visibility (2026-10-25)

- Shared shake detection handles three distinct peaks in 700 ms, a 1.8-second cooldown, stationary/walking traces, sustained acceleration, rapid sensor noise, sparse bumps and invalid samples. Four new detector tests pass; Android host suite: 108 tests, zero failures.
- Android debug assembly and lint pass; signed iPhone device build passes. Updated builds installed on both connected phones, preserving data.
- Native accelerometer listeners are scoped to the resumed, unlocked, signed-in app. Settings includes a persisted opt-out with English/French copy. Capture masking remains independent.
- Physical shake sensitivity still needs hands-on testing on the phones; synthetic sensor traces verify detection rules, not device motion in a person's hand.
