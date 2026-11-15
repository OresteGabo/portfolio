# Single Responsibility Principle review

Reviewed against `37cdd3c` and the working tree on 15 November 2026. Scope: mobile KMP application. No backend, website, schema, dependency or build configuration changes. The review treats responsibility as a cohesive reason to change, not a line-count limit.

## Inspection and prioritization

| Component | Independent change reasons observed | Decision |
|---|---|---|
| `PortfolioModel` | Session/lifecycle, account orchestration, guest mode and feature coordination; several implementations remain embedded in the committed model. | Still the largest orchestrator: committed file 1,246 lines, existing working tree 1,062. Earlier unfinished feature extractions already modify this file. Do not mix those unverified changes into this independently reviewable slice. Prior account workflow extractions remain intact. |
| `PortfolioImports` | Picker/parser orchestration, document retry/upload, journal/UI state and confirmation/persistence requests. | Keep existing orchestration and public calls here; extract history schema and retry bookkeeping from its journal collaborator in this step. The committed file has 434 lines; the older working-tree extraction reduces it to 411. Upload responsibilities require their own resumable-failure characterization. |
| Guest repository | Atomic snapshot/document writes, recovery, investment validation and backup operations. | The committed implementation shares a 469-line file with its contracts; an earlier uncommitted split is already present. Some validation remains a candidate for separate policy components, but atomic transaction/recovery ownership must stay together. Do not turn every data operation into an independent repository. |
| `GuestPdfImport` / `PurchaseReviewState` | Guest import coordinates selection, validation, duplicate lookup and review ownership; purchase review computes matching/readiness for two presentations. | These are application workflow/projection responsibilities, not proof of an SRP violation merely because they invoke several collaborators. Existing parser, repository and financial matching boundaries remain. Further duplicate-evidence policy extraction can be evaluated with its own tests. |
| `PortfolioReport` | Localized formatting, financial projection and PDF section composition in one 192-line file. | A future reporting slice should separate formatting/projection from rendering. Do not move entire sections into arbitrary helpers just to reduce length. Existing report/financial tests remain in verification. |
| `ImportJournal` | Observable account-scoped state, retry bookkeeping, and persisted JSON schema/restart recovery are implemented together. | Corrected here. These policies change independently even though the original file has only 113 lines. Existing import callers depend on the journal API; they need not know storage schema or retry arithmetic. |

The working tree contains earlier unfinished extractions, so both committed and working-tree versions were inspected. This commit contains only the journal responsibility split and its tests/report; it does not claim to complete every remaining SRP candidate in the application. Prioritized broader findings remain in [the codebase audit](KMP_CODEBASE_AUDIT.md).

## Corrected responsibilities

### Runtime/account owner: `ImportJournal.kt`

Before, the file combined receipt values, JSON encoding/validation, restart normalization, state publication, account attachment, random ID/time acquisition, retry-attempt/event accounting, persistence and document helper declarations.

After, `ImportJournal` keeps the cohesive application responsibility: own the currently attached account's observable history and coordinate its updates with settings persistence. Its API remains `attach`, `detach`, `begin`, `update`, `find`, `entries`, `persistenceFailed`. Native digest and display-name helpers outside the class retain their declarations and behavior. The file decreases from **113 to 50 lines**; the existing class decreases from **41 to 36 lines**. This modest class reduction is intentional: schema and policy ownership matter more than its size.

### Receipt lifecycle policy: `ImportReceipt.kt`

The existing `ImportState`, `ImportOutcome`, `ImportEvent` and `ImportReceipt` declarations keep the same package, fields, defaults, equality and copying behavior. New pure `updateImportReceipt(previous, changed, at)` owns retry bookkeeping: failed/cancelled/interrupted → reading/saving increments the previous attempt count and retains the last eight previous outcomes. Other transitions preserve the callback's existing values. Every update stamps the supplied time.

The journal still executes the caller's change callback, reads the real clock afterward, publishes the resulting list and persists it in the same order. The extracted policy has no Compose, platform, storage, clock acquisition or coroutine dependency. It does **not** introduce a stricter state machine: history records workflow outcomes, while financial authorization remains at the existing save boundaries.

### History format and recovery: `ImportHistoryCodec.kt`

`encodeImportReceipt`, `encodeImportHistory` and the existing `decodeImportHistory` own the privacy-limited storage representation, validation, sanitization, bounds, duplicate-ID selection and restart-state recovery. The decoder body is moved unchanged. Encoding preserves key names/order, defaults, null-outcome representation and the explicit field allowlist. The existing member `ImportReceipt.json()` remains as a compatibility delegate to the codec.

