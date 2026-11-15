# Substitutability and LSP review

Reviewed against `bfb31a9` and the working tree on 15 November 2026. Scope: KMP mobile code, its platform implementations and existing callers. No backend, website, persistence schema, financial formula, parser asset or dependency changes. Earlier unfinished implementation extractions are excluded from this commit.

## Findings and decisions

| Contract / implementations | Evidence and decision |
|---|---|
| `GuestPortfolioRepository` | Four operations had inherited `error(...)` bodies: ledger save/delete and backup export/restore. This allowed an implementation to compile while omitting operations the guest application expects. The single production common implementation supplies all four, and both native factories use it. **Remove the throwing defaults and require implementations at compile time.** Document failure versus successful persistence, complete production wiring and raw archive payload semantics. No capability split is justified when all current production repositories provide these operations. |
| Android/iOS file selection | iOS rejects an empty selected PDF/backup, while Android returned an empty byte array. **Align Android with iOS** by rejecting empty input in the bounded reader. Extract the existing stream loop into an internal Android function so the actual implementation can be tested without native picker UI. Valid bytes, 10 MB/48 MB limits, callback timing, dispatcher and stream ownership remain unchanged. |
| `PdfPicker` and `ReceivedPdfActions` | Incoming PDFs deliberately replace selection, but must preserve bytes, filename and callback behavior on both ordinary/private paths. Keep both overrides: delegated default methods can otherwise call the original host's picker. **Add shared selection-contract assertions**, exercising default private forwarding and the real incoming adapter. Clarify read errors, successful callbacks and cancellation. |
| `NativeActions` backup methods | The aggregate's default backup operations throw; both Android and iOS hosts explicitly implement them. These are optional host capabilities, unlike the full guest repository contract. Clarify that optional default behavior in KDoc. Splitting the aggregate solely to make every test host implement unsupported native UI would add churn without changing production substitutability. This does not certify arbitrary incomplete hosts as backup-capable. |
| `GuestSnapshotStorage` / `GuestDocumentStorage` | Android and iOS protect bytes using different OS mechanisms but distinguish missing from unreadable data. Both native document stores constrain identifiers and use snapshot storage with a PDF-sized capacity. No demonstrated nullability violation was found in the inspected implementations. Their error classes, encryption machinery and threading need not be identical to satisfy the missing-versus-failure contract. Existing native storage tests are retained. |
| `PdfDocumentPages` | Both renderers expose stable page counts, render a bounded page/region and release native resources. Closed/invalid-page errors differ by platform; the interface does not promise one exception subclass. Do not add or claim stronger cross-platform error guarantees without a separate compatibility change. |
| `InvestmentDocumentParser` / `EngineDocumentParser` | The adapter dispatches only the `pdf` command and forwards progress/cancellation while leaving destruction with its host. Existing cancellation, cleanup, native engine and four-document-kind tests remain. No new parser inheritance is necessary. |
| `GuestUnavailableEngine` | This is a sample-mode unavailable sentinel, not a ready parser/calculator. Committed and working composition roots select it only for non-personal guest mode; import flows are gated by that mode. It should not be advertised as interchangeable with an available engine. Replacing the sentinel with explicit optional engine availability is a remaining composition concern; this slice does not alter sample-mode lifecycle, feature gates or resource use. |
| `SecureSignIn` | Availability and authentication cancellation are explicit parts of its API. Native availability/error differences are not evidence that every valid call must succeed without user authentication. Do not weaken key-loss/corruption failures or equate a missing record with successful authentication. No implementation changes made. |
| Framework subclasses and financial values | Prior composition changes already removed the medium-widget/small-widget and singleton/mailbox implementation-reuse inheritance. Retain required framework lifecycle inheritance and separate bond/share rules. No new base class or interface was manufactured for this review. |

The production audit found throwing-default capability holes rather than a proven existing financial calculation corruption caused by substitution. Test-only `error("Not used")` implementations are not presented as production defects. No claim is made that all optional native capabilities or sample-mode engines are uniformly available.

## Implemented change and compatibility

The repository's four member signatures and result types remain unchanged; only their fallback implementations are removed. Existing repository overrides, validation, threading, cancellation, serialization, storage and API behavior are untouched. Newly incomplete implementations now fail compilation rather than reaching a generic runtime error. This is a source contract strengthening for implementers; binary compatibility with previously compiled external implementations is not claimed. This app does not publish the interface as an independently versioned SDK.

The repository KDoc states that production factories supply snapshot and document storage. The concrete class still permits document-less construction used by financial-only fixtures; those objects are not evidence of full PDF capability. This change does not add a silent in-memory document fallback or weaken document-storage failures. All new full-capability tests explicitly supply both stores, as the production factories do.

