# Review of the remaining KMP changes

Baseline: `c3f5df9`, 15 November 2026. The user authorized review, verification and separate commits for the 79 previously pending paths. No backend, website, API contract, investment formula or storage-schema change is intended.

## Verified commit boundaries

1. Portfolio/data responsibilities: snapshot loading, market reads, guest operations, local schema/persistence, PDF chunk transport, application forms, document previews and privacy preferences. Keep the model's session, lifecycle and action guard as the facade.
2. Shared UI structure, design and accessibility: extracted presentation/credential/calendar/settings/market components; neutral brand surfaces and dimensions; chart alternatives and keyboard actions; selection semantics, contrast and text scaling. These changes share component owners, so they form one cohesive UI commit.
3. Financial transparency/privacy presentation: distinguish unavailable results from zero, label estimated/unrealized values, identify cached quotes and clarify account PDF storage. Financial formulas, fee policy and server bodies remain unchanged.
4. Maintainability documentation/checks: module/source-set checks, editing defaults, CI integration and reconciled audit reports.

Mixed UI/financial files have separately reviewed intermediate versions. Each source commit was checked in its corresponding isolated state; screenshot references are authored and visually reviewed separately for the UI and financial variants. Recording candidates does not satisfy comparison-mode verification.

## Review corrections

- `EquityPriceHistory` exposed mutable maps. Callers now receive read-only observable views; explicit error-clearing operations retain existing refresh/retry behavior. A new regression protects cached histories and unrelated failures when clearing one/all retry errors.
- `UiDesignConsistencyTest` used a private fixed-delay scroll loop. It now reuses the verified event-aware driver and keeps the required visible action and callback assertion. Chart test reads clear the accessibility cache before evaluating updated states.
- A new cache test initially failed on Native because Compose’s key-set view does not share JVM set-equality behavior. Comparing `keys.toSet()` preserves the exact expected key assertion across targets; the corrected host and Native suites passed. The failed run remains recorded.
- Removed leading/extra indentation in the newly extracted privacy and settings-record components.

The other data extractions retain their request paths/bodies, 512KiB upload chunks, cache behavior, cancellation handling, schema version/IDs and normalization rules. Model state delegates to the extracted owners without introducing independent sessions or scopes. Existing financial and import regressions remain required.

## Responsibility changes and remaining risks

| Owner | Baseline lines | Reviewed lines | Result |
|---|---:|---:|---|
| PortfolioModel | 1247 | 1063 | Session/action facade delegates guest work/quotes, privacy, account reads, forms, previews and market data. Still large; migration, session and compatibility coordination remain. |
| InvestmentApp | 665 | 514 | Platform effects leave screen/tab orchestration with the existing root. Remembered route flags remain a future navigation risk. |
| GuestPortfolioRepository | 471 | 43 | Contract separated from LocalGuestPortfolioRepository (328) and GuestPortfolioSchema (116). Storage/schema behavior retained. |
| CalendarScreen | 469 | 221 | Calendar rendering helpers co-located in CalendarComponents. |
| SettingsScreen | 448 | 301 | Presentation and records/admin behavior have named owners. |
| SignInScreen | 437 | 360 | Shared credential input extracted without changing authentication. |
| InvestmentChart | 483 | 331 | Company-price presentation extracted; chart summaries/actions have a pure tested owner. |

Line counts include comments/blank lines, not complexity scores. No Gradle module, DI framework, dependency, inheritance hierarchy or backend layer was added. The remaining broad investments package, external parser regeneration toolchain, JSON-backed models, implicit root navigation and private capture implementation risks are still documented in the earlier audit. This change does not claim they are resolved.

## Verification commands

JDK 21.0.9, local Android SDK and Xcode toolchains were used. Isolated exports were made from parent `c3f5df9` plus exact reviewed groups, without changing the real Git index. `/tmp/inzira-pending-stage1` is data-only, `stage2` adds UI/accessibility, and `/tmp/inzira-pending-review` includes all changes. Temporary logs are diagnostics; the committed evidence JSON preserves the results.

