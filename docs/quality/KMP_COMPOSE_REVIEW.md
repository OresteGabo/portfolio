# Compose structure and rendering review

Reviewed against `da93b5b` on 15 November 2026. The app uses Compose Multiplatform 1.11.1 with Kotlin/Compose compiler plugin 2.3.21 in `app/shared`; Android hosts it in `app/androidApp`, iOS uses the shared framework. This is a focused annual-coupon screen improvement after surveying screen sizes, effect/state usage and existing UI tests. Backend, website and prior unfinished changes are excluded.

## Findings and changes

- **Screen organization:** `YearlyCouponsScreen.kt` combined screen state, background calculation, navigation, list orchestration and large presentation blocks. Reduce the file from **218 to 136 lines**; `YearlyCouponComponents.kt` contains six presentation components in **110 lines**: header, totals, payment row, pagination, next-year preview and empty-year state. The **23-line** `YearlyCouponPresentation.kt` owns the calculated page projection. This adds useful boundaries rather than minimizing total lines.
- **Work during composition:** the screen rebuilt the currency list and filtered all rows during composition, then `yearlyCouponTotals` ran inside the lazy totals item. Build currency grouping, sorted currency choices and totals in `couponYearPage`, called inside the existing `withContext(Dispatchers.Default)` producer. It calls existing financial rules unchanged. Changing visible pagination or selecting an already-calculated currency reads that page's groups/totals; it does not recalculate annual totals in a composable.
- **Identity:** existing `year`, totals, month, coupon, pagination and next-year keys remain. Give previously positional optional sections stable keys for history status, calculation state, currency choice, receipt status, incomplete terms and schedule note. Individual coupon keys remain purchase ID plus due date.
- **Ownership:** year, selected currency, pagination, list scroll, request guards and effects remain in the screen. New components receive values and callbacks, not `PortfolioModel`; the callback for a coupon still opens the original purchase. Keep `rememberSaveable(year, currency)` pagination reset and the existing scroll-reset/restore logic.
- **Appearance and privacy:** preserve modifiers, padding, typography, colors, separators, headings, click roles/labels, financial redaction, translated strings and preview actions. Locale formatting stays in composition so changing language does not leave cached text. Next-year preview selection and financial-null behavior remain unchanged.
- **State/lifecycle:** the producer still keys on purchases, receipts, year, market date, receipt availability and history completeness. Its null reset and selected-year guard remain. No work is launched directly during composition. No new `remember`, `derivedStateOf`, state holder, effect or design-system abstraction is added.

The page types contain `val` properties and read-only collection contracts, but retain the existing `JsonRecord` purchases. They are not annotated `@Immutable`/`@Stable`: a mutable JSON object must not receive an unsupported stability promise. Grouping keeps references to the same coupon rows rather than cloning ledger records. Small visible-page slicing/month grouping remains straightforward composition work; no cache or frame-performance claim is justified for it.

Precomputing totals for every currency can do more background work initially than calculating only the selected currency. It trades one bounded grouping/aggregation per producer result for avoiding those operations during list composition. No frame, memory or execution-time improvement is asserted without measurement.

## Other inspected areas retained

- `DocumentsScreen.kt` (335 lines) has separate item/header helpers, deep-link focus handling and search/category grouping. Its filter/scroll/locale keys and `focusConsumed` behavior should be characterized before changing cache or deep-link ownership; no blanket `remember` is added around JSON inputs.
- `ShareDetailsScreen.kt` (256 lines) already keys purchase history by lot ID and limits the initial history. Financial date/quote inputs can change; no stability annotations or speculative valuation cache are introduced.
- `PdfViewerScreen.kt` uses count-based lazy pages, document/page keyed images, asynchronous native rendering and explicit disposal. Preserve resource ownership and zoom/viewport effects rather than extracting them into stateless decorations.
- Sign-in/settings/calendar/router files have existing unfinished changes. This commit does not overlap those edits or attempt a repository-wide screen rewrite.
- Build scripts enable the Compose compiler but configure no compiler metrics/reports, macrobenchmark, recomposition counter or frame benchmark task. No diagnostic report or runtime speedup is claimed. Android release lint and configured compiler checks are run.

## Tests and visual verification

Existing Android `YearlyCouponsNavigationTest` uses real accessibility nodes and a local synthetic server. It covers Calendar→annual ledger→bond detail navigation, 12/14 pagination, restoration after detail navigation, year changes, hidden financial numbers, French text and no coupon writes. Extend it to verify that going back to the preceding year resets pagination to 12, while the existing detail-navigation assertion keeps 14. Tests do not use live accounts.

