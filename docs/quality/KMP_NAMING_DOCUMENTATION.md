# Naming and financial-contract readability

Reviewed against `14c4eb8` on 15 November 2026. Scope: KMP financial projection and share presentation contracts. This is a focused improvement, not a claim that every name in the application has been reviewed. Existing unfinished work, backend, website, schemas and external contracts are excluded.

## Improvements

- `PendingCouponEstimate.kt`: replace ambiguous private `CouponReference`, `face`, `tax`, `saved`, `tenor`, `isin`, `terms`, `exact`, `curve`, `lower` and `upper` names with treasury, principal, tax-rate, normalized-identity and tenor-specific vocabulary. Name the local projection builder `annualProjection` and its boolean `isRateAssumed`. Keep all comparisons, branch order, arithmetic order and reference values unchanged.
- Document coupon selection precedence, explicit zero versus missing terms, supported estimation inputs, conflicting exact matches, deduplication by identity/tenor/rate, median weighting, interpolation and endpoint behavior. Correct the old comment implying one vote per security: distinct rates for one identity remain distinct observations under the existing implementation.
- Document that `today` filters the built-in dated fallback points; it does not date-filter caller-supplied public terms. This describes current behavior rather than adding historical-market-data semantics.
- `ShareAcquisitionCost.kt`: name the local sum `totalFees` and document gross versus fees versus payable, separate rounding boundaries, and why the pending-order commission must not be added to recorded fees again.
- `ShareCompanyRows.kt`: rename `latest` to `latestPurchase`; document display-only company grouping, remaining balances after sales, synthetic IDs, latest-trade metadata and the retained references for ungrouped rows. The function does not return an independent mutable ledger snapshot.

No public type, function, parameter or result-property name changes. `PendingCouponEstimate.assumed`, `rate` and `netAnnual` remain compatible; their KDoc now explains their meaning. No serialization annotations, JSON keys, SQL identifiers, route names, package names, translation keys or persisted values change. No dead-comment deletion or package move was justified within these reviewed files.

## Vocabulary for contributors

| Term | Meaning in these components |
|---|---|
| Principal / `faceValue` | Bond nominal amount used for coupon calculation; distinct from acquisition cash cost. Keep the existing record key. |
| Coupon rate | Fractional annual contractual interest rate, e.g. `0.12` = 12%; not yield or investment growth. |
| Net annual coupon | Annual coupon after withholding tax; not a payment calendar or realized gain. |
| Tenor | Bond term in years used for the estimation curve; not the remaining holding period. |
| Assumed rate | Rate obtained from the tenor curve. `assumed == false` alone does not establish availability: check `netAnnual != null`. A known zero coupon and an unavailable estimate both have `assumed == false`. |
| ISIN | International Securities Identification Number. Matching normalizes whitespace/case and the existing `/MN` suffix before comparing maturity. |
| Gross share cost | Number of shares multiplied by purchase price, before recorded fees. |
| Payable / acquisition cash cost | Gross cost plus supplied recorded fees; distinct from current market value. |
| Purchase lot | Individual ledger purchase with its own evidence and remaining balances after sales. |
| Company display row | Active lots grouped by ticker and currency for presentation; not a replacement for purchase history. |

Prefer precise private names without renaming public fields solely for style. Before changing an existing ambiguous public term, inspect UI, JSON mapping, platform and test callers. Explain financial assumptions and ownership in KDoc rather than repeating the expression in prose. Keep bond coupons and share growth vocabulary distinct.

## Regression checks

Existing coupon, acquisition-cost, company-grouping, portfolio summary and shared presentation tests remain unchanged. Three additional coupon tests cover unavailable versus known-zero results, conflicting exact-match rates falling back to an assumed median, and the scope of date filtering. The first test compilation identified a nullable assertion argument in the new test; it was corrected with `assertNotNull` without changing production behavior or tolerances.

## Verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`.
Android SDK: `/Users/muhirwagabooreste/Library/Android/sdk`.

Working-tree build:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 58s`: 107 core JVM, 108 core Android host and 475 shared host tests, no failures/errors/skips. Log: `/tmp/inzira-naming-working-build.log`.

The five-file staged tree was exported using `git write-tree` and `git archive` to `/tmp/inzira-naming-index.LmGNkY`, excluding existing unfinished work. The same command with `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk` and `--project-dir "$naming_export"` passes: exit 0, `BUILD SUCCESSFUL in 1m 29s`, **679 host executions** (107 core JVM, 108 core Android host, 464 shared host), no failures/errors/skips. Android build/lint and both iOS compilations pass. Log: `/tmp/inzira-naming-index-build.log`. Eleven extra working-tree shared tests belong to excluded prior work.

Native verification on that export:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$naming_export" :core:iosSimulatorArm64Test --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --tests '*PendingCouponEstimateTest' --tests '*ShareAcquisitionCostTest' :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 49s`: **11 focused core native tests + 483 shared native tests**, no failures/errors/skips. Log: `/tmp/inzira-naming-index-native.log`.

Static/script checks run in both working and isolated trees:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Package ownership/module DAG checks and color consistency pass; **16 Python and 84 Node tests** pass in each tree. Final whitespace checks pass. Source/test bytes in the index are compared with the verified export before commit; only this report receives final result updates afterward.

Baseline compiler/lint warnings remain. No Detekt/ktlint task is configured. No physical-device UI test, connected instrumentation execution, account mutation or backend deployment is claimed. Full core-native execution was not selected: the separately documented pre-existing `ShareCompanyRowsTest.remainingSharesAndTheirCostAreUsedAfterSales` floating-point exact-equality failure remains outside this change. Company grouping is covered by full core JVM/Android host tests and its callers by shared native tests; arithmetic and tolerances are unchanged.
