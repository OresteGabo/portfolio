# Optional mobile feature cost audit

Date: 2026-11-16. Baseline: `c010137` (dashboard 3D logo). Scope: the KMP app, Android host and iOS app/extensions. No production code, assets, dependencies, backend, website, settings or installed app were changed by this audit. Nothing was removed or committed.

This is a decision inventory, not a list of things that should all be deleted. Decoration is optional; offline importing, reminders and widgets are functional product choices. Whether those features are essential belongs to the product owner.

## Main conclusions

- **The 3D logo is small in the package but is the clearest optional source of continuous rendering work.** Its identified classes account for **18,750 bytes (18.31 KiB) of defined debug DEX code**, with no new image/video/model file or 3D library. That is not an exact release-size saving. Every animation frame still projects geometry, builds its shadow, sorts faces, evaluates lighting and constructs vertex/color buffers. A six-second revolution does **not** mean one calculation every six seconds: it updates at display-frame cadence.
- **The sign-in photograph is the best straightforward decorative size/memory candidate:** 1.92 MiB packaged on Android, also present on iOS, at 3024 × 4032 pixels. A full RGBA8 bitmap would occupy 46.51 MiB before any other renderer allocations. Actual decoder/resident memory was not measured.
- **Onboarding is light in download size but its layered PNGs can be much larger after decoding.** Twenty light/dark PNGs occupy 0.72 MiB compressed. Several individual layers are 1520 × 1120, or 6.49 MiB each as a full RGBA8 bitmap. The calendar uses multiple such layers together. This makes smaller raster dimensions or tighter layer bounds more promising than deleting the onboarding explanation itself.
- **The biggest Android size item is functional, not decorative:** offline OCR's English model and three engine variants occupy **14.59 MiB** of the optimized release. Removing them would remove scanned-PDF recognition unless a tested replacement/delivery design were introduced. iOS uses PDFKit/Vision instead and does not bundle this Android OCR payload.
- Existing native widgets, charts, education and the logo loader are comparatively small. Removing useful functions just because their classes are long would not meaningfully address the largest payloads.

## Measurement and boundaries

Inspected production areas: 252 common UI Kotlin files, 18 shared Android Kotlin files, 23 shared iOS Kotlin files, 10 Android host Kotlin files and 17 iOS Swift files, plus `core`/host build dependencies, all common resources, Android engine assets and packaged APK/app contents. Source search covered animation loops, bitmap loading/caches, PDF/OCR, sensors, capture protection, widget scheduling, notification integration, charts and optional tools.

Measurements are recorded in [the evidence JSON](evidence/2026-11-16/optional-feature-costs.json). MiB means 1,048,576 bytes; KiB means 1,024 bytes.

| Current Android build | APK bytes | Size |
| --- | ---: | ---: |
| Debug | 45,657,459 | 43.54 MiB |
| Optimized release, unsigned | 25,180,647 | 24.01 MiB |

The release was built during this audit with the existing R8/resource-shrinking configuration. It is a measurement artifact, not an APK ready for a friend's installation. No signing credentials were changed. ZIP entry sums exclude archive/signature/alignment overhead. App Bundle delivery and installed disk use are different measurements.

### iPhone baseline

A fresh local **unsigned Release `.app`** build succeeded. Its uncompressed bundle is **66,375,761 bytes (63.30 MiB)**; the main `Portfolio` executable is **62,342,936 bytes (59.45 MiB)**. The currently installed-style Debug bundle measures **98,996,895 bytes (94.41 MiB)**, mostly its 94,206,768-byte debug dylib. These raw directory sizes are **not** IPA/App Store download estimates, install reports, feature removal savings or a comparison on the same basis as compressed APK size. Store archiving, symbol stripping, encryption and delivery can change the result.

