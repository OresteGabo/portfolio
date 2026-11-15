# Coroutines and Flow correctness audit

Reviewed against `97ce353` and the existing working tree on 15 November 2026. Searched production Kotlin in `core`, `app/shared` (common/Android/iOS source sets) and the Android host; inspected relevant tests and build configuration. Backend and website are excluded. Existing unfinished extraction work is distinguished from the committed implementation and remains uncommitted. This report covers every requested concurrency area, but does not claim all platform timing/resource risks have been eliminated by a static audit.

File references use `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/` unless a platform is specified (`androidMain`/`iosMain`). The transport is `core/src/commonMain/kotlin/dev/orestegabo/portfolio/network/PortfolioApi.kt`; Android intent intake is `app/androidApp/src/main/kotlin/dev/orestegabo/portfolio/IncomingPdfIntent.kt`.

## Corrections and regression evidence

| Severity | File / operation | Evidence and correction |
|---|---|---|
| High | `PdfImportPipeline.kt` / `readInvestmentPdf` | A cancelled parser that returns from a non-cancellable adapter still reached CHECKING, admission, byte selection and caller continuation. Opening/admission callbacks could cancel the owner but subsequent synchronous steps still ran. Three initial tests reproduce these failures. Capture the caller context and check activity across picker, opening, parser, admission and selected-byte handoff boundaries. Do not validate/publish/return a cancelled proposal. |
| Medium | `PdfImportPipeline.kt` / selection and parser progress | Picker and parser callbacks could update feedback after the owner was cancelled but before cleanup ran. Gate callback feedback on owner activity; keep final callback detachment and progress clearing. New tests include a picker returning late and parser progress arriving during cancellation. |
| High | `GuestPdfImport.kt` / `select` timeout catch | All `TimeoutCancellationException`s became a local timeout error, even when the enclosing owner/deadline had cancelled. A test shows code after `select` running in an already timed-out owner. Clear private state, then check owner activity before publishing the existing local-parser timeout message. The existing parser-owned timeout test remains valid. |
| High | `PortfolioNotifications.kt` / settings and inbox error handling | Rejection handling ran before checking captured account/revision, so an old 401 could reject a different current account. A synthetic account-switch test fails before the fix. Check coroutine activity and account/revision **before** passing failures to the session owner. Current unauthorized reads still reach that owner. |
| High | `PortfolioNotifications.kt` / inbox/settings result publication | Generation checks alone do not reject a cancelled coroutine when an adapter returns without a suspension/cancellation check. A non-cooperative inbox test reproduces rows published after cancellation. Check activity after reads and before publication/error policy; recheck ownership after the settings phase before starting the inbox read. |

No scopes, channels, locks, sharing policies or reactive framework were added. The fixes strengthen existing caller-owned work. They do not preempt synchronous native computation, cancel a committed server write, change financial rules, or serialize unrelated imports through a global queue.

## Scope and lifecycle inventory

| Area | Actual implementation / assessment |
|---|---|
| ViewModel work | `PortfolioModel` owns action/startup/background-lock jobs through lifecycle `viewModelScope`. Actions gate duplicate interactive writes with `busy`; explicit generation checks isolate completion, and session cleanup cancels startup work. `onCleared` releases API/native/document resources in addition to lifecycle cancellation. Do not replace this with a detached scope. |
| Structured parallel reads | Committed `PortfolioModel` snapshot fallback uses `coroutineScope`/`async`; the unfinished `AccountSnapshotReader.kt` extraction preserves this. Purchases/shares form one projection, so a failed required branch cancels its sibling. Optional startup sections use `supervisorScope` with explicit exception handling; cancellation is rethrown. Parallelizing writes would require different guarantees and is not introduced. |
| Existing optional feature scopes | `PortfolioNotifications` receives the ViewModel scope; the unfinished `PortfolioGuestWorkspace` and `PortfolioGuestQuotes` extractions receive it too, corresponding to committed inline jobs. They do not construct independent jobs. Notification loading flags, reminder revisions and preference mutexes already bound ordinary duplicate work. This audit adds cancellation/session checks at the notification boundaries described above. |
| Composition work | `rememberCoroutineScope`, `LaunchedEffect` and `produceState` own UI work in onboarding, issue reports, backup, PDF reader, dashboard and market screens. They cancel with composition/keys. CPU-heavy annual coupon/financial-tool projections already run on `Dispatchers.Default`; local display operations remain in composition. |
| Android intent intake | `IncomingPdfIntent.receivePdf` uses the activity `lifecycleScope`, streams granted content on IO with size/activity checks, and closes its input stream with `use`. Its cancellation catch rethrows before UI error handling. No process-long coroutine is created for an Activity delivery. |
| Process-owned iOS receiver | `IosPdfReceiver` is the one explicit production `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` found. Its owner is the app-level Open-In/share receiver, not a screen, so replacing it with a composable scope would drop deliveries during sign-in/onboarding. Children catch ordinary failures and always call the queued-delivery completion callback. Reads are size-bounded and close streams/security scopes. There is no explicit process-scope shutdown API; it is not a per-screen scope to instantiate repeatedly. |
| Global/unmanaged work search | No production `GlobalScope` or `MainScope` usage was found in the searched modules. No new executor or detached `launch` is introduced. JVM smoke entry points and native rendering tests use `runBlocking` outside production UI orchestration. |

