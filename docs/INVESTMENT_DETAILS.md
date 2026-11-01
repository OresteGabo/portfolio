# Investment detail screens

Added 31 October 2026. Shared Compose UI; no backend schema, API or calculation engine changes.

- Home and investment history bond entries expand only a compact summary (coupon rate/next date/maturity), linked PDF downloads and a View bond details button. Full terms, receipts and payment schedules live on the dedicated screen. Share entries navigate directly.
- Owned share cards and watchlist entries in Markets open share details. Watchlist entries without a personal position show price information and an honest empty purchase history.
- Child pages have the existing app-bar back button and hide bottom navigation. Returning from a document preview retains the selected investment. Back from an investment opened in history retains history.

## Bond page

Instrument identity/principal, next scheduled coupon including an unconfirmed payment due today, gross/net per payment and saved withholding rate, maturity progress/principal, income tracking, linked source documents, saved purchase terms/costs/notes, upcoming/past payment schedule and receipt actions. Existing confirmation/upload/edit/delete flows are reused.

Published coupon dates retain precedence through existing couponSchedule/trackBond. Only active holdings have a next payment. A manual receipt for this exact purchase/currency can complete today's payment; automatic markings remain explicitly separate from verified receipt evidence. Scheduled and accrued amounts are estimates, not bank confirmations. Maturity progress is not proof of redemption. Coupon rate is not YTM.

## Share page

Uses existing valuedShares calculation, so cost includes recorded acquisition fees and an unavailable price never becomes a fabricated market value or zero-price loss. Header distinguishes purchase cost, market value and closing price for an unowned share. Shows share count, unrealized return, quote date/staleness and refresh; full purchase history includes original cost/fee/reference fields and linked contract/statement PDFs.

Lots are scoped by both ticker and currency. Full history is requested through the existing model, and a failure gets a retry action rather than pretending the compact portfolio is complete. No new dividend ledger or forecast was introduced: the page explicitly says a verified upcoming dividend is unavailable and dividends are excluded from the return.

## Presentation / verification

Existing Struct Capital Material colors, company/BNR logos, number privacy, light/dark themes and French localization are reused. Information cards wrap; paired metrics stack on narrow content widths. Lazy lists keep long schedules/histories scrollable without rendering everything at once. No extra decorative animation or network-blocked navigation.

InvestmentDetailsTest covers published dates and gross/net reference amounts, due-today manual versus automatic receipts, wrong purchase/currency/negative receipts, inactive/matured/invalid terms, and ticker/currency lot isolation. Existing financial, document and model tests remain the authority for their respective behavior. Verification: 244 Android shared tests and 260 iOS simulator tests pass, including four new detail tests. The Android debug app and shared iOS code compile. Android instrumentation labels were updated for Home/Markets; instrumentation is compiled but not executed on the connected personal phone. A physical-device visual check is still needed before release.

Compact-preview follow-up: the library and bond tile share the same authenticated PDF download/native share helper. Two regressions cover canonical document paths, rejection of arbitrary URLs/non-PDF responses and user cancellation. The shared Android suite now has 246 passing tests; Android debug build and shared iOS compilation pass. The earlier 260-test iOS simulator run predates this preview/download follow-up.

## Visual refinement

The detail pages now lead with a dark olive instrument/value header, with bond status/rate chips or a share unrealized-return badge. Task tabs keep the overview separate from complete payment schedules, document actions and purchase fields. Documents use dedicated PDF rows with independent open/download actions. Repeated tab/section headings and the empty dividend card were removed; dividend limitations remain in a small caption.

`InvestmentDetailLayoutTest` uses only synthetic localhost account/portfolio/quote responses and captures bond/share overviews in light/dark themes. Run it on an isolated unlocked emulator (not a personal phone or production origin). A fresh emulator may need its keyguard dismissed and notification permission granted before instrumentation to avoid OS prompts covering the accessibility tree. Four captures were rendered and inspected; the final instrumentation test passes. Earlier fixture/focus assertions failed and were corrected; they were not counted as passing runs. This does not replace iOS device or accessibility/font-scale validation.
