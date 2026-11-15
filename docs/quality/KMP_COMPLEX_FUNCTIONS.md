# Complex-function review

Reviewed against `d297d86` and the working tree on 15 November 2026. Scope: KMP mobile code. This slice simplifies a high-risk transfer workflow after characterization; it does not rewrite every long function. Backend, website, parser assets, financial formulas, schemas, dependencies and build configuration are unchanged. Older unfinished work stays outside this commit.

## Risk-based audit

| Function / concern | Complexity and risk | Decision |
|---|---|---|
| `GuestMigration.transfer` | Batch authorization/consent, mutable revision checks, ordering, private PDF lifetime, dependent-record gating, acknowledgement validation, per-item recovery and batch cleanup are nested together. A short physical file span hides several simultaneous control-flow contexts. | **Refactor first**, with ordering/failure/cleanup characterization before production changes. |
| `LocalGuestPortfolioRepository.restoreBackup` | Decode/validate, stage original files, atomically replace metadata, recover failed staging and clean prior files. Transaction/failure boundaries are more important than shortening code. Its earlier implementation/file split is already unfinished in the workspace. | Retain here; a future refactor needs dedicated fault-injection characterization of each commit/rollback boundary. Do not mechanically flatten its `try/finally` structure. |
| `PortfolioModel.unlock` / action handling | Session generation, protected credentials, server identity, fallback authentication, lifecycle cancellation and UI state interact. | Retain for a separate state-transition slice. The preceding dependency-inversion change provides test seams; mixing its larger unfinished model extractions into this commit would obscure behavior review. |
| `importedPurchaseInput` | Ordered warning/date/figure/tax validation and normalization produce user-visible first-failure messages and purchase payloads. | Do not replace explicit bond/share rules with generic field validators solely to shorten the function. Future extraction must freeze validation order and defaults first. |
| `decodeImportHistory` | Nested per-file/per-record/per-event recovery preserves valid rows while dropping invalid ones, with bounds and deduplication ordering. | Already isolated from persistence. Prefer a later pure-decoder readability slice with bounds/order characterization over changing recovery semantics alongside network orchestration. |
| Long Compose/report builders | Declarative layout and record presentation can be long without comparable branching or independent side effects. | Length alone is not a reason to split them into arbitrary one-line helpers. Reporting projection/formatting remains a separate architectural candidate. |

No configured cognitive/cyclomatic complexity analyzer is available. This review does not invent a numerical Sonar/Detekt score; it describes the control-flow contexts reduced and the retained decisions.

## Major refactor: migration transfer

Before, `transfer()` contained a loop inside a batch `try`, a per-row `try`, document/financial branches, an inner PDF `try/finally`, receipt-parent checks, selected-PDF dependency checks, acknowledgement publication and recoverable-error classification. Readers had to track private byte ownership and `continue` behavior while reasoning about consent and batch cleanup.

After, the coordinator is **27 lines rather than 42 condensed lines**, and reads as:

1. Gate concurrent/unauthorized/unconfirmed work and freeze selection/date inputs.
2. Validate selected dates/types/dependencies before consuming consent.
3. Set batch state, verify revision, order the rows and process each row.
4. Preserve cancellation/fatal error behavior and clear running/progress state.

Five cohesive private operations keep context nearby in the same state owner:

| Operation | Cognitive benefit |
|---|---|
| `validateTransferSelection` | Groups document/date and parent-selection preconditions without I/O or state mutation; keeps the original error order. |
| `transferSelectedRow` | Owns one row's authorization, progress and recoverable/fatal-error boundary. Batch orchestration no longer contains a per-item catch or document/financial dispatch. |
| `uploadOriginal` | Contains the entire borrowed-byte lifetime, authorization during chunk progress and unconditional zeroing in one `try/finally`. Callers cannot accidentally separate upload from cleanup. |
| `transferRecordOrMarkForReview` | Uses early returns for unresolved parents and selected PDFs, making deferral explicit rather than burying `continue` inside a nested branch. Remote mapping/send stays after these guards. |
| `recordAcknowledgment` | Keeps authorization, existing pure acknowledgement validation and result publication together for both remote paths. This is a security/state boundary rather than a helper extracted merely to wrap an assignment. |

The whole file grows from **180 to 239 lines** because control flow is expanded into readable statements and named operations. The improvement is reduced per-function nesting and explicit ownership, not deleting business decisions or optimizing a line-count metric. No new service, interface, sealed hierarchy or parameter object was needed. The four private row-processing parameters preserve frozen selection/date context explicitly; there are no new boolean behavior flags or ambiguous sentinel return values.

## Behavior preservation

