# Composition and polymorphism review

Reviewed against `b967a74` and the working tree on 15 November 2026. Scope: KMP mobile code and Android host, including common/platform sources and callers. Backend and website are unchanged. Earlier unfinished refactors remain outside this commit.

## Findings and decisions

| Finding | Evidence | Decision and risk |
|---|---|---|
| Medium widget inherits implementation from a differently sized widget | `NextWidget.kt`: `NextMediumWidget : NextSmallWidget()` overrides nothing; both refresh through `NextWidgetStore.updateAll`. A medium widget has its own manifest provider and rendering layout, not the identity of a small widget. | Make both final, independent `AppWidgetProvider` adapters. Their two framework callbacks forward directly to the existing shared store/renderer. A few identical adapter lines are preferable to a new abstract base class. Provider class/component names, XML resources, request codes and rendering logic remain unchanged. Low risk. |
| Mailbox is open solely to provide a native singleton | `IncomingPdf.kt`: the only production subclass of `PdfImportInbox` is `NativePdfInbox`, with no overrides. All callers use mailbox operations; none passes the singleton as a `PdfImportInbox`. Swift reads `NativePdfInbox.shared.pending`. | Make the mailbox final and let the singleton own one mailbox. Explicit property/operation forwarding keeps the same singleton name and native API while removing an unused extension point. No new interface is needed. Compose reads still reach the same observable backing state. Medium integration risk because platform ingestion crosses this boundary. |
| Guest parser wrapper manually forwards independent capabilities | `GuestPdfImport.select` adapts parsing to add envelope validation but manually forwards progress get/set and cancellation. | Use `InvestmentDocumentParser by parser`, overriding only `parse`. Validation still runs on the injected dispatcher before the same parser receives bytes. Callback ownership, cancellation and host engine lifecycle remain unchanged. Low risk, covered by cancellation/cleanup and successful import tests. |
| Received-document adapter intentionally overrides both selection paths | `ReceivedPdfActions : NativeActions by original` overrides `pickPdf` and `pickPrivatePdf`; ordinary selection must not reopen a picker for an incoming/retried PDF. | Retain both overrides. Kotlin delegation of a default method can invoke the delegate's own selection path; deleting the private-path override would change behavior. Other native capabilities continue to belong to the original host. |
| Closed session variants already have the right abstraction | `SessionState` is a sealed interface with signed-out, guest and authenticated variants; `GuestPath` has explicit stable stored identifiers and unknown-value handling. | Retain sealed/exhaustive handling. Do not turn modes into mutable subclasses of the model, or change stored strings. |
| Framework subclassing represents required platform roles | `PortfolioModel : ViewModel`, Android activity/provider/receiver/service subclasses and iOS `NSObject` protocol delegates participate in framework lifecycle callbacks. | Retain these genuine framework relationships. This review found no application-defined deep hierarchy or demonstrated parent-contract violation in them. Framework inheritance alone is not technical debt. |
| Financial variants have different rules | `BondTerms`/payment schedules differ from `SharePosition`, share-sale allocation and acquisition costs. `PortfolioSnapshot` composes these records; valuation and insights project common reporting values. | Retain separate instrument models and pure calculation functions. No generic `Investment` superclass, uniform coupon API or speculative instrument plugin registry is introduced. |
| JSON discriminator branches are not all runtime type dispatch | `PurchaseReviewState`, `GuestPdfMatching`, `PurchasePdfImport` and `InvestmentLedger` inspect `kind` for confirmation, statement routing and ledger validation. Unsupported/unknown kinds have existing rejection/fallback rules. | Keep these boundary/policy branches in this slice. Replacing each string check with polymorphic objects would require a separate typed-document migration across JSON contracts. There is no repeated subclass-downcast hierarchy to eliminate here. |
| Data classes represent values rather than behavior inheritance | `BondTracking`, `SubscriptionWindow`, share-cost/valuation projections, `PdfImportProgress`, session variants and report/event rows hold cohesive values. `ReceivedPdf` stays a regular class carrying identity plus bytes. | Retain value composition. Converting byte-owning classes to data classes would introduce shallow copy/equality semantics without an ownership benefit. Existing list/JSON alias limitations are recorded in the encapsulation audit, not solved by inheritance. |
| Several interfaces are intentionally broader at the composition boundary | `NativeActions` aggregates device actions; `InvestmentEngine.run` dispatches the bundled engine protocol; `ServerSettings` aggregates preferences. Focused parser, picker, storage and account-data contracts already serve narrower consumers. | Retain existing host aggregates, platform factories and manual DI. Do not create a hierarchy per setting or per engine command. Continue narrowing actual feature dependencies when useful, as in the preceding abstraction review. |

The inventory covered handwritten Kotlin declarations and call sites, including Android/iOS implementations. Generated geometry/legal code, vendored parser/recognition assets and framework-generated classes are not treated as maintainability hierarchies to rewrite.

## Behavior and compatibility

The mailbox algorithm is unchanged: one pending file, bounded duplicate-delivery history, preparation gating, delivery sequence, validation/size limits, sanitized errors and dismissal behavior. The singleton facade holds one permanent mailbox; dismissal does not recreate it or reset its deduplication history. All native/Swift call sites retain their existing names and parameters. Removing the superclass is a source-level change for hypothetical external consumers; no such consumer exists in the repository. This is not a promise of binary compatibility with previously compiled external clients.

