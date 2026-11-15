# Final KMP maintainability review — 2026-11-15

> Historical scoped audit. Its original exclusions, failures and Git disposition below describe the earlier isolated attempt. The 15 November combined integration and current disposition are recorded in [KMP_PENDING_CHANGES_REVIEW.md](KMP_PENDING_CHANGES_REVIEW.md); that later review supersedes the status here without reclassifying a failed historical run as passed.

**Final verification status: blocked by a failing full Android app instrumentation suite. No commit or push is permitted by the requested verification gate.**

Reviewed baseline `f7ef53f`, following the [original audit](KMP_CODEBASE_AUDIT.md) and the focused quality reports in this directory. Scope: KMP mobile client and its native hosts; no backend/website change or rewrite.

## Assessment and evidence boundaries

The refactors produced useful, tested boundaries without replacing the architecture. Financial functions, data/session capabilities, parser admission, lifecycle ownership and feature-state publication are clearer. The native decimal contract failure from the original audit is corrected. This is **not** a claim that every God Class or original High finding has been eliminated.

There are still 39 pre-existing uncommitted paths. The original audit described that working tree, while many later commits deliberately excluded those extractions. This review distinguishes committed baseline sizes/owners from local ones; the verification export contains only committed code plus this guardrail patch. It does not certify the unfinished refactor as integrated. No unrelated changes are staged.

Line counts include blank lines/comments and are not complexity scores. No coverage percentage, runtime performance gain, physical-device privacy certification or absence-of-all-vulnerabilities claim is made. Historical audit findings are retained as history, with updated status below.

## Original versus current findings

Paths are relative to the repository. `shared/` below means `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/`, `core/` means `core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/`.

