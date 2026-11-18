# Share purchase timeline

Date: 2026-11-18. Scope: shared share-detail UI and its regression tests. No financial formulas, API contracts or backend changes are part of this update.

## Presentation

- The company overview retains its identity, combined valuation/return, quote freshness, position and explanatory information. The company-level document toggle was removed.
- Purchase history uses flat list tiles on a dated timeline, newest trade first, following the existing ticker/currency-scoped ordering. Each tile shows original quantity × unit price, total paid (including recorded fees), and the existing signed return percentage. A sold/partially sold purchase keeps the existing remaining-share and unavailable-return handling. The information dialog continues to explain that returns apply to shares still held and are not annualized.
- PDFs belong to the purchase that explicitly references them. Compact document rows retain the authenticated/guest preview and download/share paths without repeating the PDF date or a separate document panel. Missing evidence is stated rather than inferred from another purchase in the company.
- Tapping the purchase still opens its independent detail screen. Existing history loading, retry, empty and show-all states remain.
- Tiles have no enclosing card background, rounding or elevation. Semantic theme colors, natural text wrapping, minimum action sizes and the existing financial number/screenshot protection components are reused.

## Initial isolated verification scope

Concurrent, unrelated PDF-engine work was already present in the shared checkout and continued changing while this task ran. The shared checkout reported package/path mismatches in four new `core/.../parsing` files, a presentation-color check in `AndroidPdfTextExtractor.kt`, and an intermediate missing-extractor compile error. These files were not modified or reverted for this UI task.

To obtain stable, scoped results, verification uses `/tmp/inzira-share-timeline-review`: an archive of committed base `1e983a9f4215e5d045b8ac75c055639dfbab5102` plus only this task's three shared UI files, three device-test/fixture files and UI coverage inventory. No unfinished engine implementation is installed on a phone as part of this task. Final technical and visual results are recorded below.

The new synthetic regression uses out-of-order purchases with different dates, quantities, prices, positive/zero/negative returns and two distinct linked PDFs. It checks each purchase callback and actual document request, missing evidence, both themes, large text and hidden-number semantics. Existing detail integration assertions are updated to find purchase evidence directly in history rather than through the removed company toggle; preview/download assertions are retained.


## Build and visual evidence

Run from the isolated review directory, with JDK 21 and the locally configured Android/Apple toolchains:

```sh
./gradlew :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest \
  :app:shared:testAndroidHostTest :app:androidApp:lintDebug \
  :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 scripts/check-kmp-ui.py
```

All passed for the final scoped source: 562 shared host tests, zero failures/errors/skips; 472 source/test files satisfy package/platform boundaries. Existing compiler and compatibility notices remain visible. A test-fixture native-action provider was corrected after the first preview attempt; empty document lists also no longer read an unused platform capability. No production abstraction was made public for test access.

```sh
adb -s emulator-5554 shell am instrument -w \
  -e class dev.orestegabo.portfolio.investments.UiGoldenTest,dev.orestegabo.portfolio.investments.SharePurchaseTimelineTest \
  -e uiGoldenMode record \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

Candidate authoring passed three tests in 35.366s, including the new timeline behavior test and comparator self-test. This recording is not counted as a passed reference comparison. All twelve candidates were visually inspected across the existing six viewports in light/dark themes; four additional multi-purchase history captures covered normal and 1.8× text in both themes. Six overview PNGs change where history is visible; the other six remain byte-identical. References and manifest hashes were intentionally updated after review. Text wraps and the rail connects dated rows; no new card surface is used. Diagnostic full-window captures use a synthetic host without production Scaffold insets, so they do not certify native system-bar/safe-area behavior.

```sh
xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'id=00008101-000E3DD11E03001E' -destination-timeout 30 \
  -derivedDataPath /tmp/inzira-share-timeline-ios -allowProvisioningUpdates build
```

Final signed iPhone Debug build passed, installed and launched normally on the connected iPhone. No authentication bypass, account mutation or real financial test data was used. Physical iPhone layout/VoiceOver and real-account purchase documents were not manually inspected.

An initial final comparison/navigation run passed the timeline scenario, then was interrupted by the reference emulator freezing and disappearing during `InvestmentDetailLayoutTest`. Its emulator log reported QEMU main-loop/CPU thread hangs; that interrupted run is not counted as passing. The emulator was restarted after native builds finished. Final repeat results follow below.


## Final device results

The saved-state emulator disconnected again after completing the timeline and navigation cases, before the screenshot class finished. Neither interrupted command is reported as a successful suite. A cold-booted, read-only instance of the same reference AVD was then launched on a private port (`5590`) with snapshots disabled; no app assertions were weakened and no references were changed after the cold boot.

```sh
adb -s emulator-5590 shell am instrument -w \
  -e class dev.orestegabo.portfolio.investments.UiGoldenTest \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5590 shell am instrument -w \
  -e class dev.orestegabo.portfolio.investments.InvestmentDetailLayoutTest \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

- Reference comparison: **OK (2 tests), 21.696s**. All twelve reviewed screenshots match exactly; the comparator's changed-digits/dimensions rejection test also passes.
- Navigation/PDF integration: **OK (1 test), 40.484s**. Both themes, unchanged bond actions, purchase navigation, company-history PDF preview/download and individual-purchase PDF preview/download/breakdown assertions pass.
- New multi-purchase timeline behavior: passed in the complete candidate-authoring invocation above (**OK (3 tests)** overall), as well as completing in the interrupted final commands. Its behavior assertions do not depend on the screenshot recording flag.
- Together these verify **four distinct focused device test cases**, in addition to 562 host unit tests. This is not a claim that the full application device-test gate ran.

