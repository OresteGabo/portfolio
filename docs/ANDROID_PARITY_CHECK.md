# Android stability and iPhone feature parity

Checked 10 November 2026 against app commit `2e82fed`. Tests used isolated emulators, synthetic accounts/documents, local mock servers and offline guest workspaces. No production account was authenticated, no production data was changed, and no email or push notification was sent.

## Result

No blocking app failures were observed in the tested Android flows. Debug and release builds pass. This is broad emulator and automated coverage, not certification of every feature against the production backend or every physical Android device.

Remote push remains unconfigured. Capture protection deliberately presents differently on Android: the normal implementation masks financial numbers on screen as well as in captures. These limits prevent claiming complete iPhone parity.

## Validation results

| Check | Result |
| --- | --- |
| Core JVM tests | 70 passed; no failures, errors or skips |
| Shared Android host tests | 384 passed; no failures, errors or skips |
| Parser JavaScript tests | 81 passed |
| Python release/tooling checks | 9 passed |
| Complete native Android suite | 31 listed: 29 passed, 2 opt-in fixtures skipped; no failures |
| Explicit encrypted persistence fixture | Write, force-stop/read, and reboot/read/cleanup all passed; covers the feature fixture skipped in the ordinary suite |
| Native checks on a 16 KB device | 7 passed: local PDF parsing/calculation, native rendering/zoom/damaged-file cleanup, encrypted storage, and private PDF/OCR import |
| Debug/release builds | Both APK variants built successfully |
| Full debug lint | 0 errors/fatals, 63 warnings |
| Full release lint | 0 errors/fatals, 65 warnings |
| Release native-library alignment | All packaged ELF load segments have at least 16 KB alignment; APK `zipalign -c -P 16 4` passes |
| Release startup | Home rendered successfully on both tested OS/page-size configurations |
| Release backup via native document picker | Encrypted export, file selection, decryption, explicit replacement confirmation and restore passed |

The full native suite took approximately 329 seconds. Its two ordinary-suite skips are intentional: the staged restart/reboot persistence fixture and the disposable-device startup preparation fixture. Persistence was explicitly exercised afterward. Startup preparation was explicitly invoked only as setup for the release checks. In total, 30 distinct feature tests were exercised, with seven overlapping native checks additionally repeated on the 16 KB device.

The release APK is unsigned by the project when upload-signing credentials are absent. Runtime testing used a temporary copy signed with the existing local Android debug key. This is a local QA artifact, not a store-signed release or a publication check.

## Environments

| Environment | Configuration |
| --- | --- |
| Main feature-suite emulator | Pixel 6 profile, Android 16 / API 36 (36.1 image), arm64, 4,096-byte pages, 1080 × 2400 |
| Additional native/release emulator | Pixel 6 profile, Android 17 / API 37 `google_apis_ps16k` image, arm64, 16,384-byte pages, 1080 × 2400 |

The Android 17 image is additional compatibility coverage, not a claim of broad device certification. The app still targets API 36 and declares minimum API 24.

`am start -W` reported cold Activity launch times of 1,177 ms and 998 ms respectively. These are emulator Activity-launch measurements, not physical-device scrolling benchmarks, authentication timings, or time-to-complete-network-sync guarantees.

## Feature coverage