The widget store owns all shared rendering, persistence and update behavior. Both providers retain their public zero-argument construction and `AppWidgetProvider` inheritance for manifest instantiation. No provider IDs or preference keys change. No parser JSON, persisted data, session variant, financial formula, network payload or schema changes.

## Regression coverage

- Existing mailbox tests cover pending-file retention, duplicate delivery, explicit preparation, wrong IDs, dismissal and invalid PDF/size boundaries. A new singleton-facade test covers the same externally visible operations, default arguments, duplicate history after take, sanitized errors and cleanup.
- Existing received-file tests run bond, equity, statement and public-issuance variants through the original parser without reopening selection. Guest tests include private selection, duplicate detection, review/save, invalid documents, timeout, engine cancellation and secure-storage recovery. The cancellation test now also checks that late delegated progress cannot revive cleared state.
- Existing Android widget tests cover both rendering sizes, installed provider metadata and empty state. A new instrumentation test invokes both providers through their update/options callbacks and confirms that refresh does not change their persisted snapshot.
- Existing financial and session suites are retained; no assertion or monetary tolerance is relaxed.

## Verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`; Android SDK: `/Users/muhirwagabooreste/Library/Android/sdk`.

Focused working-tree checks:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*IncomingPdfTest' --tests '*GuestPdfImportTest' --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 14s`; log `/tmp/inzira-composition-focused.log`.

Full working-tree check:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 58s`; **100 core JVM + 101 core Android host + 433 shared host tests**, zero failures/errors/skips. Log `/tmp/inzira-composition-working-build.log`.

The seven changed source/test/report files were staged without earlier unfinished work and exported using `git write-tree` / `git archive` to `/tmp/inzira-composition-index.o7iw9f`. Against that exact tree, the full command above was repeated with `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk`, `--project-dir "$audit_export"` and the additional task `:app:shared:linkDebugFrameworkIosSimulatorArm64`.

Exit 0, `BUILD SUCCESSFUL in 1m 53s`; **100 core JVM + 101 core Android host + 422 shared host tests**, zero failures/errors/skips. Android app/instrumentation APK compilation, release lint, both iOS compilations and the simulator framework link pass. Log `/tmp/inzira-composition-index-build.log`. The working tree's additional 11 tests belong to the excluded unfinished refactor. All staged Kotlin source/test files match the export byte-for-byte.

Swift compatibility check against the rebuilt export:

```sh
xcrun --sdk iphonesimulator swiftc -typecheck -target arm64-apple-ios18.0-simulator -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" -F "$audit_export/app/shared/build/bin/iosSimulatorArm64/debugFramework" "$audit_export/app/iosApp/SharedImport/SharedPdfInbox.swift" "$audit_export/app/iosApp/iosApp/SharedPdfImportHost.swift"
```

Exit 0; log `/tmp/inzira-composition-swift-typecheck.log`. This checks the actual Swift import host's singleton access without claiming a complete signed Xcode app build.

Android emulator and shared native tests, also against the export:

```sh
android emulator start --headless Medium_Phone_API_36.1
ANDROID_SERIAL=emulator-5554 ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$audit_export" :app:androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.orestegabo.portfolio.NextWidgetTest :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 40s`; **4 Android widget tests + 445 shared iOS native tests**, zero failures/errors/skips in the XML reports. Log `/tmp/inzira-composition-index-devices.log`. The shared suite includes all mailbox/parser tests and native received-file integrations.

Gradle's device runner logged a shutdown-time `NoClassDefFoundError: io/netty/util/concurrent/DefaultPromise$1` after reporting all four Android tests complete; the task and build still returned success. A direct instrumentation rerun bypassed that host runner:

```sh
android install --device=emulator-5554 "$audit_export/app/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
android install --device=emulator-5554 "$audit_export/app/androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk"
/Users/muhirwagabooreste/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell am instrument -w -r -e class dev.orestegabo.portfolio.NextWidgetTest dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

Direct rerun: exit 0, `OK (4 tests)`, four successful test completions and no runner shutdown error. Logs `/tmp/inzira-composition-direct-install.log` and `/tmp/inzira-composition-direct-widget-tests.log`.

Both working and isolated trees pass:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Results: package ownership/module DAG and color checks pass, **16 Python tests and 84 Node tests** pass, no diff whitespace errors. Logs: `/tmp/inzira-composition-{working,index}-{packages,colors,python,node}.log`. Source comparison confirms the mailbox algorithm is unchanged, and the guest parser wrapper differs only by equivalent interface delegation.

No Detekt/ktlint task is configured. Baseline lint/compiler warnings remain, including the framework bundle-ID inference warning. The CLI initially needed host-cache access even for help; rerunning with scoped host access succeeded. No personal-phone test, live-account write, backend deployment, signed distribution or physical widget pinning check was performed. Widget callback/rendering coverage is on the emulator.

No core source changed; the separate full-core-native `ShareCompanyRowsTest.remainingSharesAndTheirCostAreUsedAfterSales` floating-point equality baseline failure remains outside this slice. The reported full core JVM/Android host suites and full shared native suite are distinct from an all-core-native/all-task green claim.

Commit only this verified slice as `refactor(kmp): improve composition and polymorphism`; keep earlier unrelated work uncommitted.