| ID / original severity | Current status | File-level evidence / remaining action |
| --- | --- | --- |
| F01 High — native monetary contract | Resolved for the demonstrated failure | `core/src/iosMain/.../DecimalAmount.ios.kt` converts rounded decimal text through Kotlin rather than Foundation doubleValue; `MonetaryPlatformContractTest`, financial/reference/grouping/ledger tests run on Native. This does not replace every Double boundary with exact money. |
| F02 High — native/device CI gap | Partially addressed; High remains | `.github/workflows/mobile-release-checks.yml` now also runs core Android-host tests. Native/device suites are executed locally, but automated PR/push CI still lacks simulator and connected-device lanes. The manual iOS job builds packaging rather than running native tests. Add bounded automatic platform lanes next. |
| F03 High — external parser build toolchain | Open, High | `scripts/bundle-shared-logic.mjs` still resolves TypeScript through an external package directory; `sync-web-logic.mjs` defaults to the developer website checkout and copies assets/sources. Normal app runtime is independent of that checkout. Pin a mobile-owned toolchain and verify regenerated artifacts before expanding formats. |
| F04 High — broad session ViewModel | Partial, High | Committed `shared/PortfolioModel.kt` is 1,247 lines. Ledger/orders/statements now have separate internal owners in `features/*`; session, guest, migration, navigation-related coordination, refresh and compatibility entry points still coexist. Characterize another cohesive lifetime before extracting it. |
| F05 High — implicit navigation state | Open, High | Committed `shared/InvestmentApp.kt` is 657 lines; `InvestmentApp` coordinates independent remembered booleans, nullable targets and native-event precedence. `SessionState` models session variants, not the complete route state machine. Require a route/return/account-switch matrix before a navigation slice. |
| F06 High — mutable JSON domain/state contract | Partial, High | `core/JsonRecord.kt` provides detached read-only publication and feature workflows use it. `PortfolioSnapshot`, imports and persisted/API records still use JSON/primitive money and can be constructed outside validated adapters. Add typed admission/projection values incrementally without changing wire/schema contracts. |
| F07 High — private capture APIs | Retained platform risk, High | `app/androidApp/.../ExperimentalCaptureLayer.kt`, `app/shared/src/iosMain/.../CaptureProtection.ios.kt` use platform-specific capture machinery. Existing opt-in/fallback/privacy behavior and tests remain. OEM/OS changes require physical validation; do not weaken the secure fallback to simplify implementation. |
| F08 Medium — UI admission/payload rules | Partial | Shared `PendingPurchases.kt` admission/review policies isolate several admissions, but `shared/PurchaseForms.kt` still validates and builds commands inside composables. Extract only characterized pure form admission next; preserve editor behavior and backend bodies. |
| F09 Medium — broad package boundaries | Partial; guardrails improved | `core/.../network` and three `features/*` packages have explicit direction checks. This patch also rejects acyclic forbidden module edges and core UI-framework imports. The legacy `investments` package still mixes presentation/application/adapters; `DocumentThumbnail.kt` explicitly owns a preview-loading effect including transport/cache/native decoding. Treat it as an app UI adapter, not a pure display function or a reason to put Compose in core. This is not strict Clean Architecture. |
| F10 Medium — numeric policies differ | Open, Medium with financial regression risk | `web-logic/source/calculations.ts` uses JS Number; `core` monetary helpers use platform decimal rounding; forms/reports still contain binary sums. [Precision review](KMP_FINANCIAL_PRECISION_AUDIT.md) documents boundaries. No new convention/rounding policy is invented here. |
| F11 Medium — stale business date | Open | `shared/PurchaseForms.kt` top-level field defaults capture `Today`; screens do not all observe date rollover. Use a characterized business-date source and lifecycle/midnight policy before changing defaults. |
| F12 Medium — process/native ownership | Partial | Mailbox composition/byte ownership and native cleanup have tests; `app/shared/src/iosMain/.../IosPdfReceiver.kt` retains a process-owned scope and native bridges remain global. Distinguish app lifetime from screen lifetime; test replacement/detachment instead of mechanically deleting globals. |
| F13 Medium — synchronous OCR cancellation | Retained limitation | `IosPdfTextExtractor.kt` and Android local engine enforce limits and common cancellation checks. Synchronous native OCR cannot be immediately preempted by coroutine cancellation. Do not claim timeout implies native computation stopped. |
| F14 Medium — report read/dispatcher policy | Open, confirmed | `shared/PortfolioReports.kt::portfolioPdf` catches all ordinary coupon-receipt failures as null, unlike its explicit session handling on other reads, and uses `Dispatchers.Default` despite an injected report dispatcher. Add unauthorized/cancellation/optional-read characterization before this small follow-up; no unrelated report behavior change is hidden here. |
| F15 Medium — duplicate-PDF lookup cost | Open | `shared/PortfolioImports.kt` narrows candidates then may download/hash several full documents. Exact bytes and linked financial records still matter; the separate share-ledger speedup does not solve this network/library-growth risk. Measure before adding a digest cache or changing duplicate contracts. |
| F16 Medium — implicit command acceptance | Open | `shared/PortfolioModel.kt::runAction` still returns Unit and declines busy/locked/guest-ineligible work. Guards protect writes, but callers do not have a typed accepted/rejected outcome. Add it only with incoming-event/retry contract tests. |
| F17 Medium — whole guest snapshot growth | Retained, bounded/tested | Committed `shared/GuestPortfolioRepository.kt` validates/rewrites the bounded JSON workspace and assembles backups; atomic write/recovery/failed-write/concurrent-sale tests protect durability. Do not replace this with a database solely to reduce file size. |
| F18 Medium — broad capabilities/defaults | Partial | `GuestPortfolioRepository` no longer silently supplies required ledger/backup fallbacks; narrow parser/data/picker contracts exist. `NativeActions` still aggregates optional host capabilities and `InvestmentEngine.run` uses an explicit command protocol. Composition-root aggregates are not automatically ISP failures. |
| F19 Medium — error identity/copy | Partial | API response/timeout causes and cancellation are protected; `shared/UserFacingErrors.kt` still allowlists phrases. Preserve safe user messages and do not log raw causes containing private data. Typed recoverable failures can grow at demonstrated seams. |
| F20 Low — quality signals | Improved, not fully closed | Existing package/color/script/lint gates now include module direction, core UI exclusion, four new rule tests, mobile editor defaults and all host test tasks in CI. No file-length budget or detekt/ktlint rollout. Release lint remains warning-bearing. |
| F21 Medium — report policy/layout together | Open | `shared/PortfolioReport.kt`, `AnnualReport.kt` and `ReportPdf.kt` have separated inputs/rendering but formatting/cutoff/presentation still change together. Closing-year sale cutoffs now have dedicated tests. Preserve intentionally different annual/current report policies. |
| F22 Medium — financial default policy | Open | `core/PendingCouponEstimate.kt` documents tenor fallback assumptions; projection defaults live in `web-logic/source/calculations.ts`. A common versioned policy was not introduced. Do not merge estimates with recorded contract terms. |
| F23 Medium — contradictory architecture notes | Partial | This review and [developer guide](KMP_DEVELOPER_GUIDE.md) state committed ownership explicitly. Existing `docs/ARCHITECTURE.md` is part of the unfinished local changes and must be reconciled when that refactor is integrated; it was not overwritten or quietly committed. |
| F24 Low — dependency ownership | Focused issue resolved; reproducibility debt remains | [Build audit](KMP_BUILD_DEPENDENCY_AUDIT.md): catalog ownership consolidated, unused aliases removed, 14 selected-version inventories unchanged. Locking/artifact verification and fully pinned parser/toolchain regeneration remain separate work. |