- Same consent/concurrency checks, account authorization points, validation messages and precondition order. Selection and document dates are copied at the same stage.
- Revision verification still precedes row ordering and transfer. Ordering remains documents → investments → receipts → cash, with stable order within groups.
- Borrowed PDF bytes are acquired at the same point and zeroed on success, rejection, malformed acknowledgement, fatal error and cancellation. Original stored PDFs are not replaced or erased.
- Unresolved parent/document references still mark a row for review without remote writes. Only selected, successfully acknowledged PDF targets are attached.
- HTTP 400/409/413 remain per-item review outcomes; other exceptions stop the batch. Cancellation propagates rather than becoming a generic failure. Running/progress cleanup and consent consumption remain unchanged.
- Pure `outcome` validation and `migrationRecord` field mapping remain unchanged; no financial arithmetic, PDF parsing or API payload rules move or change.
- All code before the transfer region and from `clear()` through the end of the file is byte-identical. Public/internal caller-facing contracts and DI are unchanged.

## Characterization before refactoring

Five new tests were added and successfully executed **against the unchanged production implementation** before extraction:

1. Full ordering across a selected PDF, dependent/independent bonds, receipt and cash; successful target linking and byte cleanup.
2. Each recoverable status (400/409/413) on PDF upload defers dependent records while independent transfers continue.
3. Unauthorized/server failures and malformed acknowledgements stop the batch without publishing an unverified result; borrowed bytes are erased.
4. Each recoverable status on a financial item preserves the earlier successful PDF result, defers its receipt and continues independent work.
5. Cancellation propagates, erases the upload buffer and prevents subsequent transfers.

The existing migration suite additionally exercises consent, missing dates, selected-only disclosure, retries/lost acknowledgements, prior transfers, account switching, concurrent calls, review closure, changed revisions and the real HTTP adapter's chunk/field contract. The capability-boundary tests remain. No existing assertion, validation rule or financial tolerance is weakened.

## Verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`; Android SDK: `/Users/muhirwagabooreste/Library/Android/sdk`.

Before changing production code, all five characterization tests and the existing migration/capability tests ran with:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*MigrationTransferCharacterizationTest' --tests '*GuestMigrationTest' --tests '*GuestCapabilityBoundaryTest' --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 3s`; log `/tmp/inzira-clean-functions-characterize-final.log`. The initial four-test characterization run also passed before the financial-item case was added (`/tmp/inzira-clean-functions-characterize.log`). The exact command was repeated after extraction: exit 0, `BUILD SUCCESSFUL in 12s`; log `/tmp/inzira-clean-functions-focused.log`.

Full working-tree command:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 1m 1s`; **102 core JVM + 103 core Android host + 471 shared host tests**, zero failures/errors/skips. Android build/instrument APK compilation, release lint and both iOS compilations pass. Log `/tmp/inzira-clean-functions-working-build.log`.

The three-file source/test/report tree was staged and exported using `git write-tree` / `git archive` to `/tmp/inzira-clean-functions-index.hEmqW8`, excluding older unfinished work. The full command above was repeated with `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk` and `--project-dir "$audit_export"`.

Exit 0, `BUILD SUCCESSFUL in 1m 28s`; **102 core JVM + 103 core Android host + 460 shared host tests**, zero failures/errors/skips. Android build/lint/instrumentation compilation and both iOS compilations pass. Log `/tmp/inzira-clean-functions-index-build.log`. The 11 additional working-tree tests belong to excluded older refactoring.

Shared native suite against the same export:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$audit_export" :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 40s`; **479 shared native tests**, zero failures/errors/skips. Includes the five new characterization tests, existing migration/capability suites and native PDF/storage integrations. Log `/tmp/inzira-clean-functions-index-native.log`.

Both working and isolated trees run the existing static/engine checks:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Both pass package ownership/module DAG and color checks, **16 Python tests and 84 Node tests**, and final diff whitespace checks. Logs `/tmp/inzira-clean-functions-{working,index}-{packages,colors,python,node}.log`. Source comparison confirms byte-identical code outside the transfer region, including pure mapping and acknowledgement validation; staged Kotlin files match the verified export byte-for-byte.

No configured Detekt/ktlint task exists. Baseline compiler/lint warnings remain; no numerical complexity reduction is claimed from an unexecuted tool. No connected Android instrumentation execution, physical-device UI run, live account migration/write, backend deployment or signed distribution occurred. Instrument APK compilation is not test execution.

No core source changed. The separately documented full-core-native share-cost floating-point equality baseline failure remains outside this slice; full core JVM/Android host and full shared native suites are not an all-core-native/all-task green claim.

Commit only this verified slice as `refactor(kmp): simplify complex functions`; preserve older unfinished work.