## Flow collection and sharing

The application uses Compose observable state, not a StateFlow/SharedFlow store. No production `stateIn`, `shareIn` or `launchIn` configuration was found to tune; adding them would manufacture a reactive layer.

- `CalendarScreen` collects reminder feedback with `snapshotFlow.filterNotNull().collectLatest` inside `LaunchedEffect(model)`. A newer snackbar replaces an older one; this is latest transient feedback, not a financial transaction stream. Clearing feedback to null is filtered before `collectLatest`, so it does not itself cancel the snackbar. The collector is composition-owned. It is not separately STARTED-gated; if background toast consumption becomes a product issue, characterize the delivery policy before changing it.
- `OnboardingScreen` uses `snapshotFlow(...).filterNotNull().first()` to await measured bounds, not a permanent collector.
- `OnboardingMotion` uses `repeatOnLifecycle(RESUMED)`; the calendar date timer uses `repeatOnLifecycle(STARTED)`. Existing lifecycle boundaries prevent offscreen/background animation or date polling from running indefinitely.
- No database Flow, duplicated hot-flow subscription or inappropriate replay buffer was found in these paths. Screen reconstruction may create a new collector by design; the previous effect is cancelled. No broadcast channel/event bus is warranted.

## Dispatchers, resources and remaining risks

| Priority | Evidence / decision | Follow-up or retained guarantee |
|---|---|---|
| Retain | Android `PlatformPorts`, protected snapshot/document storage use `Dispatchers.IO`; iOS storage/shared-PDF reading and PDF extraction/rendering use worker contexts. Stream/native handles are closed with `finally`/`use`. Native selection/auth bridges use cancellable continuations; Android biometric prompts/iOS picker and authentication callbacks have cancellation cleanup. | Keep native UI calls on Main and file/OCR work on worker contexts. Compile both iOS targets and run native tests; do not mechanically move UI APIs to Default. |
| Medium, retained | `PortfolioApi.request` decodes JSON synchronously in the caller context; bodies are bounded to 24 MB. `JsonRecord(raw)` is not a suspend cancellation point. | No runtime UI-blocking duration is measured here. Profile large responses; if warranted, introduce a decoding-dispatcher seam and update transport/ViewModel test factories together. Do not silently send deterministic TestDispatcher fixtures to an uncontrolled global worker or change JSON number/payload semantics as an audit shortcut. |
| Medium, retained | iOS `LocalInvestmentEngine.evaluate` executes JavaScriptCore synchronously on Main. A coroutine timeout cannot interrupt a synchronous native call before it returns. The engine mutex serializes iOS operations; Android WebView work uses bounded deferred acknowledgments and timeouts. | The new pipeline checks prevent post-cancellation publication, not hard preemption. Moving a reusable JSContext requires a deliberate thread-confinement/host-lifecycle design and platform tests, not a per-call Default switch. Profile before claiming a rendering improvement. |
| Low, retained | Android `LocalInvestmentEngine.shouldInterceptRequest` has a `runBlocking` fallback to satisfy a synchronous resource-response API; it is not a detached ViewModel job. | Prefer asset preloading only if fallback usage/latency warrants it. Preserve the strict local-resource allowlist and WebView lifecycle; do not replace it with fire-and-forget I/O. |
| Retain | Protected guest JSON persistence is mutex-serialized on its work dispatcher. The committed repository implementation and its unfinished extraction use bounded `NonCancellable` pending-file cleanup to preserve recovery consistency. `PdfViewerScreen` uses non-cancellable native-handle creation followed by close-if-owner-cancelled. | These blocks protect resource/transaction completion; removing them would create leaks/orphans. They are not general-purpose scopes for networking or UI work. No Room/SQLDelight reactive database is configured. |
| Medium, retained | `IosPdfReceiver` bounds each document but does not cap the number of simultaneous process-owned deliveries before inbox admission. | If batch/rapid delivery is supported at scale, test duplicate/queue semantics and add admission bounds without deleting a sender's file or completing a queued delivery early. No measured memory leak is claimed. |
| Medium, retained | Native crypto/OCR/JavaScript and `EncryptedBackupScreen` worker-result handoffs are not fully preemptible. Cancellation during native work can delay completion; temporary-array ownership around a cancelled dispatcher handoff needs dedicated platform/backup tests. | Keep existing byte erasure/finally behavior. Do not claim immediate interruption or guaranteed zeroization of native/internal copies. A broader backup/native-resource redesign is outside this verified slice. |
| Retain / separate work | Portfolio refresh ownership combines an existing sync mutex, startup revision and action generation. The prior state audit added latest-read/client/clear guards to ledger/orders/statements. Existing unfinished viewer/guest/market extraction files remain outside this commit. | Continue testing account switches, clear/retry and non-cooperative providers before changing another owner. This report does not claim every synchronous/native step in those files is cancellable. |

