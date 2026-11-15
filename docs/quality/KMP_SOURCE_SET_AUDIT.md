# Kotlin Multiplatform source-set audit

Reviewed against `9201f51` and the existing working tree on 15 November 2026. Scope: the mobile KMP modules and Android host. The Spring Boot backend and website are unchanged. Existing unfinished extractions are not included in this commit.

## Targets and ownership

| Module / source set | Responsibility / verification scope |
|---|---|
| `core/commonMain` | Shared bond/share/portfolio rules, JSON values and Ktor transport contracts/policy. No Compose, Android SDK, Java or Apple SDK imports were found. |
| `core/androidMain` | Android HTTP-engine/build-policy actuals. Android host tests add an Android policy case. |
| `core/jvmMain` | JVM HTTP-engine/development-policy actuals. `jvmTest` also contains an optional local-backend smoke entry point. |
| `core/src/jvmShared/kotlin` | **Shared source directory, not a source set or target.** The unchanged BigDecimal actual implementation is compiled separately by `androidMain` and `jvmMain` via two explicit `kotlin.srcDir` entries. |
| `core/iosMain` | Foundation decimal and Darwin transport actuals, shared by iOS device/simulator. Common core tests run on the simulator. |
| `app/shared/commonMain` | Compose Multiplatform screens, feature orchestration, interfaces and shared parsing/backup/formatting policy. Dependencies include multiplatform Compose, lifecycle, coroutines and `core`. |
| `app/shared/androidMain` | Android native actions, biometrics/vault, content URI/file reading, PDF renderer/engine, capture protection and Android factories. Android-only dependencies remain here. |
| `app/shared/iosMain` | UIKit/PDFKit/Vision/JavaScriptCore/CoreCrypto/Security integrations, native files, receiver and factories. iOS-specific dependencies/types remain here. |
| `app/shared/commonTest` | Shared financial, import, state, security-policy and serialization tests. Existing test dependencies are multiplatform. |
| `app/shared/androidHostTest` | Android/JVM adapter tests. `androidDeviceTest` is configured dynamically by the Android KMP plugin and inherits the common test tree; it had no runner artifact before this change. |
| `app/shared/iosTest` | Native engine/OCR, file handoff/storage, PDF rendering and capture-layer tests. The configured native test task requires a booted simulator for OS services. |
| Android host `src/main`, `src/debug`, `src/androidTest` | Android application/services/intent/widget integration; debug test hosts and instrumentation. This is an Android application module, not another common KMP source set. |

Configured targets are **core: JVM, Android, iosArm64, iosSimulatorArm64** and **shared: Android, iosArm64, iosSimulatorArm64**. Default generated `nativeMain`/`appleMain`/`iosMain` hierarchy nodes serve the two iOS leaves; they need not all have handwritten directories. The shared UI module has no JVM desktop, JS, wasm, macOS, Windows or Linux target; no missing actual is inferred for an unconfigured target. No target or module is added.

## Findings and changes

| Severity | Evidence | Correction |
|---|---|---|
| Medium | `core/src/androidMain/.../DecimalAmount.android.kt` and `core/src/jvmMain/.../DecimalAmount.jvm.kt` were byte-identical financial implementations. Separate copies could drift in validation/rounding order. | Move the unchanged JVM file to `core/src/jvmShared/kotlin/dev/orestegabo/portfolio/investments/DecimalAmount.jvm.kt`; register that source root with each JVM-backed leaf and remove the duplicate Android copy. Keep Java types outside `commonMain` and outside Apple metadata. |
| High | Full `core:iosSimulatorArm64Test` baseline: 107 tests, one failure in `ShareCompanyRowsTest.remainingSharesAndTheirCostAreUsedAfterSales`: expected `102523.68`, actual `102523.68000000001`. Foundation `.doubleValue` can introduce an extra binary rounding step after the correct two-decimal monetary rounding. | In the iOS actuals, parse the already-rounded decimal `stringValue` with Kotlin's `toDouble`, including proportional cost. Keep decimal arithmetic, tax/addend order, validation and HALF_UP-equivalent rounding unchanged. No tolerance is relaxed and the existing failing test is retained. |
| High (verification gap) | `app/shared` configured `AndroidJUnitRunner` but its Android device-test APK lacked that class. The connected target failed to instantiate instrumentation and ran **zero tests**. | Add `androidx.test:runner:1.7.0` only to `getByName("androidDeviceTest").dependencies`, matching the existing Android host runner version. Production/iOS dependencies remain unchanged. |
| Medium (preventive boundary) | No platform-only import leak was found, but the existing package/module checker did not reject one. A common import can be missed until a different platform builds. | Extend `scripts/check-kmp-packages.py` to reject `android.`, `java.`, `javax.`, `platform.` and `kotlinx.cinterop.` imports in actual `src/commonMain` roots, including vendored namespaces. Tests preserve valid AndroidX multiplatform imports and platform/JVM-only source roots. |

