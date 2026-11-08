# Android stability and iPhone feature parity

Checked 8 November 2026 against the current working tree. Android runtime checks used a dedicated Pixel 8 Pro emulator, Android API 36.1, arm64, 1080 × 2400. Tests used synthetic documents/accounts and local mock servers; they did not authenticate to production accounts.

## Result

The tested Android feature flows pass. This establishes emulator coverage, not certification of every Android device or complete iPhone parity. Remote push is currently unconfigured, and capture protection has a deliberate platform-specific presentation difference.

| Check | Result |
| --- | --- |
| Core JVM tests | 65 passed |
| Shared Android host tests | 365 passed, no test exclusions |
| Parser JavaScript tests | 81 passed |
| Python release/tooling checks | 9 passed |
| Native Android instrumentation | 28 distinct feature tests passed across the suite and targeted reruns |
| Optional startup measurement fixture | Not invoked; requires explicit benchmark setup |
| Release lint | 0 errors/fatals, 65 warnings |
| Debug app and instrumentation APKs | Built successfully |
| Release app bundle | Built successfully; store signing/publication not verified |

The initial native suite ran 29 tests: 21 passed, six failed, and two opt-in fixtures were skipped. After correcting stale fixture assumptions, all six failing tests passed targeted reruns. The guest persistence fixture was then explicitly run through write, force-stop/read, and reboot/read/cleanup stages, bringing coverage to 28 distinct native tests. The remaining skipped item is a startup benchmark preparation fixture, not a feature assertion.

Native coverage includes login/invitation/password visibility, French and theme switching, onboarding, guest entry and CRUD, encrypted persistence and corruption/key-loss handling, explicit guest migration, document import/review/retry, local PDF extraction and renderer recovery, library navigation, PDF rendering/zoom/sharing, cancellation, target settings and notification editing, issue-report review, bond/share details in both themes, receipt confirmation/reversal, yearly coupons, and capture protection.

The new calendar test verifies that a non-owner can open the shared application form during the issuance month before the bid window opens, view the PDF in-app, and return to the calendar. It asserts one download, no automatic sharing, and explicit sharing of the original bytes without downloading again. The owner upload control is absent for that non-owner. Owner authorization and shared-form matching/upload behavior also have shared test coverage.

## Corrections made during this check

- Updated stock history tests to use the current `ChartPeriod` enum, restoring six tests to the Android host run.
- Updated login labels and explicitly initialized/restored onboarding state in the calendar fixture.
- Made the optional capture experiment test explicitly opt in and restore its preference.
- Refreshed accessibility caches and used accessibility clicks/scrolling in UI fixtures; waited for edited target text before saving. Receipt confirmation and reversal assertions remain intact.
- Added the native calendar application-form preview/share regression test.

These corrections affect tests only; this audit did not change production Android runtime behavior.

## Parity limits and remaining checks

- **Remote push:** `app/androidApp/google-services.json` is absent. The Google Services plugin is conditional, and `MainActivity` safely reports push unavailable when Firebase is unconfigured. In-app inbox and notification settings are covered; Android remote delivery needs Firebase setup and a real-device end-to-end test.
- **Capture protection:** the normal Android implementation masks financial figures on the screen as well as in screenshots/recordings. Protected original PDF windows use secure-window protection. iPhone's secure canvas can retain readable on-screen figures while protecting captures. Android's capture-only surface experiment is debug-only and opt-in; its test passing does not make it the release implementation.
- **Hardware and OS coverage:** physical biometric enrollment/unlock, hardware-backed Keystore behavior across vendors, real-device scroll frame timing, older supported Android releases (minimum API 24), and background push delivery were not certified by this emulator run.
- **PDF/OCR:** native rendering/import fixtures passed. Android uses a different renderer/OCR stack from iPhone; arbitrary scanned document quality still needs representative device testing.
- **Lint warnings:** primarily dependency-update suggestions and launcher/icon/build configuration hygiene. No dependency or SDK upgrade was bundled into this audit.

## Reproduction

```sh
./gradlew :core:jvmTest :app:shared:testAndroidHostTest \
  :app:androidApp:lintRelease :app:androidApp:assembleDebug \
  :app:androidApp:assembleDebugAndroidTest :app:androidApp:bundleRelease
node --test tests/*.test.mjs
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
adb -s DEVICE install -r app/androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s DEVICE install -r app/androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
adb -s DEVICE shell am instrument -w -r \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

The staged persistence test accepts `-e guestPersistenceStage write`, `read`, or `cleanup` with class `dev.orestegabo.portfolio.investments.GuestPersistenceTest#guestInvestmentsSurviveProcessRestartAndReboot`. Run the stages in separate test processes, force-stop before `read`, reboot before `cleanup`. It uses only its dedicated synthetic store.

Build/run logs and synthetic screenshots for this session are under `/private/tmp/struct-android-*`; temporary files are not durable project artifacts.
