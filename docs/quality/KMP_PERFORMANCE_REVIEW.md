# KMP performance and memory review — 2026-11-15

Scope: KMP client only, baseline `b51d726`. No backend, website, API, financial formula, persistence format, or visual changes. Existing unrelated working-tree refactors are excluded from the commit and verified export.

## Instrumentation and baseline

- `UnlockPerformance` and `docs/UNLOCK_PERFORMANCE.md` provide opt-in unlock, protected-store and HTTP stage timing. Historical measurements are not current device baselines. SecureSession tests use controlled virtual time, which is not measured biometric latency.
- Native investment-engine tests emit PDF extraction timings. These are test-runtime observations, not release-device import benchmarks.
- No configured JMH, Android Macrobenchmark, frame/recomposition benchmark or retained-heap benchmark was found. This review adds an opt-in JVM experiment without dependencies or CI timing gates.
- Physical-device startup, authentication, dashboard latency, scrolling frames, PDF peak memory and lifecycle retention were not measured. No improvement in these metrics is claimed.

## Measured bottleneck and change

`core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentLedger.kt:shareBalances` filtered the complete ledger twice for each holding. Its callers include local guest snapshot construction (`GuestPortfolioRepository`) and cutoff-filtered share valuation in `AnnualReport` and `PortfolioReport`. This does not establish that authenticated dashboard startup spends the same time here.

The new implementation indexes matching sales once per invocation. It preserves encounter order, duplicate/blank/case-sensitive IDs, unknown JSON fields, quantity arithmetic, decimal addend order, zero-share handling and caller-owned date/status filtering. No aggregate rounding or cached financial state is introduced. An ID set excludes unrelated sales from the index. Time is linear in holdings, ledger entries and matched sales instead of repeatedly scanning all entries per holding; a duplicate holding still processes its own matching sales.

The extra index and ID set consume transient memory proportional to holdings and matching sales. They are discarded after each call. Existing output cloning and decimal arithmetic remain. This is a CPU optimization with an allocation cost, not a memory-reduction claim. A per-call index avoids stale financial balances and needs no invalidation mechanism.

## Reproducible experiment

Synthetic data only: 1,000 shares per holding; four sales and one deposit per holding, with orphan sales in the unmatched case. The largest ledger has 5,000 entries, within the local store's existing ledger limit. Fixture construction, hashing and output serialization occur outside timed regions. The full result is retained through a volatile benchmark sink.

Three separate JVM forks per version, ten global large-case warmups plus ten warmups and fifteen timed calls per case per fork. Fixed case order, 512 MiB JVM heap, macOS/aarch64, OpenJDK 21.0.11+10-LTS. Raw timing, allocation, environment and input/output/source fingerprints are in [evidence](evidence/2026-11-15/share-balances-benchmark.json). The harness and fixtures were identical before and after; all input/output fingerprints agree across all six runs.

Table values are medians of the three fork medians. Allocations are current-thread allocated bytes from ThreadMXBean, not retained heap, peak RSS or native allocations.

| Case | Holdings / entries | Before ms | After ms | Speed ratio | Before bytes | After bytes |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Small | 20 / 100 | 0.058667 | 0.060500 | 0.97× | 169,744 | 183,320 |
| Medium | 200 / 1,000 | 4.479458 | 1.303625 | 3.44× | 1,744,064 | 1,831,048 |
| Large | 1,000 / 5,000 | 106.148292 | 3.660167 | 29.00× | 8,729,664 | 8,898,888 |
| Mostly unmatched | 20 / 5,000 | 2.355417 | 0.278708 | 8.45× | 174,224 | 177,720 |

Small-case differences are too small for a reliable speed claim. Allocations increased approximately 8%, 5%, 1.94% and 2.01%, respectively. These HotSpot measurements do not predict Android ART or Kotlin/Native timings. OS load/thermals and GC were not controlled; this is a local microbenchmark, not a statistical mobile performance study.

Reproduce with the same harness on the baseline implementation and changed implementation:

```sh
for run in 1 2 3; do
  ./gradlew :core:benchmarkShareBalances -PbenchmarkLabel=baseline \
    -PbenchmarkOutput="/tmp/inzira-ledger-before-$run.json" --console=plain
done
# Repeat with label=indexed and output=/tmp/inzira-ledger-after-$run.json.
```

Baseline characterization tests were run before editing production code. Six final tests cover exact reference equivalence on mixed seeded histories, metadata/input preservation, malformed values, changing the same input objects between calls, exactly one ledger traversal, and no traversal for empty holdings. Existing ledger and financial suites cover the surrounding calculations. Traversal tests enforce algorithmic cost without machine-dependent timing assertions.

## Other inspected areas and decisions

