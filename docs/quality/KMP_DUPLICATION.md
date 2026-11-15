# Harmful duplication review

Reviewed against `09a59ab` and the working tree on 15 November 2026. Scope: mobile KMP policy, UI, parser/transport boundaries and relevant native adapters. Backend, website, parser assets, schemas and build configuration are unchanged. Older unfinished work remains outside this commit. This is a targeted semantic review, not a claim that an automated clone detector found every duplicate in the repository.

## Classification

- **A — exact duplication:** identical fragments or configuration values repeated for the same responsibility.
- **B — semantic duplication:** differently written expressions enforce the same business rule.
- **C — intentional similarity:** code looks alike but has different inputs, guarantees or responsibilities.

| Area | Class | Evidence / action |
|---|---|---|
| Treasury denomination literals | A | `100_000` was repeated as lower bound and remainder divisor in pending, personal-PDF and issuance-confirmation validation; `ApplicationAmountStep` separately repeated the same denomination. Consolidate into `TREASURY_PRINCIPAL_INCREMENT`. |
| Principal acceptance | B | `pendingBondInput`, `importedPurchaseInput`, `pendingIssuanceConfirmation` and bid eligibility all require finite principal ≥ one increment and an exact increment multiple. Extract **`validTreasuryPrincipal`**; keep caller-specific checks/messages. |
| Bid/selector upper limit | B | Bid eligibility repeated `10_000_000_000_000.0` while the selector already declared `MaximumApplicationAmount`. Reuse that existing UI constant. Do not apply it to the other principal consumers. |
| Financial formatting | C | `Formatting.formatNumber` trims fractional zeros, uses a typographic minus and French narrow no-break grouping; report formatters use ordinary grouping spaces and different currency/decimal/non-finite/sign rules. Keep these output contracts rather than introducing many formatting flags. All already use the shared monetary rounding boundary where applicable. |
| Percentage presentation | C | `percent(rate)` scales a fractional rate by 100; notification code formats an already supplied `percent` field. Unifying calls blindly would alter displayed values. No input-scale changes made. |
| Share estimate versus acquisition cost | C | Both already use authoritative `decimalAmount`/`sumMoney`. A pending order applies the fixed commission estimate; recorded acquisition cost adds the supplied fee components once. Keep distinct functions/result types instead of a pending/confirmed flag. |
| Generic bond terms versus recorded principal | C | `BondTerms` supports general valuation/schedule inputs and different principal preconditions. It must not inherit the primary-request/allocation increment rule. Coupon, price, tax and date rules stay instrument-specific. |
| Fixed commission display copy | B retained | The existing translated “1.71%” label still duplicates explanatory rate copy. It is not generated from locale-sensitive `percent(rate)` because that would change the exact translation key/spacing. A future rate change needs deliberate copy review; no flag-driven formatter or new label output is introduced here. |
| Equal numeric defaults/examples | C | Monthly target presets, editable projection `purchaseMinimum`, and form/example amounts also use `100000`. These are different responsibilities, not copies of the recorded treasury rule. Keep separate defaults and examples. |
| PDF processing | B already shared/retained; C native lifecycle | `PdfImportPipeline` already owns common picker/parser/progress/cancellation mechanics. Guest and account admission remain different policies. Bounded native reads share a contract/limits, but Android/iOS buffer assembly and content-resolver/security-scoped URL lifecycles differ. Keep the small native loops behind tested ports rather than introducing a new generic I/O framework solely to remove them. |
| Errors and state changes | Shared already / C | `safeErrorMessage`/`userErrorMessage` centralize safe public text. A session rejection, unavailable optional endpoint and per-item migration review have different recovery/state effects. Do not replace them with a handler requiring role flags or hidden mutation callbacks. |
| DTO mapping | C | Incoming purchase normalization validates proposed fields; `migrationRecord` exports a whitelist from saved records and removes local identities/file references. Similar JSON operations do not make those transformations interchangeable. |
| Compose elements | Shared already / C | `FinancialText` handles shared financial presentation; `ApplicationAmountSelector` is reused by bid and pending-request screens. Bond/share forms still have different fields and actions. No flag-driven universal investment form/card was introduced. |

The common calculation primitives and PDF orchestration were retained rather than wrapped again. Configurable TypeScript projection assumptions are not rewritten as fixed Kotlin recorded-investment constraints; parser/projection assets remain unchanged and their existing Node tests run.

## Consolidation implemented

`core/…/investments/TreasuryPrincipal.kt` is the authoritative implementation of the existing finite/minimum/increment predicate. It does not round input, infer an allocation, calculate cost or impose a budget cap. It has no UI, JSON, storage, platform or networking dependency; no generic utility class or policy interface is needed.

