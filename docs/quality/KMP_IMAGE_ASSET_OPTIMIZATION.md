# Lossless sign-in and onboarding image optimization

Date: 2026-11-17. Production scope: one sign-in JPEG, twenty onboarding PNGs, their rendering adapter and reproducible asset generators. No financial, persistence, networking, widget, security, navigation or 3D logo implementation changed. No commit or push was made. The earlier optional-feature audit files remain separate pending work.

## Results

| Asset group | Before | After | Change |
| --- | ---: | ---: | --- |
| Sign-in JPEG | 2,012,637 bytes | 1,937,453 bytes | 3.7% smaller; same 3024 × 4032 resolution and decoded RGB pixels |
| Twenty onboarding PNGs | 755,149 bytes | 587,562 bytes | 22.2% smaller together |
| Onboarding full RGBA8 pixel budget, both themes combined | 79,462,400 bytes | 23,631,568 bytes | 70.3% fewer bitmap pixels/bytes |
| Four calendar layers in one theme | 27,238,400 RGBA8 bytes | 4,819,456 RGBA8 bytes | 82.3% lower pixel budget |
| Current optimized unsigned release APK | 24.01 MiB before this task | 23.78 MiB after | Whole artifact comparison; code/packaging overhead also varies |

The exact combined source-image saving is 242,771 bytes. Two individual calendar base PNGs are slightly larger after re-encoding, while the group total is smaller and their decoded area is reduced. Pixel-budget figures assume four bytes per pixel; they are not measured application RSS, GPU allocation, battery usage or peak memory. Both themes are not necessarily resident together. The JPEG's decode memory is unchanged because its resolution was deliberately preserved.

## How appearance is preserved

- `jpegtran -copy all -optimize -progressive` changes JPEG coding without resampling, requantizing or discarding metadata. `djpeg -dct int` decoded the original and optimized files to identical PPM bytes, SHA-256 `4908fc3ecbe4587b466daca5caa33f38bd5040cd2c3164cccd3a83564fc04541`.
- `scripts/onboarding_png.py` decodes the trusted RGBA8 generator output, trims only fully transparent margins, retains a four-pixel transparent filtering gutter and preserves color metadata. It does not resize or quantize visible pixels. All twenty cropped RGBA regions were checked byte-for-byte against the pre-change files; discarded pixels have zero alpha.
- Both onboarding generation scripts emit the optimized PNGs and their `.generated.kt` bounds. Their existing `--check` modes now verify positioning data as well as image bytes, so regeneration cannot silently restore oversized canvases or leave stale crop metadata.
- `OnboardingImagePainter` reports the original intrinsic canvas. Layers keep their existing size, offset, Fit behavior and rotation/scale pivots. It rounds the original full destination once, then draws the crop in source-pixel coordinates; separately rounding each cropped destination caused a small line-position difference during the first comparison and was corrected.
- Import chip, calendar, notices, reports and document-stack artwork use this adapter. Story timing, vectors, labels and all existing features remain.

## Visual evidence

[Evidence JSON](evidence/2026-11-17/lossless-image-optimization.json) records per-image dimensions, crop coordinates, byte counts, hashes, source-pixel/color-metadata equivalence and eight settled Android screen comparisons (four pages × two themes).

The comparison used the pre-change APK and final APK on the same disposable API-36.1 emulator. Reduce Motion was enabled for deterministic completed artwork and restored afterwards. Only the system clock/top 100 pixels and bottom 60 system-navigation pixels were excluded. App content and artwork were retained. The initial non-settled captures were not treated as valid visual baselines: one captured the old report before asynchronous image decoding had completed. An opt-in `settledArtworkSnapshots` test argument now allows reproducible captures without changing normal scenario assertions.

The two overview pages were pixel-identical in the compared region. Other pages have small GPU filtering/rounding differences: mean absolute channel differences ≤0.028 on the 0–255 scale, with maximum per-channel difference 18 on localized edges. Source image pixels are exact; the rendered UI is not claimed to be bit-identical. The calendar and report before/after renders were visually inspected and retain their design and placement. No golden reference was recorded or overwritten.

All 21 optimized resources were verified byte-identical to their current source in both the Android debug APK and signed iPhone Debug app. The iPhone build was installed for user review; no automated or manual physical-iPhone screenshot comparison is claimed. Tablets/landscape were not newly visually profiled.

## Verification

Passed:

- Shared Android host suite: 561 tests; Android Debug compilation, both iOS target compilations and Android lint.
- Python script suite: 41 tests, including all PNG filters, alpha/color-preserving cropping, transparent gutters, empty/opaque images, corruption rejection, generated-art freshness and the original JPEG pixel hash. No additional runtime dependency was introduced.
- Package/module, UI inventory and design-color checks; `git diff --check`.
- Android optimized release build and existing `apk-size-report.py --verify-release --max-mib 30` guard; offline OCR model/variants and other required assets remain.
- Signed iPhone Debug build and installation.
- Complete `OnboardingUiTest` before/after with settled captures, including themes, French, larger text, skip/completion/replay and preference checks.
- Final configured Android Test Orchestrator run: both complete `OnboardingUiTest` and `DashboardBrandMarkTest` passed with normal system animation settings.

Commands used for KMP verification (local JDK 21 configured in `JAVA_HOME`):

```sh
./gradlew :app:shared:testAndroidHostTest :app:androidApp:assembleDebug \
  :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintDebug \
  :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
ANDROID_SERIAL=emulator-5554 ./gradlew :app:androidApp:connectedDebugAndroidTest \
  :app:androidApp:lintDebug \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.orestegabo.portfolio.investments.OnboardingUiTest,dev.orestegabo.portfolio.investments.DashboardBrandMarkTest
./gradlew :app:androidApp:assembleRelease
xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'id=00008101-000E3DD11E03001E' \
  -derivedDataPath /tmp/inzira-logo-ios -allowProvisioningUpdates build
```

The existing 3D test exposed a capture race during validation: after toggling Reduce Motion, waiting for the UI thread to be idle did not guarantee the new frame had reached the Surface buffer sampled by PixelCopy. It failed in the manual combined run and was later reproduced under the configured runner. A test-only host marker and frame-commit fence now wait for the updated rendered scene. The exact zero-changed-pixel assertion, positive motion checks, themes and full background/resume scenario remain intact. No production 3D behavior or security setting was weakened. The final isolated run passed; earlier failures were not silently discarded.

The full inventoried UI suite, device performance/peak-memory benchmarks and physical Honor/iPhone visual matrix were not run for this asset change. Existing toolchain notices remain visible.