| Area | Evidence / decision | Follow-up measurement |
| --- | --- | --- |
| Startup / authentication | Existing staged unlock instrumentation; no new cache or authentication bypass. | Capture cold/warm release-device unlock traces, with protected-store and network stages distinguished. |
| Dashboard / requests | Existing refresh/state coordination and guest/report share balance callers inspected. No new request batching or quote cache; failed refresh behavior unchanged. | Measure authenticated end-to-end sync and count requests on representative accounts before changing scheduling. |
| Scrolling / collections | Compose lazy lists already use keys in investment history. No frame capture demonstrates recomposition/jank. | Release-device frame capture with large synthetic portfolios. |
| PDF imports / native resources | Existing byte/page/OCR/text bounds and native cleanup remain. No real user documents used in the experiment. | Time acquisition, extraction, OCR, parsing and persistence independently; capture peak native and managed memory. |
| Persistence | Local guest storage is protected JSON/files, not a client SQL database. No SQL query optimization is justified by this client review. | Time whole-file read/write at supported size limits before redesigning storage. |
| Images / cache | `DocumentThumbnail` has a session cache bounded to eight entries / 4 MiB, revision invalidation, mutex coordination and native cleanup. No duplicate cache introduced. | Heap/native allocation capture while repeatedly opening/closing document previews. |
| Coroutines / retention | Changed balance function is synchronous/pure with no jobs or long-lived references; scheduling and existing lifecycle ownership remain unchanged. Static inspection cannot demonstrate absence of leaks. | Retention capture after repeated account changes/import cancellation; instrument caller-thread CPU for report preparation. |

Only the independently measured ledger bottleneck was optimized. No speculative micro-optimizations, financial-data cache or main-thread scheduling claims are included.

## Verification

Commands use the installed JDK and Android SDK; staged source was exported to an isolated directory so unrelated working-tree changes cannot enter the tested commit. No production credentials or real financial documents were used.


Environment prefixes used for Gradle:

```sh
export JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home
export ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk
# perf_export is the isolated export of git write-tree, not the dirty working tree.
```

Executed checks:

```sh
# Before production edit: baseline behavior characterization and existing ledger tests.
./gradlew :core:jvmTest --tests '*ShareBalancesCharacterizationTest' \
  --tests '*InvestmentLedgerTest' :core:benchmarkShareBalances \
  -PbenchmarkLabel=baseline -PbenchmarkOutput=/tmp/inzira-ledger-before-1.json --console=plain
# After edit: focused ledger and financial regressions (25 tests).
./gradlew :core:jvmTest --tests '*ShareBalancesCharacterizationTest' \
  --tests '*InvestmentLedgerTest' --tests '*FinancialPrecisionAuditTest' \
  --tests '*FinancialReferenceTest' :core:benchmarkShareBalances \
  -PbenchmarkLabel=indexed -PbenchmarkOutput=/tmp/inzira-ledger-after-1.json --console=plain
```

Both passed. The initial experiment outputs above were replaced by the standalone three-fork cohorts described earlier after the harness was finalized. Only the final identical-harness cohorts are used in the results table.

```sh
./gradlew --project-dir "$perf_export" \
  :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest \
  :core:allMetadataJar :app:shared:allMetadataJar \
  :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest \
  :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 \
  :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Passed on the sequential repeat: 139 core JVM + 139 core Android-host + 514 shared Android-host tests (792 total, no failures/errors/skips); common metadata, Android app/test APK assembly, Android release lint, and both configured iOS main compilations passed. Release lint reported 100 warnings and no errors; it was not warning-free.

```sh
xcrun simctl boot 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4
xcrun simctl bootstatus 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 -b
/Users/muhirwagabooreste/.local/bin/android emulator start Sequo_References_API_36_1 --headless
ANDROID_SERIAL=emulator-5554 ./gradlew --project-dir "$perf_export" \
  :core:iosSimulatorArm64Test --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
  :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
  :app:shared:connectedAndroidDeviceTest -Pandroid.injected.device.serial=emulator-5554 --console=plain
```

Passed: 138 core + 540 shared iOS simulator tests; 508 Android emulator tests, no failures/errors/skips. Core native tests include all six balance characterization/traversal tests. These are functional regressions, not phone performance benchmarks. iOS device target compilation is separate from simulator execution; no physical iPhone performance test was run.

```sh
# From the isolated export:
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
# From the working repository:
git diff --cached --check
```

Passed: 401 Kotlin source/test files satisfy package/platform/module checks; design resources consistent; 19 Python and 84 JavaScript tests passed; staged diff has no whitespace errors. No additional detekt/ktlint configuration or Compose performance diagnostics were added. Existing compiler deprecation/redundant-nullability warnings remain outside this change.

The first combined build failed while a second Gradle invocation was compiling the same isolated export: Kotlin/Native's generated `signatures.knt` disappeared during output snapshotting. This was a verification scheduling mistake, not a source correction. Native/runtime verification subsequently passed, and the identical build command was repeated sequentially. No unverified production change is included.
