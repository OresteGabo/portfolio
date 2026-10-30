# Existing investment calculations: audit and policy

Reviewed 30 October 2026. This follows the codebase and parser audits; it does not introduce another investment engine or change dashboard layout. Scenarios are synthetic regression fixtures, never account records.

## Reference results

| Scenario | Gross calculation | Result | Net with the illustrative 5% default |
|---|---|---|---|
| BNR 7-year, allocation RWF 2,200,000, annual coupon 11.50%, July 2033 maturity | principal × annual rate ÷ 2 | RWF 126,500 per semiannual coupon | RWF 120,175 |
| BNR 10-year, allocation RWF 1,000,000, annual coupon 12%, August 2036 maturity | principal × annual rate ÷ 2 | RWF 60,000 per semiannual coupon | RWF 57,000 |
| 400 IMR at RWF 90 | 36,000 + 615.60 fees | RWF 36,615.60 acquisition cash cost | Not coupon income |
| 500 BOK at RWF 670 | 335,000 + 5,728.50 fees | RWF 340,728.50 acquisition cash cost | Not coupon income |

The supplied bond maturities specify months, not exact days. Test month-end anchors are explicitly synthetic; actual document dates are not replaced with those anchors. Fixture data: [investment-reference.json](../tests/fixtures/investment-reference.json), core `FinancialReferenceTest`, shared `FinancialPresentationTest`, companion Spring `FinancialCalculationTests` and Node `financial-calculations.test.mjs`.

## Inspected ownership

- `core`: `BondTerms`/`BondMath`, record-to-terms mapping, portfolio snapshots/valuation and monthly goals.
- `app:shared`: purchase forms/details, dashboard coupon/valuation presentation, calendar events, annual/portfolio PDF reports, receipts and simulator inputs.
- Existing TypeScript `calculations.ts`: hypothetical monthly projections and conversion of actual holdings into starting lots. This remains a planning tool, not persisted cash accounting.
- Companion Spring: `PurchaseValidation`, `PortfolioService`/`MobileOverview`, `LifecycleNotifications`, `StatementService`, NUMERIC/JSONB schema and existing coupon receipt persistence.

The backend remains authoritative. KMP calculations are independently testable projections; no client calculation bypasses persisted validation.

## Verified defects fixed

1. Explicit Treasury withholding rates, including zero, were overwritten with 5%. Spring now respects validated supplied rates and retains 5% only when omitted. Existing stored rates are not rewritten. Form help text reflects configurability, and a later personal confirmation retains an existing configured rate when the PDF does not supply tax.
2. Backend coupon receipt calculation truncated to whole francs. New calculations round to two decimal places HALF_UP; existing receipt rows are not retrospectively changed.
3. Inferred schedules stepped from previously clamped dates, allowing February to move later August payments to the 28th/29th. Core and Spring now step from the original first-coupon/maturity anchor. The backend's 300-payment truncation is removed within bounded 100-year terms.
4. Non-divisor frequencies such as seven payments/year cannot be inferred with integral calendar months. Published dates remain supported; core/backend do not fabricate an inferred schedule, and new backend writes require published dates for those frequencies.
5. Native estimates and totals used binary arithmetic at monetary boundaries. Existing calculations now use a small platform decimal primitive: BigDecimal on JVM/Android, NSDecimalNumber on iPhone. No decimal dependency or new investment model was introduced.
6. RWF UI and portfolio report formatting truncated recorded fractions; annual reports also rounded away fractional francs. Recorded two-place values are now displayed. English/French privacy masking continues to conceal the entire amount.
7. The simulator did not reuse core-generated schedules for holdings without published dates. KMP now supplies the existing core schedule and decimal coupon amounts to the existing engine. Multiple distinct explicit payments in a month are retained; duplicate dates are not double-counted. Zero-coupon holdings retain their principal through maturity.
8. Undated market quotes cannot establish an as-of valuation and now fall back to cost. Explicitly dated older/stale quotes remain usable as dated estimates, with existing stale/date presentation.

## Monetary policy and assumptions

Rates are fractions (`0.115`, not `11.5`). Gross coupon is principal × annual coupon rate ÷ frequency. Net coupon applies the configured withholding rate to the **unrounded** gross amount, then rounds once to two places HALF_UP. Scheduled cumulative amounts sum rounded payments. Annual contractual estimates are not cash receipts and may differ from the sum of payment rounding by a small fraction; they never increase portfolio principal automatically.