Release extensions are modest: **437,158 bytes** for the widget bundle and **241,054 bytes** for the PDF share extension. The two large common decorative resource groups remain the 2,012,637-byte sign-in photo and 755,149 bytes of onboarding images. Android's Tesseract model/engines are absent from the iPhone bundle. Most iPhone bytes are in the combined Kotlin/Compose/application executable; this audit cannot assign a precise marginal iOS size to an individual feature without controlled feature-off link/build comparisons.

### Optimized Android release payload

| Category | Compressed size | Interpretation |
| --- | ---: | --- |
| English OCR model | 10.40 MiB | Scanned-PDF recognition, Android only |
| OCR engines | 4.19 MiB | Baseline, SIMD and relaxed-SIMD LSTM variants |
| PDF import runtime | 2.61 MiB | PDF.js, worker, character maps, fonts, codecs and adapters |
| DEX | 2.90 MiB | All optimized application/framework code together |
| Sign-in photo | 1.92 MiB | One decorative photograph |
| Other shared resources | 1.00 MiB | Includes onboarding, issuer logos, common JS rules and notices |
| Android resources/metadata | 0.77 MiB | Platform resources and package metadata |
| Native libraries | 0.06 MiB | No large bundled 3D engine |

Resource byte amounts below are **current packaged payloads**, not guaranteed net savings. For example, keeping a static onboarding illustration retains some artwork, replacing an OCR engine adds another cost, and shared library code may remain needed by other features. Exact removal savings require a separate feature-off release build.

## A. Decoration and convenience: first decisions

