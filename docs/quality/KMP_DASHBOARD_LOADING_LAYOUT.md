# Dashboard initial loading layout

Updated 2026-11-18.

The initial skeleton now follows the current dashboard: the real brand header, centered balance placeholder outside the fading metrics surface, responsive metrics, allocation carousel and investment list placeholders. The hero frame/header and fading surface are shared with the populated screen. Unknown financial values remain placeholders. Refreshing an already loaded portfolio retains the existing data path.

The synthetic snapshot fixture can pause the overview response. `DashboardLoadingLayoutTest` checks the initial loading state, absence of fabricated zero balances, notification action and identical loading/loaded logo bounds in light/dark themes at 1.0 and 1.8 font scale. Diagnostic captures were visually reviewed; they are not recorded screenshot baselines. Financial values, price freshness notices and investment contents determine the final populated layout, so the skeleton does not promise pixel-identical content heights.

## Verification

Passed:

- `./gradlew :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintDebug :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain` — 570 shared tests, zero failures/errors/skips, Android builds and both iOS compilations succeeded.
- After adding the UI regression test, `./gradlew :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintDebug --console=plain` — passed. An initial test compilation failed because it referenced internal `LocalReducedMotion`; that reference was removed before this successful build.
- `adb -s emulator-5590 shell am instrument -w -e class dev.orestegabo.portfolio.investments.DashboardLoadingLayoutTest dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner` — one test passed, covering four theme/font profiles, on a cold disposable API 36.1 emulator. No real account data used.
- `python3 scripts/check-kmp-packages.py`, `python3 scripts/check-kmp-ui.py`, `python3 scripts/sync-design-colors.py --check`, `git diff --check` — passed.
- `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'id=00008101-000E3DD11E03001E' -destination-timeout 30 -derivedDataPath /tmp/inzira-share-timeline-ios -allowProvisioningUpdates build` — succeeded; installed and launched on the connected iPhone.

Lint reports zero errors and six existing warnings: three available toolchain version notices and three transitive Bouncy Castle trust-manager findings. No dependency/security configuration changes were made here. Physical-iPhone loading appearance, screen readers and reduced-motion behavior were not manually verified. No performance measurements or screenshot baseline assertions were performed. No backend, API, financial calculation or persistence changes.
