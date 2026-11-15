# INZIRA KMP usability and navigation audit

Date: 2026-11-15. Baseline: `f7ef53f`, branch `main`.

## Scope and implementation

KMP application only. No backend, website, API contract, investment formula, encryption format or persistence migration changes. The working tree already contained unrelated responsibility extractions, visual consistency and accessibility work. This patch is verified separately against HEAD; those earlier changes are excluded from its isolated verification tree. File references below use the current working-tree owners; the corresponding baseline owner is noted where necessary.

The application uses Kotlin 2.3.21, Compose Multiplatform 1.11.1 and Material 3 1.11.0-alpha07. Shared Android/iOS UI lives in `app/shared`; Android has a native host and iOS an Xcode host/extensions. `InvestmentApp` owns navigation with remembered destination flags, records and saveable tab state, rather than a navigation library. `PortfolioModel` coordinates account and guest work. Its existing synchronous busy guards prevent duplicate saves. This audit retains that architecture and brand.

The review uses [Nielsen's usability heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/) as a practical lens: visible status, familiar language, user control, consistency, error prevention, recognition, efficient actions, restrained presentation, recovery and contextual guidance. This is a source and targeted interaction review, not a representative-user study or a claim that every screen has been manually tested.

## Implemented findings

Paths below are relative to `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/` unless qualified.

| ID / severity | Evidence and user impact | Change |
|---|---|---|
| U01 High — edited investment drafts lost on Back | `PurchaseForms.kt` (`PurchaseEditor`, `EquityPurchaseEditor`), `PurchaseImportScreens.kt` (`PendingBondScreen`) and `ShareOrderScreen.kt`: toolbar/system Back and form Cancel immediately removed a draft. | `FormBackHandler.kt` gives the visible form one exit policy. Changed drafts offer **Keep editing / Discard changes**; unchanged forms exit directly. A save in progress blocks user exit. Successful-save callbacks retain their original completion path. No extra confirmation for read-only pages. |
| U02 Medium — errors vanished before recovery | `InvestmentApp` cleared `model.error` after five seconds regardless of whether the failure was resolved. Working-tree extraction owner: `InvestmentPlatformEffects.NoticeLifetimeEffects`. | Errors stay until dismissal or a subsequent action clears/replaces them. Existing dismiss controls and model recovery remain. Success notices retain their three-second lifetime. Tests wait beyond the previous error lifetime after a failed save. |
| U03 Medium — retained quotes appeared current after refresh failure | `ShareOrderScreen`: `quoteRefreshFailed` was only communicated when no companies were available. Old quotes can remain after failure. | **Using previously loaded closing prices** plus in-place Retry. Loading and refresh have distinct text; **Latest closing price** clarifies that this is not a live execution quote. No invented quote age or offline guarantees. |
| U04 Medium — unavailable order API required leaving the form | `ShareOrderScreen`: recovery said to pull down on Home. That interrupted the current task. | **Pending orders unavailable** with in-place `model.refresh()` Retry. Existing disabled save conditions and draft values remain. |
| U05 Medium — empty issue list confused loading with absence | `PendingBondScreen`: an empty issues list showed the empty state during an active request. | Use `LoadingState` while busy and no choices exist; retain the existing empty state after work finishes. |
| U06 High — misleading cancellation during destructive restore | `EncryptedBackupScreen`: Cancel/dismiss remained available after `restoreGuestBackup` started. The restore itself already copied its input and was not cancelled by this button. | Disable Cancel/confirm and block dialog dismissal and page Back during restore; show **Working…**. Cancel before confirmation still closes without writing. No false claim that this changes the repository's atomicity or rollback behavior. |
| U07 Medium — backup errors detached from fields | Backup validation ran inside asynchronous work and used a generic status; password fields lacked explicit Next/Done behavior. | `BackupFormValidation.kt` checks existing 12–256 limits and exact export confirmation before file/encryption work. Inline errors, persistent labels, Next/Done and editable retry state. Passwords are not trimmed, saved in instance state, or logged. Restore does not require export confirmation. |
| U08 Medium — cancelled export was silent | `saveBackup` returning false produced no result, leaving uncertainty about whether a file was saved. | Neutral **Backup not saved. You can try again.** Retain fields after cancellation/failure; clear secrets only after actual successful export/restore. Set the local work guard before launching, and recheck it inside click handlers. |
| U09 Medium — widget opened Calendar behind a child page | `InvestmentApp.openCalendarAt` changed tab/date but did not close About, legal/history/details or other read-only child state. | Close unrelated read-only destinations and document preview when no form/workflow is active. Preserve active investment forms, backup, cash ledger, target and migration flows. This fixes the hidden destination without redesigning deep-link queuing. |
| U10 High — handled widget routes replayed after recreation | `HomeWidgetBridge` retained `requestedDate`; root used a composition-local handled counter. A fresh root replayed a previously handled route, taking a later session unexpectedly to Calendar. The unfiltered device run exposed this through `NotificationTargetEditorTest`. | Consume a route once at the bridge after account unlock, preserving a newer request if an older consumer runs. Add shared consumption/stale-request tests and an actual root-recreation interaction assertion. Unhandled requests still wait for authentication/unlock. |

French translations accompany the new labels. Brand, layout hierarchy, share stepping, price calculation, commission, records and API payloads remain unchanged. The back dispatcher uses identity-based registration cleanup so disposing an old form cannot remove the next form's handler.

## Flow inventory and state review

| Implemented flow / source | Existing behavior retained; observations and remaining limits |
|---|---|
| First launch, brand intro, onboarding — `InvestmentApp`, `BrandIntroScreen`, `OnboardingScreen` | Completion preferences, replay from Settings and onboarding sequencing already exist. No new mandatory step. Physical cold-start, interrupted onboarding and reduced-motion transitions still require manual review. |
| Authentication — `SignInScreen`, `PortfolioModel` | Login/register fields, password-manager handoff, busy state and recoverable model errors exist. Email/mode are saveable; passwords are intentionally in memory. Registration validates invitation and matching passwords. Inline field-specific authentication errors remain a follow-up; server rejection does not need to erase the draft. |
| Device unlock — `UnlockScreen`, secure sign-in adapters | Locked state, verifying-server-session text, disabled busy unlock and **Use password instead** fallback. No bypass or weakening of biometric/capture protection. Physical biometric cancellation, background/resume and expired-session journeys require both phones. |
| Guest choice/local workspace — `GuestScreens`, `GuestPresentation.GuestStorageSettings`, guest workspace/repository | Personal local and fictional sample modes are distinguished; sample is read-only. Storage errors have Retry. Guest deletion and migration have explicit explanations/confirmation. No new account-like synchronization promise. Switching modes/process termination remains a draft-loss boundary. |
| Portfolio overview — `PortfolioDashboard`, `PortfolioPullRefresh`, `PortfolioModel` | Initial skeleton vs recoverable initial error, **Try again**, refresh and investment action menu exist. Empty portfolio and filters are separate. An existing portfolio is retained during refresh failure; durable account offline access is not implemented by this patch. |
| Bond details — `BondDetailsScreen`, `PurchaseForms`, `PurchaseImportScreens` | Overview, schedules, evidence and editing remain. New form exit policy protects edits; calculation/validation rules unchanged. Pending bond selection waits for loading before showing absence. |
| Share/company details and purchases — `ShareDetailsScreen`, `ShareOrderScreen`, `PurchaseForms` | Grouped overview/history and individual purchase navigation remain. Pending order edit mode is distinct from viewing; known model busy and version/id behavior retained. Quote refresh failure now visibly distinguishes retained prices and allows retry. |
| PDF acquisition/preparation — `PdfImportPreparationScreen`, native inbox/share extensions, root | Preparation names the file and explains review before saving. Acquisition/parsing/progress remain distinct from committing an import. No new auto-save on share and no change to supported formats. Native PDF lifecycle remains covered by existing regression tests, with the suite limitation recorded below. |
| PDF review, duplicate/conflict and completion — `ConversationReview`, `PurchasePdfImport`, `PortfolioImports`, `ImportCompletionReceipt`, `ImportJournal` | Review precedes persistence; duplicate/conflict states and saved completion receipts exist. Keep original errors and recovery paths. No duplicate matching or server import API changes. External import events are not all guarded by the new Back policy. |
| Documents/preview — `DocumentsScreen`, `PdfViewerScreen` | Library/import navigation, unavailable/error recovery and document loading exist. Preview failures have retry. No new cross-platform file-provider behavior. |
| Investment/transaction history — `InvestmentHistoryScreen`, `CashLedgerScreen` | Loading/unavailable/retry, search filters and **Clear filters** already exist. Cash transactions have a review/confirmation stage. Cash-ledger draft Back protection is still a separate follow-up. |
| Calendar/reminders/widgets — `CalendarScreen`, native widget route bridge | Month/date navigation and reminder permission feedback remain; widget request now reveals the destination from read-only pages. Permission denial and real notification presentation depend on system settings; no new notification delivery claim. |
| Markets — baseline `LibraryScreens` / working-tree `StocksScreen`, market data owner | Existing load/retry and quote data retained. This patch clarifies retained quotes specifically in the share order flow; application-wide last-success timestamp presentation is not added. |
| Settings/security/reporting — `SettingsScreen`, `IssueReportScreen`, `PortfolioReportDialog` | Existing controls, platform availability and export feedback remain. Issue reporting already protects unsent drafts and distinguishes opening an email draft from sending it; no redundant guard added. Backup improvements described above. |
| Sign out/delete/clear — `SettingsScreen`, `GuestPresentation`, purchase/delete components | Sign out explains removal of device secure sign-in while retaining server data/password-manager entries. Account deletion requires password and explains irreversibility. Guest data deletion offers **Keep my data**. Existing investment delete confirmation retained. No extra confirmation for harmless toggles or ordinary read-only Back. |
| Tools/simulator/education/legal/about — `InvestmentToolsScreen`, `PlanningScreens`, `LegalScreen`, `AboutScreen` | Read-only/tool destinations remain reachable and return via existing hierarchy. About/other read-only children no longer obscure widget calendar navigation. No simulation or financial rule changes. |

## Remaining usability risks and execution order

1. **High: interrupted drafts and external routing.** Root navigation mixes saveable flags with remembered record/form data. `MainActivity.onCreate` also re-reads the launch `widgetDate` extra; a recreated native host may issue a new request even after the shared root consumed the old request. U10 prevents shared-root replay, not every native-intent replay or process-death case. The new guard covers toolbar/system Back and the existing form Cancel, not process death, logout/mode change or every incoming PDF/notification action. `InvestmentApp` notification/import effects can clear editor flags. Define an explicit defer/discard policy for those events before broader navigation work. Do not persist private free-form inputs/passwords automatically.
2. **Medium: remaining forms.** `CashLedgerScreen`, `ApplicationBidScreen` and target dialogs have independent state; this patch does not claim universal unsaved-change handling. Add targeted draft/exit tests before extending the guard.
3. **Medium: partial refresh visibility.** `PortfolioDashboard` can retain a previously loaded account snapshot; global notices help explain failures, but there is no consistent last-success timestamp across all screens. Avoid labelling an unrefreshed value as live. This needs a product policy and tests for partial endpoints, not an invented offline queue.
4. **Medium: discoverability.** Add-investment actions intentionally live behind the investment-menu icon, per existing product design. Source/semantics presence does not establish that users discover it. Validate with first-time users before adding persistent visual bulk.
5. **Medium: authentication/form errors.** Some multi-field/server errors are global rather than associated with individual fields. Retaining errors improves reading time, but field-level mappings need concrete server error contracts and tests.
6. **Medium: retained global error placement.** Root notices do not render in every fullscreen/nested workflow. Avoid promising that persistent `model.error` makes all workflow failures locally visible. Review each delegated screen's local error surface before migrating errors globally.
7. **Medium: full-process Android instrumentation stability.** Existing PDF/share/capture lifecycle testing has a previously observed native EGL/RenderThread crash. Do not hide it with disabled tests or weaken financial screenshot protection. See actual current result below.
8. **Low: terminology.** Existing source identifiers still use `temporary` in places while visible labels use pending. No broad rename, financial semantics change or new confirmation convention is included.

## Tests and manual scenarios

New common tests: once-only widget route consumption and stale/invalid request handling; dispatcher ownership/route fallback; backup length boundaries 0/11/12/256/257, exact confirmation including whitespace, restore independent of export confirmation; closing-price loading/refresh/unavailable/cached/ready states.

New Android instrumentation uses the real `InvestmentApp` on the existing debug host with synthetic account API or memory-only guest repository and real backup encryption. `NavigationUsabilityTest` covers changed/unchanged form Back, Keep editing/discard, failed-save persistence, retained quotes and Retry, duplicate submission while saving, success navigation and calendar widget navigation from About. `BackupUsabilityTest` covers preflight field errors, cancelled export retry without re-entry, actual export clearing secrets, cancelling restore before writing, and disabled dismissal during restore. No real account, financial PDF or private device storage is used. `UsabilityTestSupport` follows exported accessibility semantics using the existing instrumentation stack; it introduces no testing framework dependency.

The existing notification instrumentation fixture is corrected to grant Android 13+ notification permission synchronously before testing eligibility. It previously closed an asynchronous shell-command descriptor immediately. This is test setup only; notification production behavior is unchanged.

Manual regression checklist (not a claim of execution):

- Android and iPhone: fresh install → intro/onboarding → login; failed login → edit and retry; sample/personal guest → sign in → return; biometric cancel/background/expired session → password fallback.
- Both themes, small display, landscape and large font: create/edit a bond, confirmed share and pending order; keyboard Next/Done; Back/Cancel unchanged vs changed; Keep editing; discard; busy save; server error and retry. Confirm totals/evidence are unchanged.
- Offline: launch without account data, retry once; disconnect after loading, refresh; failed quote refresh in an order, retained-price notice, reconnect/retry. Ensure no unsaved work is reported as synchronized.
- Share synthetic valid/invalid/duplicate PDFs from platform Files; unlock if required, prepare/review/cancel/retry, save and inspect completion/library. Never show saved success merely after acquisition.
- Open a widget from Home, About, purchase detail, active draft and locked app; verify requested month, protected data and preserved draft policy. Verify notification/deep-link external transitions separately.
- Guest backup: invalid/mismatched password, cancelled file picker/export, wrong password, valid export, cancelled restore, restore failure and successful replacement; while writing try toolbar/system Back and dialog dismissal. Cancel before confirmation must write nothing.
- Sign out cancel/confirm, guest delete cancel/confirm, account deletion validation, purchase deletion cancel/confirm and cancellation during report generation. Use disposable test data only.

## Verification and Git disposition

Verification results are recorded after execution below. No unexecuted task is considered successful. Native shared tests are not iPhone UI/VoiceOver or real biometric tests. There is no configured Compose screenshot-golden or dedicated navigation test framework; existing Android instrumentation is used. Earlier pending visual/accessibility work is not part of the isolated verification patch.

### Compilation, host and native tests

Isolated UX export: `/tmp/inzira-ux-index.zBkXR3`. Verification deliberately excludes unrelated pending production/test extractions and visual/accessibility changes. Final production/test inputs were compared with the staged patch; the report records results separately.

```sh
export JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home
export ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk
ux_export=/tmp/inzira-ux-index.zBkXR3
xcrun simctl boot 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4
xcrun simctl bootstatus 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 -b
./gradlew --project-dir "$ux_export" :core:jvmTest :core:testAndroidHostTest \
  :app:shared:testAndroidHostTest :core:allMetadataJar :app:shared:allMetadataJar \
  :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest \
  :app:androidApp:bundleRelease :app:androidApp:lintRelease \
  :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 \
  :core:compileTestKotlinIosArm64 :app:shared:compileTestKotlinIosArm64 \
  :core:iosSimulatorArm64Test --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
  :app:shared:iosSimulatorArm64Test \
  -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

**BUILD SUCCESSFUL**, final sources, 3m 13s (`/tmp/inzira-ux-revised-verification.log`). Test executions: core JVM **145**, core Android-host **145**, shared Android-host **533**, core native iOS **144**, shared native iOS **559**; zero failures/errors/skips. Android release lint: **94 warnings, zero errors**, including existing deprecated API/configuration warnings; this is not warning-free verification.

Native tests: iPhone 16 Pro simulator / iOS 18.6, Xcode 26.3. Initial sandbox-only `simctl list` access failed; rerunning with simulator permissions succeeded. These are native shared-code tests, not a full iPhone UI interaction suite or physical biometric/file-picker tests. No iOS UI test target was found in the host project.

```sh
python3 "$ux_export/scripts/check-kmp-packages.py"
python3 "$ux_export/scripts/sync-design-colors.py" --check
python3 -m unittest discover -s "$ux_export/scripts/tests" -p 'test_*.py'
git diff --cached --check
```

Passed: **414** source/test files satisfy existing package, source-set import and module DAG checks; authored/generated colors consistent; **19** script tests; no patch whitespace errors. No configured detekt/ktlint, dedicated navigation checker or screenshot-golden task was invented or claimed.

Working-tree integration, including unrelated pending UI/extractions:

```sh
./gradlew :app:shared:testAndroidHostTest --tests '*FormBackDispatcherTest' \
  --tests '*BackupFormValidationTest' --tests '*ClosingPriceFeedbackTest' \
  --tests '*HomeWidgetRouteTest' :app:androidApp:assembleDebug --console=plain
```

**BUILD SUCCESSFUL**, 15s (`/tmp/inzira-ux-working-revised.log`), seven focused tests. Working-tree package checks passed **444** source/test files and **23** script tests. This is a focused integration check, not a claim that the entire unrelated working tree passed a new full suite.

### Android interaction tests

API 36.1 ARM64 emulator `Sequo_References_API_36_1`, booted with the Android CLI. `adb` below is `$ANDROID_HOME/platform-tools/adb`.

```sh
adb -s emulator-5554 install -r "$ux_export/app/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
adb -s emulator-5554 install -r "$ux_export/app/androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk"
adb -s emulator-5554 shell am instrument -w -r \
  -e class dev.orestegabo.portfolio.investments.NavigationUsabilityTest,dev.orestegabo.portfolio.investments.BackupUsabilityTest,dev.orestegabo.portfolio.investments.NotificationTargetEditorTest \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

**OK (3 tests)**, 114.316s (`/tmp/inzira-ux-revised-focused.log`), final production/test sources. Includes the new root-recreation regression and the existing target-notification save/cancel flow immediately after it. The test driver waits for asynchronous accessibility nodes and real cryptography; it does not change global keyboard/touch mode. Initial fixture issues (missing native actions, selecting an already-selected tab and assuming exactly one internal restore write) were corrected before this run. The restore test asserts one repository restore and the resulting workspace revision rather than constraining legitimate preparation/replacement writes.

These are English/light-theme Android interactions. iOS UI, both-theme visual screenshots, font-size matrices, physical keyboards and real native document pickers were not manually verified in this UX phase. Manual scenarios remain listed above.

The unfiltered device command is:

```sh
./gradlew --project-dir "$ux_export" :app:shared:connectedAndroidDeviceTest \
  :app:androidApp:connectedDebugAndroidTest --console=plain
```

The first run (`/tmp/inzira-ux-connected-final.log`, 6m 18s) failed: the app report contained the target-editor navigation failure caused by U10 and a process crash in `PdfSharingTest.pdfTargetsReceiveColdAndWarmIntentsAndExportsGrantOnlyReadAccess`. Shared device tests passed **525/525** on that pre-U10 revision. The target-editor problem was fixed and verified by the three-test batch above. The native crash matches the earlier full-suite baseline: RenderThread SIGSEGV in `RefBase::incStrongRequireStrong`, `libEGL_emulation.so` window-surface destruction and HWUI `EglManager::destroySurface`. No renderer, capture protection, assertion tolerance or production security flag was changed to conceal it. The final unfiltered rerun includes U10 and is recorded below.

### Final result and Git gate

**Final unfiltered rerun: BUILD SUCCESSFUL**, 6m 41s (`/tmp/inzira-ux-connected-revised.log`). XML reports: shared Android device **527 tests, zero failures/errors/skips**; Android application **44 tests, zero failures/errors, four opt-in skips**. This includes successful `PdfSharingTest`, both new UX journeys, the existing notification target editor and existing capture/import regressions. The terminal progress counter counted some assumption events twice; the XML counts above are authoritative.

The four skipped methods were:

- `FirebaseDeliverySetupTest.receivesARealFirebaseMessageWithoutAnAccountSession`: real Firebase delivery opt-in setup absent.
- `BondPdfRegressionTest.suppliedConfirmationReachesValidatedReviewWithoutSaving`: no actual user PDF supplied to the test.
- `GuestPersistenceTest.guestInvestmentsSurviveProcessRestartAndReboot`: process/reboot opt-in absent.
- `GuestStartupFixtureTest.prepareEmptyOfflineGuestForProcessLaunchMeasurements`: startup measurement fixture opt-in absent.

These skips do not establish real-device delivery, user-document compatibility, reboot persistence or measured startup performance. Synthetic financial/PDF tests ran. No test was disabled, no assertion tolerance was weakened and no capture/renderer/security setting was changed. The earlier EGL process crash remains an **intermittent verification risk**, not a proven fix attributable to U10. Track its reproduction separately, retain the full lifecycle suite in CI and compare emulator/native-driver versions and physical-device results before changing production rendering.

Final review: 17 production/test inputs matched the isolated patch byte-for-byte; the audit is the eighteenth file. Only KMP UI/test files and this report are included. Backend, website, API, investment calculations, encryption/persistence formats and unrelated pending work are excluded. Available required checks now pass with the four documented opt-in limitations. The authorized commit message is `refactor(kmp-ui): improve navigation and usability`; the current branch is `main`.
