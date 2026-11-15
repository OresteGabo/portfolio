# INZIRA final KMP UI quality review

Date: 2026-11-15. Baseline: `40aedf4`, branch `main`. Scope: Android/iOS client UI, tests and build guardrails. No server, website, backend API, calculation, import-matching, persistence or authentication-policy changes.

## Scope and evidence ownership

The repository already had substantial uncommitted responsibility extractions, design-system, accessibility and financial-transparency changes. Those are **not silently approved or included** in this patch. Verification of this patch uses an isolated export of committed sources plus the files listed below. Working-tree integration compilation/tests are recorded separately. The golden manifest names the committed UI revision; accepting pending theme or financial-label changes requires reviewing their pixel differences and updating references in that change.

This is a comprehensive source/test-infrastructure review, **not a claim that every screen was manually operated on every device**, that performance improved, or that the app is WCAG-certified or legally compliant.

## Implemented guardrails

- `app/AGENTS.md`: concise rules for visual identity, tokens, navigation, state ownership, financial presentation, accessibility, text scaling, synthetic fixtures, profiling, privacy and truthful verification.
- `UiGoldenTest` / `UiGoldenImages`: real Compose rendering and PixelCopy comparison against 12 reviewed PNGs. Six constrained viewports, both themes, fixed synthetic share records. Every pixel is checked; channel differences over two levels fail, with no area-based mismatch allowance. Actual images are written before comparison, so failures remain inspectable. A changed financial digit and changed dimensions are rejected by a comparator regression test.
- `UsabilityTestSupport.scroll`: arm a window-scoped wait before each action, accept `TYPE_VIEW_SCROLLED` or Compose subtree updates, then re-query the settled target. Event absence is an expected bounded-wait outcome, never a replacement for the required target assertion. Only visible containers advertising the requested action are used; unavailable directions end that search rather than issuing repeated rejected actions. The first complete run exposed a Settings click failure. Investigation reproduced the driver defect in large-text reverse scrolling: quiet-queue checks could return before the first scroll frame, dispatching repeated actions only milliseconds apart. All existing assertions/profiles remain; this is a test-driver correction, not a production navigation rewrite.
- `UiQualityPortfolioFixture`: controlled Ktor MockEngine, fictional TEST holdings, fixed historical dates, no real credentials, documents or live prices. Model ownership is attached to the test host's ViewModelStore and released with ActivityScenario; mock engine is closed.
- `AuthenticationUiQualityTest`: real `InvestmentApp` guest-choice cancellation and sign-in form, delayed failures, disabled loading action, no queued duplicate request, retained credentials and retry without retyping. Passwords are synthetic and not logged. This does not emulate native biometrics or password-manager services.
- `UiFrameBenchmarkTest`: opt-in Android Window FrameMetrics diagnostic around accessibility-driven scrolling of a 200-purchase synthetic share history, after expanding the history. Writes sample count, refresh rate, total-frame p50/p95/max and dropped metric reports. No emulator-specific latency threshold is imposed on CI.
- Android capture lifecycle: `ExperimentalCaptureLayer` now retires its surface before cleanup, rejects drawing outside a started Activity, and ignores transaction callbacks from destroyed/older surface generations. `ExperimentalCaptureBackdrop.attach` returns an idempotent, attachment-specific release handle; teardown retires state before invoking callbacks. Older Activities cannot detach a later registration, even with the same renderer function. Three regression contracts protect these guarantees. Existing masked-pixel, secure-window and PDF lifecycle assertions remain. This is an intentional lifecycle correction, not a change to the number-hiding or screenshot-protection policy.
- Android Test Orchestrator 1.6.1, **test-only**, with the existing runner 1.7.0. Each method retains its complete lifecycle scenario in a fresh instrumentation instance. No tests are filtered, privacy flags weakened or package data automatically cleared. A method crash must still fail the run; process isolation is not a production renderer fix. [Android runner/Orchestrator documentation](https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner), [AndroidX Test releases](https://developer.android.com/jetpack/androidx/releases/test) (accessed 2026-11-15).
- `-PcomposeReports=true`: toolchain-native compiler diagnostics, opt-in for all configured shared compilations. No compiler behavior or skipping policy is changed. Stability/skipping metrics are **not** runtime frame measurements. [Compose diagnostics](https://developer.android.com/develop/ui/compose/performance/stability/diagnose) (accessed 2026-11-15).
- `docs/quality/ui-coverage.json` and `scripts/check-kmp-ui.py`: required critical journey inventory, source-test existence, complete light/dark golden profiles, dimensions and SHA-256 integrity. With `--results`, missing/skipped required methods or any reported failure/error fail the check. The inventory is not a line/branch coverage percentage.
- Eight Python regression tests verify inventory validity, passing/failing/skipped/missing test evidence, and missing-theme, changed-hash and changed-dimension rejection. `.github/workflows/kmp-ui-quality.yml` runs inventory checks, their tests, JVM/Android-host tests, Android test compilation, lint and Compose diagnostics, preserving reports. It **does not claim to run emulator or iOS UI tests**. Existing release checks remain unchanged.

No visual rewrite or speculative performance cache was justified by the measurements. The focused Android capture-lifecycle correction is described separately above. Financial formulas, formatting implementations, screenshot exclusion and secure-window fallback policy are unchanged.

## Actual framework and architecture

- AGP 9.0.0; Kotlin 2.3.21; Compose Multiplatform 1.11.1; Material 3 dependency 1.11.0-alpha07. No framework or major dependency upgrade.
- `app:shared`: `commonMain`, Android and iOS actuals, shared tests. `app:androidApp`: Android activity/platform services and instrumentation tests. `core`: shared investment engine/domain dependency. iOS Swift host and native extensions also exist. No desktop target is configured.
- Theme: `design/StructTheme.kt`, generated semantic branding colors, Material typography and reusable investment components. Additional token/neutral-surface work is pending separately.
- Navigation: `InvestmentApp`, remembered/saveable route state, existing Back and unsaved-work policy, widget/import external entry points. There is no new navigation framework or DI system in this change.
- Presentation consumes `PortfolioModel`; financial calculations and imported record semantics stay with existing core/shared owners. Existing package/source-set checks detect common-source platform imports and module dependency violations.

## Screen and flow inventory

Paths below are relative to `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/`. Native platform hosts/extensions are additional surfaces, not new KMP targets.

| Implemented surface / owners | Coverage and remaining needs |
|---|---|
| First launch, path intro, onboarding — `InvestmentApp`, `BrandIntroScreen`, `OnboardingScreen` | Existing theme/French/skip/completion/persistence journey. Cold startup, interruption and actual reduced-motion settings need physical-device review. |
| Account sign-in/registration — `SignInScreen` | New controlled loading/error/retry and cancelled guest-choice journey. Invitation validation exists; full identity-provider, password-manager/autofill, IME and real expired-session checks remain manual. |
| Device unlock — `UnlockScreen`, native secure-session adapters | Existing state/session tests; physical biometric latency, denial, background/resume and session-expiry transitions not measured here. |
| Guest choice, local/sample workspace, migration — `GuestScreens`, `GuestPortfolioRepository`, `GuestMigrationScreen` | Existing offline navigation/deletion and selected migration journeys. Process/reboot persistence test requires explicit opt-in. Sample and personal data remain distinct. |
| Portfolio dashboard and summaries — `PortfolioDashboard`, `PortfolioInsights`, `InvestmentChart` | Existing guest/synthetic flow tests. No full-dashboard golden matrix or physical frame trace yet. Pending transparency/token work is not counted as committed coverage. |
| Bond overview, pending entry, purchase editing and review — `InvestmentDetails`, `PurchaseForms`, `PurchaseImportScreens`, `ApplicationBidScreen` | Existing both-theme detail rendering, bid/editor and import regressions. Native pickers, keyboard, all validation errors and large-text payment tables need visual fixtures. |
| Company/share overview, purchase detail and pending order — `ShareDetailsScreen`, `ShareOrderScreen` | Existing rendering/order/navigation tests plus new light/dark six-viewport share-detail goldens, lower-content reachability and explanation dialog interaction. Individual purchase navigation remains covered by existing journeys; its pixels are not separately golden-tested. |
| PDF acquisition, preparation, progress, review, duplicate/conflict and completion — `PdfImportPreparationScreen`, `PdfImportProgressDialog`, `ConversationReview`, `PortfolioImports`, `ImportCompletionReceipt` | Existing synthetic review/retry/library and native cold/warm external-sharing lifecycle methods retained intact. User-supplied real-PDF regression is opt-in, not a default compatibility guarantee. |
| Document library, previews, missing documents and statements — `DocumentsScreen`, `DocumentsHub`, `DocumentDetails`, `PdfViewerScreen`, `StatementScreens` | Existing import-to-library/detail routes. Native preview windows, permissions, statement modals and error/empty visual baselines remain incomplete. |
| Investment history, cash transactions and coupons — `InvestmentHistoryScreen`, `CashLedgerScreen`, `YearlyCouponsScreen` | Existing cash review/correction and history tests. No full large-text/screenshot matrix for financial tables; transaction-form Back/draft cases remain follow-up. |
| Calendar, reminders, notification preferences/inbox and sign-in activity — `CalendarScreen`, `NotificationPreferences`, `Notifications`, `SignInActivityScreen` | Existing target editor save/cancel and route tests. Real delivery/permission denial, system cards, widgets, lock screen and timezone/orientation interactions need separate device scenarios. |
| Markets — baseline `LibraryScreens.StocksScreen` | Existing quote/domain tests and shared rendering; no complete loading/empty/offline/error screenshot matrix. Working-tree split to `StocksScreen.kt` is unrelated pending work. |
| Settings, target, backup, reporting and support — `SettingsScreen`, `InvestmentGoalSettings`, `EncryptedBackupScreen`, `PortfolioReportDialog`, `IssueReportScreen` | Existing destructive restore recovery, cancellation, diagnostics fallback and reports. Native export/picker cancellation and all modal variants remain incomplete visually. |
| Simulator, investment tools, education, about and legal — `PlanningScreens`, `InvestmentToolsScreen`, `AboutScreen`, `LegalScreen` | Existing local/domain/navigation coverage. No assertion of legal-content completeness; real links, large text, hardware keyboard and iPad require review. |
| Android/iOS widgets, share/action extension and live status | Existing platform-specific tests/builds elsewhere. No native-extension screenshot, VoiceOver or physical notification/widget session was performed in this phase. |

## Findings and remaining defects

Evidence identifies a confirmed failure separately from source-level risks. Do not interpret every source observation as a reproduced user-visible bug.

| Priority | Evidence / impact | Resolution or next action |
|---|---|---|
| High — intermittent native rendering crash | Existing full-process Android runs recorded `PdfSharingTest.pdfTargetsReceiveColdAndWarmIntentsAndExportsGrantOnlyReadAccess` dying on RenderThread in `RefBase::incStrongRequireStrong`, `libEGL_emulation.so`, `EglManager::destroySurface`. Affects reliable verification; production-device impact is unproven. | Per-method isolation prevents one crash from truncating all following cases. Keep the full lifecycle method and report failures. Compare native driver/emulator and physical-device traces before changing `ExperimentalCaptureLayer`/capture protection. A passing isolated suite does not resolve the original root cause. |
| High — capture renderer ownership and teardown | `ExperimentalCaptureBackdrop.detach` invoked its renderer while still advertising it as available, then erased any replacement installed by that callback. `ExperimentalCaptureLayer` globally detached on old Activity/surface destruction and could attach from a late transaction callback. | Corrected with attachment-specific release, retire-before-callback ordering, surface generation/disposal guards and stopped-Activity drawing rejection. The new teardown contract failed against the original implementation before the fix. This source defect is concrete; its relationship to the vendor EGL crash remains an inference, not a proven root cause. |
| Medium — absent reviewed pixel comparisons | Existing instrumentation saved screenshots but did not compare reviewed reference pixels. Visual regressions could escape unit/semantics checks. | Corrected for one critical share-detail surface: 12 references, strict comparator, integrity check and changed-digit regression. Extend to portfolio, bond, import states and native modals rather than pretending this covers the whole app. |
| Medium — execution evidence could be confused with inventory | Source presence alone does not prove a journey ran. Optional assumptions may skip required evidence. | New checker validates JUnit results when requested; required methods cannot pass by being absent/skipped. Gradle failures still block release/commit. Host CI explicitly excludes device execution claims. |
| Medium — UI route networking remains coupled | Baseline `InvestmentApp` lines 494/498/550 resolves purchase/statement routes through `model.api.request`. Root file 665 lines. Pending root extraction reduces size but does not constitute this patch's architecture fix. | Move route resolution behind model intent while preserving error/Back behavior, with external-entry regression tests. Do not replace navigation as a side effect. |
| Medium — layout/large-text evidence incomplete | `InvestmentChart` uses 184dp plot height, 54dp axis column and 42dp caption strip. `YearlyCouponsScreen` and legacy form/details use dense rows. Source constraints alone do not prove clipping. | Add deterministic chart/calendar/coupon/editor fixtures at 2× text and narrow/short windows; fix reproduced clipping with actual pixel/semantics evidence. Current goldens are not chart accessibility tests. |
| Medium — native visual divergence | Swift widget/share/live-activity colors/fonts are separate from shared theme; native modal windows are outside activity PixelCopy. | Review native extension-specific tokens and native screenshot/accessibility fixtures separately. Preserve platform presentation and branding. |
| Medium — incomplete performance attribution | No release Macrobenchmark, startup/biometric trace or allocation/retained-heap baseline exists. Compiler diagnostics cannot establish runtime jank. | New frame diagnostic is a baseline, not a claimed optimization. Profile an actual slow interaction on physical Android/iOS before caching or changing state ownership. |
| Medium — CI does not execute full device matrix | Hosted host workflow compiles tests and checks integrity only; reference font/raster/SDK image is specific. | Add a controlled API36 ARM device runner with `connected...` and `check-kmp-ui.py --results`; retain PNG/XML/crash evidence. Add iOS UI automation before calling iOS visually regression-protected. |
| Medium — pending quality work not verified as final product | Theme, chart semantics and financial-transparency improvements exist as unrelated uncommitted changes. | Review/verify them independently. This report does not count them as shipped fixes or approve new goldens automatically. |
| Low — adaptive tablet design remains basic | Reviewed 800×1000 share view fills available width with large inter-column spacing. No reproduced loss of content in this fixture. | Product/design decision for max content width or list/detail navigation, backed by native tablet testing; avoid an incidental redesign in a test-only patch. |

Largest committed handwritten composable-containing files: `InvestmentApp.kt` 665, `InvestmentChart.kt` 483, `GuestPortfolioRepository.kt` 471 (includes UI plus data responsibilities), `CalendarScreen.kt` 469, `PortfolioDashboard.kt` 450, `SettingsScreen.kt` 448, `SignInScreen.kt` 437. The working tree already splits several of these (root 514, chart 331, settings 301, sign-in 360 lines). This is **not a new decomposition in this phase**. `FrenchStrings.kt` is a declarative translation map, not a God Class because of line count. No arbitrary size rule was added.

Lazy cash entries, documents, share purchases and coupon rows already have stable identity keys. Small static choice/category lists without keys are not treated as demonstrated scrolling bottlenecks. Share overview performs valuations/formatting during composition, but this phase has no attributable trace proving those operations cause jank. No speculative remember/cache or altered financial state was introduced.

## Accessibility and visual consistency status

- Screens use the existing Material theme and financial components; new tests reuse those owners. No alternate design system, arbitrary colors or button style was added.
- The new golden matrix was visually inspected: light/dark, 320×568, 360×720, 412×892, 800×1000, 740×360, 360×720 at fontScale 2. Lower purchase content is asserted reachable by scrolling; the share-value dialog opens and dismisses.
- Goldens preserve currency, signed return, source date and purchase history. This is a presentation regression fixture, not an independent certification of investment formulas.
- Accessibility-tree interactions verify accessible action names, a disabled loading action and reachable dialog controls. They do not reproduce a human TalkBack/VoiceOver reading session or validate every touch target/contrast ratio.
- Existing pending accessibility review identifies chart summaries, contrast, form boundaries and target/layout work. It remains subject to its own verification. No WCAG claim is made from source checks or a bound service.
- Manual requirements: TalkBack/VoiceOver reading order and privacy masking, hardware keyboard traversal/focus, reduced motion, system font/zoom, RTL/localization, native dialogs/pickers, notification permissions, safe areas and IME. Do not globally disable font scaling to make a screenshot pass.

## Practical device matrix

| Scenario | What was actually checked | Remaining manual/platform check |
|---|---|---|
| Small Android (320×568) | Share-detail Compose viewport, both-theme pixels and lower-content reachability | Physical small device, system bars and keyboard |
| Typical Android (360×720) | Same deterministic viewports; real emulator app journeys separately | Physical Honor/Samsung, OEM font/permission/background behavior |
| Large Android (412×892) | Same deterministic viewports | Physical large phone/foldable posture |
| Tablet (800×1000) | Same deterministic viewport | Native tablet density, navigation, split-screen and keyboard |
| Landscape (740×360) | Same deterministic viewport and scrolling | Real orientation/configuration change and state restoration |
| Large text (2×, 360×720) | Share header wraps, lower content reachable in both themes | System-wide settings, all other screens, native controls |
| Dark mode | Six reviewed dark share-detail references and existing theme journeys | Native extensions, system dialogs and dynamic theme changes |
| iOS configured targets | ARM device/simulator compilation and native shared/core unit tests | No iOS UI screenshot suite or manual VoiceOver, keyboard, iPad/safe-area verification in this phase |

All six visual profiles use one disposable API36.1 ARM emulator. Compose Density(1f) constrains viewports independently of its physical screen; **six viewports are not six separately tested devices**. Golden manifest pins SDK/font/raster assumptions. No user phone or real account was operated for this phase.

## Compliance and trust items requiring review

No legal text, regulatory policy, consent interface, operator identity or licensing statement was published or changed. The pending financial/privacy audit identifies operator/jurisdiction, data-controller/transfer/SDK disclosures, retention/deletion and tracker-versus-regulated-service classification as requiring factual/professional confirmation. Technical test success cannot settle those questions. Account deletion must follow actual confirmed server behavior; new fixtures never delete real accounts. This is not a legal compliance assessment.

## Performance evidence

Compiler reports were generated for Android and native compilations. Android main recorded **1,692 total**, **1,667 restartable**, **1,184 skippable** composables. These include compiler output for the existing UI and do not count observed recompositions, frame jank or before/after improvements. Do not optimize toward a skipping percentage.

Final frame diagnostic: **328 frames**, **60Hz** reference display, total-frame **p50 17.482391ms**, **p95 31.337324ms**, **max 35.502581ms**, **zero dropped metric reports**. Reference display: 1080×2400 / 420dpi. This is one debug/emulator diagnostic with accessibility-driven scrolling, not a release benchmark: JIT, automation and graphics-emulation overhead are included; startup, biometric latency, memory and platform capture are excluded. Diagnostic timings varied across runs. The p95 exceeds a nominal 60Hz frame budget here, but there is no attributable release-device trace supporting a production-code optimization. Use dedicated hardware, warm-up and release profiling before drawing performance conclusions. Results are retained in the final evidence JSON. No production performance improvement is claimed. Existing `UnlockPerformance` stage instrumentation and the prior domain ledger benchmark are useful separate tools, not new UI latency measurements in this audit.

## Reproducing the checks

Use JDK21, installed Android SDK and the documented API36 ARM reference image. Use an explicit disposable emulator serial; do not run mutating tests on a real user's phone. No test uses production credentials. `UiGoldenTest` runs on API36; other SDKs skip it, and the required-evidence checker rejects that skip for the reference gate.

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 scripts/check-kmp-ui.py
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
./gradlew -PcomposeReports=true :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest \
  :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest \
  :app:androidApp:lintDebug :app:androidApp:lintRelease \
  :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 :app:shared:compileTestKotlinIosArm64
./gradlew :core:iosSimulatorArm64Test :app:shared:iosSimulatorArm64Test \
  -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4
./gradlew :app:shared:connectedAndroidDeviceTest :app:androidApp:connectedDebugAndroidTest
python3 scripts/check-kmp-ui.py --results app/androidApp/build/outputs/androidTest-results/connected/debug
```

Frame diagnostic (after installing the matching debug/test APKs):

```sh
adb -s emulator-5554 shell am instrument -w -r \
  -e class dev.orestegabo.portfolio.investments.UiFrameBenchmarkTest -e uiFrameBenchmark true \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 exec-out run-as dev.orestegabo.portfolio cat files/ui-frame-baseline.json
```

**Authoring is separate from verification.** To propose references, run only `UiGoldenTest` with `-e uiGoldenMode record` on the disposable reference emulator. Retrieve `files/ui-quality-goldens/*.png` via `run-as`, visually inspect every image, review the source diff, then intentionally update PNGs and manifest hashes. Recompile assets and run comparison mode (without record). A successful recording is not a passed visual comparison. Do not overwrite failing actuals without reviewing differences. Actuals are not automatically uploaded; fixtures are synthetic but future real-data captures must not be published.

## Final verification record

Initial failed runs and corrective work are retained below. The final complete gate after the capture correction passed; see the final record and machine-readable evidence. No unexecuted checks are considered passed.

### First complete device run (failed; retained)

`/tmp/inzira-ui-final-connected.log`: **BUILD FAILED**, 11m12s. App JUnit XML: **48 tests, one failure, zero errors, five optional skips**. `NavigationUsabilityTest` timed out activating “About Inzira” after scrolling Settings. All other executed methods, including `PdfSharingTest` and the new comparison-mode goldens, passed. Shared Android device XML: **527 tests, zero failures/errors/skips**. Reports retained in `/tmp/inzira-ui-final-first-results`. The new evidence checker correctly returned failure for the navigation method and the report's failure; this run is not treated as successful.

The fifth optional skip is the new opt-in frame diagnostic, executed explicitly in a separate run. The original four skips are real Firebase delivery, an explicitly supplied private PDF, guest process/reboot persistence and a startup measurement fixture. These scenarios are not default-suite guarantees.

### Scroll investigation (failed intermediate check; retained)

The first attempt replaced the fixed delay with accessibility-queue idleness alone. The six-method focused run then failed at “About share values” after all pixel comparisons and lower-content checks; the other five methods passed, including the explicit frame diagnostic. A failure-only synthetic capture showed the list still at the bottom. A temporary large-text-only diagnostic reproduced it and logged multiple accepted/rejected actions milliseconds apart: queue idleness can precede delivery of the first scroll event. A strict scroll-event-only prototype also failed: Compose sometimes emitted only `TYPE_WINDOW_CONTENT_CHANGED`, or no event at a boundary. The final driver waits for either window-scoped notification, handles the bounded optional-event timeout, re-queries actual targets, and waits for measured scrollable content before starting. It does not convert a missing target into success. Temporary logging, diagnostic-only viewport filtering and failure capture code were removed; every original profile and interaction assertion remains. Diagnostic logs: `/tmp/inzira-ui-final-settled-focused.log`, `/tmp/inzira-ui-final-large-text-diagnostic.log`. These failed runs are not passed verification or visual approval.

### Corrected-driver focused verification

`/tmp/inzira-ui-final-content-event-focused.log`: **OK (4 tests)**, 44.181s. Final source, all profiles restored: navigation with its original save/error/widget/restoration assertions; strict golden comparator; 12 share-detail pixel comparisons, lower-content and reverse-scroll/info interaction; explicitly enabled expanded-history frame diagnostic. No temporary diagnostic code or viewport filtering remains. The full app suite is rerun because the shared test driver changed. The unchanged shared/core host/native and 527-device suites do not require repetition merely to obtain another green result.

### Capture-lifecycle investigation and correction

The corrected-scroll full run also failed: `/tmp/inzira-ui-final-connected-corrected.log`, **8m07s**, app XML **48 tests, one native failure, zero errors, four skips**. Navigation and all other executed methods passed; `PdfSharingTest` again crashed on RenderThread at `RefBase::incStrongRequireStrong` / `libEGL_emulation.so` / `EglManager::destroySurface`. Isolation let later methods run; it did **not** turn this failure green. Reports were retained in `/tmp/inzira-ui-final-native-failure-results`.

Source inspection found independent, actionable lifetime defects in the renderer bridge. The first new `CaptureAttachmentTest` failed against the original implementation (`/tmp/inzira-ui-capture-before.log`, **1 test / 1 failure**, available state still true inside release). The fix scopes releases to a registration token, clears old state before invoking a release callback, stops hardware-canvas access before surface retirement, rejects stale transaction callbacks and rejects drawing while the Activity is stopped/destroyed. This respects Android's surface lifetime/threading contract; it does not remove the capture surface, change graphics backend, weaken exclusion, merge privacy preferences or suppress the native test. [Android SurfaceHolder lifecycle](https://developer.android.com/reference/android/view/SurfaceHolder.Callback), [SurfaceView threading](https://developer.android.com/reference/android/view/SurfaceView) (accessed 2026-11-15).

The new contracts cover state retirement before callback, old/idempotent release handles (including reused renderer functions), and a replacement registered reentrantly by the old callback. The existing real screenshot-mask, secure-window/background-resume and cold/warm/recreation PDF tests are the integration gate. Native crash elimination is not claimed from the source change alone.

### Capture fix focused verification

`/tmp/inzira-ui-capture-fixed-focused.log`: **OK (6 tests)**, 26.89s. All three ownership/reentrancy contracts plus the existing `CaptureProtectionTest`, `ComposeCaptureLayerTest` and `PdfSharingTest`. This includes actual screenshot masking/override, secure-window background/resume, independent number-visibility behavior, native PDF cold/warm/recreation handling and read-only URI grants. No privacy assertion, screenshot tolerance, graphics backend or lifecycle scenario was weakened. The final combined KMP gate reruns the changed Android implementation before any commit/push.

## Final successful gate — after the lifecycle correction

**`/tmp/inzira-ui-final-capture-gate.log`: BUILD SUCCESSFUL, 9m36s.** The command used JDK21 and an isolated export at `/tmp/inzira-ui-final-export`; the only connected Android device was the disposable `emulator-5554`. Source/test inputs matched the reviewed patch byte-for-byte. The actual repository index remained empty during verification.

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew --project-dir /tmp/inzira-ui-final-export \
  :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest \
  :app:shared:connectedAndroidDeviceTest :app:androidApp:connectedDebugAndroidTest \
  :app:androidApp:lintDebug :app:androidApp:lintRelease \
  :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 \
  :app:shared:compileTestKotlinIosArm64 :core:iosSimulatorArm64Test :app:shared:iosSimulatorArm64Test \
  -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
  -Pandroid.testInstrumentationRunnerArguments.uiFrameBenchmark=true -PcomposeReports=true --console=plain
python3 /tmp/inzira-ui-final-export/scripts/check-kmp-ui.py \
  --results /tmp/inzira-ui-final-export/app/androidApp/build/outputs/androidTest-results/connected/debug
```

| Suite | JUnit tests | Failures/errors | Skipped |
|---|---:|---:|---:|
| Core JVM | 145 | 0 | 0 |
| Core Android host | 145 | 0 | 0 |
| Shared Android host | 533 | 0 | 0 |
| Core iOS simulator | 144 | 0 | 0 |
| Shared iOS simulator | 559 | 0 | 0 |
| Shared Android device | 527 | 0 | 0 |
| Android application instrumentation | 51 | 0 | 4 |

The final command reused valid up-to-date host/native outputs where inputs were unchanged. Native tests had actually executed earlier in `/tmp/inzira-ui-final-ios-tests.log` (**BUILD SUCCESSFUL, 1m16s**); the Android-only lifecycle correction does not change common/iOS inputs. Both iOS ARM targets and ARM device test sources compiled. Android debug/test APK compilation passed. The app JUnit XML is authoritative: terminal progress counts some assumption events twice. The frame diagnostic ran explicitly and passed; it is not one of the final four skipped methods.

The four skips were `FirebaseDeliverySetupTest.receivesARealFirebaseMessageWithoutAnAccountSession`, `BondPdfRegressionTest.suppliedConfirmationReachesValidatedReviewWithoutSaving`, `GuestPersistenceTest.guestInvestmentsSurviveProcessRestartAndReboot`, and `GuestStartupFixtureTest.prepareEmptyOfflineGuestForProcessLaunchMeasurements`. Real delivery, a private supplied PDF, reboot persistence and actual startup profiling are not claimed tested.

The required-journey/PNG integrity checker returned **“required journeys passed”** against the final XML, including the unchanged native PDF lifecycle, capture privacy and all three new attachment contracts. All 12 comparison-mode references passed. Recording mode was used only to author candidates, never to satisfy this gate.

Static checks: `python3 scripts/check-kmp-packages.py` passed **420** source/test paths and source-set/module rules; `python3 scripts/sync-design-colors.py --check` passed; `python3 scripts/check-kmp-ui.py` passed; `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` passed **27** isolated-export tests, including **8** UI guardrail tests; `node --test tests/*.test.mjs` passed **84**; workflow YAML parsed; `git diff --check` passed. Byte compilation passed with `PYTHONPYCACHEPREFIX=/tmp/inzira-ui-pycache python3 -m py_compile scripts/check-kmp-ui.py scripts/tests/test_kmp_ui.py` (the first attempt without that prefix was denied by the sandbox's external cache permissions).

Android lint passed with **zero errors**, **92 debug warnings / 94 release warnings**. These existing warnings were not suppressed or converted into a success claim of zero warnings. No ktlint, detekt, release Macrobenchmark, coverage percentage or iOS UI/screenshot task was configured to execute. Hosted GitHub Actions has not been executed locally; YAML validation is not execution.

Existing Swift fixture commands also passed:

```sh
xcrun swiftc app/iosApp/NextWidgetShared/NextWidgetData.swift scripts/tests/next-widget-data.swift -o /tmp/inzira-ui-final-widget-check
/tmp/inzira-ui-final-widget-check
xcrun swiftc app/iosApp/SharedImport/SharedPdfInbox.swift scripts/tests/shared-pdf-inbox.swift -o /tmp/inzira-ui-final-inbox-check
/tmp/inzira-ui-final-inbox-check
xcrun swiftc app/iosApp/iosApp/CalendarEventDay.swift scripts/tests/calendar-event-day.swift -o /tmp/inzira-ui-final-calendar-check
/tmp/inzira-ui-final-calendar-check
```

Working-tree integration (separate from the scoped commit): Android host **551 tests, zero failures**, Android/test compilation and debug lint passed with the pending refactors/UI changes, plus both iOS compilation targets. Logs: `/tmp/inzira-ui-final-working-integration.log`, `/tmp/inzira-ui-final-working-capture-integration.log`. Its Python suite passed **31** tests, including unrelated pending checker tests. This is not approval of its changed screenshot baselines or a full device verification of those uncommitted themes.

[Machine-readable final evidence](evidence/2026-11-15/ui-final-verification.json) preserves counts, skips, failed earlier gates and final frame observations. The original vendor EGL root cause remains unconfirmed despite the passing final lifecycle suite; keep the full recreation/share test and compare native driver/physical-device traces if it recurs. No test was disabled, no security or pixel assertion was weakened, and no repeated unchanged full-suite run was used merely to obtain green output.

Recommended next steps: separately review the pending UI/accessibility/transparency changes and their golden differences; add dedicated reference-device CI execution and portfolio/bond/import/error/modal goldens; perform physical Android/iOS accessibility/IME/safe-area and native-extension checks; profile release startup/biometrics and scrolling on dedicated hardware. No backend or financial behavior changes are included in this patch.

Additional release check: `./gradlew --project-dir /tmp/inzira-ui-final-export :app:androidApp:compileReleaseKotlin --console=plain` returned **BUILD SUCCESSFUL, 1s** (`/tmp/inzira-ui-final-release-compile.log`); the release compilation output was valid/up-to-date. This is compilation, not a signed release, distribution or physical-device verification.

## Subsequent integration of the pending work — 15 November 2026

The earlier scoped results and working-tree caveat above remain historical evidence. The [pending-change review](KMP_PENDING_CHANGES_REVIEW.md) now covers the previously excluded responsibility, design/accessibility and financial-presentation changes. It includes separate visually reviewed reference sets for the UI-only and financial variants, comparison-mode tests, final combined verification and the remaining device/platform limitations. Consult that review for the current integration disposition.
