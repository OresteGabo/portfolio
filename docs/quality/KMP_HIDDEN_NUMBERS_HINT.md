# Hidden-number guidance

Implemented and verified 2026-11-19.

When the explicit Hide numbers setting is on, tapping a masked `FinancialText` value or `HiddenFinancialChart` opens a short accessible help dialog: “To show numbers, turn off Hide numbers in Settings.” It has a Got it action and supports normal dismissal. The shared composition-local callback controls presentation only; it never reveals figures or changes persisted privacy settings. Touches on the masked value show help rather than activating an enclosing card; surrounding card actions remain available. English and French strings are provided.

The existing Android and iOS Home Screen widgets publish only generic events/dates and public issuance facts, without financial amounts or credentials. Their shared calendar route now requests the same help after sign-in/unlock if Hide numbers is enabled. It consumes each route once and retains the requested calendar date. No widget snapshot schema or native URL contract changed. Capture protection alone does not activate this guidance.

A first snackbar implementation failed the physical-tap regression: the dashboard total's existing explanation dialog covered the snackbar. The passing implementation uses a help dialog above existing dialogs and consumes the masked value's tap. The original failing log is retained at `/tmp/inzira-hidden-number-ui.log`; assertions were not relaxed.

## Verification

- `JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintDebug :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain` — passed. 587 shared tests, zero failures/errors/skips. Existing compiler deprecations and six lint dependency/toolchain notices remain.
- After the final test scenarios were added, `./gradlew :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintDebug --console=plain` — passed with the same JAVA_HOME.
- `adb -s emulator-5590 shell am instrument -w -e class dev.orestegabo.portfolio.investments.HiddenNumbersHintTest dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner` — passed (one test, 42.788 seconds). Synthetic data on a disposable emulator only. Covers physical tap, accessible activation, masked amounts inside an existing dialog, hidden chart activation, widget calendar entry with hiding enabled/disabled, unchanged privacy state and no exposed fixture balance. Both native widget hosts use this shared entry route; actual Home Screen tapping was not manually tested on either platform in this task.
- Visually inspected `/tmp/inzira-hidden-numbers-hint.png`: guidance fits and figures remain concealed. This was a light-theme Android capture; no dark-theme or physical iPhone screenshot verification is claimed.
- Signed iPhone Debug `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'id=00008101-000E3DD11E03001E' -destination-timeout 30 -derivedDataPath /tmp/inzira-share-timeline-ios -allowProvisioningUpdates build` — passed.
- `python3 scripts/check-kmp-packages.py`, `python3 scripts/check-kmp-ui.py`, `python3 scripts/sync-design-colors.py --check`, `git diff --check` — passed.

No backend, financial calculation, masking algorithm, screenshot-protection policy or persisted-data changes were made for this feature. The native widgets' generic date labels continue to remain visible by design.

The signed build was successfully installed and launched on the connected iPhone with `devicectl`. The user can review the shared help popup there; no real account values were inspected or altered during verification.
