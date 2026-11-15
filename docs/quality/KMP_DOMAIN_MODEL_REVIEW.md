# Investment domain modeling review

Reviewed against `927349c` on 15 November 2026. Scope: KMP domain/calculation models and their mobile record boundaries. Backend, database schemas, website and prior unfinished changes are excluded. This is a pragmatic review of the implemented concepts with one focused value-object refactor; it is not a migration to a new DDD framework.

## Existing concepts and boundaries

| Concept | Current representation / domain meaning |
|---|---|
| Portfolio | `PortfolioSnapshot` is a read projection over saved records and server aggregates, not a write aggregate or a second ledger. Summary/valuation operations keep currencies separate. |
| Bond purchase | A persisted/API `JsonRecord` has investor identity, dates, amounts, status and evidence. `BondTerms` is validated calculation input derived from it, not the persistence entity. Changing a schedule must not overwrite original evidence. |
| Share purchase / holding | Original lot IDs and remaining balances differ from ticker/currency company display rows. `ShareCompanyRows` creates synthetic display IDs; those are not purchase IDs for sale/import writes. |
| Pending investment | Submitted bond requests and pending share orders contribute to projections under existing policies, but do not become executed/sellable holdings without confirmation. Order estimates and recorded acquisition costs have different fee inputs. |
| Transactions | `validateLedgerEntry` validates/normalizes a proposed command against holdings/prior events/as-of date and computes sale allocation/net proceeds. Stored entries feed derived balances and recorded cash. A generic transaction event framework is not required. |
| Coupon payment | `BondTerms`/`scheduledBondPayments` describe contractual expectations; recorded receipts are separate. Receipt identity includes purchase and due date, not just date. Principal repayment is not coupon income. |
| Returns | `ValuedSharePosition` computes market gain against remaining acquisition cash cost. Combined return is cost-weighted, not an average of lot percentages or an annualized yield. Unpriced positions retain the existing cash-cost estimate. |
| Amount/currency | Wire/UI amounts remain Double with the shared decimal monetary boundary. Currency tags partition aggregation; RSE equity quotes only value RWF positions. No FX conversion is invented. |
| Dates/rates | Domain dates use `LocalDate`; JSON dates remain strings. Coupon/tax rates use fractions, not whole percent values. Tenor in years and payments per year are different units. |

## Corrections

**Medium — primitive frequency conflated with an arbitrary integer.** `BondTerms.frequency`, JSON admission and `couponSchedule` independently knew the frequency range, integer count or month-spacing behavior. The new internal `CouponFrequency` value object in `core/commonMain` owns:

- the existing whole count of 1–12 coupon payments per year;
- controlled construction with the existing error message;
- nullable numeric admission without rounding/truncating fractional values;
- the distinction between a valid frequency and a frequency with a calendar-month interval.

`BondTerms` constructs its validated typed view before the remaining validations. The public constructor/property remains `frequency: Int` for compatibility; the internal value is immutable and is reconstructed by the existing data-class `copy`. Schedule inference and gross/net coupon division now use that typed concept. `JsonRecord.terms` delegates numeric admission to the same type, retaining absent-field default 2 and malformed-field null results. No new public value-class API is exported to app/Swift callers.

A frequency of 7 is still valid when official payment dates are supplied. It still yields no inferred month schedule. An enum limited to annual/semiannual/quarterly/monthly frequencies would incorrectly reject supported inputs. The published-date branch still runs before inference.

No coupon amount, rounding, tax, accrual, allocation, commission, valuation, gain or cash-flow formula changes. Frequency validation remains first for invalid constructor/copy inputs. BondTerms constructor components, equality, `copy` call sites and primitive record fields remain compatible. No schema/serialization annotations, JSON identifiers, persisted values, API payloads or UI strings change.

## Other findings and decisions