The JVM arithmetic file is byte-for-byte unchanged from the two originals. iOS numerical behavior changes only to correct the decimal→Double representation: the intended amount remains the same rounded monetary amount, and its JSON number becomes canonical. The new common contract tests verify exact rounded sums/proportional amounts, JSON text, positive/negative HALF_UP cases, tax/addend order and controlled invalid input failures. They run on JVM, Android host and iOS native; no arithmetic assertions are weakened.

The shared directory is deliberate: Kotlin's current hierarchy documentation explicitly excludes a JVM+Android intermediate-source-set combination. We retain the default Apple hierarchy and use ordinary leaf source roots rather than introducing manual unsupported `dependsOn` edges. This is a repository-specific reuse decision, not a claim that a new KMP hierarchy node exists. [Kotlin source-set hierarchy documentation](https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html).

## Expect/actual and interface decisions

- Keep `expect/actual` for platform primitives (decimal adapters, crypto/digest, HTTP engines) and composable factories/platform UI hooks. Final metadata/target/framework compilation checks actual resolution on every configured production target; no unmatched/duplicate actual was found.
- Keep ordinary common contracts such as `PdfPicker`, `InvestmentDocumentParser`, `SecureSignIn`, `GuestSnapshotStorage`, `GuestDocumentStorage`, `PortfolioDataApi` and `PortfolioSessionApi`. These already permit fake/injected implementations without one expect class per service.
- Shared financial rules do not construct `File`, `Context`, `LAContext`, PDFKit or biometric implementations. Platform factories are called by the shared composition roots/UI, where platform selection and lifecycle belong. Ktor implementation construction stays in infrastructure, not investment calculations.
- `androidx.compose.*` and the used lifecycle artifacts are multiplatform dependencies. Their namespace alone is not evidence of an Android leak. The new import guard intentionally distinguishes `android.` from `androidx.`; compilation remains the authority for dependency/API availability. Fully qualified references and third-party library internals are outside this lightweight import check.
- Core still has one module for domain/value and transport packages, with existing package/dependency checks. No Gradle cycle or source-set edge problem was found. No module fragmentation, new platform, crypto implementation, DI framework or generic platform-service registry is introduced.

## Platform differences intentionally retained

| Capability | Shared contract / genuine native difference | Test coverage and limits |
|---|---|---|
| Files and PDF access | Shared workflows use bytes/validated local IDs. Android uses granted content URIs/content resolver and atomic/protected files; iOS uses sandbox/security-scoped URLs, protected files and native stream ownership. Do not move Java InputStream or Foundation URLs into common APIs. | Common picker/import/storage-policy tests; Android stream adapter tests; iOS receiver/native-storage tests. Sender originals and native permissions remain platform-owned. |
| PDF extraction and display | Parsing/admission/limits are shared; Android WebView/native PDF and iOS PDFKit/Vision/JavaScriptCore have different lifecycle, OCR and rendering APIs. | Native Android synthetic engine tests and the full shared iOS suite are run. Real scanned-document/device variation is not established by compilation alone. |
| Biometrics and credentials | Common capability/record contracts; Android Keystore/Keyguard/BiometricPrompt and iOS Keychain/LocalAuthentication remain native. Keys/auth contexts must not become common domain state. | Shared session/state tests and native compilation; no physical fingerprint/Face ID enrollment/secure-hardware parity claim. |
| Capture protection | Platform hooks share desired privacy state, not OS implementation. Android capture/window behavior differs from UIKit capture/redaction layers. | Existing native/host tests compile; iOS native layer tests run. This task does not execute the entire Android host capture/UI matrix or certify all OEM recording behavior. |
| Backup and digest | Shared envelope/limits/admission; Java crypto and CommonCrypto/CoreCrypto primitives remain native. | Existing OpenSSL-reference backup envelope, crypto/restore and import tests run on host/native/device targets. No new cryptographic algorithm or wire format. |
| Networking | Common Ktor request/session contracts. CIO/JVM versus Darwin/URLSession cookie/cache settings remain native. Android-only build flags and process-cookie checks stay outside common code. | Mock transport/security tests across core targets; no live-backend/TLS/provider delivery certification. Release HTTPS defaults are unchanged. |
| UI/device experience | Shared theme/financial presentation; haptics, reduced motion, bars, back handling, device model, keyboard options and permission presentation use native factories/hooks. | All target compilation/framework links; no cross-device accessibility or hardware UX equivalence claim. |

Earlier quality reports recorded the core-native floating-point equality failure as an excluded baseline. This audit resolves it and runs the **full** core-native suite; those earlier reports remain historical records of their commits.

## Verification

Toolchains: JDK `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`; Android SDK `/Users/muhirwagabooreste/Library/Android/sdk`; Xcode 26.3 (17C529), iphoneos/iphonesimulator SDK 26.2. Existing Android emulator: `Sequo_References_API_36_1`, serial `emulator-5554`. iOS simulator: `1726E7D8-877B-4A6D-ABCD-7292D35D5BD4`.

