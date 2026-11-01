# Investment detail pages

Updated 1 November 2026. Shared Compose implementation; the existing protected navigation, APIs and financial calculation services are retained. See [design audit, specification and verification](INVESTMENT_DETAIL_DESIGN.md).

## Bond: principal and cash flows

A continuous page leads with issuer, investment status, ISIN, principal and annual coupon rate. One restrained highlight presents the next expected net coupon, due date, gross amount, withholding and schedule confidence. An open maturity timeline follows; reaching its endpoint does not confirm principal redemption.

Income tracking separates manually confirmed receipts from automatic markings and scheduled/accrued estimates. The payment ledger filters Upcoming/Past, initially shows three entries, and can expand to the complete schedule. Recorded amounts and expected amounts have distinct labels. Confirmation and reversal retain existing model operations; accessible action descriptions identify the payment date.

The identity's Documents action reveals original evidence with independent preview/download controls. Purchase details disclose all existing saved fields. Pending confirmation/import, editing and confirmed deletion retain their existing workflows. Missing financial terms display an unavailable state rather than a zero coupon or tax exemption. Official saved coupon dates retain precedence over generated dates; coupon rate is never described as yield to maturity.

## Share: ownership and market evidence

The company/ticker header presents the existing calculated market value and explicitly unrealized gain/loss. Missing usable prices show acquisition cost, never a fabricated current valuation or zero-price loss. Quote provenance includes the published date, cached state and refresh action. Ownership and acquisition cost remain separate from market evidence.

The purchase ledger displays dates, quantity, unit price, gross consideration, recorded fees and total payable where available. Original documents and full purchase breakdowns remain accessible per lot. The first three lots can expand to all history. History loading, retry and empty states reuse the existing model. Lots remain scoped to both ticker and currency. Dividend income, realized gains and future selling fees are excluded from the displayed unrealized return.

## Implementation and accessibility

`InvestmentDetails.kt` contains the bond composition; `ShareDetailsScreen.kt` contains the equity composition. Small shared presentation primitives live in `InvestmentDetailComponents.kt`; `InvestmentDetailPresentation.kt` contains pure selection/availability helpers. No universal hero, section-card or tab system is required.

The screens reuse the exact Struct Capital Material palette, issuer logos, financial masking, formatting and English/French strings. Financial values use tabular figures and wrap without ellipses. Paired values stack on narrow widths or increased font scale. Dates remain readable through the application's existing date conventions. Lazy lists, saved disclosure/filter state, semantic headings and labelled actions support everyday navigation without added animation or dependencies.

`InvestmentDetailsTest` covers date precedence, coupon reference amounts, receipt semantics, ticker/currency isolation, missing terms and quote availability. `InvestmentDetailLayoutTest` renders synthetic local fixtures in the actual palette and exercises evidence, receipt and purchase actions on an isolated emulator. Current run details and remaining platform limitations are recorded in the design document and [verification log](VERIFICATION.md).

## Annual coupon list from Calendar

Calendar's top-right **Coupons** action opens a protected yearly payment list, initially the current year in Rwanda time. Year arrows and This year navigate without changing the Calendar month. Back returns to that selected month; entering Calendar anew from its bottom tab still starts at Today. Selecting a coupon opens the existing Bond details page, where receipt confirmation, documents and purchase management remain available. Returning preserves the selected year, currency and expanded payment count. The first twelve coupons are shown, with Load more in twelve-row increments; summaries cover the full selected year, not only visible rows.

The list reuses `couponSchedule`, the existing gross/net/tax calculation boundary, receipt-state presentation and authenticated purchase-history/receipt APIs. It adds no payment ledger, endpoint or financial write. Saved dates take precedence over generated dates. Actual receipt amounts remain independent of expected coupons; automatic markings are never described as verified bank receipts. Manual and automatic totals remain separate, currencies are never added together, and receipt totals show unavailable rather than inventing missing amounts. Decimal aggregation delegates to the existing platform decimal implementation through `sumMoney`.

Schedules include recorded active and matured bonds, after acquisition and through maturity. Sold bonds have recorded entries only because a complete disposal-date ledger is unavailable. Submitted/cancelled/future purchases are excluded, as is principal redemption. Receipts are grouped by coupon due year, not actual bank settlement year. Missing rate, withholding, frequency or schedule information prompts bond review rather than a zero-income assumption. Complete purchase history is required before showing annual totals; receipt retrieval may finish separately with its own retry state. Expected amounts use currently saved terms; they are not historical term-revision accounting or guaranteed payments.

The yearly composition is shared Compose, uses the existing palette, financial masking and English/French localization, and hides bottom navigation while open. Financial rows wrap at small widths or increased font scale. `YearlyCoupons.kt` contains the pure annual selection/aggregation adapter; `YearlyCouponsScreen.kt` contains presentation. Regressions and rendered verification are recorded in [the verification log](VERIFICATION.md).