| Area | Coverage |
| --- | --- |
| Sign-in and onboarding | Login/invitation form, password reveal, translated copy, onboarding completion/restart, English/French and theme switching |
| Portfolio and calculations | Bond/share valuation, acquisition costs, monthly goals, charts/history data, portfolio/annual-report generation and financial references through core/shared/parser tests; native projection engine and detail layouts in both themes |
| Calendar and pending purchases | Form preview before explicit sharing, non-owner upload control, both parsed-form and older-server bid paths, formatted 100,000-step amounts, automatic bond selection, pending Home visibility, and saved-purchase privacy |
| Purchases and documents | Import/review/retry, duplicate handling, links between records and library, document grouping/filter/expand/download, PDF rendering and zoom, malformed-PDF cleanup, cold/warm incoming PDF intents, read-only share grants, local renderer recovery and cancellation |
| Scanned PDFs | A synthetic image-only equity PDF was imported through the native private flow with `usedOcr` verified; arbitrary real-world scan quality is not certified |
| Guest mode | Entry/switching, CRUD/deletion, offline interaction, encryption/tampering/key-loss handling, explicit migration review/transfer and original-data retention |
| Persistence | Synthetic bonds, shares and coupon receipt states survived a process restart and emulator reboot |
| Cash and share sales | Native sale review, remaining-share updates and correction; core/shared ledger, deposits/withdrawals/dividends and backup restoration checks |
| Notifications and settings | In-app target notification editor, settings target editor, coupon confirmation/reversal and yearly coupon navigation; shared notification/device activity/preferences coverage |
| Privacy | Foreground/background capture behavior, protected PDFs, experimental capture-only path, number hiding, and private local document processing |
| Reports and support | Shared report/annual-report generation checks, PDF sharing infrastructure, issue-report review/fallback/discard without sending mail |
| Portable backup | Shared crypto/reference-envelope and nonempty restore/integrity tests, plus actual release UI export/restore of an empty synthetic workspace through Android's Save/Open dialogs |

The native release backup export produced a 373-byte `SCBK1` encrypted envelope. It did not contain the plaintext `workspace` marker. The restore returned the visible `Backup restored.` success state. This verifies the Android document-provider lifecycle and native crypto path; an actual Android-to-iPhone file transfer was not performed during this audit.

## Remaining limits

- **Remote push:** `app/androidApp/google-services.json` is absent. Firebase configuration is conditional, and the app reports remote push unavailable without it. In-app notifications are covered; actual foreground/background/terminated FCM delivery needs Firebase configuration and end-to-end testing.
- **Capture presentation (updated 11 November 2026):** Android installs its capture-only surface automatically in debug and release on Android 13+. Manual number visibility is independent of capture protection. The ordinary window receives masked pixels and the excluded surface shows the readable live UI. This uses a private SurfaceControl API, so device/OS support varies. Until the surface is ready, or if it is unavailable, FLAG_SECURE blocks captures while leaving live numbers readable. Protected original PDFs also use secure-window protection. iPhone retains its existing secure canvas.
- **Physical hardware:** enrolled biometrics/device-credential authentication, vendor Keystore behavior, actual shake gestures and scrolling frame timing were not certified on a physical Android phone.
- **Older OS versions:** minimum-supported API 24/25 devices were not available in this run. Compile/lint guards and crypto fallback checks do not replace physical/runtime coverage there.
- **Production integration:** authenticated account flows use controlled mock servers. Production backend rollout, live account operations, broker/BNR submissions, live remote notifications and store-delivered upgrades were not tested. Recording a bid remains a personal record, not a broker submission.
- **Lint:** outstanding warnings concern dependency/build/target-SDK suggestions, launcher-icon shape/monochrome assets, resource hygiene and KTX suggestions. No runtime API or permission errors failed lint. No dependency/SDK upgrade was bundled into this audit.

No production runtime code needed changing during this audit. The temporary test stores, reboot fixture and emulators were cleaned up.

## Reproduction

```sh
./gradlew :core:jvmTest :app:shared:testAndroidHostTest \
  :app:androidApp:lintDebug :app:androidApp:lintRelease \
  :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest \
  :app:androidApp:assembleRelease
node --test tests/*.test.mjs
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
adb -s DEVICE install -r app/androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s DEVICE install -r app/androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
adb -s DEVICE shell am instrument -w -r \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

For the dedicated persistence method, pass `-e class dev.orestegabo.portfolio.investments.GuestPersistenceTest#guestInvestmentsSurviveProcessRestartAndReboot` with `-e guestPersistenceStage write`, `read`, or `cleanup`. Run stages in separate test processes, force-stop before `read`, reboot before `cleanup`. It uses only its dedicated synthetic store.

Logs are under `/private/tmp/struct-android-full-audit-*` and `/private/tmp/struct-android-16k-audit-*`. They are temporary local evidence, not durable project artifacts. The native Save/Open backup check was performed interactively on the isolated release app; it is not an additional JUnit test.