No Critical finding was established by this review. Remaining High items are real architectural/platform/CI risks, not evidence of currently corrupted investment records or an exploited vulnerability.

## Size and responsibility reassessment

| Handwritten file | Committed baseline lines | Local working tree lines | Assessment |
| --- | ---: | ---: | --- |
| `FrenchStrings.kt` | 2,006 | 2,006 | Translation data, not a God Class. No arbitrary split. |
| `PortfolioModel.kt` | 1,247 | 1,063 | Genuine broad lifetime/orchestration responsibilities; still the highest-risk decomposition candidate. |
| `InvestmentApp.kt` | 657 | 500 | Routing/event precedence and UI coordination; needs transition characterization. |
| `InvestmentChart.kt` | 483 | 302 | Rendering/projection interactions; the local extraction is not committed. No runtime recomposition claim. |
| `GuestPortfolioRepository.kt` | 471 | 43 | Committed aggregate includes the concrete repository; local interface/concrete separation is unfinished (`LocalGuestPortfolioRepository.kt`, 328 lines). |
| `CalendarScreen.kt` | 469 | 221 | Screen state/rendering; local component extraction excluded. |
| `PortfolioDashboard.kt` | 450 | 450 | Large declarative screen plus derived display state; separate work only when a coherent presentation boundary is clear. |
| `SettingsScreen.kt` | 448 | 301 | Multiple device preferences; local helper extraction excluded. |
| `SignInScreen.kt` | 437 | 360 | Auth presentation and feedback; preserve native/security workflow when extracting presentation. |
| `PortfolioImports.kt` | 434 | 411 | Important review/duplicate/retry orchestration; tests matter more than an arbitrary length threshold. |

The original audit's 1,118-line model and 500-line app were **working-tree** counts, not committed-baseline counts. They must not be compared directly with today's committed counts to claim growth/regression. Original committed model size in the first decomposition report was 1,302; today's 1,247 is a modest reduction, not elimination of the God Class. No cyclomatic/cognitive-complexity measurement is claimed from these counts.

## Regression and complexity assessment

- Useful composition remains: independent small feature state owners, data/session capability split, parser admission separated from acquisition and native extraction, typed coupon frequency, shared sale/valuation rules, explicit protected byte ownership. No new DI, event-reducer or inheritance framework was required.
- State generations/read-only publication add necessary lifecycle complexity: tests demonstrate that old reads previously republished after clear/account changes. Do not delete those guards merely to shorten code. Framework inheritance remains where required; implementation-only widget/mailbox inheritance was removed.
- Financial duplication was reduced at authoritative seams. Order estimates, recorded fees, report cutoff and scheduled/received income remain intentionally different; forcing them through one flag-heavy helper would be a regression.
- The test phase found and fixed original-request replay rejection caused by raw numeric JSON text versus normalized fields. The performance phase replaced repeated ledger scans with a per-call index without introducing stale financial caching. No production behavior change is made during this final review; one Android instrumentation permission-setup race is corrected below.
- Remaining complexity includes model getter/forwarding facades and broad host aggregates. Their concrete benefit is retained public contracts and single session ownership. Remove an abstraction only after tracing actual callers; avoid another repository-wide rewrite.

## Prepared guardrails and future CI (uncommitted)

[Developer guide](KMP_DEVELOPER_GUIDE.md) covers architecture, feature placement, safe financial changes, new PDF formats, lifecycle/ownership and a concise reviewer checklist.

- Package guard: current source-set/import/feature boundaries plus forbidden mobile module edges and core presentation-framework dependencies. It recognizes the literal Kotlin DSL dependency forms used here, not all possible dynamic Gradle behavior. Four new positive/negative regression tests protect the rule implementation.
- Editing: mobile-scoped `.editorconfig` defaults; no global backend/website formatting or mass source edits. Existing Kotlin official style remains. This is editor configuration, not an executed ktlint formatting pass.
- Static scope: generated build trees are outside the source walk. No complexity cap for generated vectors, translations or large declarative code. No arbitrary length limit or giant suppression baseline introduced.
- Proposed CI change: host release checks include `:core:testAndroidHostTest`. Existing rule/script checks automatically run the new tests. An orchestrator trial did not resolve the native crash and was removed; no additional test framework/dependency remains. Native/device CI remains recommended, not represented as deployed/passing cloud infrastructure.
- Next CI priority: automatic macOS native financial/parser tests and a bounded Android emulator lane, preserving full suites in scheduled/manual runs if resource use requires it. Pin runners/tools and retain platform XML/logs. Local command success is not proof a proposed GitHub runner lane has executed.