| Priority | Candidate and evidence | Current cost | Choice and consequence |
| --- | --- | --- | --- |
| 1 | **Continuously spinning dashboard logo** — `DashboardBrandMark.kt`, `InziraInitialMesh.kt`, `InziraInitialRenderer.kt`, `InziraMetalFrame.kt`; used only by `PortfolioHero.kt` | 18.31 KiB identified debug code; no new binary assets/dependency. Source shows per-frame face projection, lighting, sorting, path/vertex/list allocation. CPU/GPU time and battery impact are **not measured**. Backgrounding and Reduce Motion already stop motion. | Keep it; play one turn on dashboard entry and settle; or use a static logo. A finite animation addresses ongoing work while retaining the design. Package savings alone are tiny. |
| 2 | **Full-resolution sign-in photo** — `SignInScreen.kt:107`, `drawable/kigali_assets_36653828.jpg` | 2,012,637 packaged bytes; 3024 × 4032; theoretical full RGBA8 decode 48,771,072 bytes | Keep; resize/re-encode to a tested phone-appropriate resolution; or replace with a drawn background. Reducing dimensions can preserve the design and cut decode memory. Only the sign-in page uses this photo; the dashboard already uses drawn effects. |
| 3 | **Layered onboarding illustrations** — `OnboardingScreen.kt`, `OnboardingStories.kt`, `ProcessingChip.kt`, `OnboardingCharts.kt` | 20 PNGs, 755,149 packaged bytes. Calendar phone/notice layers individually decode to 6,809,600 RGBA8 bytes. Lazy pager retains a neighbouring page. Actual simultaneous bitmap residency was not profiled. | Keep; reduce image dimensions/tighten transparent bounds; or flatten each story and retain text/navigation. Removing animation alone does not remove the PNGs. Keep both theme variants unless replaced with genuinely theme-aware artwork. |
| 4 | **Onboarding/opening motion** — `BrandIntroScreen.kt`, `OnboardingMotion.kt`, `ImportFlowDiagram.kt` | No video/3D asset. Finite opening and per-visit stories; the story helper animates once and then awaits cancellation. Offscreen neighbours do not animate; backgrounding pauses stories. | Keep, shorten, or make static. This is a lower steady-state priority than a dashboard animation that repeats indefinitely. The current onboarding is already flat, not 3D. |
| 5 | **Automatic PDF thumbnail previews** — `DocumentThumbnail.kt` | No large bundled asset, but opening Documents can download full PDFs merely to render a first-page excerpt. Files accepted up to 24 MiB by the thumbnail helper; 480-pixel render width; cache bounded to eight entries / 4 MiB of decoded images. The cache limit does not cap transient PDF/download/decoder memory or network use. | Keep; load only on explicit tap; or show a document icon until opened. This can avoid network and native PDF work without removing full PDF viewing. Existing preview failures are nonfatal; hidden/locked content is not decoded. |
| 6 | **Shake-to-hide convenience** — `ShakeGesture.android.kt`, `ShakeGesture.ios.kt`, `App.kt:54` | Tiny code; Android requests `SENSOR_DELAY_GAME`, iOS requests a 0.02-second accelerometer interval (50 Hz). Enabled by default; lifecycle/locked/session gates and disposal already exist. Battery cost not measured. | Keep the preference, choose a less frequent rate after testing, or disable this gesture by default. Preserve the explicit hide-numbers control and capture protection. |
| 7 | **Animated logo instead of a normal loading spinner** — `InziraLoadingMark.kt`, `ProgressContent.kt`, PDF/download/reminder call sites | 6,741 bytes (6.58 KiB) identified debug code; vector paths, no raster/video/library. Runs only while mounted in loading UI and RESUMED; Reduce Motion is static. Multiple visible loading marks can create multiple frame loops. | Keep or use a static mark/standard spinner. Lower priority than persistent decoration; some loading feedback must remain. |
| 8 | **Shimmer skeleton** — `PortfolioLoadingSkeleton.kt` | Small drawing code; repeated transition only while the loading skeleton is composed, static under Reduce Motion. No image payload. | Keep or replace shimmer with static placeholders. Preserve loading/error feedback. |
| 9 | **Background glows, decorative hexagons, import wallpaper, fades and list transitions** — `PortfolioHero.kt`, `ImportWallpaper.kt`, `UnlockScreen.kt`, `AppMotion.kt` | Procedural gradients/paths, not large image files. Static drawing generally redraws with its owner; transitions are brief. No profiling evidence of a bottleneck. | Simplify for visual preference; do not expect megabytes of savings. Do not remove useful interaction feedback merely to remove drawing code. |
| 10 | **Minimum feedback/animation holds** — `PdfImportPipeline.kt`, `PurchasePdfImport.kt`, `PortfolioPullRefresh.kt`, `OnboardingScreen.kt` | No significant size/busy CPU cost; delays affect perceived speed. Imports hold fast results up to a one-second minimum including elapsed work; pull refresh has a one-second minimum plus success/error settling; the launch introduction is 3.6 seconds when played. Cancellation remains immediate where implemented. | Shorten after UX review if speed matters more than the presentation. A coroutine delay is not a CPU-heavy polling loop. These are latency choices, not package bloat. |

## B. Larger functional options: decide based on product value

