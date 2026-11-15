# KMP regression test quality review — 2026-11-15

Baseline: `a60f21b`. Scope: KMP mobile client. Four new common-test suites add **18 behavior tests**. A focused one-line guest-ledger retry fix was required by a newly exposed failure. Financial formulas, serialized data, backend contracts and dependencies are unchanged. The 39 unrelated working-tree changes are excluded; verification uses an isolated export of the staged tree.

## Existing testing architecture

- `core` runs shared `commonTest` financial/domain/network tests on JVM, Android host and iOS simulator. `jvmTest` includes a BigDecimal oracle and the opt-in balance benchmark; `androidHostTest` supplies platform test coverage. The ordinary JVM target exists only in core.
- `app/shared` runs `commonTest` ViewModel/workflow/repository tests on Android host, Android device and iOS simulator. `androidHostTest` also covers JVM/platform adapters, picked-file bounds and backup compatibility. `iosTest` exercises PDFKit, Vision, protected storage, capture protection and JavaScriptCore/native extraction.
- `app/androidApp/src/androidTest` contains Android UI integration, protected storage, WebView PDF engine, rendering, intent and transport cancellation tests, using AndroidJUnitRunner and synthetic fixtures.
- Existing libraries are kotlin.test, kotlinx-coroutines-test and Ktor MockEngine; Android device suites use AndroidX runner/JUnit/Compose testing. No new libraries, mocks framework, test runner or coverage plugin was introduced.
- Existing suites already protect coupon tax/rounding, pending-order estimates, valuation, import duplicates, encrypted backup, account ownership/generation, guest isolation and cancellation. New cases target missing combinations rather than reproducing these suites.
- No configured Kover/JaCoCo reporting was found in the mobile Gradle builds, version catalog or checked-in CI configuration. No line/branch coverage percentage is claimed. Test counts count executions, not distinct business rules or coverage percentages.

## New behavior protection

All fixtures are synthetic; dates, clocks, dispatchers and suspension gates are controlled. Tests use public domain/repository operations or the existing shared import pipeline boundary. No native service, live backend, production credentials or user document is needed for the new suites.

| Suite / source set | Cases | Protected behavior |
| --- | ---: | --- |
| `core/src/commonTest/.../LedgerRegressionTest.kt` | 6 | Complete disposal distributes the final cent and reconciles net proceeds/gain/cost; unrelated sales/dividends do not consume another lot; supplied derived fields cannot forge gains/ticker; invalid quantities/prices/charges and inactive holdings reject sales; currencies stay separate and cash dates are inclusive; paid bond price/fees/accrued interest and manually confirmed due coupons drive cash; generic cash validation preserves inputs and enforces amount/currency/date/note limits. |
| `app/shared/src/commonTest/.../GuestLedgerRegressionTest.kt` | 6 | Sale and deletion survive repository reconstruction without changing original purchase facts; replay does not duplicate a sale and conflicting identities cannot overwrite it; failed writes preserve the durable snapshot and retry once; cancelled blocked writes preserve state and release ownership; competing sales cannot oversell, and linked purchases cannot be edited/deleted. |
| `app/shared/src/commonTest/.../AnnualShareClosingRegressionTest.kt` | 2 | Year-end share balances include boundary-day sales and exclude next-year sales; year-to-date stops at today, excluding tomorrow's disposal/purchase; original holdings are not mutated. |
| `app/shared/src/commonTest/.../PdfPipelineBoundaryRegressionTest.kt` | 4 | Picker dismissal does not parse/publish; acquisition failures retain their cause and stop parsing; parser failure cannot publish bytes or leave late progress active; a non-cooperative parser finishing after cancellation cannot validate/publish its result. |

The storage double follows the existing atomic-replacement contract. A read deliberately captures its snapshot and yields, making a lost-serialization race reproducible under the coroutine test scheduler. Cancellation gates use CompletableDeferred, not wall-clock sleeps or timing guesses. Tests do not demand random IDs, internal helper calls, mutex types, revision increments on harmless replays, or UI formatting. Expected money values are hand-calculated literals, not calls to the formula under test.

No production extraction was needed: constructor-injected storage, time, dispatcher and parser capabilities already provide the required isolation.

### Regression discovered and corrected

Replaying the exact original sale request failed because the repository compared raw JSON text (`100`) with the validated saved number (`100.0`). Comparing the validated/normalized fields with the existing transaction fixes both sale and cash-request retries. The original saved event is retained; changed price, quantity, fees, tax, note and cash amount are rejected without replacing the durable snapshot. Derived sale amounts/gains continue to come from validation, not caller-supplied claims. This changes retry acceptance only; it does not change monetary validation, calculations or stored records. The test was observed failing against the baseline before this one-line fix; it was not weakened to use only persisted/normalized request objects.

The fix is staged against the committed `GuestPortfolioRepository.kt` implementation; the unrelated pending extraction is excluded from the commit. The same line is reflected in the extracted working implementation so the user's pending refactor remains consistent.

## Test sensitivity and flakiness checks

Three temporary faults were injected one at a time in the isolated export, then restored:

1. Discard the last sale's remaining allocated cost: complete-disposal conservation test fails.
2. Exclude the reporting date from cash events: inclusive-date/currency test fails.
3. Remove local repository serialization: concurrent overselling test fails (the forced read suspension lets both writers otherwise read the same old snapshot).

Each failure was an assertion failure from the intended new test, not a compilation failure. Both production files were then checked byte-for-byte against HEAD before the initial full verification. The subsequent, intentional retry fix was verified again with the final staged sources. [Fault-injection evidence](evidence/2026-11-15/regression-fault-injection.json) records the exact failing test names. This is a limited sensitivity experiment, not a mutation score for the application.