| Priority | Evidence / risk | Pragmatic decision |
|---|---|---|
| Medium, retained | `JsonRecord` is used for API/persisted facts and read projections throughout `PortfolioSnapshot`, imports and ledger adapters. It does not itself enforce a complete investment entity. | Treat it as a lossless record/DTO boundary. Continue deriving validated inputs such as `BondTerms` and validating writes through domain services. Replacing all records would require unknown-field/schema compatibility and incomplete-review policies, not cosmetic wrapper types. |
| Medium, retained | `BondTerms.confirmedDates` accepts a List supplied by its caller; the type alone does not guarantee deep ownership. Result/projection data classes can also be constructed outside their calculators. | Do not claim all domain snapshots are deeply immutable. Existing record publication owns read-only copies. A future schedule-ownership change needs a deliberate constructor/copy compatibility design; do not replace the data class with large handwritten boilerplate merely to hide a list. |
| Medium, retained | Amounts, quantities and rates use primitives, but their valid ranges differ by context. Pending-order price has a cent boundary and caps; recorded share cost uses supplied fees; cash balance/gains may legitimately be negative. | No universal positive `Money`/`Rate` wrapper is introduced. A future currency-bearing amount type should first protect actual cross-currency operations, not silently restrict existing tags, amounts or unknown quotes. Keep current decimal primitives and currency segregation tests. |
| Medium, retained | `pendingBondInput` / `importedPurchaseInput` combine domain admission and JSON command mapping in shared app code. | The authoritative denomination predicate is already in core. Keep the tested boundary intact in this slice rather than moving evidence/field-default policy into a generic entity constructor. No persistence or network operation is added to domain models. |
| Low, intentional difference | UI share steppers advance by 100, while `shareOrderEstimate` explicitly accepts a single whole share (tested), and sale validation uses remaining quantities. | Do not propagate a UI increment into historical holdings, partial fills or sales. That would be a business-rule change, not a type improvement. |
| Low, intentional difference | `BondTerms` permits zero principal/rate and non-calendar frequencies; primary request validation uses treasury increments and extra record constraints. | These are different calculation/admission contexts. Do not impose request minimums on every valuation/schedule model. |
| Low, terminology | `BondTracking.couponsPaid` is a legacy name for scheduled income to date; `scheduledCouponsToDate` and KDoc clarify it. Coupon receipt evidence and assumed coupon estimates remain separate. | Retain compatibility names and existing unknown/assumed flags instead of making scheduled payments imply received cash. |
| Low, identifiers | Server/guest IDs, synthetic company IDs and ISIN representations serve different roles. | Avoid blanket UUID value classes or one universal security ID normalizer. Such changes could reject valid guest/display IDs or weaken matching/evidence rules. |

Some result types are intentionally simple immutable read projections, not anemic write entities awaiting persistence methods. Business behavior already lives in cohesive common services (`BondMath`, `InvestmentLedger`, `PortfolioValuation`, pending coupon/order estimation). No aggregate root, repository rewrite, domain event bus, use-case layer or factory hierarchy is justified for this correction.

## Tests

Three characterization tests pass before production changes: numeric/missing frequency record handling, constructor error precedence/message, and published-date inference/payment behavior. Three new value-object tests cover every supported count and month interval, non-finite/fractional/out-of-range states, equality and BondTerms copy revalidation. Existing schedule, monthly goal, receipt, ledger, pending estimate, valuation and import callers run unchanged.

## Verification

All commands below ran from this repository using JDK 21 (`ms-21.0.9`). The isolated verification used `--project-dir /tmp/inzira-domain-index.ApO5LF`, a `git archive` of HEAD plus only these six staged files, with `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk`. This excludes the 39 pre-existing unfinished entries.

1. Before production edits: `./gradlew :core:jvmTest --tests '*CouponFrequencyBoundaryTest' --tests '*BondMathTest' --tests '*BondPaymentsTest' --console=plain` — passed, including the new characterization tests.
2. Working tree: `./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain` — BUILD SUCCESSFUL; 748 host test executions (116 core JVM, 117 core Android host, 515 shared Android host), zero failures/errors/skips.
3. Isolated staged tree: `./gradlew --project-dir /tmp/inzira-domain-index.ApO5LF :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :core:allMetadataJar :app:shared:allMetadataJar :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain` — BUILD SUCCESSFUL; 737 host test executions (116 + 117 + 504), zero failures/errors/skips. Both configured iOS production targets and shared metadata compile; Android app and instrumentation APK assemble; release lint passes. Existing deprecation/compiler warnings remain.
4. Static checks in both working and isolated trees: `python3 scripts/check-kmp-packages.py` (411 working / 388 isolated source/test files), `python3 scripts/sync-design-colors.py --check`, `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` (19 passing), `node --test tests/*.test.mjs` (84 passing), and `git diff --check` — passed.
5. `./gradlew --project-dir /tmp/inzira-domain-index.ApO5LF :core:iosSimulatorArm64Test --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain` — BUILD SUCCESSFUL; 116 core + 523 shared iOS simulator test executions, zero failures/errors/skips. The six added common tests execute on JVM, Android host and iOS.

No physical-device UI test, Android instrumentation execution or runtime performance measurement is claimed for this domain-only change. The instrumentation APK was built. Review of the final diff confirms five Kotlin files and this report only; no financial formula, API, persistence, PDF parsing or UI implementation changed.
