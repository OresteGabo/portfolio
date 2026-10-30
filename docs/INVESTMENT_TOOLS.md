# Standalone investment tools

Reviewed and implemented 30 October 2026. This extends the existing investor app; it needs no broker connection, paid service, new database table or subscription system. The entry point is **Settings → Tools → Investment outlook**, with Cash flows and Buying fees sections. Existing calculations, simulator, calendar, history and reports remain available.

## Candidate review before selection

| Candidate | Existing implementation and decision |
|---|---|
| Bond coupon income forecasting | Individual schedules, next payment, annual net coupon and dashboard income cards already exist (`BondMath`, `PortfolioDashboard`, `CalendarEvents`). Extended with monthly aggregation over a stated horizon, not a second coupon engine |
| Treasury maturity timeline | Calendar and dashboard already show maturity dates/principal due. Extended with monthly redemption totals alongside coupons; redemption remains separate from income |
| Bond ladder planning | No interactive ladder-construction/allocation tool found. Deferred: requires investor-selected instruments, available terms, constraints and clear scenario assumptions; no recommendations added |
| Reinvestment simulations | Implemented in `SimulatorScreen` and `calculations.ts`; reused unchanged rather than rebuilt |
| Contribution planning | Existing monthly target/progress and simulator contribution periods/cash injections; retained |
| Portfolio income projections | Existing hypothetical bond simulator and annual net coupon estimates. The new view covers scheduled saved-bond cash flows only, not total portfolio/dividend income |
| Dividend income history | No authoritative dividend ledger/API found. Not fabricated from share quantities or projected yields; remains a separate product/data decision |
| Investment fee analysis | Itemized fees/costs already exist per purchase and fee-aware equity valuation exists. Added a per-currency cross-purchase summary over complete acquisition history |
| Historical portfolio performance | Existing observed-price charts, unrealized gains and annual reconstruction have explicit limits. Complete historical total return still lacks cash/dividend/disposal/valuation evidence; not implemented as another chart |
| Portfolio concentration analysis | Existing asset/currency allocation and individual share positions. Issuer/ticker exposure percentages and maturity concentration/scenario limits are deferred; no risk recommendations |
| Annual investment summaries | Implemented generated calendar-year/YTD PDFs (`AnnualReport`), not recreated |
| Bond comparison using verified terms | Existing market listing/scores and individual verified terms. No new side-by-side comparison: reliable comparable execution price/date, gross/net yield conventions and stale-source policy need specification first |
| Exportable reports | Portfolio and annual PDF export already work; reused. New tools do not pretend to be historical tax reports |
| Financial scenario comparison | One configurable simulator exists, without saved side-by-side scenarios. Deferred pending a small, explicit scenario contract and demand; no invented future rates |

## Scheduled cash-flow outlook

`PortfolioSnapshot.bondCashFlowOutlook(asOf, monthCount)` in `core/InvestmentInsights.kt` reuses `BondTerms`, `couponSchedule`, `grossCouponPayment`, `netCouponPayment` and the existing decimal money primitive. It creates no persisted transactions.

The UI uses the current Rwanda date and twelve calendar-month buckets: today inclusive through the first day of the same month next year exclusive. The first bucket is only the remaining part of this month, not a complete prior month. Both endpoints are displayed. Empty months remain visible for currencies with eligible active bonds.

Only currently active acquisitions dated on/before the cutoff and not already past maturity are eligible. Submitted, sold/matured statuses and future-dated acquisitions are excluded. Current `active` status is an input, not proof of a bank payment. Cash-flow output is contractual **scheduled** gross coupons, net coupons, the gross-minus-net withholding difference and face-value principal repayments. It never adds repayments to coupon income or combines currencies through an assumed FX rate. It does not subtract receipt acknowledgements: a payment scheduled today may already have been received, and this view is not an unpaid-cash balance.