## Verification

All checks use isolated exports, the installed JDK/Android SDK, Xcode 26.3 and synthetic fixtures. `final_export` below is the staged-tree export; `ios_export` is a separate export with identical mobile production/native sources to avoid concurrent Gradle output writes.

```sh
export JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home
export ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk
./gradlew --project-dir "$final_export" :core:jvmTest :core:testAndroidHostTest \
  :app:shared:testAndroidHostTest :core:allMetadataJar :app:shared:allMetadataJar \
  :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest \
  :app:androidApp:bundleRelease :app:androidApp:lintRelease \
  :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 \
  :core:compileTestKotlinIosArm64 :app:shared:compileTestKotlinIosArm64 --console=plain
```

Passed: 145 core JVM + 145 core Android-host + 526 shared Android-host tests (816 executions, no failures/errors/skips), metadata, Android debug/test APKs and release AAB, both iOS main targets and iOS device test-source compilation. Android Lint passed with **94 warnings**, zero errors; it was not warning-free. Kotlin deprecation/redundant-nullability warnings remain. No signing/credential setting was changed.

```sh
xcrun simctl boot 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4
xcrun simctl bootstatus 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 -b
/Users/muhirwagabooreste/.local/bin/android emulator start Sequo_References_API_36_1 --headless
ANDROID_SERIAL=emulator-5554 ./gradlew --project-dir "$final_export" \
  :core:iosSimulatorArm64Test --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
  :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
  :app:shared:connectedAndroidDeviceTest -Pandroid.injected.device.serial=emulator-5554 --console=plain
```

Passed: 144 core + 552 shared iOS simulator tests (696), plus 520 shared Android emulator tests, no failures/errors/skips. These and the host suites total 2,032 test executions; they are not a coverage percentage. The optional iOS supplied-statement hook returns without exercising a private file when its environment input is absent; no arbitrary user-document compatibility is claimed.

### Complete Android app suite and test-setup correction

The first unfiltered app suite exposed an existing setup race: `BondReminderNotificationTest` launched `pm grant` and closed its output immediately, then `receive` synchronously checked notification eligibility. A focused original-test rerun also failed with “Opening card was not posted.” The platform log recommended UiAutomation's permission API. The test now grants synchronously on API 33+ and asserts permission before exercising the same production behavior/assertions. The corrected focused run passed. No application notification/security code was altered.

The initial full run additionally terminated during `PdfSharingTest` with SIGSEGV on RenderThread; its native stack was in emulator `libEGL_emulation.so` surface destruction and `libhwui`. That points to the emulator graphics path rather than establishing a Kotlin defect. The same shared-process crash reproduced with a cold software renderer, while the exact `PdfSharingTest` passed alone in a fresh process. This establishes suite interaction, not a proven root cause or a production capture fix. No app hardware-acceleration flag or test exclusion was added. Android CLI does not expose GPU selection, so the SDK emulator was used:

```sh
# Stop only the disposable emulator, then launch it without loading/saving snapshots:
"$ANDROID_HOME/platform-tools/adb" -s emulator-5554 emu kill
"$ANDROID_HOME/emulator/emulator" -avd Sequo_References_API_36_1 -no-window \
  -gpu swiftshader -no-snapshot-load -no-snapshot-save -no-audio
# Wait for sys.boot_completed=1, then run the complete suite with no class selector:
ANDROID_SERIAL=emulator-5554 ./gradlew --project-dir "$final_export" \
  :app:androidApp:connectedDebugAndroidTest --rerun \
  -Pandroid.injected.device.serial=emulator-5554 --console=plain
```

**Failed.** The default full app run found the notification setup failure and a native process crash during PDF sharing. After the verified permission fix, the complete suite still failed at `PdfSharingTest::pdfTargetsReceiveColdAndWarmIntentsAndExportsGrantOnlyReadAccess`. The software-rendered run failed too. The exact PDF-sharing test passed alone in a fresh process, so passing the isolated case is not sufficient to accept the complete suite.