Full core-native baseline before changes:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:iosSimulatorArm64Test --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 1 in 2s: 107 tests, one exact monetary equality failure described above. Log `/tmp/inzira-kmp-native-before.log`. After the fix and three common contract tests, `:core:jvmTest :core:testAndroidHostTest :core:iosSimulatorArm64Test --device <same UDID>` passes in 28s: **110 JVM, 111 Android host and 110 core native tests**, no failures/errors/skips. Log `/tmp/inzira-kmp-core-after.log`.

Working-tree target compilation and host tests:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :core:allMetadataJar :app:shared:allMetadataJar :core:compileTestKotlinIosArm64 :app:shared:compileTestKotlinIosArm64 :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:linkDebugFrameworkIosArm64 :app:shared:linkDebugFrameworkIosSimulatorArm64 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 2m 23s`; 110 core JVM, 111 core Android host and 515 shared host tests, no failures/errors/skips. Metadata, iOS device-test compilation, Android app/test builds, release lint and both framework links pass. Log `/tmp/inzira-kmp-working-build.log`.

The configured Android shared-device target was exercised, not presumed valid:

```sh
android emulator start Sequo_References_API_36_1 --headless
ANDROID_SERIAL=emulator-5554 JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:connectedAndroidDeviceTest -Pandroid.injected.device.serial=emulator-5554 --console=plain
```

Before adding the runner, exit 1 in 52s: instrumentation could not instantiate `AndroidJUnitRunner`; **zero tests ran**. Log `/tmp/inzira-kmp-device-working.log`. The first attempted typed source-set accessor was unavailable in this AGP DSL; it was corrected to the actual container name via `getByName`. With the Android-only test dependency, exit 0 in 29s: **509 tests, no skips/failures**. Log `/tmp/inzira-kmp-device-after.log`.

The scoped tree was staged and exported with `git write-tree` / `git archive`, excluding previous unfinished work. The full target command above was repeated with `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk` and `--project-dir "$kmp_export"`.

Export `/tmp/inzira-kmp-index.Y7bMBE`: exit 0, `BUILD SUCCESSFUL in 2m 7s`; **725 host test executions** (110 core JVM, 111 core Android host, 504 shared host), no failures/errors/skips. Metadata, device-test compilation, Android app/test builds, release lint and iOS arm64/simulator framework linkage pass. Log `/tmp/inzira-kmp-index-build.log`.

Final complete core/shared simulator tests and the configured shared Android device suite on the isolated tree:

```sh
ANDROID_SERIAL=emulator-5554 ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$kmp_export" :core:iosSimulatorArm64Test --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 :app:shared:connectedAndroidDeviceTest -Pandroid.injected.device.serial=emulator-5554 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 1m 13s`: **110 core native + 523 shared native tests**, no failures/errors/skips; **498 shared Android device tests**, zero skipped/failed/errors. Log `/tmp/inzira-kmp-index-runtime.log`. The working tree has eleven extra shared tests belonging to earlier unfinished work.

Android native-engine integration against the isolated host app/test APKs:

```sh
adb -s emulator-5554 install -r "$kmp_export/app/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
adb -s emulator-5554 install -r "$kmp_export/app/androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk"
adb -s emulator-5554 shell am instrument -w -e class dev.orestegabo.portfolio.investments.LocalEngineTest dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

Pass: **OK (2 tests)** in 4.06s; synthetic RSE purchase PDF extraction and offline projection run through the real Android engine. Log `/tmp/inzira-kmp-index-engine.log`. The commands use `/Users/muhirwagabooreste/Library/Android/sdk/platform-tools/adb`.

Static checks run in both trees:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Both pass common-platform import/package/module checks and color consistency, **19 Python and 84 Node tests**. Staged Kotlin/build/script bytes are compared against the verified export; the moved JVM body is compared against both original implementations. Only this report receives final result updates afterward. No configured Detekt/ktlint task exists.

### Execution limits

- **iOS device runtime:** iosArm64 production/test compilation and framework linkage succeed; no physical iOS test host/runner is configured or selected for execution. The listed Gradle tasks provide simulator execution and device test compilation/binaries, not a physical-device test run. iOS hardware biometrics, secure-enclave behavior and device/OEM capture behavior are not validated by simulator tests.
- **Other OS/architecture targets:** not configured, so none are added or claimed tested. There is no missing configured Windows/Linux/JS/wasm/macOS toolchain to waive.
- **External integration:** `:core:verifyLocalSpringApi` requires a running external backend and is not run; backend work is excluded. No live-account test, deployment, signed distribution or full Android host UI/hardware matrix is claimed. The selected native engine integration uses synthetic data only.
- Existing compiler/deprecation and framework bundle-ID inference warnings remain. The previously failing full core-native equality test and the missing Android runner are resolved; neither is waived for this commit. No test tolerance, schema or financial rule is changed.
