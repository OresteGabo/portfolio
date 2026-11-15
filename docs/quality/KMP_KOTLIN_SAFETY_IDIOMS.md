# Kotlin safety and idioms

Reviewed against `f815dcd` on 15 November 2026. Scope: handwritten KMP null handling and collection/allocation patterns, with focused changes in shared PDF rendering, financial-tool presentation and PDF envelope validation. Backend, website, model formats and existing unfinished work are excluded.

## Changes

- **`PdfViewerScreen`**: snapshot the nullable delegated document into a local `val pages` before branching. The existing loading branch proves non-nullness for rendering; smart casts replace `document!!`. Preserve failure/loading/privacy precedence and native handle ownership, cancellation and disposal.
- **`InvestmentToolsScreen`**: snapshot the asynchronously produced nullable result into `val toolResults` for one list-content build. The loading branch narrows that value, removing both assertions. Keep null as the meaningful not-yet-calculated state; calculation inputs, background dispatcher and branch labels stay unchanged.
- **`PdfPageList`**: use the lazy-list count overload instead of constructing `(0 until pageCount).toList()` during composition. Indices, stable integer keys, page order and lazy rendering remain identical; no new page cache is introduced.
- **`validatePdfEnvelope`**: compile the immutable header regex once, match trailing padding with `when` rather than allocating a list for each examined byte, and decode bounded byte-array ranges directly instead of copying a prefix/list and suffix array. Preserve the exact six accepted padding bytes, size-before-trim policy, first-16-byte header window, last-1024-byte suffix window, UTF-8 replacement decoding, supported versions and public error text. The local cursor remains `var` because scanning changes it.

These changes do not rename public symbols, change nullable result contracts, introduce model variants, or alter serialized fields. No financial arithmetic, PDF parsing engine, networking, persistence, native ABI or platform permission changes.

## Intentional Kotlin patterns retained

- `JsonRecord` is an explicit mutable builder with controlled mutation; published copies are detached and read-only. Its serialization-based deep copy is not replaced with a shallow `toMap`: `JsonRecordTest.readOnlyCopyOwnsNestedInputAndKeepsTheExactWireRepresentation` demonstrates externally mutable nested JSON input. Avoiding this copy would break ownership.
- PDF loading and tool calculation use Compose state and coroutine lifecycle mutation intentionally. A `val` snapshot is a rendering aid, not a replacement for their state holders.
- Existing `SessionState` is a genuine sealed operating-mode model. `GuestPath` is a closed enum with persisted values. Neither requires a replacement hierarchy or value-class migration.
- Core date/cursor loops and bounded stream readers need mutable counters. Mechanical `var` removal would obscure the algorithms.
- Platform-specific graphics handles, Android system-service APIs and vendored icon caches have different runtime contracts. Their assertions/casts were inspected separately; this slice does not claim they were eliminated. In particular, removing the icon builders' generated cache assertions or inventing generic platform wrappers would add churn.
- No new value classes, sealed wrappers or casts are needed for the changed paths. Nullable document/results still represent loading; missing data is not replaced by empty financial results or a zero-page document.
- Existing unrelated `InvestmentApp`, import coordination and extracted settings work contains additional assertions and remains outside this isolated change. No repository-wide safety claim is made.

## Tests

Four PDF envelope tests pass against unchanged production code before optimization: supported versions/header separators, every accepted trailing padding byte, invalid/missing/truncated envelopes and unsupported trailing controls, and the 10 MB limit applied before padding trim. They assert the exact controlled failure message. Existing import/picker/cancellation/review and financial-tool rule tests are retained. In particular, `PdfPickerContractTest.cancelledSelectionReturnsNullWithoutReportingASelectedFile` covers null selection and the pipeline tests cover policy rejection and cancellation without publishing bytes.

The rendering edits use compiler-checked local narrowing, not a new presentation state machine. No physical UI rendering test is claimed. The resource loader, disposal effect and native PDF implementation are unchanged.

## Verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`; Android SDK: `/Users/muhirwagabooreste/Library/Android/sdk`.

Before production edits:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*PdfEnvelopeTest' --tests '*SharedRulesTest' --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 4s`; log `/tmp/inzira-idioms-characterize.log`.

Full working-tree verification:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 1m 7s`: **694 host test executions** (107 core JVM, 108 core Android host, 479 shared host), no failures/errors/skips; Android build/lint and both iOS compilations pass. Log `/tmp/inzira-idioms-working-build.log`.

The staged five-file tree was exported with `git write-tree` and `git archive`, excluding older unfinished work. Repeat the command above with `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk` and `--project-dir "$idioms_export"`.

Export `/tmp/inzira-idioms-index.2LpaIP`: exit 0, `BUILD SUCCESSFUL in 2m 15s`; **683 host test executions** (107 core JVM, 108 core Android host, 468 shared host), no failures/errors/skips. Android build/lint and both iOS compilations pass. Log `/tmp/inzira-idioms-index-build.log`. The working tree has eleven additional tests from excluded prior work.

Shared native suite on the isolated export:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$idioms_export" :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 53s`; **487 shared native tests**, no failures/errors/skips. Log `/tmp/inzira-idioms-index-native.log`.

Static/script checks run in working and isolated trees:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Both trees pass package ownership/module DAG and design-color checks, **16 Python and 84 Node tests**. Final whitespace and staged-source/export comparisons are checked before commit. Only this report receives final result updates after the source export is tested.

Baseline compiler/lint warnings remain; no configured Detekt/ktlint task was found. No benchmark, physical-device UI run, connected instrumentation, account mutation or deployment is claimed. No full core-native run is claimed; the previously recorded share-cost exact floating-point equality baseline issue is outside this shared-only change. Existing financial arithmetic and test tolerances remain unchanged.
