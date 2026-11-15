# ViewModel and UI state management review

Reviewed against `0c071e8` on 15 November 2026. Scope: KMP account-feature state delegated by `PortfolioModel`, plus its public reload/logout boundary. Backend, website, UI layout, persistence and unfinished work are excluded.

## Existing architecture

`PortfolioModel` extends lifecycle `ViewModel`, uses `viewModelScope` and Compose observable state, and delegates account ledger, pending-order and statement work to feature holders. There is no `MutableStateFlow`/`StateFlow` architecture to replace. Domain calculations already live in shared rule components; feature readers depend on `PortfolioDataApi`, with authenticated client/session lifecycle owned by the ViewModel.

The committed `PortfolioModel.kt` is **1,247 lines**; the existing unfinished working version is **1,063 lines**. This change does not edit either version or commit the earlier extraction work. The large model still coordinates many features, but replacing it with MVI or a generic reducer would obscure the concrete publication races below and overlap unfinished work.

## Concrete issues corrected

| Issue | Evidence | Correction |
|---|---|---|
| Late ledger response restores cleared account data | Public `PortfolioModel.reloadLedger()` delegates a suspend read; `logout`/`clearPrivateData` clears the holder. The holder previously published the response unconditionally. A gated ViewModel test reproduces rows returning after logout. The screen normally wraps reload in `action`; the public suspend boundary must remain safe without relying on that caller convention. | Each ledger refresh captures client identity and a local generation. New reads/clear invalidate old publication. Session clearing cannot be undone by an earlier response, including when the same client object remains after logout. |
| Loading/error/data fields updated independently | `PortfolioCashLedger` separately published `entries`, `available` and `error`, with no local refresh status. | One read-only observable `CashLedgerUiState` snapshot owns rows, availability, error and `isRefreshing`. Existing `entries`/`available`/`error` getters remain, so no ViewModel/UI caller contract changes. Lists and records stay detached/read-only. |
| Older refresh results overwrite newer intent | Ledger, pending-order and statement-list reads could complete out of order. Older API failures could also invalidate newer state. | Publish only for the current generation and client. Discard stale optional-data failures and stale unauthorized responses; current 401 responses still propagate to the session owner. Latest **started** read owns publication, even if it fails. This is not request coalescing or write retry. |
| Dismissed/replaced statement review reopens | `PortfolioStatements.showDocument` wrote `reviewRows` after awaiting data even if `closeReview`, `showRecord`, another document read or `clear` had changed the review. Three tests fail against the unchanged implementation. | Separate review generation from list generation. Closing/selecting/clearing invalidates pending document-review results; list reloads do not replace the selected review. |
| Cancelled work can return from a non-cooperative provider | A data capability need not itself check coroutine cancellation before returning. A late return should not publish into the UI. | `ensureActive` immediately after read completion and before publication, with cancellation rethrown. Tests use a deliberately non-cancellable provider to exercise this edge. |
| Clearing optional account state is not final while a read is pending | `PortfolioShareOrders.reload` could republish rows through the portfolio callback after `clear`; `PortfolioStatements.load` could restore list rows after clear. | Invalidate pending generations in their existing `clear` methods; preserve protected snapshot projection and existing callback ordering. |

All state mutation remains on the ViewModel/UI dispatcher, matching the existing Compose ownership model. Generation counters are lifecycle tokens, not persisted IDs or cross-thread synchronization primitives. They are private to each holder; no shared event reducer or new DI framework is introduced.

## State and action contracts retained

- **Ledger initial:** empty rows, unavailable, no error, not refreshing. **Successful empty endpoint:** available with an empty list. **Missing entries field:** unavailable with no invented error. These remain distinct.
- Refresh starts by retaining the previous rows/availability/error and setting its refresh flag. Success replaces the snapshot and clears error. A transient failure keeps rows, marks unavailable and exposes the existing retry text. 404/405 clears optional rows with the existing unavailable message.
- Active cancellation and active 401 retain the previous data/error/availability, stop their own refresh flag and propagate. Cancelling an older refresh cannot stop a newer refresh indicator. Stale results/errors after clear/client replacement do not publish.
- No new spinner or error text is wired into the screen. The ViewModel's existing action/busy presentation remains; local refresh status makes ownership explicit without changing intended visuals.
- Save/delete/resolve/create/check HTTP payloads, routes, financial rules, server acknowledgments, completion callbacks and post-write refresh order stay unchanged. Host `action` still prevents duplicate interactive writes and owns session rejection. Read-generation guards do not imply cancellation of a server-side write or retry semantics.
- Pending orders remain authoritative feature data and project the same protected list into `PortfolioSnapshot`; the projection is not an independently edited ledger.
- Review rows are durable until closed/replaced; `showDocument` still retains the prior review while loading or failing. No new loading screen/state-reset policy is introduced for it.
- Error/message banners in `InvestmentApp` are dismissible state, not one-shot navigation events. Their existing dismissal and retry semantics remain; no Channel/SharedFlow is added solely to rename them as events.