There is no new repository, use-case wrapper, storage interface, inheritance hierarchy or DI framework. Two cohesive files are added; no new production class is needed beyond relocating the existing value declarations. The data record's compatibility method delegates the representation details rather than duplicating schema decisions.

## Compatibility and regression evidence

- Existing callers were inspected in the committed/working `PortfolioImports`, journal tests and duplicate-import tests. No caller changes are required.
- Same account-scoped settings keys and write/read behavior, including storage failure indicators, attach/detach semantics, 1,000-row limit and in-memory updates when storage fails.
- Same clock/callback evaluation order, ID generation, receipt IDs, error handling, attempt counts and eight-event retention. No ownership or concurrency policy changes.
- Same decoder recovery: reading/review/saving becomes interrupted after restart; invalid rows are skipped, unknown metadata is normalized, duplicate valid IDs keep their first decoded row, and input limits are retained.
- Same PDF/parser assets, financial calculations, request payloads and UI actions. No existing assertion or financial tolerance is weakened.
- Internal top-level codec declarations move JVM file facades; all repository callers are Kotlin and rebuild together. No Java/Swift source consumer calls these internal functions. Binary compatibility with separately compiled external artifacts is not claimed.

Seven new tests isolate the responsibilities: all **64 state pairs**; bounded retry history and unchanged inputs; exact receipt JSON and round trip; all states × four document kinds; unsafe/unknown metadata normalization; malformed/duplicate records; bounded stored receipt/event history. Existing journal, duplicate and conversation tests characterize the original workflow before extraction and run again afterward.

## Verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`; Android SDK: `/Users/muhirwagabooreste/Library/Android/sdk`.

Before extraction:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*ImportJournalTest' --tests '*ImportDuplicatesTest' --tests '*ImportConversationModelTest' --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 2s`; log `/tmp/inzira-srp-characterization.log`. After extraction, the same command with `--tests '*ImportReceiptPolicyTest' --tests '*ImportHistoryCodecTest'` also passes, exit 0, `BUILD SUCCESSFUL in 13s`; log `/tmp/inzira-srp-focused.log`.

Full working-tree command:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 39s`; **100 core JVM + 101 core Android host + 440 shared host tests**, zero failures/errors/skips. Android build, instrument APK compilation, release lint and both iOS compilations pass. Log `/tmp/inzira-srp-working-build.log`.

The six staged source/test/report files were exported with `git write-tree` / `git archive` to `/tmp/inzira-srp-index.Ooad9E`. The full command above was repeated against this exact tree with `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk` and `--project-dir "$audit_export"`, without earlier unfinished refactors.

Exit 0, `BUILD SUCCESSFUL in 1m 7s`; **100 core JVM + 101 core Android host + 429 shared host tests**, zero failures/errors/skips. All Android build/lint and iOS compilation tasks pass. Log `/tmp/inzira-srp-index-build.log`. The working tree has 11 additional tests from the excluded earlier refactor.

Native shared-suite command against the same export:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$audit_export" :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 56s`; **452 shared native tests**, zero failures/errors/skips. Includes the seven new policy/codec tests, common import workflows and native PDF/receiver integrations. Log `/tmp/inzira-srp-index-native.log`.

Both working and isolated trees run the existing static/engine checks:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Results: package ownership/module DAG and color audit pass, **16 Python tests and 84 Node tests** pass, and no final diff whitespace errors. Logs `/tmp/inzira-srp-{working,index}-{packages,colors,python,node}.log`. The initial whitespace gate found a trailing blank line in the new codec; it was removed before the isolated export. Source comparison confirms unchanged decoder/encoder expressions and exact staged-source/export agreement.

No configured Detekt/ktlint task exists; release lint/compiler baseline warnings remain. No connected Android instrumentation execution, physical-device UI run, live-account write or backend deployment occurred in this slice. Instrument APK compilation is explicitly distinguished from test execution.

No core source changed. The previously documented separate full-core-native `ShareCompanyRowsTest.remainingSharesAndTheirCostAreUsedAfterSales` floating-point equality failure remains outside this scope; full core JVM/Android host and full shared native results do not imply an all-core-native/all-task green run.

Commit only this verified slice as `refactor(kmp): apply single responsibility principle`; preserve earlier unrelated changes.
