# Verified toolchain/dependency upgrade — 16 November 2026

Follows the independently verified Android lint cleanup (`5083a24`). The user explicitly authorized Kotlin, Compose, Gradle and dependency upgrades as a separate verified phase. All required locally available gates below completed successfully before the upgrade commit. Historical failed attempts remain recorded.

## Selected versions

| Component | Before | Selected |
|---|---|---|
| Gradle wrapper | 9.1.0 | 9.7.0 |
| Android Gradle Plugin | 9.0.0 | 9.3.1 |
| Kotlin / Compose compiler | 2.3.21 | 2.4.21 |
| Compose Multiplatform | 1.11.1 | 1.12.2 |
| Material3 | 1.11.0-alpha07 | 1.12.0-alpha03 |
| Lifecycle | 2.10.0 | 2.11.0 |
| Compile/target SDK | 36 | 37 (minimum remains 24) |
| Ktor | 3.6.0 | 3.6.1 |
| Coroutines | 1.10.2 requested | 1.11.0 |
| Serialization JSON | 1.9.0 requested | 1.13.0 |
| Datetime | 0.7.1 | 0.8.0 |
| Fragment | 1.8.9 | 1.9.1 |
| Firebase BOM | 35.1.0 | 35.2.0 |
| Google Services plugin | 4.5.0 | 4.6.0 |
| HiddenApiBypass | 6.1 | 6.2 |
| Logback (local starter catalog alias) | 1.6.5 | 1.6.7 |

Other direct entries already match applicable stable baselines. Material3 was already an alpha dependency; the selected alpha is the component paired with this Compose release, not an unrelated preview migration. No new framework, target, module or application feature.

Primary references checked 16 November 2026:

- [Kotlin compatibility matrix](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html): 2.4.21 lists Gradle through 9.7.0 and AGP through 9.3.1. We retain those bounds instead of blindly selecting the newer versions suggested by lint.
- [Compose 1.12.2 component set](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.2): Material3 1.12.0-alpha03 and Lifecycle 2.11.0 align with the chosen runtime/UI/foundation release.
- [AGP 9.3 requirements](https://developer.android.com/build/releases/agp-9-3-0-release-notes) support API 37 and require Gradle 9.5 or newer.
- [IDE compatibility table](https://developer.android.com/build/releases/about-agp): Quail 2 / 2026.1.2 or newer supports AGP 9.3. The local application metadata identifies Android Studio Rabbit 2 / 2026.2.2, build AI-262.10968.63.2622.16730706. No IDE GUI sync/build or global IDE update is claimed.
- Direct artifact versions were checked against official Maven Central (`repo.maven.apache.org/maven2`) and Google Maven (`dl.google.com/dl/android/maven2`) metadata; no dynamic versions were introduced.

The wrapper was regenerated with the old wrapper, then again with Gradle 9.7.0. Distribution SHA-256 `84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae` and wrapper JAR SHA-256 `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d` both match the official `services.gradle.org/distributions/gradle-9.7.0-{bin.zip,wrapper.jar}.sha256` endpoints. Distribution URL validation and existing retry/timeout settings remain enabled/preserved.

## Runtime incompatibilities found before acceptance

The first upgraded host/native compilation, host tests, local starter test and lint passed. Focused device tests correctly rejected that initial combination:

1. App runtime packaged AndroidX Lifecycle 2.11.0; instrumentation compile resolved 2.10.0. `UiGoldenTest` failed with `NoSuchMethodError` for `ViewModelStore.put`. `PortfolioModel` publicly extends ViewModel, but the shared module hid the Lifecycle base contract under implementation-only transitive dependencies. A focused `lifecycle-viewmodel` alias is now an **api** dependency in shared commonMain, exposing the actual public superclass to consumers. No forced global resolution or copied platform class.
2. Keeping the older Material3 alpha beside Compose 1.12 caused `OutlinedTextFieldMeasurePolicy` to throw `NoSuchElementException`. Aligning Material3 with the official component set corrected the form crash.

The corrected **ten-test** focused batch passed, including twelve comparisons against the existing reviewed screenshots, chart accessibility/privacy, form reachability, widgets and notification updates. No reference PNG or pixel tolerance changed. The initial failed run and crash log remain diagnostics, not a passing verification claim.

## Android 17 review

[Target-specific changes](https://developer.android.com/about/versions/17/behavior-changes-17) and [all-app changes](https://developer.android.com/about/versions/17/behavior-changes-all) were reviewed. Relevant code checks: widgets use resource vectors and complete updates rather than bitmap payloads; no app-owned `System.load` or MessageQueue reflection was found; sharing grants are explicit; production networking remains public HTTPS; no deprecated ContentCapture disabling call or new LAN permission was introduced. Existing protected-window/fallback behavior remains under privacy regression tests. Native loading, widget callbacks, imports, sharing, navigation and protection require runtime validation on the fresh API-37/16KB emulator. No AI-content-capture or OEM privacy certification is inferred from these checks. SMS, contacts, Bluetooth and background-audio capabilities were not added. Large-screen behavior is covered only to the extent of the actual documented test matrix.

## Firebase and remaining warnings

Clean Android lint reports currently contain **three AndroidGradlePluginVersion notices**, for newer Gradle and the two AGP aliases beyond the selected Kotlin-supported range. Keep these visible; no blanket lint disable, baseline or version-check suppression is added. Final report counts must be rechecked after all edits.

Firebase now deprecates `getToken`/`onNewToken` in favor of opt-in FID registration. The [official API](https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/FirebaseMessaging) still documents the existing token API; enabling its new registration mode changes the identifier/registration workflow. This client upgrade retains the current backend token contract and existing auto-init/privacy guards. It does not silently enable FID mode, replace token values, change endpoints or migrate production messaging. Plan a coordinated client/backend migration separately, with real delivery testing. Compiler deprecation warnings for this retained integration remain visible, alongside existing compiler nullability/icon/JVM-benchmark warnings. Android lint success is not a claim of a warning-free compiler.

The catalog also versions the repository's small Ktor starter, so its existing JVM test is run. No Spring Boot backend, website, authorization endpoint, deployment, credential, signing configuration, schema, financial formula or external parser artifact was modified.

## Verification matrix and commands

- Reference Android: disposable Sequo_References_API_36_1, ARM, API 36.1; existing six-profile/two-theme pixel references remain unchanged.
- New-target Android: fresh Inzira_Upgrade_API_37, installed Google APIs ARM64 16KB-page image; no real user account.
- Native: iosArm64 / iosSimulatorArm64 compilation and device-test source compilation; simulator unit tests on iPhone 16 Pro / iOS 18.6, Xcode 26.3.
- iOS release: unsigned application/extensions and built-package verification required; no distribution signing or physical installation claim.

First complete reference-device command:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home \
ANDROID_SERIAL=emulator-5554 ./gradlew \
 :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :server:test \
 :app:shared:connectedAndroidDeviceTest :app:androidApp:connectedDebugAndroidTest \
 :app:androidApp:lintDebug :app:androidApp:lintRelease \
 :app:androidApp:compileReleaseKotlin :app:androidApp:bundleRelease \
 :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 \
 :app:shared:compileTestKotlinIosArm64 :core:iosSimulatorArm64Test :app:shared:iosSimulatorArm64Test \
 -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
 -Pandroid.testInstrumentationRunnerArguments.uiFrameBenchmark=true -PcomposeReports=true --console=plain
```

## Final results

| Suite | Tests | Failures/errors | Skipped |
|---|---:|---:|---:|
| Core JVM / Android host | 145 / 145 | 0 | 0 |
| Shared Android host | 552 | 0 | 0 |
| Local Ktor starter JVM | 1 | 0 | 0 |
| Core / shared iOS simulator | 144 / 578 | 0 | 0 |
| API 36.1 shared / app | 546 / 54 | 0 | 0 / 4 |
| API 37 / 16KB shared / app | 546 / 53 | 0 | 0 / 4 |

Reference full gate: **BUILD SUCCESSFUL, 13m 35s**. Android 17 full gate: **BUILD SUCCESSFUL, 9m 53s**. JUnit XML is authoritative; Gradle's live counters temporarily exceeded discovered totals. All **17** required UI journeys passed on API 36.1; all **16 applicable** methods passed on API 37. The unchanged OS-specific pixel method is excluded at discovery on API 37, not reported as executed or disabled for this upgrade. The same four operator-gated methods remain skipped on each OS.

All twelve reference pixels matched unchanged. Both native widget renderings were visually inspected on API 37 and are byte-identical to the corrected API-36 PNGs. The first **8** API-37 smoke tests also passed. The device reports were copied to separate temporary reference/target directories before subsequent runs; reference evidence was validated against its own completed reports.

The complete unsigned iOS device Release app and all extensions returned **BUILD SUCCEEDED**; `check-ios-release.py` passed. No signing/provisioning change or distribution signature claim. Android release bundling, debug/release lint and Compose compiler diagnostics completed; both lint XMLs contain **3 warnings / 0 errors**, all AndroidGradlePluginVersion notices. No rule or reference was disabled to obtain that result.

Static gates passed: package/module/source-set checker (**454** paths), generated color check, golden/coverage inventory, **31 Python tests**, **84 Node tests**, and diff whitespace review. Existing three Swift standalone harnesses passed in the preceding cleanup phase; their sources did not change in the upgrade. Hosted CI and manual physical-device/screen-reader testing were not executed.

Second-device command (after booting the named API-37 emulator, which reused serial emulator-5554):

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home \
ANDROID_SERIAL=emulator-5554 ./gradlew \
 :app:shared:connectedAndroidDeviceTest :app:androidApp:connectedDebugAndroidTest \
 -Pandroid.testInstrumentationRunnerArguments.uiFrameBenchmark=true -PcomposeReports=true --console=plain

JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home \
xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
 -destination 'generic/platform=iOS' -derivedDataPath /tmp/inzira-upgrade-ios-release-derived \
 CODE_SIGNING_ALLOWED=NO build
python3 scripts/check-ios-release.py \
 /tmp/inzira-upgrade-ios-release-derived/Build/Products/Release-iphoneos/Portfolio.app

python3 scripts/check-kmp-ui.py --results /tmp/inzira-upgrade-api36-app-results
```

The merged release manifest confirms minSdk **24**, targetSdk **37**, Firebase auto-init **false**, and no FID-mode enablement. Launcher JDK is Microsoft 21.0.9; existing daemon criteria select Java 21 / Azul Zulu. Gradle's embedded Kotlin DSL version (2.4.0) is distinct from the project's Kotlin/Compose compiler version (2.4.21).

[Machine-readable evidence](evidence/2026-11-16/toolchain-upgrade.json) preserves versions, official checksums, corrected failure diagnoses, test counts, source manifest checks, image hashes, log hashes and limitations. Source review confirms only build/dependency configuration, wrapper and documentation changed in this phase: no production Kotlin/Swift/JS, calculation, parser, persistence, API or backend/website source changes. The architecture note was corrected to match the pre-existing both-variant capture-helper dependency; this is documentation accuracy, not a new release privacy policy. The two requested phases remain separate commits, with the upgrade committed/pushed only after these gates passed.

## Limits

 The API-36-only screenshot method is not a valid cross-OS golden on API 37; its existing SDK restriction must remain intact. Real delivery, supplied-user-PDF, reboot persistence and startup-fixture tests remain operator-gated. No hosted CI execution, manual screen-reader/physical-device verification or measured performance improvement is claimed.