| Candidate and files | Current cost / when incurred | Consequence of removing or changing it |
| --- | --- | --- |
| **Offline scanned-PDF OCR on Android** — `androidMain/assets/engine/bond-import/eng.traineddata.gz`, `tesseract/*.wasm.js`, `pdf-import.js:119` | 10.40 MiB model + 4.19 MiB engines, roughly 61% of the release APK. The English model expands to 23,466,654 bytes in the APK; engines total 11,701,723 bytes before compression. Native adapter documents AAPT's `.gz` expansion. OCR initializes lazily for pages with insufficient extracted text; worker termination exists. Peak OCR/Wasm memory is not measured. | Text-based PDFs can still be a separate capability, but scans need OCR. Investigate a smaller tested model, different delivery or a native alternative only as a separate compatibility project. Download-on-demand adds first-use network/offline availability trade-offs. Do not move private documents to a server as an incidental size optimization. |
| **Three Android OCR engine variants** — same asset directory | 4,394,317 compressed release bytes together. Only a selected variant is instantiated; three bundled files do not mean three active workers. | A single compatible engine might reduce package size, but dropping SIMD/fallback variants without device/WebView testing can trade speed or compatibility for size. No variant is declared unused by this audit. |
| **PDF.js character maps, fallback fonts and codecs** — `bond-import/cmaps`, `standard_fonts`, `wasm` | 976,758 + 544,537 + 547,847 compressed bytes respectively; subsets of the 2.61 MiB PDF runtime, not additional totals. | Restricting known document formats could make a subset viable, but removing these blindly risks international text, substituted fonts and encoded/scanned pages. This is compatibility data, not decorative media. |
| **Native PDF reader, zoom and document sharing** — `PdfViewerScreen.kt`, platform `PdfDocument` and native actions | On-demand PDF decoding and bitmaps; render size capped at three million pixels (~11.44 MiB RGBA8 for one maximum-size bitmap), with page/region limits. No new large PDF reader SDK in runtime dependencies. Multiple pages, thumbnails, byte buffers and native decoders can exceed one bitmap's footprint. | Keep normal viewing; optionally reduce eager previews/zoom rendering before removing the reader. The full reader is valuable for checking financial evidence. |
| **Widgets** — `NextWidget.kt`, `NextWidgetData.kt`, `HomeWidgetBridge.kt`, `NextWidget/WhatsNextWidget.swift` | Android uses RemoteViews and cached snapshots, with a 30-minute update period. iOS supplies system timelines with a 30-minute next-refresh request, not a continuously spinning in-app UI. The iOS widget extension totals 437,158 bytes in the unsigned Release build (780,952 bytes in Debug); neither is an App Store download size. | Removing widgets saves some code/extension size but sacrifices the feature. No evidence they are a large CPU/package offender. Host snapshot generation on portfolio updates is real work, but no timing bottleneck was measured. |
| **Push reminders and live status** — Android `BondReminderNotifications.kt` / presentation, iOS `BondLiveActivityMonitor.swift`, Firebase runtime | Event/push-driven and OS-rendered. Firebase resolved runtime includes its own code and Play Services transitives; an isolated Firebase class subtotal is not the full marginal cost. No evidence of a client 15-minute portfolio polling service in these paths. | Removing push defeats locked-phone reminders. Removing only live styling will not remove Firebase, since normal push still needs it. Keep this distinct from decorative dashboard animation. |
| **Share-to-import extension, shortcuts and calendar export** — `ShareImport/ShareViewController.swift`, `StructAppIntents.swift`, `CalendarExportHost.swift` and Android intents | Native platform functionality, no large media library. The iOS share extension totals 241,054 bytes in the unsigned Release build (522,937 bytes in Debug), not a store download size. Work is initiated by user/system actions. | Modest size benefit at a meaningful convenience cost. Not a first target for bloat reduction. |
| **Investment simulator, outlook and reports** — `PlanningScreens.kt`, `InvestmentToolsScreen.kt`, `AnnualReport.kt`, `PortfolioReport.kt`, `ReportPdf.kt`, shared engine projection | On-demand calculations/PDF generation, with shared existing calculation infrastructure. Report-related class sample is ~65 KiB of debug code; shared business dependencies mean this is not removal savings. | Keep unless the product does not need the feature. No evidence these make an idle dashboard continuously expensive. Preserve authoritative financial rules if any UI tool is removed. |
| **Education, issuer logos and chart variations** — `education.json`, `CompanyPriceChart.kt`, `InvestmentChart.kt` | Education is 18,112 compressed bytes; eight issuer PNGs total 219,202 bytes. Charts are Canvas/Compose, without a separate heavyweight charting SDK. | Low savings. Keep useful information/accessibility summaries; chart fills/animations can be simplified separately from deleting market history or data sources. |
| **Encrypted guest backups and guest workspace** — `EncryptedBackupScreen.kt`, `BackupCrypto.kt`, guest repositories | User-initiated serialization/encryption/file I/O and supported investment workflows. Not a large bundled optional binary/model. | Removing this sacrifices recovery/offline functionality. Not justified by a class-size count or the animation discussion. |

## Things not to cut as “decoration”