`SessionCookies` protects access with a mutex; authentication restoration is ordered by the session coordinator before account synchronization. The restore operation clears then adds cookies through separate lock acquisitions, so it is not documented as a concurrent multi-cookie transaction. If concurrent restore/snapshot is made a supported API use case, add a batch-restore contract test and perform replacement under one lock rather than assuming current ordering provides that guarantee.

Broad `runCatching`/`catch(Exception)` were reviewed in context: pure JSON validation, synchronous settings/diagnostics and cleanup-then-rethrow are not automatically cancellation swallowing. The guest timeout and notification error paths above required changes; replacing all catches mechanically would break optional-data/error presentation semantics. Parser/operation instances retain their existing one-active-import contract; independent imports can run concurrently with separate parser instances.

## Behavior and test contracts

- Success, invalid-input and local parser-error messages remain unchanged. Picker cancellation still returns null. Caller cancellation still clears progress and invokes parser cancellation; normal fast success/error minimum-feedback timing remains. Parent cancellation skips that hold through the inactive caller context.
- Borrowed PDF bytes are not erased or copied by the shared pipeline. Existing workflow/native owners retain their cleanup responsibilities; changing `ReceivedPdfActions` ownership would break reuse/retry contracts.
- Progress setters are detached in `finally`. Cancelled operation callbacks cannot publish new opening/extraction status; final null progress clears the owned indicator.
- Tests cover cancelled non-cooperative parser/picker completion, cancellation initiated by opening/admission/selected callbacks, late progress, two independent concurrent imports, parent timeout versus local parser timeout, stale notification 401 versus current 401, and a cancelled non-cooperative inbox read.
- Existing import/progress/parser/guest, reminder/preferences and account read lifecycle tests remain. No financial rounding, persistence, payload or test-tolerance changes are made.
- In `PortfolioNotifications.kt`, only coroutine guards/imports are staged. The pre-existing unrelated `signInActivity` helper is deliberately excluded and remains uncommitted; exact-index compilation verifies that no new change depends on it.

## Verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`; Android SDK: `/Users/muhirwagabooreste/Library/Android/sdk`.

Reproduction before fixes:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*PdfPipelineCancellationTest' --tests '*GuestPdfImportTest.parentTimeoutCancelsSelectionWithoutReturningOrPublishingALocalError' --console=plain
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*NotificationCoroutineSafetyTest' --console=plain
```

Exit 1 as expected: four PDF/timeout tests fail (14s), then two notification tests fail while the current-401 control passes (5s). Logs `/tmp/inzira-coroutines-before.log`, `/tmp/inzira-coroutines-notifications-before.log`. These failures are corrected, not waived.

Final focused checks:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*NotificationCoroutineSafetyTest' --tests '*NotificationPreferenceSwitchTest' --tests '*BondMonthReminderTest' --tests '*PdfPipelineCancellationTest' --tests '*GuestPdfImportTest' --tests '*PdfImportPipelineTest' --tests '*PdfImportProgressTest' --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 9s`; log `/tmp/inzira-coroutines-focused-final.log`. Earlier PDF/parser/account-read focused checks pass in 13s; log `/tmp/inzira-coroutines-focused.log`.

Working-tree full check after PDF changes, before notification hardening:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 1m 11s`; log `/tmp/inzira-coroutines-working-build.log`. Notification hardening then passed the final focused run above; the isolated full run below is authoritative for every final staged source change.

The seven-file staged tree was exported with `git write-tree` / `git archive`, excluding older unfinished work and the unrelated notification helper. Exact isolated command:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$coroutine_export" :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Export `/tmp/inzira-coroutines-index.O19W57`: exit 0, `BUILD SUCCESSFUL in 1m 34s`; **719 host test executions** (107 core JVM, 108 core Android host, 504 shared host), no failures/errors/skips. Android app/test compilation, release lint and both iOS compilations pass. Log `/tmp/inzira-coroutines-index-build.log`.

Shared native verification:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$coroutine_export" :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 47s`; **523 shared native tests**, no failures/errors/skips. Log `/tmp/inzira-coroutines-index-native.log`.

Static/script checks run in both working and isolated trees:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Both pass package ownership/module DAG and color consistency, **16 Python and 84 Node tests**. Final whitespace and index/export byte comparisons verify all six staged Kotlin files; the notification helper is checked absent from the index. Only this report receives verification-result updates after source testing.

No new test dependency is needed: existing `kotlinx-coroutines-test` supplies `runTest`, virtual time, deferred gates and cancellation tests. Baseline compiler/lint warnings remain. No configured Detekt/ktlint task was found. Android instrumentation is compiled, not executed for this common coroutine fix. No physical-device UI run, runtime performance/memory profiling, live account write or deployment is claimed. No full core-native run is claimed; the previously documented share-cost exact floating-point equality issue is outside the affected shared coroutine paths. No arithmetic or tolerance changes.