Bond acquisition cost is face value × price-percent ÷ 100 + recorded accrued interest paid + recorded fees, rounded once at the transaction boundary. Equity cost basis is recorded net payable, which already contains buying fees. Valuation does not add those fees a second time. Quote value minus buying cash cost is **unrealized** gain/loss; future selling fees are excluded. Portfolio money sums and monthly contribution thresholds use decimal arithmetic. Percentages, progress ratios, wire/UI compatibility values and hypothetical JS simulation still use floating-point types; this is not a wholesale arbitrary-precision wire migration.

The two-decimal policy preserves the existing database amount scale and broker charges even for RWF. It is an application recording policy, not a claim about cash denomination or a broker's settlement rounding. The default 5% withholding and existing RWF 11,000 pending-purchase commission are software defaults, not tax/legal advice or a guarantee for every instrument/investor/date. Applicable rates must be entered/verified. Projection purchase charges remain modeled separately; they are not evidence of a settled cash debit.

Coupon rate is contractual interest on face value. Auction `yieldRate` remains separate issuance metadata; it does not replace coupon rate or implement yield to maturity. Accrual remains a linear fraction between relevant schedule dates, an estimate rather than market clean/dirty pricing or a full day-count convention model.

## Dates, maturity and valuation limits

- Published coupon dates take precedence; entitlement filtering excludes dates on/before acquisition and after maturity. No generated date is appended to a supplied explicit schedule.
- Generated schedules are estimates. Month-end anchoring and bounded frequency handling are consistent in core and Spring. Stub-period proration and ex-coupon entitlement are not newly invented.
- At/after maturity, no further coupon or accrual is generated. Principal redemption and last coupon remain separate in the simulator and are credited once each.
- The live dashboard keeps an active bond's recorded principal until its status changes; maturity alone is not evidence that cash was redeemed. Annual reports exclude expired bonds from closing holdings. The app has no cash account to represent redeemed proceeds.
- Currencies remain separate. RWF quotes do not value USD holdings and no FX conversion is implied. Missing/invalid/future/undated quotes use cost and unavailable gains, not zero profit. Stale prices are dated estimates, not live executable prices.
- Receipt confirmations and automatic due-date markings are not bank verification. Reports show receipt records separately from scheduled coupons; no coupon/dividend cash is added to holdings valuation automatically.

## Existing gaps, not new accounting features

Realized gains, sale proceeds/lot allocation, partial disposals, dividend accounting, cash balances, corporate actions, FX and complete TWR/MWR/XIRR remain absent. A sold status excludes holdings but does not implement a sale ledger. No transactions or assumed dividends were created to fill those gaps. Mutable historical statuses still limit audited year-end reconstruction; append-only financial mutation history remains separate debt. Legacy receipt amounts and historical persisted values were preserved.

## Deferred by request

Advanced bond pricing/YTM solvers, institutional valuation, portfolio optimization, investment advice and trading analytics remain future work. They require explicit instrument/day-count/price-source policies, complete financial evidence and appropriate product review. No advice/execution/custody integrations were added.

## Verification

See [VERIFICATION.md](VERIFICATION.md) for actual run counts and environment limits. Core regressions ran on JVM and a disposable iPhone simulator, including decimal ties, gross/net tax, official-date precedence and month-end anchors. Shared tests cover reports, French/English privacy and simulator reuse. Backend tests exercise BigDecimal validation and schedule/amount functions without a live account; the PostgreSQL integration suite still requires its isolated database runtime.


### Standalone read-only analysis

[Investment outlook](INVESTMENT_TOOLS.md) extends `PortfolioSnapshot` with monthly scheduled coupon/redemption aggregation and complete-history purchase-fee analysis. It reuses the existing schedule, gross/net coupon, cash-cost and decimal primitives. Rates/taxes come from saved records, principal is separate from income, currencies are never summed across an assumed exchange rate, and incomplete values are flagged. No new coupon engine, market-price forecast or persisted investment records. The linked document specifies inclusive/exclusive cutoffs, history scope, fee denominator and regression cases.