All six scoped production/test source files in the main checkout match the isolated verified files. The main UI inventory also contains concurrent native-engine test additions; those were preserved and were not claimed as executed by this task. Inventory/golden integrity and final `git diff --check` pass. The purchase timeline source, tests, reviewed reference updates and this report were left uncommitted for user review at this stage.


## Pending-purchase extension — 2026-11-18

Pending account share orders now appear alongside confirmed purchases. The existing RWF order editor can be opened from the history's small add action (company/quantity prefilled), or from a pending row. Each pending row has the existing clock status badge, an explicitly labeled request date, estimated total and an Upload contract action. It does not show a since-purchase return before execution. Guest/unsupported-currency creation is not newly enabled.

`ShareHistoryEntry` keeps request dates separate from actual trade dates and namespaces order/purchase keys. Only pending orders enter this history; cancelled/completed orders are excluded. Missing dates remain unknown. Existing per-purchase PDF links and confirmed return calculations are unchanged. Order uploads retain the explicit order ID and existing review/confirmation API; the actual date, price and fees come from the contract, while security/currency/full quantity still must match. Confirmation can move the row to a different chronological position. No new backend deployment or automatic date-based matching is introduced.

`loadInvestmentHistory` previously rebuilt `PortfolioSnapshot` with only purchases, holdings and documents, dropping pending orders (and other snapshot fields). It now copies the latest snapshot and replaces only fetched purchases/holdings. Regression tests verify pending costs survive full-history loading and that PDF confirmation replaces the estimate once, using the actual trade date and amounts.

The same isolated checkout was extended with these scoped changes, preserving the concurrent native-engine migration in the main checkout. Shared host tests: **567 passed**, zero failures/errors/skips. Android APK/test APK builds, lint, both iOS compilations and the signed iPhone Debug build passed. Package/source-set checks (**474 files**), semantic colors, UI inventory/golden integrity and final whitespace checks passed in the scoped build. Existing notices were not suppressed. The final app was installed and launched normally on the connected iPhone; physical iPhone layout/VoiceOver and production account imports were not manually tested.

Final focused device command:

```sh
adb -s emulator-5590 shell am instrument -w \
  -e class dev.orestegabo.portfolio.investments.SharePurchaseTimelineTest,dev.orestegabo.portfolio.investments.InvestmentDetailLayoutTest,dev.orestegabo.portfolio.investments.UiGoldenTest \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

Result: **OK (5 tests), 53.44s**. The pending case checks add/manage/upload identities, request-date labels, absence of a pending return, both light/normal and dark/1.8× text layouts, and a refreshed server execution with a different date/price/fees and exact totals. All twelve existing confirmed-history screenshot comparisons pass; their PNGs were not changed for this extension. Pending diagnostic screenshots were visually inspected in light/normal and dark/large text, but are not new pixel-regression baselines.

During verification, synthetic nested-JSON mutation and a JUnit double-assertion overload were corrected. Amount assertions use a zero delta. A real test-driver race was also fixed: `await` now returns the node from the snapshot that matched its polling predicate, rather than clearing accessibility state and rescanning during an in-flight scroll/layout. Every original scenario/assertion remains; no failure was disabled or accepted as success. The final five-case run passed after these corrections.

The controller Back branch preserves the selected company when returning from its pending editor. PDF review continues through the established Documents import flow. The feature was left uncommitted for user review; unrelated source/build/engine changes were preserved.


## Commit gate on the combined branch — 2026-11-18

After user approval, verification was repeated directly in the shared repository, on base `e59013c585dfe18f42d4fc37cd628da110aee93a`. The independent native-engine migration (`fd66d38`) and CI fix were already committed/pushed; these commits do not include that work. Earlier isolated-build results above describe the review stage, while the following results cover the actual combined source being committed.

```sh
./gradlew :core:jvmTest :app:shared:testAndroidHostTest \
  :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest \
  :app:androidApp:lintDebug :app:shared:compileKotlinIosArm64 \
  :app:shared:compileKotlinIosSimulatorArm64 --console=plain
adb -s emulator-5590 shell am instrument -w \
  -e class dev.orestegabo.portfolio.investments.SharePurchaseTimelineTest,dev.orestegabo.portfolio.investments.InvestmentDetailLayoutTest,dev.orestegabo.portfolio.investments.UiGoldenTest \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

Results: Gradle **BUILD SUCCESSFUL**, 147 core JVM tests and 570 shared Android host tests, zero failures/errors/skips. Device tests: **OK (5 tests), 59.469s**, including all twelve exact screenshot comparisons. No recording flag was used. Package/platform boundaries (484 source/test files), semantic-color audit, UI inventory/golden integrity and `git diff --check` passed. Existing notices remain visible. Current UI reference source metadata pins this combined source; no further reference PNGs were recorded or altered at this gate.

The commits separate the snapshot-retention bug fix and its regression from the cohesive dated purchase timeline, pending actions, actual-date confirmation regression, reviewed references and documentation. No backend or website changes are included. The private read-only test emulator is stopped after verification.