- Capture/screenshot privacy has real runtime cost: both platform backdrop hosts have 50 ms scheduling checks while enabled, and protected scenes may record/convert images during transitions/scrolling. They already gate capture work on revisions/animation windows. This is security behavior, not a cosmetic effect. Profile it separately; do not disable it or conflate it with the optional shake gesture.
- Biometric authentication, token storage, session isolation, financial validation, duplicate-import handling, data freshness and accessible feedback are functional/reliability requirements. This scan is not authorization to weaken them.
- Compose, Lifecycle, Kotlin, networking and serialization are shared app infrastructure. Their package presence is not evidence of waste. `material-icons-extended`, a game/3D engine, Lottie/Rive, bundled marketing video and a separate charting SDK were not found in the current app runtime configuration/artifact inventory.
- No unreferenced common drawable candidates were found by a production-source name/reference scan. This heuristic is not proof that every asset is necessary, but there is no evidence of another large folder of unused photos waiting to be deleted.
- Test fixtures, reports, brand-generation sources and local build caches are developer/repository costs; they must not be counted as phone download size. The generated 3D concept presentation image is not bundled in the app.
- The current local JS/WebView engine is lazy. Its presence does not prove a hidden WebView is constantly running on an idle signed-in dashboard. iOS uses native PDFKit/Vision plus lazy JavaScriptCore; it does not package the Android browser OCR assets.

## Suggested decision order

1. Decide whether the dashboard logo should loop forever or do one polished turn on entry. This is the first purely decorative runtime trade-off to profile on the Honor and iPhone.
2. Resize/re-encode or replace the sign-in photo. Then reduce onboarding PNG dimensions and transparent layer areas. These can preserve the design while reducing known bytes/pixel allocations.
3. Decide whether PDF previews should be automatic or tap-to-load. This addresses real downloads/rendering rather than small icon code.
4. Review the default shake gesture separately from privacy protection. Consider slower sampling only with gesture regression testing.
5. Keep the logo loading feedback, widgets, charts and education unless the product no longer wants them. Their known size is small.
6. Consider Android OCR delivery/model changes only if the remaining ~24 MiB release package is still too large. Validate synthetic text/scanned/encoded PDFs and compatibility before choosing a replacement.

No “maximum savings” total is given: the tables overlap, capabilities share code, and alternatives retain costs.

## Verification and limitations

Executed for this audit:

- `./gradlew :app:androidApp:assembleRelease` with the configured local JDK: passed; current unsigned optimized APK built.
- `python3 scripts/apk-size-report.py <debug.apk> <release.apk> --json /tmp/inzira-footprint-apks.json`: passed.
- `python3 scripts/apk-size-report.py <release.apk> --verify-release --max-mib 30`: passed; selected OCR assets, active photo and absence of preview tooling verified.
- `android analyze <debug.apk> --format=json`: passed; confirms D8 rather than R8 for debug. CLI prepended a download-status line, so the raw file is not a JSON-only document; artifact sizes come from ZIP inspection and the dedicated Python report.
- Android SDK `apkanalyzer dex packages --defined-only <debug.apk>`: passed. The class filters and byte counts are preserved in evidence. These are encoded class-code metrics, not compressed or transitive feature savings.
- Resolved Android `releaseRuntimeClasspath` dependency report: passed.
- `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release -destination 'generic/platform=iOS' -derivedDataPath /tmp/inzira-footprint-ios-release CODE_SIGNING_ALLOWED=NO build`: passed. This builds locally without changing signing configuration, provisioning or either installed phone app.
- Python ZIP inventory, PNG-header dimensions, `sips` JPEG dimensions and common drawable reference scan: executed.

No production code changed, so financial/UI regression suites were not rerun for this read-only report. No fresh device profiler, battery experiment, frame-time benchmark, GPU trace, peak memory recording, authenticated traffic capture or physical iPhone/Honor interaction was performed. Source observations about ongoing work are not measured millisecond, energy or leak claims. Actual store delivery sizes and exact per-feature removal savings remain unmeasured.