Explicit saved coupon dates take precedence through the existing schedule helper. Saved `scheduleConfidence=confirmed` plus explicit dates is labeled accordingly; inferred or otherwise unconfirmed schedules remain estimates. This is saved-data provenance, not cryptographic/bank verification. Duplicate explicit dates are deduplicated by the existing helper. Supplied irregular schedules remain irregular; no invented final coupon is inserted into an explicit date list. Generated schedules retain the existing month-end/leap-year anchors. Unsupported calendar frequencies without explicit dates produce a warning rather than manufactured dates.

Recorded coupon rates, payment frequencies and withholding rates are required for coupons; absence does not silently mean zero interest, tax exemption or semiannual payments. Known maturity/principal can still appear when coupon terms are incomplete. Invalid acquisition/maturity/currency/principal records are reported and excluded. Tax-free and zero-coupon terms work when explicitly recorded. Principal uses face value, not purchase premium/cash cost, and is not coupon-taxed. Accrued purchase interest/fees are not projected coupon income.

There are no assumed future auction rates, extra purchases, reinvestment, new dividends, exchange rates or market prices. Saved rates/tax policies remain fixed; amendments, defaults, early redemption, changing tax treatment and payment delays are outside the view. Scheduled timing and returns are not guaranteed. The UI links each month to the existing calendar to inspect dates and saved terms.

## Recorded acquisition fees

`PortfolioSnapshot.purchaseFeeAnalysis(asOf)` requires complete history rather than the dashboard's recent previews or older aggregated positions. The screen calls the existing full-history loader when needed; no new endpoint. Computation runs off the UI thread over a captured snapshot.

For eligible completed purchases on/before the cutoff, grouped by saved currency:

- Bond consideration = face value × recorded execution price / 100; accrued interest and `feesPaid` remain separate. Cash cost reuses `bondCashCost`.
- Equity consideration = `grossConsideration`; buying charges = `totalCharges`; cash cost = `netAmountPayable`, which already includes those charges. Itemized fee fields are not added again. Inconsistent payable versus consideration + charges is excluded using the existing two-cent compatibility tolerance.
- Fee rate = total recorded buying charges / total trade consideration. Its denominator excludes fees and accrued interest, and is displayed explicitly.
- Sold/matured acquisitions remain historical costs. Submitted, cancelled/unknown and future transactions are excluded; sale proceeds/fees are not reconstructed from a sold flag. Quantities/statuses are the current saved records, not immutable historical lots.
- Missing fee/price/cost values are flagged, not assumed zero. An explicit zero charge is valid. Aggregate share rows are not treated as transactions. The summary is of usable records; omitted records are listed before the totals.

The result does not include custody, statement-only cash movements, future selling charges, external bank fees, or charges not saved against the purchase. It does not claim all-in lifetime cost, tax deductibility or performance after disposal. Corrections to saved purchases can change this read-only summary. Existing purchase disclosures/original-PDF links remain the route to verify fees.

## Boundaries and tests

All money aggregation reuses the core decimal primitive and existing two-place HALF_UP recording policy. Coupon amounts round per payment before monthly aggregation; percentages remain ratios of those recorded amounts. No persisted data, purchase status or receipt evidence is changed. The Spring APIs remain authoritative for saved data; the new tools only read the existing account-scoped history. Native capture protection/number masking, English/French copy and existing navigation styles are reused.

`InvestmentInsightsTest` covers the supplied BNR and BK Capital examples, gross/net/tax separation, horizon boundaries, empty months, simultaneous maturity/final coupon, explicit-date precedence and deduplication, inferred leap dates, zero coupons, non-divisor schedules, malformed/missing terms, scope exclusions, currency separation, fee denominator, accrued interest, incomplete costs, payable mismatch, compact-history rejection, cents/zero fees and non-mutation. Run counts and platform checks are recorded in VERIFICATION.md. Synthetic records are tests, not user investments.

## Deferred advanced tools

Keep ladder construction, issuer concentration stress scenarios, side-by-side simulations, benchmark/total-return analytics, tax-grade fee attribution, dividend forecasts and institution/club planning deferred. They need trustworthy complete data and explicit assumptions, not extra decorative charts. Potential premium value is reproducible saved scenarios, deeper evidence-backed analytics and higher-volume exports after demand is validated. No billing, entitlements, paywalls, investment recommendations, direct broker access or new infrastructure is implemented.