```sh
# Data-only intermediate state
./gradlew --project-dir /tmp/inzira-pending-stage1 :app:shared:testAndroidHostTest \
 :app:androidApp:assembleDebugAndroidTest :app:shared:compileKotlinIosArm64 \
 :app:shared:compileKotlinIosSimulatorArm64 --console=plain

# UI-only intermediate state
./gradlew --project-dir /tmp/inzira-pending-stage2 :app:shared:testAndroidHostTest \
 :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest \
 :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain

# Exact UI-only state after the portable assertion correction
./gradlew --project-dir /tmp/inzira-pending-stage2 :app:shared:testAndroidHostTest \
 :app:shared:iosSimulatorArm64Test \
 -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain

# Full combined gate; recording mode is NOT enabled
ANDROID_SERIAL=emulator-5554 ./gradlew --project-dir /tmp/inzira-pending-review \
 :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest \
 :app:shared:connectedAndroidDeviceTest :app:androidApp:connectedDebugAndroidTest \
 :app:androidApp:lintDebug :app:androidApp:lintRelease :app:androidApp:compileReleaseKotlin \
 :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 \
 :app:shared:compileTestKotlinIosArm64 :core:iosSimulatorArm64Test \
 :app:shared:iosSimulatorArm64Test \
 -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
 -Pandroid.testInstrumentationRunnerArguments.uiFrameBenchmark=true \
 -PcomposeReports=true --console=plain

python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 scripts/check-kmp-ui.py
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
ruby -e 'require "yaml"; Dir[".github/workflows/*.yml"].each { |p| YAML.load_file(p) }'
git diff --check
```

Existing Swift synthetic harnesses are compiled using `xcrun swiftc` with: `NextWidgetShared/NextWidgetData.swift` + `scripts/tests/next-widget-data.swift`; `SharedImport/SharedPdfInbox.swift` + `scripts/tests/shared-pdf-inbox.swift`; `iosApp/CalendarEventDay.swift` + `scripts/tests/calendar-event-day.swift` (native inputs under `app/iosApp`). All three executables are run.

Release/package commands (executed after the combined gate):

```sh
./gradlew --project-dir /tmp/inzira-pending-review :app:androidApp:bundleRelease --console=plain
xcodebuild -project /tmp/inzira-pending-review/app/iosApp/iosApp.xcodeproj \
 -scheme iosApp -configuration Release -destination 'generic/platform=iOS' \
 -derivedDataPath /tmp/pending-ios-derived CODE_SIGNING_ALLOWED=NO build
python3 /tmp/inzira-pending-review/scripts/check-ios-release.py \
 /tmp/pending-ios-derived/Build/Products/Release-iphoneos/Portfolio.app
python3 scripts/check-kmp-ui.py \
 --results /tmp/inzira-pending-review/app/androidApp/build/outputs/androidTest-results/connected/debug
```

## Screenshot review

Two candidate sets were explicitly recorded, then inspected visually across six sizes and both themes. UI-only and final financial variants have separate PNGs/manifests in their corresponding commits. The twelve final images preserve synthetic values/dates and show the intended neutral surfaces, wrapping, cached-price and estimated/unrealized labels. Scrollable content extends below short/large-text viewports; pixel capture alone does not verify scrolling, so the interaction tests remain required. Manifest metadata identifies the parent, variant and shared-Kotlin digest.

UI-only comparison/interaction command (without recording) used `adb -s emulator-5554 shell am instrument -w -r -e class` with `UiGoldenTest,ChartAccessibilityTest,UiDesignConsistencyTest,NavigationUsabilityTest,GuestExperienceUiTest,ImportConversationUiTest` under `dev.orestegabo.portfolio.investments`, runner `dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner`: **7 tests passed**, including all twelve comparisons. Candidate recording runs (**2 tests each**) are not counted as comparison verification. Final comparison is also part of the unfiltered combined app suite.

## Verification and disposition

