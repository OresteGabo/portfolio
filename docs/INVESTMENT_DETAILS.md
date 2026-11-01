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
