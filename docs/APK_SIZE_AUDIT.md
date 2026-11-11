# Android package size audit

The workspace's pre-cleanup APKs were 99.18 MiB (debug) and 88.22 MiB
(release). They bundled full-resolution unused photographs, redundant OCR
engine builds, and unshrunk dependency code. A clean optimized release is
24.44 MiB, about 72% smaller. The final debug APK is 41.04 MiB, about 59%
smaller than its previous 99.18 MiB.

## What changed

- Removed ten unreferenced Kigali photographs and ten obsolete static
  onboarding images. Removed the unused Compose template graphic. Total
  source drawable removal: 38.51 MiB. The active sign-in and dashboard photos,
  logos, and animated onboarding layers remain.
- Removed standalone Tesseract Wasm payloads that were duplicated inside the
  embedded `.wasm.js` files, and legacy OCR cores that the OEM 1 importer never
  requests. Kept baseline, SIMD, and relaxed-SIMD LSTM engines, the original
  English model, PDF.js codecs/fonts, and upstream license notices. OCR stays
  local and offline, with its existing model and engine selection.
- Enabled R8 minification and Android resource shrinking for release. Explicit
  keep rules protect the JavaScript bridge methods called by the local engine.
  Release mapping files remain under `app/androidApp/build/outputs/mapping/release/`.
- Removed unused Compose tooling/preview dependencies. Replaced the extended
  Material Icons dependency with the 75 upstream icon definitions actually
  referenced by this project, preserving vector geometry, mirroring, and
  Apache license headers. Common icons/builders still use `material-icons-core`.
- Removed uncalled legacy review/entry surfaces and guest/evidence helpers.
  Current import conversations, reconciliation, native PDF operations, server
  code, financial calculations, tests, and design source packs remain.
- Restricted asset sync scripts so they do not restore removed OCR or static
  onboarding payloads on the next regeneration.

## Release APK payload

| Category | Before (MiB) | After (MiB) |
| --- | ---: | ---: |
| Photography | 40.06 | 2.43 |
| OCR cores | 16.01 | 4.19 |
| English OCR model | 10.40 | 10.40 |
| DEX code | 16.03 | 2.69 |
| PDF import runtime | 2.61 | 2.61 |
| Shared UI/data assets | 1.73 | 1.18 |
| Android resources/metadata | 1.14 | 0.71 |
| Native libraries | 0.06 | 0.06 |

Values are compressed ZIP payloads; archive overhead means their sum differs
slightly from APK file size. The comparison baseline is the existing workspace
artifact captured before cleanup, not a newly rebuilt historical checkout.

## Build and prevent regressions

Compose's generated resource copies retained deleted files during incremental
builds. Clean once after this cleanup to avoid packaging stale photographs:

```sh
./gradlew :app:androidApp:clean :app:shared:clean :core:clean
./gradlew :app:androidApp:assembleRelease
python3 scripts/apk-size-report.py \
  app/androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk \
  --verify-release --max-mib 30
```

The verifier checks package size, exact OCR core selection, the offline model,
required photography, and absence of a developer preview adapter. Use the
release APK or a release App Bundle for distribution. Debug APKs deliberately
retain debugging information and unoptimized runtime code. Without upload-key
configuration, the generated release APK is unsigned; sign it through the
normal Android Studio/release signing workflow before distributing it.

When adding icons, regenerate the curated set using
`scripts/sync-material-icons.py` and the official version 1.7.3 sources JAR.

Android's official guidance describes [R8 optimization](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)
and [APK size reduction](https://developer.android.com/topic/performance/reduce-apk-size).
Tesseract documents [automatic core selection](https://github.com/naptha/tesseract.js/blob/master/docs/api.md);
all LSTM variants selected by the current importer remain bundled.

## Validation

- Clean debug/release APK builds and release lint passed.
- Shared Android host tests: 385 passed; core JVM tests: 70 passed.
- JavaScript parser/calculation tests: 82 passed; script tests: 9 passed.
- Three existing device regressions passed in a disposable read-only emulator:
  local projections, text-PDF import, and a complete private import workflow
  including scanned-PDF OCR, review, save, preview, duplicates, and malformed input.
- A temporary copy of the optimized release was signed with the local Android
  debug certificate for QA only. After fresh guest startup with the emulator in
  airplane mode, the actual document picker imported a synthetic share-purchase
  PDF and opened the populated review form. Nothing was confirmed or uploaded.
  This verifies the minified WebView bridge and common parser in the release app.
- The baseline embedded OCR core initialized successfully without any separate
  `.wasm` file, and its existing English model initialized successfully.
- Shared iOS simulator Kotlin compilation passed after the source/icon cleanup.
- Package verification passed with a 30 MiB release budget. The active drawable
  scan found no remaining unreferenced candidates.

An early device-test attempt was interrupted while generated build outputs were
being cleaned and the emulator reported a native class-loading crash. The final
clean-build device run completed all three tests with zero failures. This is why
release size and validation above use the clean final artifacts.
