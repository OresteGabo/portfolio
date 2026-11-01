# Investment details — design audit and specification

1 November 2026. Implementation target: the existing shared Compose detail pages; no backend or calculation changes.

## A. Audit of the implemented language

Reviewed InvestmentApp/theme/navigation, PortfolioDashboard and its financial summary, LibraryScreens/company charts, ShareHoldingCard, PortfolioPurchaseTiles, PurchaseForms, localized/financial text, motion, evidence actions, bond tracking, share valuation, receipt presentation, and current screenshot tests. The app uses Material 3 typography, cream/ink backgrounds, olive/cream primary colors, outlined icons, issuer logos and account-scoped financial text masking. These are the reusable identity. The scenic dashboard and its large summary surfaces serve a home-screen purpose, not a requirement for every financial record.

Current weaknesses: oversized dark gradient header obscures the next task; repeated containers and nested metric tiles give unrelated values equal weight; generic pairs make bond cash flows and equity ownership look alike; top-level tabs hide next actions and force movement between receipts and supporting evidence; document cards are nested inside sections; full purchase facts are verbose. Strong existing capabilities to preserve: saved-date precedence, manual/automatic receipt distinction, cost-inclusive valuation, unavailable/stale quotes, private PDF preview/export, confirmation/import and edit/delete.

## B. Specification before implementation

### Shared brand and behavior

Use the current Material colorScheme and typography exclusively, with tabular numeric figures, semibold amounts and quiet metadata. No gradients, new brand colors, decorative charts or invented returns. Open background and ruled sections provide structure; reserve a single tinted surface for the bond's next cash flow. Section titles are content-specific, not generic dashboard cards. Minimum 48dp interactive targets; semantic headings and labelled actions; FinancialText for financial values and privacy-aware descriptions. Logical start/end alignment, flexible rows, wrapping values and a narrow/large-font stacked layout. Existing app bar/back contract and hidden detail-page navbar remain unchanged.

Use one continuous, lazy page. The payment ledger has Upcoming/Past filters and an explicit full-schedule expansion, initially showing a short useful slice. Purchase/technical breakdowns disclose inline on request; supporting documents remain directly reachable. Save disclosure/filter state by investment identity. No new animated transitions; existing reduced-motion behavior is retained where used.

### Bond: an instrument and its cash flows

1. Compact issuer identity, wrapping name/ISIN and explicit purchase status.
2. Principal held with currency caption, followed by a restrained annual coupon-rate line.
3. One cash-flow highlight: expected net payment, due date/distance, gross amount, withholding and schedule confidence. Submitted/inactive/missing-term states do not invent a payment.
4. Ruled maturity timeline with actual endpoints/progress and a clear redemption disclaimer.
5. Income reconciliation: separately labelled manual and automatic totals; scheduled/accrued amounts remain estimates.
6. Payment ledger, with readable dates, expected versus recorded amounts, explicit receipt states and existing confirmation/reversal actions.
7. Document rows with open and download actions; existing personal confirmation/import remains available. Purchase details and edit/delete follow, with existing delete confirmation.

### Share: ownership and market evidence

1. Company/logo/ticker/exchange identity on the open page.
2. Available market value (or explicitly acquisition cost/unavailable price), paired with a signed, labelled unrealized change and return on recorded cost.
3. A ruled ownership statement: quantity and total acquisition cost, with precise market-quote date/cache status and refresh action. No fake chart or dividend forecast.
4. Chronological purchase ledger: date, quantity, price, fees and total when present in source records; original source document actions and optional complete purchase breakdown.
5. Explicit loading/error/unowned states; full history loads via existing model. All amounts remain scoped to ticker and currency.

## Verification plan

Re-run financial/presentation/model/export regressions and Android/iOS compilation. Expand synthetic emulator tests to cover both themes, 320dp width, increased font scale, long names/large values, and unavailable quotes. Inspect actual rendered captures, including scrolled ledger/documents, and refine after rendering. Record actual results and external limitations below; no physical-device or iOS visual claim unless executed.

## C. Implemented composition and decisions

The old `DetailHero`, `DetailSection`, `DetailMetric`, `DetailPair`, `DetailTabs` and `DetailDocuments` have been replaced by two product-specific continuous pages. The shared vocabulary is deliberately small: section headings, currency amounts, responsive financial rows, disclosures and original-evidence rows. No new calculation engine, API contract, dependency, backend change or animation was introduced.

Bond reading order is identity → principal/rate → next expected coupon → maturity → income reconciliation → payment ledger → complete purchase fields/management. Documents are one tap from the ISIN row; pending confirmation/import stays directly after the coupon highlight. This prioritizes the next cash flow without hiding evidence or original purchase terms. A filtered ledger distinguishes expected, manually confirmed, automatically marked and explicitly unreceived payments. Date-specific action descriptions distinguish repeated receipt buttons for assistive technology. Missing rate/tax/principal terms remain unknown in the summary and expanded financial fields.

