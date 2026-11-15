# KMP decomposition, step 1: account-record workflows

Reviewed 15 November 2026. Starting commit: `2c53a21`. This is a bounded follow-up to [the audit](KMP_CODEBASE_AUDIT.md), focused on one high-priority class, `PortfolioModel`.

## Inspection and scope

The working tree already contained a broad, uncommitted responsibility refactor. This step preserves it and commits only three account-workflow extractions, their public-boundary characterization tests, the necessary model delegation and this report. To avoid shipping a patch that depends on unstaged work, the staged source was exported and independently built/tested.

Ranking after inspecting declarations, dependencies and call sites:

| Priority | Candidate / current size before this step | Responsibility and coupling | Risk / decision |
|---|---|---|---|
| 1 | `PortfolioModel`, 1,118 lines in working tree / 1,302 at HEAD | Session/lifecycle orchestration plus ledger, orders, reconciliation, targets, imports, migration and reports | High. Extract independently testable account workflows first; retain session/action/guest gates. |
| 2 | `InvestmentApp`, 500 working-tree lines | Root routing and native incoming-event precedence; one 463-line composable | High. Requires a navigation transition matrix before a separate state-machine refactor; untouched here. |
| 3 | `PortfolioImports`, 408 working-tree lines | Parser review, duplicate detection, receipts, retry and uploads | High PDF/evidence risk. Already has focused protocol tests; leave import byte/retry contracts unchanged in this step. |
| 4 | `PortfolioDashboard`, 450 working-tree lines | A 338-line composable combines display/filter/action sections and memoized projections | Medium. A feature read model is a future slice; no UI or valuation changes here. |
| 5 | `LocalGuestPortfolioRepository`, 328 working-tree lines | Cohesive durable transaction/recovery owner; backup is the next separable responsibility | High persistence risk. Keep atomic write, deletion and restore ordering unchanged. |
| Excluded from size-driven extraction | `FrenchStrings`, 2,004 lines; vector/generated assets; financial math; native PDF engines | Translation data and cohesive algorithms/adapters are not God Classes merely because of size | Keep domain calculations, PDF policies and native extraction intact. |

Inspected call sites include `CashLedgerScreen`, share purchase/activity/annual-report views, `ShareOrderScreen`, import hosts, `StatementScreens`, `DocumentsHub`, the root app, portfolio loading/history/refresh, report loaders, guest operations, and session/logout cleanup. A repository-wide search found model-owned writes to the extracted state; external callers read it and invoke the existing model methods. No caller had access to its private setters.

Paths below are under `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/` unless stated otherwise.

## Before and after responsibilities

| Owner | Before | After | Size |
|---|---|---|---|
| `PortfolioModel` | Direct ledger HTTP/error state, order HTTP/projection state, statement review/check/resolve/create workflows, plus all session/guest orchestration | Delegates account-record workflows; retains authenticated action guard, guest-vs-account branching, callbacks/messages, session/lifecycle and portfolio synchronization | Working tree **1,118 → 1,051**; independently staged baseline **1,302 → 1,235** |
| `PortfolioCashLedger` | Embedded in model | Account ledger loading/availability/error retention, save/delete and refresh sequence | New **49 lines** |
| `PortfolioShareOrders` | Embedded in model | Pending-order loading, save/cancel and publishing order records into the existing portfolio projection | New **41 lines** |
| `PortfolioStatements` | Embedded in model | Statement list/review state, explicit reconciliation decisions, statement check normalization and creating a bond from selected terms | New **64 lines** |
| `AccountRecordWorkflowsTest` | No dedicated characterization of these combined public-model contracts | Eight public-boundary behavior tests, run before and after extraction | New **196 lines**, `commonTest` |

Total line count increases because responsibilities have explicit owners, dependencies and tests. No utility bucket or inheritance hierarchy was added. The model is **still oversized**; this step does not claim to eliminate every God Class or satisfy an arbitrary line cap. Its remaining auth/session transitions, guest migration, target settings and compatibility facade need separate characterized slices. The larger sizes at committed HEAD reflect the earlier uncommitted refactor being deliberately excluded from this commit.

## Dependency and contract changes

- Each collaborator has **two constructor dependencies**: a provider for the existing current `PortfolioApi`, plus the applicable portfolio refresh/projection callback. The provider is explicit because origin/session replacement must not leave a feature holding an old client.
- No additional constructor arguments were added to `PortfolioModel`, no DI framework or runtime dependency was introduced, and no new interface was needed. The collaborators do not own clients, scopes, session restoration, background jobs or authentication.
- The full concrete client dependency remains the existing transport contract; the earlier uncommitted narrower data interface is not required by this commit. A later port migration can be evaluated separately.
- External model entry points and readable property names/types remain compatible. Properties whose setters were private are now getter facades over collaborator-owned Compose state. State reads remain observable; request payloads, URLs, status handling, safe messages and callback ordering are preserved.
- Financial calculations, company grouping, order estimates/fees, parser engines, original PDF bytes, upload protocols, guest persistence/schema/encryption, platform bridges, navigation and all screen source files were unchanged by this step. Existing source hashes were checked against a beginning-of-step manifest.

## Sequential characterization and extraction

1. Added the eight tests against the unchanged model and ran them with existing statement characterization tests: **pass** (`/tmp/inzira-decompose-characterization.log`).
2. Extracted the ledger only; ran account-workflow, model and encrypted-backup tests: **pass** (`/tmp/inzira-decompose-ledger.log`).
3. Extracted pending orders; ran account-workflow, model and guest-session tests: **pass** (`/tmp/inzira-decompose-orders.log`).
4. Extracted statement workflows; ran account-workflow, statement and model tests: **pass** (`/tmp/inzira-decompose-statements.log`).
5. Ran broad working-tree verification, then independently verified the exact staged implementation against committed callers.