## Remaining scope

`PortfolioModel` still has broad orchestration and some UI-writable properties (`error`/`message`, dismissed by the app). A larger immutable application-state API would require coordinated call-site and navigation changes; this slice does not claim to privatize them. Statement reconciliation writes still rely on the host action/session lifecycle; their workflow state and completion sequencing are unchanged. Startup/session/guest/report controllers have their own lifecycle and generation policies and are not replaced here.

The three account holders intentionally retain separate generations: ledger refresh, pending-order refresh, statement list and statement review have different invalidation rules. A generic request manager would add lifecycle flags and callback plumbing without simplifying these small owners.

## Tests

Before production changes, `AccountRecordWorkflowsTest.logoutCannotBeUndoneByAnInFlightLedgerRefresh` fails on late account rows. The three initial `StatementReviewLifecycleTest` cases also fail: close, newer document selection, and local selection/account clear do not block the earlier response. These are reproduced product bugs corrected by the change, not unresolved baseline failures.

New tests cover loading→success→transient failure→retry; unsupported/missing/successfully empty endpoint distinctions; read-only snapshots; active cancellation/401; older success/401 after a newer response; clear/client replacement; cancellation without affecting a newer loading flag; non-cooperative providers; statement review close/selection/out-of-order responses; independent statement list/review state; and protected pending-order projection after clear.

Existing `AccountRecordWorkflowsTest`, `FeatureStateOwnershipTest`, `AccountWorkflowDataBoundaryTest` and `StatementReconciliationTest` preserve ViewModel actions, write completion ordering, protected snapshots, optional endpoint behavior, cancellation and authenticated session handling.

## Verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`; Android SDK: `/Users/muhirwagabooreste/Library/Android/sdk`.

Regression reproduction before production fixes:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*AccountRecordWorkflowsTest.logoutCannotBeUndoneByAnInFlightLedgerRefresh' --console=plain
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*StatementReviewLifecycleTest' --console=plain
```

Both exit 1 as expected: one failing logout/refresh test (6s), then three failing review lifecycle tests (4s). Logs `/tmp/inzira-state-regression-before.log`, `/tmp/inzira-state-review-before.log`. These failures are fixed, not waived.

Final focused verification after guards/state changes:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*CashLedgerStateTest' --tests '*StatementReviewLifecycleTest' --tests '*AccountReadLifecycleTest' --tests '*AccountRecordWorkflowsTest' --tests '*FeatureStateOwnershipTest' --tests '*AccountWorkflowDataBoundaryTest' --tests '*StatementReconciliationTest' --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 5s`; log `/tmp/inzira-state-focused-final.log`. Earlier focused checks also pass (12s). The final isolated run below includes the subsequent statement-read change that checks ownership before constructing a publication snapshot.

Working-tree full verification before final staging:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 1m 24s`; log `/tmp/inzira-state-working-build.log`. The final active-authorization contract test was then added and passed in the focused run above. Existing unrelated working changes are present only in this working-tree check.

The eight-file staged tree was exported with `git write-tree` / `git archive`, excluding prior unfinished work. Exact isolated verification:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$state_export" :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Export `/tmp/inzira-state-index.LAJjgI`: exit 0, `BUILD SUCCESSFUL in 1m 34s`; **709 host test executions** (107 core JVM, 108 core Android host, 494 shared host), no failures/errors/skips. Android app/test compilation, release lint and both iOS compilations pass. Log `/tmp/inzira-state-index-build.log`.

Shared native tests:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$state_export" :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 52s`; **513 shared native tests**, no failures/errors/skips. Log `/tmp/inzira-state-index-native.log`.

Static/script commands run in both working and isolated trees:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Both pass package ownership/module DAG and color consistency, **16 Python and 84 Node tests**. Final whitespace and staged-source/export byte comparisons are checked before commit; only this report receives final verification updates afterward.

Baseline compiler/lint warnings remain; no configured Detekt/ktlint task was found. Android instrumentation is compiled, not executed for this state-only change. No physical-device UI run, performance benchmark, live account write or deployment is claimed. Full core-native execution is not claimed; the previously recorded share-cost exact floating-point equality issue is outside the affected shared state code. Financial calculations, persistence formats, payloads and test tolerances remain unchanged.