The common native aggregate and picker interface changes are contract documentation only. Android now reports `The selected PDF is empty.` at the same read boundary as iOS, rather than returning an empty selection for later PDF/archive validation to reject. The original bounded stream loop is otherwise unchanged apart from explicit current-coroutine cancellation access inside the extracted function. The caller still owns the stream and closes it with `use` on every path. A picker reading failure may throw; it cannot be called successful selection of an empty file. A successful selection invokes the selection callback once, and cancellation does not signal selection. Neither a filename nor direct file content is required on cancellation. Ownership/copying of selected bytes is not redefined; import workflows keep their existing retention/zeroing rules.

## Contract tests

Six new common tests:

1. Required ledger operations persist through `GuestPortfolioRepository`, remain available after owner reconstruction, and delete durably.
2. Required archive operations restore a real synthetic imported PDF and its holding, accessible through a reconstructed destination repository.
3. Invalid ledger/delete/archive operations and unreadable storage fail while retaining the prior saved bytes.
4. The same selection assertions run against a basic picker with default private forwarding and `ReceivedPdfActions`; both ordinary/private paths retain bytes/filename and invoke one callback. The original adapter host throws if either picker is reopened.
5. Cancellation returns null without selection callbacks or an invented filename, including the default private path.
6. The received adapter preserves the host's unrelated interaction flag, save result and sharing behavior.

Four additional Android host tests exercise the production bounded reader: exact-limit valid bytes and caller-owned stream lifetime; empty/oversized errors with unchanged limit message; propagated I/O failures; and cancellation stopping before another read. The iOS reader already checks `total > 0` and is unchanged.

There is one production repository implementation shared by both platform factories, not two different native repository algorithms. The shared assertions compare meaningful picker implementations and interface defaults; they do not claim that a synthetic picker exercises Android/iOS system UI. Existing repository, encrypted-backup, incoming-file, parser and native-storage suites are retained. No existing validation, test assertion or monetary tolerance is relaxed.

## Verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`; Android SDK: `/Users/muhirwagabooreste/Library/Android/sdk`.

Final focused command:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*GuestRepositoryContractTest' --tests '*PdfPickerContractTest' --tests '*PickedFileBytesTest' --tests '*GuestPortfolioRepositoryTest' --tests '*EncryptedBackupTest' --tests '*IncomingPdfTest' --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 15s`; log `/tmp/inzira-lsp-focused-final.log`. The first backup test fixture omitted an ISIN required by the existing import validator; the fixture was corrected and the focused run repeated successfully. Production validation was not weakened.

Full working-tree command:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 1m 35s`; **100 core JVM + 101 core Android host + 459 shared host tests**, zero failures/errors/skips. Android build/instrument APK compilation, release lint and both iOS compilations pass. Log `/tmp/inzira-lsp-working-build-final.log`.

Nine source/test/report files were staged, taking only the contract hunk from the already-dirty repository file and preserving its committed implementation. The staged tree was exported using `git write-tree` / `git archive` to `/tmp/inzira-lsp-index.zCd5Xs`. The same full command was repeated with `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk` and `--project-dir "$audit_export"`.

Exit 0, `BUILD SUCCESSFUL in 1m 47s`; **100 core JVM + 101 core Android host + 448 shared host tests**, zero failures/errors/skips. All Android build/lint/instrumentation compilation and both iOS compilation tasks pass. Log `/tmp/inzira-lsp-index-build.log`. The working tree has 11 additional tests belonging to excluded earlier refactoring.

Native shared-suite command against the same export:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$audit_export" :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 35s`; **467 shared native tests**, zero failures/errors/skips. Includes the six new common contract tests and existing native storage/PDF integrations. The four Android-only reader tests run in the host suite, not this native suite. Log `/tmp/inzira-lsp-index-native.log`.

Existing static/engine checks in both working and isolated trees:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Results: package ownership/module DAG and design-color audit pass, **16 Python tests and 84 Node tests** pass, final whitespace checks pass. Logs `/tmp/inzira-lsp-working-{packages,colors,python,node}-final.log` and `/tmp/inzira-lsp-index-{packages,colors,python,node}.log`. All staged Kotlin files match the tested export byte-for-byte; the repository implementation split remains unstaged.

No configured Detekt/ktlint task exists; baseline compiler/lint warnings remain. No native picker UI interaction, connected Android instrumentation execution, physical-device encryption/lock verification, live account write or backend deployment occurred. The Android bounded reader is directly executed by Android host tests; that is distinct from system-picker UI automation.

No core source changed. The separate full-core-native `ShareCompanyRowsTest.remainingSharesAndTheirCostAreUsedAfterSales` floating-point equality baseline failure recorded by prior audits remains outside this slice. Full core JVM/Android host and shared native suites are not an all-core-native/all-task green claim.

Commit only this verified slice as `refactor(kmp): enforce substitutable contracts`; keep earlier unfinished work uncommitted.