Three common tests cover sorted/mixed currencies and preserved row order, manual receipt amount versus expected income, unavailable receipts/incomplete terms, next-year discovery and unchanged purchases. Existing annual schedule/totals rules remain covered by `YearlyCouponsTest`.

The unchanged committed baseline was exported, built and run on `Sequo_References_API_36_1` (`emulator-5554`), using both normal and `futureOnly=true` instrumentation arguments. Both pass. Preserve baseline screenshots before installing the refactored build, then inspect/compare the same synthetic scenes. Screenshot equality is limited to those fixtures, not proof of every device/theme configuration. UI durations are test-run timings, not rendering benchmarks.

## Verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`; Android SDK: `/Users/muhirwagabooreste/Library/Android/sdk`.

Focused domain/presentation tests:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*YearlyCoupon*' --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 23s`; log `/tmp/inzira-compose-focused.log`.

Full working-tree verification:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 1m 22s`; **703 host test executions** (107 core JVM, 108 core Android host, 488 shared host), no failures/errors/skips. Android app/test builds, release lint and both iOS compilations pass. Log `/tmp/inzira-compose-working-build.log`.

The six-file staged tree was exported via `git write-tree` / `git archive`, excluding prior unfinished work. The command above was repeated with `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk` and `--project-dir "$compose_export"`.

Export `/tmp/inzira-compose-index.PPGffj`: exit 0, `BUILD SUCCESSFUL in 2m 7s`; **692 host test executions** (107 core JVM, 108 core Android host, 477 shared host), no failures/errors/skips. Android app/test builds, release lint and both iOS compilations pass. Log `/tmp/inzira-compose-index-build.log`. Eleven additional working-tree shared tests belong to excluded prior work.

Shared native suite:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$compose_export" :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 1m 10s`; **496 shared native tests**, no failures/errors/skips. Log `/tmp/inzira-compose-index-native.log`.

Android UI verification (ADB at `/Users/muhirwagabooreste/Library/Android/sdk/platform-tools/adb`):

```sh
android emulator start Sequo_References_API_36_1 --headless
adb -s emulator-5554 install -r "$compose_export/app/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
adb -s emulator-5554 install -r "$compose_export/app/androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk"
adb -s emulator-5554 shell am instrument -w -e class dev.orestegabo.portfolio.investments.YearlyCouponsNavigationTest dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class dev.orestegabo.portfolio.investments.YearlyCouponsNavigationTest -e futureOnly true dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

The unchanged baseline was separately exported from HEAD and built with `:app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest` (exit 0, 11s). The same install/instrument commands with that baseline path pass: **OK (1 test)** in 24.684s and **OK (1 test)** in 25.254s. Logs `/tmp/inzira-compose-baseline-{build,ui,future-ui}.log`.

The isolated refactored build passes both extended journeys: **OK (1 test)** in 46.493s and **OK (1 test)** in 37.763s. Logs `/tmp/inzira-compose-index-{ui,future-ui}.log`. A repeat populated run is used to recapture the four normal-year scenes after the first comparison attempt found no Python Pillow installation; native `sips` decoding is used instead. The recapture run also passes: **OK (1 test)** in 36.936s; log `/tmp/inzira-compose-index-ui-repeat.log`.

Measured visual/source results (separate from runtime performance):

- All **six** 1080×2400 synthetic scenes have **zero differing RGB pixels** after native PNG decoding: calendar, overview, hidden financial values, French text, empty-year preview and next-year preview. Comparisons: `/tmp/inzira-compose-{normal,future}-visual-comparison.json`; baseline images `/tmp/inzira-compose-baseline{,-future}-images`, refactored images `/tmp/inzira-compose-index{,-future}-images`.
- Baseline overview and refactored empty-year preview were visually inspected; full image comparisons cover the six paired scenes. The temporary comparison uses `sips -s format bmp` plus Python standard-library BMP pixel comparison; the first optional Pillow-based attempt could not run because Pillow was not installed.
- The screen file reduction, component sizes and passing test counts are verified. No frame time, recomposition count, allocation profile or benchmark was measured, so moving work out of composition is a structural improvement with unmeasured runtime impact. No claim of faster rendering is made.

Static/script checks in working and isolated trees:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Both trees pass package ownership/module DAG and color consistency, **16 Python and 84 Node tests**. Final whitespace and staged-source/export comparisons are checked before commit; only the report receives final verification updates afterward.

Baseline compiler/lint warnings remain. No configured Detekt/ktlint or Compose metrics/benchmark task was found. Native tests do not execute an iOS UI journey, and Android emulator results do not establish physical-device or all-theme parity. No signed distribution, account mutation, live backend write or deployment occurred. Full core-native execution was not selected; the previously recorded share-cost exact floating-point equality baseline issue is outside this Compose change. No arithmetic or tolerance changes.