The four validation callers now use the predicate. The selector retains its existing internal `ApplicationAmountStep` name as a constant alias to the shared denomination. Bid validation uses the existing selector maximum through a small pure `applicationBidAmount(String)` boundary, so parsing and cap behavior can be tested directly without rendering Compose.

Context differences are deliberately preserved:

- Pending requests and PDF confirmations have no new upper bound. Characterization includes a valid principal above the bid UI's cap.
- Bid eligibility retains that cap and the same `toDoubleOrNull` parsing; no French input normalization or new accepted number syntax is introduced.
- The selector retains existing clamping and stepping behavior, including a non-multiple legacy starting value. It is not converted into a validator.
- Personal allocation and public issuance retain their separate identity/date/tax/price/fee rules. Auction totals never replace an investor's principal.
- Exact increment validation does not round near-multiple inputs. No cost, tax, commission, rounding or cash-flow calculation changes.
- Existing `require` messages, first-failure order, immutable-input behavior and JSON fields/defaults remain. User-facing error copy stays context-specific; changing the denomination in the future still requires deliberate copy/translation review.

## Regression evidence

Three caller characterization tests were added and passed before production consolidation: pending requests, personal PDF validation, and public-confirmation input/cost handling. They freeze valid/invalid boundaries, error text, inputs retained unchanged, and amounts above the bid cap. The initial personal-PDF fixture lacked the required government issuer; it was corrected before the passing characterization run without weakening validation.

After consolidation, two core tests cover finite/exact increments, zero/negative/non-finite inputs, fractional values near multiples, and absence of rounding or an implicit upper cap. A fourth caller test exercises the actual pure bid-validation boundary, preserving the cap, scientific-number parsing, rejected separators, invalid/non-finite strings and fractional invalid amounts. Existing pending/issuance/application selector/model tests remain; no assertion or monetary tolerance is relaxed.

## Verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`; Android SDK: `/Users/muhirwagabooreste/Library/Android/sdk`.

Before consolidation, the three caller characterization tests ran with the existing callers:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*TreasuryPrincipalCallSiteTest' --tests '*PendingPurchasesTest' --tests '*BondIssuanceTest' --tests '*ApplicationAmountTest' --tests '*ApplicationBidModelTest' --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 3s`; log `/tmp/inzira-duplication-characterize-final.log`. The initial fixture failure is recorded in `/tmp/inzira-duplication-characterize.log`; it was resolved before production consolidation. After consolidation and the additional UI-boundary test, the same selectors plus `:core:jvmTest` pass: exit 0, `BUILD SUCCESSFUL in 13s`; log `/tmp/inzira-duplication-focused-final.log`.

Full working-tree command:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 1m 5s`; **104 core JVM + 105 core Android host + 475 shared host tests**, zero failures/errors/skips. Android build/instrument APK compilation, release lint and both iOS compilations pass. Log `/tmp/inzira-duplication-working-build.log`.

The eight-file source/test/report tree was staged and exported using `git write-tree` / `git archive` to `/tmp/inzira-duplication-index.TKSIfd`, excluding earlier unfinished work. The full command above was repeated with `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk` and `--project-dir "$audit_export"`.

Exit 0, `BUILD SUCCESSFUL in 1m 38s`; **104 core JVM + 105 core Android host + 464 shared host tests**, zero failures/errors/skips. Android build/lint/instrumentation compilation and both iOS compilations pass. Log `/tmp/inzira-duplication-index-build.log`. The working tree's additional 11 tests belong to excluded earlier refactoring.

New core-native rule tests and the full shared native suite:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$audit_export" :core:iosSimulatorArm64Test --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --tests '*TreasuryPrincipalTest' :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 47s`; **2 core native rule tests + 483 shared native tests**, zero failures/errors/skips. Includes all affected caller, bid/application, confirmation and import tests. Log `/tmp/inzira-duplication-index-native.log`.

Static/engine checks run in both working and isolated trees:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Both pass package ownership/module DAG and design-color checks, **16 Python tests and 84 Node tests**, and final whitespace checks. Logs `/tmp/inzira-duplication-{working,index}-{packages,colors,python,node}.log`. Source comparison verifies predicate/constant substitutions without changes to the surrounding pending/issuance/selector logic. All staged Kotlin matches the passing export byte-for-byte.

No clone detector was run; no Detekt or ktlint task is configured; this report does not assert an unmeasured duplication percentage. Baseline compiler/lint warnings remain. No connected Android instrumentation execution, physical-device UI run, live-account write, backend deployment or signed distribution occurred. Instrument APK compilation is not test execution.

Existing core arithmetic implementations are unchanged. The previously documented separate full-core-native share-cost floating-point equality baseline failure remains outside the selected new rule class; full core JVM/Android host and full shared native suites are not an all-core-native/all-task green claim.

Commit only this verified slice as `refactor(kmp): remove harmful duplication`; preserve older unfinished work.
