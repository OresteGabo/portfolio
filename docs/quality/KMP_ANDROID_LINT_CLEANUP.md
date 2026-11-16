# Android lint cleanup — 16 November 2026

Parent: `54fbb5f`. This first phase fixes Android code/resource findings; the user separately authorized the toolchain/dependency upgrade phase. No backend/website, financial formula, API/schema, session policy or privacy-default change.

## Findings and changes

The previous review recorded 92 debug / 94 release warnings. Clean current reports contain **35 warnings each**, all version/target notices: NewerVersionAvailable 25, GradleDependency 5, AndroidGradlePluginVersion 3 and OldTargetApi 2. These remain visible until the separately verified upgrade; this phase does **not** claim zero lint warnings.

| Finding | Correction |
|---|---|
| 10 UseKtx suggestions | MainActivity, BondReminderNotifications, PriceMessagingService and NextWidget use existing AndroidX `edit`/`toUri` helpers. Preference writes still use asynchronous apply; reconciliation preserves cancellation/removal order. No new dependency. |
| ObsoleteSdkInt | Move the API-24 launcher vector into ordinary drawable: the app's minimum is already 24. Update the branding generator so regeneration preserves the fix. |
| 20 launcher-shape warnings | Generate circular transparent legacy PNGs for API 24/25 from the approved light/dark masters. Modern adaptive foreground/background geometry remains unchanged. |
| 10 duplicate-icon warnings | Share one launcher/roundIcon resource and remove duplicate round PNG/XML resources. Both modern launcher masks retain the same approved mark. |
| 2 monochrome warnings | Include the existing monochrome layer in the remaining adaptive icon resource. |
| Widget API-31 attributes | Keep legacy provider metadata in xml; put target cells/previewLayout in matching xml-v31 variants. No minSdk increase or lost legacy layout fallback. |
| Launcher shortcut API-25 metadata | Explicit `tools:targetApi=25` documents when launcher shortcuts are understood. Preserve App Actions capability metadata rather than deleting it from older-device resources. |
| 2 compound-header suggestions | Use a framework TextView with a native 20dp compound brand drawable, reducing header view nesting. The header can grow with text rather than fixing its height. |
| 2 nested-weight warnings | Use RelativeLayout for medium-widget vertical columns and root height allocation; retain equal horizontal weights, event ordering and expansion when the second event is absent. |

Two header-level `UseCompatTextViewDrawableXml` exceptions are deliberate and commented inline: AppCompatTextView/custom subclasses are unsupported by RemoteViews; `app:drawableStartCompat` is not the framework widget solution. Native drawableStart/vector support is available throughout this app's API range. The strict runtime test asserts intrinsic size, drawing bounds and text padding. This is a targeted false-positive exception, not a global lint disable or baseline file.

References checked 16 November 2026: [Android RemoteViews restrictions](https://developer.android.com/reference/android/widget/RemoteViews), [widget layout guidance](https://developer.android.com/develop/ui/views/appwidgets), [tools API context](https://developer.android.com/studio/views/tool-attributes-views). Pinned upgrade choices must respect the [Kotlin compatibility matrix](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html).

## Review and regression coverage

`NextWidgetTest` retains real RemoteViews inflation/drawing, event labels, provider callbacks, one/two-event visibility, cleanup and saved-state restoration. Added assertions protect nonzero brand drawing bounds, text padding, remaining title space, equal event columns, no header overlap and full-width content when the second event is absent. No assertion was relaxed.

Initial offscreen screenshots lacked the relative header icon because the unattached English fixture had no launcher parent to resolve its inherited layout direction. A stricter focused run correctly failed on missing compound padding. The fixture now explicitly resolves LTR before measurement, while keeping the strict size/bounds/padding assertions. The corrected **six-test** widget/notification run passed. Both resulting synthetic widget images and light/dark legacy launcher images were visually inspected. No user documents/accounts were used.

An initial incremental resource build failed after moving the foreground vector; a clean Android build passed. An initial full gate was deliberately cancelled to investigate the widget rendering; it is not counted as a successful full run. Logs/evidence retain these attempts. The corrected full gate must complete before commit.

## Verification

JDK 21.0.9, disposable `Sequo_References_API_36_1` (`emulator-5554`) and iPhone 16 Pro/iOS 18.6 simulator `1726E7D8-877B-4A6D-ABCD-7292D35D5BD4`. Exact full command:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home \
ANDROID_SERIAL=emulator-5554 ./gradlew \
 :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest \
 :app:shared:connectedAndroidDeviceTest :app:androidApp:connectedDebugAndroidTest \
 :app:androidApp:lintDebug :app:androidApp:lintRelease \
 :app:androidApp:compileReleaseKotlin :app:androidApp:bundleRelease \
 :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 \
 :app:shared:compileTestKotlinIosArm64 :core:iosSimulatorArm64Test :app:shared:iosSimulatorArm64Test \
 -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
 -Pandroid.testInstrumentationRunnerArguments.uiFrameBenchmark=true -PcomposeReports=true --console=plain

python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 scripts/check-kmp-ui.py --results app/androidApp/build/outputs/androidTest-results/connected/debug
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
```

Full corrected gate: **BUILD SUCCESSFUL in 10m 28s**. Core JVM/host **145/145**, shared host **552**, core/shared iOS Native **144/578**, shared Android device **546**, app instrumentation **54** (zero failures/errors, four existing operator-gated skips). All **17** critical UI journeys and twelve unchanged pixel comparisons passed. Host/build/native compilation, release bundle, both lint variants (zero errors, 35 version/target notices), **31 Python**, **84 Node**, both workflow YAML parses, three Swift harnesses, package/color checks and whitespace review passed. [Machine-readable evidence](evidence/2026-11-16/android-lint-cleanup.json) records final counts and limitations. Existing reference screenshots are unchanged: this Android native-resource cleanup does not change shared Compose presentation. No manual physical-device, API-24 launcher, iOS visual, hosted-CI, legal compliance or performance improvement claim.

## Subsequent verified upgrade

The 35 version/target notices above describe this independently verified first phase. The subsequent [toolchain/dependency upgrade](KMP_TOOLCHAIN_UPGRADE.md) reduces the current reports to three compatible-toolchain update notices, with separate reference/Android-17/native/release verification. No earlier failed attempt is reclassified as successful.