A temporary AndroidX Test Orchestrator 1.6.1 trial provided separate instrumentation processes (confirmed by distinct process IDs) and continued reporting after the crash, but still failed the same test. The unsuccessful build/catalog changes were removed; no new dependency remains. [Android's isolation guidance](https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner) describes what orchestration guarantees; it is not a fix for a native crash.

The four existing assumption-gated cases were not exercised: live Firebase delivery (`firebaseSetupCheck`), a supplied private bond PDF (`privateBondPdf`), staged guest persistence/reboot (`guestPersistenceStage`) and startup measurement preparation (`isolatedGuestBenchmark`). No test was newly skipped/excluded. The crashing default run did not complete every app test, so its partial XML count must not be treated as a passing full-suite count.

This unresolved rendering/lifecycle failure is a **High-priority verification blocker** relevant to F07: inspect `PdfSharingTest.kt`, `MainActivity.kt`, `ExperimentalCaptureLayer.kt` and `CaptureBackdrop.android.kt` together with the native trace. The observed sequence remains relevant to F07. A passing isolated test does not prove that all long-lived activity/native capture sequences on physical OEM devices are safe; reproduce/stress those separately before changing capture internals. No speculative production surface fix was shipped.

### Full native host and Swift checks

```sh
xcodebuild -project "$ios_export/app/iosApp/iosApp.xcodeproj" -scheme iosApp \
  -configuration Release -destination 'generic/platform=iOS' \
  -derivedDataPath /tmp/inzira-final-ios-derived CODE_SIGNING_ALLOWED=NO build
python3 "$ios_export/scripts/check-ios-release.py" \
  /tmp/inzira-final-ios-derived/Build/Products/Release-iphoneos/Portfolio.app
```

Both passed: complete unsigned iOS device Release application/extensions and release package checks. Distribution signing, installation and physical-phone behavior were not verified.

```sh
xcrun swiftc "$final_export/app/iosApp/iosApp/CalendarEventDay.swift" \
  "$final_export/scripts/tests/calendar-event-day.swift" -o /tmp/inzira-final-calendar-check
/tmp/inzira-final-calendar-check
xcrun swiftc "$final_export/app/iosApp/NextWidgetShared/NextWidgetData.swift" \
  "$final_export/scripts/tests/next-widget-data.swift" -o /tmp/inzira-final-widget-check
/tmp/inzira-final-widget-check
xcrun swiftc "$final_export/app/iosApp/SharedImport/SharedPdfInbox.swift" \
  "$final_export/scripts/tests/shared-pdf-inbox.swift" -o /tmp/inzira-final-inbox-check
/tmp/inzira-final-inbox-check
```

All three existing standalone harnesses passed: civil dates/time zones, widget boundaries/routing/privacy projection, and shared-PDF validation/queue/expiry/backup exclusion. These execute Foundation logic on the local Mac, separate from device compilation.

### Static checks and unavailable verification

From the isolated export:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
ruby -e 'require "yaml"; YAML.load_file(ARGV[0])' .github/workflows/mobile-release-checks.yml
# From the working repository:
git diff --cached --check
```

Passed: 405 Kotlin source/test paths and boundary/module rules; color/resource consistency; 23 Python tests (including four new guardrail tests); 84 JavaScript tests; workflow YAML syntax and staged whitespace checks. Working-tree boundary checks also pass (428 files), but this is not binary verification of its unfinished extractions. A YAML parse does not execute GitHub CI.

No configured detekt/ktlint/coverage task exists to run. No desktop/web KMP target is configured; core JVM is tested and shared has no JVM target. Physical iOS/Android unlock/capture/OEM behavior, APNs/real FCM delivery, distribution signing and orchestrated reboot/performance/private-document checks are not claimed passed. No tests were disabled or assertion tolerances relaxed. Because the full app suite still fails, these changes remain uncommitted and unpushed despite the successful independent checks.

The independent full host/build/lint command above was repeated successfully after removing the unsuccessful orchestrator trial. [Verification evidence](evidence/2026-11-15/final-maintainability-verification.json) retains the failing app cases and diagnostic outcomes. **The overall gate is failed; no commit/push was made.**

## Recommended execution order

1. Resolve the PDF-sharing/full-suite native crash and rerun the complete app suite before committing these guardrails. Then review/integrate or deliberately discard the 39 unfinished paths as a separate characterized change; reconcile the older architecture notes against the actual committed result.
2. Close F02's automatic native/device coverage gap and F03's self-contained parser regeneration gap before substantial parser or financial changes.
3. Decompose one characterized session/guest/model lifetime, then navigation with a route/return/deep-link matrix. Keep the existing action owner.
4. Address F14 report read/dispatcher policy and F11 date rollover with focused behavior tests.
5. Introduce typed admission/read projections incrementally where mutable JSON can violate an invariant; preserve persistence/API compatibility.
6. Make numeric-policy/toolchain upgrades and private-platform capture compatibility separate explicit projects. Do not hide them in formatting or SOLID cleanup.
