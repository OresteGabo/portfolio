# Portfolio locked screen

Date: 2026-11-17. Scope: shared Android/iOS UI only; no backend, authentication policy, financial calculation, advertising integration or dependency changes.

## Design and implementation

- `UnlockScreen.kt` keeps the existing authentication actions and navigation ownership. A clear primary unlock button is followed by an outlined password alternative, then wrapping guest and legal/privacy actions with icons. Public legal information remains available before unlocking.
- `UnlockBrandArtwork.kt` reuses the existing projected solid logo and metallic fixed hexagon. A spotlight and restrained orbital lines frame a six-second full turn followed by a six-second reverse turn. The fixed surround remains stationary. This adds no bitmap, video or external model asset.
- The animation never delays authentication, repeats until the user pauses it, pauses below the resumed lifecycle state, and respects the existing reduced-motion preference. A 48dp pause/play control is available alongside the artwork; its choice survives backgrounding and restoration. Reduced motion removes the control and shows a static angled mark. Its phase is read while drawing rather than triggering form recomposition on every frame. No runtime performance improvement is claimed.
- A static bundled “Your way forward / Bonds · Shares · Documents” panel supplies brand content without private values, account identity, network requests or investment promises. This is not a claim that a locked app can safely display arbitrary content: the existing gate, session validation, screen-capture protection and secure storage remain essential.
- Theme tokens, scrolling, a maximum content width, compact artwork for short windows, scalable text, heading/live-region semantics and minimum 48dp action targets support accessibility. Decorative art has no screen-reader description. French translations cover the new strings.

## Accessibility, privacy and advertising decisions

Official sources accessed 2026-11-17:

1. [W3C WCAG 2.2, understanding SC 2.2.2](https://www.w3.org/WAI/WCAG22/Understanding/pause-stop-hide.html): automatically moving content lasting more than five seconds alongside other content generally needs a pause/stop/hide mechanism, subject to the criterion's exceptions. The initial design used a finite turn; the requested repeating version provides a persistent pause/play control and also supports reduced motion. That alone does not establish WCAG conformance.
2. [Apple Motion guidance](https://developer.apple.com/design/human-interface-guidelines/motion): respect accessibility preferences and communicate without relying exclusively on motion. All authentication instructions and actions remain usable with static artwork.
3. [Google Play advertising policy](https://support.google.com/googleplay/android-developer/answer/9857753?hl=en) and [Apple App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/), especially 2.5.18 and 5.1.2: advertisements must meet presentation and privacy requirements; tracking has additional conditions. No ads, tracking SDK, personalized promotion, impression reporting or new permission request is introduced. This is an in-app authentication gate, not an operating-system lock-screen advertising surface.
4. [Rwanda DPO, processing and quality of personal data](https://dpo.gov.rw/dpp-law/processing-and-quality-of-personal-data), Articles 5–8: privacy protections and consent requirements where consent is the processing basis, including withdrawal. No new personal-data processing is added by the bundled artwork. Future advertising needs a separate assessment of the provider, data flows, jurisdiction, lawful basis, disclosures and any applicable consent/ATT requirements. Do not infer that every contextual advertisement requires ATT or that consent is always the correct legal basis.

The design implements focused UI safeguards, not legal advice or certification. No invented legal text, operator credentials or regulatory status was published. Future ads should never gate unlock, mimic security controls, expose account data or imply guaranteed returns.

## Initial-design verification

Environment: JDK 21 (`ms-21.0.9`), locally configured Android and Apple toolchains. Synthetic fixtures only; no production credentials or financial documents used.

Commands and results:

```sh
./gradlew :app:shared:testAndroidHostTest :app:androidApp:lintDebug \
  :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
./gradlew :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest --console=plain
python3 scripts/check-kmp-packages.py
python3 scripts/check-kmp-ui.py
python3 scripts/sync-design-colors.py --check
git diff --check
```

All commands above passed for the final slightly turned resting pose. The shared host suite ran 561 tests with zero failures/errors/skips. Lint keeps previously reviewed compatibility notices rather than hiding them. UI inventory validation does not itself execute device tests.

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:androidApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.orestegabo.portfolio.investments.UnlockUiQualityTest,dev.orestegabo.portfolio.investments.AuthenticationUiQualityTest \
  -Pandroid.testInstrumentationRunnerArguments.uiAuditSnapshots=true --console=plain
```

Four complete tests passed under configured Android Test Orchestrator before the final resting-angle adjustment. They cover native-unlock cancellation/retry, slow verification/password cancellation, private locked state, legal/guest navigation, authentication failure/input retention, theme/language/large-text layouts, minimum action sizes, finite motion, background pause and reduced motion. No assertions or scenarios were disabled.

An additional direct-run preview attempt was interrupted when a concurrent share-order test run replaced the target app on the shared emulator. This was not counted as a passing check. Final direct-run results on an isolated emulator and visual observations are recorded below.

```sh
adb -s emulator-5556 shell am instrument -w \
  -e class dev.orestegabo.portfolio.investments.UnlockUiQualityTest,dev.orestegabo.portfolio.investments.AuthenticationUiQualityTest \
  -e uiAuditSnapshots true \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

Final result: `OK (4 tests)`, 90.582 seconds, on the separate `Medium_Phone_API_36.1` emulator. These are the same intact scenarios, including cancellation/lifecycle assertions, executed after the resting-angle adjustment. Four synthetic screenshots were inspected: light/dark at normal English text and light/dark French at 1.8× text in a constrained 360×540dp app viewport. Normal layouts show the complete primary and recovery hierarchy. Large text wraps without horizontal clipping; the short viewport requires vertical scrolling, and all four actions were verified reachable with minimum targets. These diagnostic captures are not new screenshot-test goldens.

```sh
xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'id=00008101-000E3DD11E03001E' -destination-timeout 30 \
  -derivedDataPath /tmp/inzira-logo-ios -allowProvisioningUpdates build
```

Signed iPhone Debug build passed for the final pose; installed and launched normally on the connected iPhone. No secure sign-in bypass was added. Physical iPhone screen-reader/layout behavior has not been manually verified; user review remains necessary. TalkBack/VoiceOver traversal, real biometric prompts and a formal contrast/compliance assessment are not certified by these tests.


## Back-and-forth revision

The user requested repeating forward/reverse spin and very transparent hexagons in the background. `UnlockBackdrop.kt` draws six static edge motifs from canonical brand paths; three contain faint initial outlines. A clipped decorative canvas keeps them out of adjacent app chrome, and no backdrop animation competes with the logo or text. Geometry is remembered and requires no new image assets. The pause icon uses one additional unchanged Apache-licensed vector from the already-used upstream Material source archive, rather than adding the full extended library.

The motion regression now crosses both reversal points, verifies continued motion, checks exact pixel stability after user pause and backgrounding, and verifies play resumes it. The original reduced-motion and lifecycle assertions remain. Latest verification results follow; the initial finite-motion results above describe the preceding design, not the new motion policy.

Latest verification (back-and-forth version):

- `./gradlew :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:shared:testAndroidHostTest :app:androidApp:lintDebug :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain` — passed; 561 host tests, zero failures/errors/skips. An initial compile caught a missing curated Pause icon; the upstream vector was added and the complete command passed afterward.
- `ANDROID_SERIAL=emulator-5556 ./gradlew :app:androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.orestegabo.portfolio.investments.UnlockUiQualityTest,dev.orestegabo.portfolio.investments.AuthenticationUiQualityTest -Pandroid.testInstrumentationRunnerArguments.uiAuditSnapshots=true --console=plain` — passed, four tests, zero skipped/failed, 3m23s, using configured Test Orchestrator.
- Package boundaries (470 handwritten source/test files), UI inventory/golden integrity, semantic color check and `git diff --check` passed.
- The same signed iPhone `xcodebuild` command above passed; installed and launched normally on the connected iPhone. No physical iPhone visual or VoiceOver verification is claimed.
- Fresh normal-English screenshots in both themes and a constrained 1.8× French light screenshot were inspected. Edge motifs remain faint, central actions remain distinct, and the short large-text viewport remains scrollable. The new dark French capture was incomplete when copied before Orchestrator cleanup and could not be visually inspected; its layout/action assertions passed. Continuous motion screenshots are diagnostic captures at varying angles, not deterministic golden references.

All changes remain uncommitted. Unrelated image optimization, share-order and build work was preserved.

Header follow-up: the locked root now omits the duplicate small toolbar logo; nested Back actions are retained. `./gradlew :app:shared:compileAndroidMain :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain` and the signed iPhone build passed. `git diff --check` passed. Device tests were not rerun for this one-line visibility adjustment.