All 18 new cases passed the corrected focused run, full host/native suites, and two additional forced host repetitions (`--rerun` on each test task). No intermittent failures were observed in these local runs. This is not proof that the entire application test suite is non-flaky.

No tests were disabled, relaxed or excluded to address a failure. The initial new PDF test assertion needed an explicit nullable list type to compile; the corrected assertion subsequently passed. Intentional fault-injection failures were temporary and are not present in the committed source.

## Verification

Build environment:

```sh
export JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home
export ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk
# test_export is an isolated git-index export, excluding unrelated changes.
```

Focused tests (passed):

```sh
./gradlew --project-dir "$test_export" :core:jvmTest --tests '*LedgerRegressionTest' \
  :app:shared:testAndroidHostTest --tests '*GuestLedgerRegressionTest' \
  --tests '*AnnualShareClosingRegressionTest' --tests '*PdfPipelineBoundaryRegressionTest' --console=plain
```

Full host/build verification (passed):

```sh
./gradlew --project-dir "$test_export" \
  :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest \
  :core:allMetadataJar :app:shared:allMetadataJar \
  :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest \
  :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 \
  :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

145 core JVM, 145 core Android-host and 526 shared Android-host tests: **816 executions**, zero failures/errors/skips. Common metadata, Android app/test APKs, release lint, iOS device main and iOS simulator main compilation passed. Existing compiler warnings remain; release lint reported 100 warnings and no errors.

Native/common runtime verification:

```sh
xcrun simctl boot 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4
xcrun simctl bootstatus 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 -b
/Users/muhirwagabooreste/.local/bin/android emulator start Sequo_References_API_36_1 --headless
ANDROID_SERIAL=emulator-5554 ./gradlew --project-dir "$test_export" \
  :core:iosSimulatorArm64Test --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
  :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
  :app:shared:connectedAndroidDeviceTest -Pandroid.injected.device.serial=emulator-5554 --console=plain
```

Passed: 144 core + 552 shared iOS simulator tests (696 total), plus 520 shared Android emulator tests, with zero failures/errors/skips. All 18 new common tests execute on both Android and iOS; core cases also execute on JVM. See [suite results](evidence/2026-11-15/regression-suite-results.json).

Relevant Android native integration tests (passed, six tests, zero failures/errors/skips):

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew --project-dir "$test_export" \
  :app:androidApp:connectedDebugAndroidTest -Pandroid.injected.device.serial=emulator-5554 \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.orestegabo.portfolio.investments.LocalEngineTest,dev.orestegabo.portfolio.investments.PdfDocumentRenderingTest,dev.orestegabo.portfolio.investments.HttpCancellationTest \
  :core:compileTestKotlinIosArm64 :app:shared:compileTestKotlinIosArm64 --console=plain
```

These cover the real Android local PDF engine and calculation bridge, rendering/zoom/temporary-file cleanup, malformed rendering, and cancellation of a stalled loopback PDF transport from the main dispatcher. The class selector scopes this additional app-level run; it does not restrict the full shared Android/iOS suites above.

```sh
for run in 1 2; do
  ./gradlew --project-dir "$test_export" :core:jvmTest --rerun --tests '*LedgerRegressionTest' \
    :app:shared:testAndroidHostTest --rerun --tests '*GuestLedgerRegressionTest' \
    --tests '*AnnualShareClosingRegressionTest' --tests '*PdfPipelineBoundaryRegressionTest' --console=plain
done
```

Both forced repetitions passed. Full-suite counts were captured before the focused reruns replaced their per-task XML reports.

Physical iOS test-source compilation also passed in the combined final command above. No physical-device test execution is claimed.

Static and fixture checks (passed, from the isolated export):

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
# From the original repository:
git diff --cached --check
```

405 Kotlin source/test files pass package, common-platform-import and module-DAG checks; resource audit consistent; 19 Python and 84 JavaScript tests passed; staged whitespace check passed. No JVM/native coverage percentage was measured.

## Remaining gaps and recommended order

1. Add more sanitized broker-format text/scanned fixtures as supported formats actually change. Native synthetic extraction and common orchestration are tested, but this does not prove compatibility with every real broker layout. The opt-in supplied-statement native test returns early when its environment path is absent; that is not real-document validation in this run. No user's financial files were accessed.
2. Exercise real biometric cancellation, protected-store lock/key-loss and background/foreground behavior on physical Android/iPhone hardware. Host doubles and simulator tests cannot reproduce every OS/hardware failure.
3. If numerical coverage is needed, evaluate a compatible coverage tool across the existing AGP/Kotlin versions separately. JVM-only coverage must not be presented as native/platform coverage.
4. Broader Compose UI instrumentation/screenshot/accessibility suites exist but were not all executed for this test-only domain/workflow change. Maintain representative UI journeys in emulator CI; compare platform rendering separately from business-rule tests.
5. Repeat full native/emulator suites in CI over multiple runs before declaring them non-flaky. Existing UI/native tests use OS deadlines, network-loopback waits and native OCR; successful local execution alone is not a statistical flakiness assessment. The new tests use virtual time and explicit gates.
6. Existing encrypted-store and document cleanup adapters have native tests. Longer interruption/recovery sequences across process death and OS storage pressure remain valuable integration scenarios; the new atomic-storage double cannot prove OS-level atomicity.

No backend authorization, remote API implementation, database schema or investment policy was changed as part of this review.