Share reading order is company → available value/unrealized performance → dated quote evidence → ownership/cost → purchase ledger/evidence. It deliberately has no bond-style highlight or maturity treatment. Missing prices show cost with an explicit valuation disclaimer; cached quotations remain labelled. Fees are shown within each purchase rather than mixed with price performance. No historical price chart or dividend forecast is fabricated.

Both compositions retain the existing exact light/dark palette, typography family, issuer assets, financial masking and source operations. Values use tabular figures, wrap, and stack at narrow widths/increased font scale. Existing saved-route/back handling and model operations remain authoritative. Filters/disclosures use saveable state keyed by investment.

## D. Rendered review and refinements

Actual shared Compose content was rendered using synthetic local account, purchase, receipt, market and PDF responses on an isolated API 37 emulator. The personal connected phone and production services were not used. The test app bar is a fixture; it does not replace or certify the real protected app navigation.

Six final instrumented profiles passed: normal 411dp, wide 548dp, narrow 320dp, 320dp/160% system font/French/long instrument names/large values, unavailable price and cached price. Both products were captured in light and dark in every profile; ledger and full large-value captures bring the total to 30. Representative images were examined directly, including both themes, narrow/large-font layouts, receipt/purchase ledgers and missing/cached price states.

Rendered review led to concrete refinements: removed the pre-coupon action block in favor of the identity document action; corrected overlapping equity return fragments; stacked amount/percentage on narrow or scaled layouts; removed a redundant equity role caption; kept complete large values readable. Ledger screenshots are scrolled views, so content at the viewport edge is naturally partially visible.

Preview index (local artifact, intentionally outside Git):
`/Users/muhirwagabooreste/.codex/visualizations/2026/10/27/01a17b49-309a-7441-9802-3f5997ee739f/struct-detail-redesign-2026-11-01/README.md`.

## E. Functional verification

- Shared Android-host regressions: **248 passed**; core JVM financial regressions: **58 passed**.
- Shared iOS simulator regressions: **264 passed**.
- Android debug application and instrumentation APK assembly, and shared iOS simulator compilation: passed.
- Six synthetic emulator profile runs: **one instrumented test passed per profile**. The normal profile additionally fetches the original private PDF for preview, invokes native share/save through a test stub, confirms a specific automatic receipt manually, reverses that receipt, checks edit callback, cancels deletion without a DELETE, and expands share purchase fields.
- New pure regressions cover unavailable numeric bond terms versus an explicit zero tax, and valid/cached/future/non-RWF/zero/missing quotations. Existing date precedence, gross/net coupon, manual/automatic receipt, valuation, evidence/export and model tests remain in the suite.

Commands used:

```sh
./gradlew :core:jvmTest :app:shared:testAndroidHostTest :app:shared:compileKotlinIosSimulatorArm64 :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest
./gradlew :app:shared:iosSimulatorArm64Test -PiosTestDevice=<temporary-simulator-uuid>
adb -s emulator-5584 shell am instrument -w -r -e class dev.orestegabo.portfolio.investments.InvestmentDetailLayoutTest -e profile <profile> -e quoteMode <fresh|missing|stale> -e language <en|fr> dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

Earlier test iterations failed because of a test-only icon dependency/internal-state access, first-render visibility and ambiguous repeated receipt controls/scroll position. These were corrected; failed attempts are not counted as successful runs. Logs and the reproducible matrix script are in `/private/tmp/struct-detail-redesign/`.

Limitations: no iOS visual/device inspection, VoiceOver/TalkBack session, RTL visual validation, release-signing build, production network measurement or physical mid-range-device profiling. Existing financial calculations and APIs were preserved, not re-audited comprehensively in this visual task. Pending/inactive/matured and network-error branches remain implemented and covered through existing model/pure tests where available, but the screenshot matrix does not claim every state. Native share UI and actual PDF-reader rendering are outside this detail-screen fixture. Human device review remains appropriate before release.

## Modified files

Paths relative to the repository root:

| File | Purpose |
|---|---|
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentDetails.kt` | Bond composition, maturity, income and receipt ledger/actions |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/ShareDetailsScreen.kt` | Distinct equity identity, valuation, quote and purchase ledger |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentDetailComponents.kt` | Small responsive typography, row, disclosure and evidence primitives |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentDetailPresentation.kt` | Existing selection helpers moved; quote metadata and unknown-term guards |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PortfolioPurchaseTiles.kt` | Existing full purchase facts use responsive rows; unavailable term display |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentApp.kt` | Extract identical palette for production and rendered tests |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/FrenchStrings.kt` | Detail labels and disclosures |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/InvestmentDetailsTest.kt` | Missing-term and market metadata regressions |
| `app/androidApp/src/androidTest/kotlin/dev/orestegabo/portfolio/investments/InvestmentDetailLayoutTest.kt` | Responsive/theme captures and synthetic evidence/receipt/management actions |
| `docs/INVESTMENT_DETAILS.md` | Current implementation guide, replaces obsolete gradient/tab description |
| `docs/INVESTMENT_DETAIL_DESIGN.md` | Audit, prior specification, rationale, file inventory and actual validation |
| `docs/VERIFICATION.md` | Dated verification record |