The full combined gate passed in **11m 2s**. Android release bundling passed (**1m 2s**); the complete unsigned iOS device Release app/extensions and built-package checks also passed. Distribution signing and store submission were not tested. All required locally available verification completed before creating the four authorized commits. Historical failures in earlier scoped audits are preserved and are not retroactively called successful.

| Suite | Tests | Failures/errors | Skipped |
|---|---:|---:|---:|
| Core JVM | 145 | 0 | 0 |
| Core Android host | 145 | 0 | 0 |
| Shared Android host | 552 | 0 | 0 |
| Core iOS simulator | 144 | 0 | 0 |
| Shared iOS simulator | 578 | 0 | 0 |
| Shared Android device | 546 | 0 | 0 |
| Android app instrumentation | 54 | 0 | 4 |

The completed JUnit reports, not the inconsistent live progress counter (which ended at 58/54), determine these counts. All **17** inventoried critical methods passed the report checker. An earlier report-check invocation ran before UTP finished writing XML and rejected the incomplete evidence; after the **same** full run completed, the check passed without rerunning or excluding tests.

Data-only: **539** host tests and affected Android/iOS compilation passed. UI-only: **550** host and **576** Native tests, Android/test compilation, both iOS compilations and **7** comparison/interaction tests passed. Final combined build includes debug/release Kotlin, Android test APKs, both iOS targets, iOS device-test compilation and Compose compiler diagnostics. Native results reused by the final Gradle invocation had actually executed successfully in the preceding corrected-source run; UP-TO-DATE is not claimed as a fresh execution.

Static/script gates passed: package/module/source-set checker (**454** paths), generated design-color audit, golden/coverage inventory, **31** Python tests, **84** Node tests, both workflow YAML parses, three executed Swift harnesses and whitespace review. Android Lint: **0 errors**, **92 debug / 94 release warnings**, unchanged and unsuppressed.

The debug frame diagnostic on a 200-lot fixture recorded 327 frames at 60Hz: p50 **23.4665ms**, p95 **46.910229ms**, max **69.931738ms**, zero dropped metric reports. This is a single emulator observation of accessibility scrolling, excluding startup/biometric/PDF/platform capture. It does not establish an improvement or release performance budget.

[Machine-readable evidence](evidence/2026-11-15/pending-changes-verification.json) retains the corrected-test failure, intermediate trees, results, skips and limitations.

## Limits and manual follow-up

- Android visual/device evidence uses a disposable API 36.1 ARM emulator (1080×2400, 420dpi). Six synthetic screen profiles are not six physical devices, nor a complete app-wide visual audit.
- Both iOS shared targets and device-test sources compile; Native unit tests run on an iPhone 16 Pro / iOS 18.6 simulator. No manual iPhone UI, VoiceOver, TalkBack, physical Honor/Samsung, folding or hardware-keyboard certification is claimed.
- Four operator-gated tests require real Firebase delivery, a supplied real PDF, process/reboot guest persistence or the startup fixture. Synthetic PDF/import tests run; real account/document credentials were not used.
- Lint warnings remain visible. No configured ktlint/detekt, coverage percentage or release performance benchmark exists to claim passed. Frame observations are debug diagnostics, not proof of a performance gain.
- Official regulatory references in the financial audit were rechecked on 15 November 2026; operator jurisdiction/licensing, distribution status, processing arrangements and legal classification still require professional confirmation. No legal proposal, consent requirement or server deletion promise was silently published.
- CI YAML validation is not hosted workflow execution. Automatic native/device test lanes remain follow-up work.


## Git review

The data, UI and financial source/resource trees were compared byte-for-byte with their tested exports (607, 626 and 629 mobile source/resource files respectively). The combined mobile source matches the final working tree. Mixed files and screenshot assets are split by their tested intermediate versions. Documentation/checks form the final commit. Final tree equality and staged whitespace checks guard against omitted or accidental files. No backend/website/core production, dependency, signing, API/schema or calculation-formula changes are included.