Characterized contracts include:

- Ledger transient failures retain prior entries; 404/405 clear them; cancellation/401 propagate rather than becoming optional-data errors.
- Ledger completion follows ledger reload and portfolio refresh. Guest writes still go through the guest repository and sample mode remains guarded in the model.
- Order completion precedes reload. A failed optional reload retains pending projections and marks availability false; unsupported endpoints clear both order state and portfolio projection. Rejected writes do not invoke completion or reload.
- Reconciliation uses the trimmed reason and exact expected decision timestamp; review publication precedes list reload/completion. Creating a bond calls completion before portfolio refresh and does not mutate the input figures.
- Logout clears all extracted account state. Existing session, guest, import, financial, report and persistence tests remain unchanged.

## Verification commands and results

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`.
Android SDK for isolated builds: `/Users/muhirwagabooreste/Library/Android/sdk`.

Focused test commands used this prefix:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest
```

with, sequentially:

```text
--tests '*AccountRecordWorkflowsTest' --tests '*StatementReconciliationTest' --tests '*StatementPurchasesTest' --console=plain
--tests '*AccountRecordWorkflowsTest' --tests '*PortfolioModelTest' --tests '*EncryptedBackupTest' --console=plain
--tests '*AccountRecordWorkflowsTest' --tests '*PortfolioModelTest' --tests '*PendingShareContractTest' --tests '*GuestSessionTest' --console=plain
--tests '*AccountRecordWorkflowsTest' --tests '*Statement*Test' --tests '*PortfolioModelTest' --console=plain
```

All four commands returned exit 0. The initial order command also supplied `PendingShareContractTest`; no class matches that filter, so it contributed no tests. Actual contract coverage is in `PendingPurchasesTest`, included in both complete shared suites below. Broad working-tree verification:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

**Results:** both exit 0; Android build/release lint and device/simulator Kotlin compilation passed. Working-tree shared tests: **417 host + 440 native, all pass**. Core JVM XML: **89 pass** (the task was up-to-date in this working-tree run; it was freshly rerun in the isolated verification below). Logs: `/tmp/inzira-decompose-full-working.log`, `/tmp/inzira-decompose-native-working.log`.

### Independent proposed-commit verification

Only the focused model patch, three collaborators and test file were staged; the broader existing refactor remained unstaged. The staged tree was exported using:

```sh
audit_tree=$(git write-tree)
audit_export=$(mktemp -d /tmp/inzira-decompose-index.XXXXXX)
git archive "$audit_tree" | tar -x -C "$audit_export"
```

The first partial-staging export omitted an existing market declaration and failed compilation. That staging-only mistake was corrected before creating the final export; it was never committed or applied to the working-tree app. The corrected source was exported to `/tmp/inzira-decompose-index.0Vam1R` and the full build/test/lint/compilation command above was repeated with `ANDROID_HOME` and `--project-dir "$audit_export"`.

**Corrected isolated result:** exit 0, `BUILD SUCCESSFUL in 55s`; **406 shared host tests + 89 core JVM tests, zero failures/errors/skips**; Android debug build, release lint and both iOS Kotlin compilations pass. The lower shared count compared with the working tree is expected: 11 tests belonging to the earlier uncommitted refactor are excluded. Log: `/tmp/inzira-decompose-index-build-final.log`.

Native verification of the same isolated source, plus a fresh core JVM execution:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk ./gradlew --project-dir "$audit_export" :core:jvmTest --rerun :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

**Result:** exit 0, `BUILD SUCCESSFUL in 40s`; **429 shared native tests and 89 freshly executed core JVM tests pass**, zero failures/errors/skips. Log: `/tmp/inzira-decompose-index-native.log`. The eight new workflow characterization tests pass on native as well as host.

Static/regression checks were executed both from the working tree and isolated export:

```sh
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Results: design-color check passes; **13 Python tests and 84 Node engine/parser tests pass**; diff whitespace checks pass. No configured Detekt/ktlint task exists. Release lint warnings remain baseline debt; passing lint does not mean warning-free. Logs: `/tmp/inzira-decompose-index-python.log`, `/tmp/inzira-decompose-index-node.log` (and equivalent working-tree logs).

### Limits and unchanged baseline failure

The prior audit independently reproduced `ShareCompanyRowsTest.remainingSharesAndTheirCostAreUsedAfterSales` failing in the separate **core native** suite (`102523.68` versus `102523.68000000001`). This step changes no core financial source and does not alter, weaken or exclude that assertion in its suite. The core native task was not rerun here; its known failure is unresolved. Relevant native verification here is the full shared suite containing the changed model, new tests and native integration tests. Do not interpret these passing scoped checks as an all-target/all-task green baseline.

No physical-device UI, live account writes/push delivery, new PDF import into an account, Xcode signing/distribution or connected Android instrumentation run was performed in this step. Unchanged UI source, public-model characterization, host/native integration suites and KMP compilation provide the stated evidence; they do not constitute exhaustive UI equivalence proof.

## Handoff

The focused commit should contain exactly six files: `PortfolioModel.kt`, the three collaborator files, `AccountRecordWorkflowsTest.kt`, and this report, with message `refactor(kmp): decompose oversized classes`. Review the staged diff and status before committing and pushing. The existing broader uncommitted refactor, other documentation and all unrelated work must remain in the working tree.
