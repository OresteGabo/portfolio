# Mobile Gradle and dependency hygiene — 2026-11-15

Baseline: `2ab7535`. Scope: `:core`, `:app:shared`, `:app:androidApp` and their shared version catalog. Backend/website build systems, root project inclusion, wrapper, signing, release credentials and production source are unchanged. Verification uses an isolated export, excluding 39 unrelated working-tree changes.

## Changes and evidence

| Finding | Severity | Action / evidence |
| --- | --- | --- |
| Mobile versions were split between the catalog and literal coordinates in two module scripts. Android test-runner and Coroutines versions were duplicated. | Medium | Centralized the existing Google Services plugin, Firebase BOM/Messaging, Fragment, Biometric, Android test runner, desugaring and hidden-API dependency declarations. Core/test/Android Coroutines now share one catalog version reference. All requested versions remain unchanged. |
| Four catalog library aliases had no consumers; three existed only to hold unused versions. | Low | Removed `ktor-client-android`, `androidx-core-ktx`, `androidx-espresso-core`, `junit`, and their exclusively unused Core/Espresso/JUnit version keys. No packaged artifact was removed: transitive JUnit/Core remain where required. Server-used aliases, including `kotlin-testJunit`, remain unchanged. |
| Declared dependency versions do not fully describe selected platform versions. | Medium | Exported 14 mobile configurations before/after. All selected coordinates are identical, with zero unresolved dependencies: 367 unique Maven coordinates. Added an opt-in mobile inventory script so future audits can reproduce this evidence without a new Gradle plugin. |
| No Gradle dependency lockfiles or artifact verification metadata are checked in. | Medium | Documented the reproducibility limitation. Did not enable root-wide locking/verification, which could affect the out-of-scope server or require Native configuration-specific decisions. No lockfile changes. |
| Material3 is an explicitly pinned alpha; some Android transitive artifacts retain older Kotlin runtime labels. | Medium / Low | Retained working versions. No broad upgrade, exclusion, forced BOM override or compiler migration. See compatibility notes below. |

The selected-module fingerprints and unchanged retained catalog coordinates are in [resolution comparison](evidence/2026-11-15/gradle-resolution-comparison.json). They compare selected package/version sets per configuration, not artifact byte hashes, dependency-edge identity, binary reproducibility or APK size. This cleanup does **not** claim a size or speed improvement.

## Architecture and dependency ownership

The mobile graph remains Android app → shared Compose application → core. Core contains an ordinary JVM target plus Android and both configured iOS targets; shared contains Android plus iOS device/simulator targets. No new modules or source-set edges were introduced.

- Android/JVM CIO and iOS Darwin are actual engine dependencies, not duplicates to remove. The two JVM-compatible leaf source sets intentionally compile `src/jvmShared/kotlin` independently.
- Core's exported JSON/date/coroutine/Ktor contracts and shared's exported core project remain `api`; this change does not alter consumer visibility.
- Shared activity, biometric and Android coroutine dependencies remain platform-only. Common code gets no Android SDK dependency.
- AppCompat is required by `AndroidManifest.xml`'s `Theme.AppCompat.DayNight.NoActionBar`. FragmentActivity/biometric integration, notification APIs and capture-only HiddenApiBypass usage are present. These runtime libraries were retained after inspecting consumers.
- Firebase Messaging stays versionless under the existing Firebase BOM. Google Services retains its existing conditional application when `google-services.json` is present; the plugin version moved to the catalog without changing the condition.
- Similar SDK/JVM target declarations in the two KMP modules are short and have different resource/test responsibilities. A convention-plugin/buildSrc migration would add more infrastructure than this cleanup justifies.

## Compatibility constraints and intentional differences

| Component | Existing configuration retained | Evidence / constraint |
| --- | --- | --- |
| Gradle / AGP | Wrapper 9.1.0 / AGP 9.0.0 | Google's AGP 9.0 compatibility table requires Gradle 9.1.0 and JDK 17 or newer; API 36 is supported. The Android application is separate from the two KMP libraries, using the AGP KMP library plugin. [Official AGP notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes). |
| Kotlin / Compose compiler | Both 2.3.21 | Both compiler-plugin aliases use the same Kotlin version key. This matches the required Compose compiler/Kotlin pairing. [Official Compose compatibility guidance](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html). |
| Kotlin / AGP / Xcode | Kotlin 2.3.21, AGP 9.0.0; local Xcode 26.3 | Kotlin's published 2.3.20–2.3.21 range covers Gradle through 9.3.0 and AGP through 9.0.0; it lists Xcode 26.0. Local 26.3 compilation/tests are reported separately, not presented as vendor certification for every Xcode version. [KMP compatibility table](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html). |
| Compose libraries | Multiplatform 1.11.1, Material3 1.11.0-alpha07, icons 1.7.3 | These artifacts have independent release lines. Android resolves Compose 1.11.2 and Material3 1.5.0-alpha17 through their published Android variants. No attempt was made to give all UI artifacts the same version string. |
| Coroutines | Requested 1.10.2; selected core 1.11.0 on all inspected platforms | Existing transitive resolution already selects the newer core; Android and JVM test artifacts also select 1.11.0. The catalog consolidation preserves both requests and selected results. |
| Serialization | Requested 1.9.0; selected JSON 1.11.0 on JVM/Android, 1.9.0 on iOS main | Actual baseline platform resolution differs. No forced cross-platform upgrade was introduced. Any deliberate alignment needs native JSON/HTTP regression verification as a separate change. |
| JVM | Android bytecode/Java target 11; daemon toolchain major 21 | Existing settings agree. Daemon criteria specify a JDK major/vendor, not a fully frozen patch version. The actual installed daemon/runtime can differ from JAVA_HOME's directory label. |

`dependencyInsight` traced `kotlin-android-extensions-runtime:1.9.22` through Parcelize → AndroidX DataStore 1.1.7 → Firebase installations/common. The project does not apply the obsolete Android Extensions plugin. Excluding a required transitive runtime solely because of its name would be unsafe; any removal should follow an upstream dependency change.

No dynamic `+`/SNAPSHOT dependency requests were introduced. The wrapper already pins its distribution SHA-256 and validates its URL. Exact catalog/plugin requests help reproducibility, but unlocked transitive selection, JDK patches, SDK/Xcode versions and absent artifact verification mean bit-for-bit reproducible builds are not established.

## Dependency advisory check

Queried the 367 unique selected Maven coordinates against OSV on the report date. **Zero matching advisory IDs returned.** The evidence includes every public package/version query: [OSV results](evidence/2026-11-15/gradle-dependency-advisories.json). Only package metadata was sent; no source, keys, project/account identifiers or financial data was submitted. API behavior follows the [official OSV batch API](https://github.com/google/osv.dev/blob/master/docs/api/post-v1-querybatch.md).

This is an indexed-advisory lookup, not proof of safety or a reachability assessment. It does not cover all Gradle plugin/tooling distributions, JDK/SDK/OS components, or vendored non-Gradle assets. Prior client security reviews cover additional vendored assets separately; those assets were not changed here.

## Reproducing dependency inventory

Run the diagnostic task explicitly; it is not added to normal builds, resolves only mobile configurations and fails on unresolved dependencies. Configuration caching is intentionally disabled for this execution-time diagnostic. Normal mobile builds keep their existing cache settings.

```sh
./gradlew --no-configuration-cache -I scripts/gradle/mobile-dependency-audit.init.gradle \
  :core:exportMobileDependencyAudit :app:shared:exportMobileDependencyAudit \
  :app:androidApp:exportMobileDependencyAudit -PauditOutput=/tmp/inzira-mobile-dependencies --console=plain
./gradlew :app:androidApp:dependencyInsight --configuration releaseRuntimeClasspath \
  --dependency kotlin-android-extensions-runtime --console=plain
```

The baseline export was collected before edits; the changed export was collected twice, including with the checked-in diagnostic tool. All 14 corresponding configuration records match exactly. Resolving existing retained catalog entries to requested coordinates also confirms no version change to aliases used by the server.

## Verification

All Gradle commands run from an isolated export of the staged build files, with:

```sh
export JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home
export ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk
```

Executed the following twice. Configuration succeeded; the second invocation reused the configuration cache. The two Kotlin configuration-error tasks reported SKIPPED because no diagnostics were present; they are not additional test executions. No Gradle deprecation warning was emitted by these configuration checks.

```sh
./gradlew help :core:checkKotlinGradlePluginConfigurationErrors \
  :app:shared:checkKotlinGradlePluginConfigurationErrors --warning-mode=all --console=plain
```

```sh
./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest \
  :core:allMetadataJar :app:shared:allMetadataJar \
  :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest \
  :app:androidApp:bundleRelease :app:androidApp:lintRelease \
  :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 \
  :core:compileTestKotlinIosArm64 :app:shared:compileTestKotlinIosArm64 --console=plain
```

Passed: 145 core JVM + 145 core Android-host + 526 shared Android-host tests (816 executions, zero failures/errors/skips); common metadata, debug app/test APKs, unsigned release bundle, both iOS main compilations and iOS device test-source compilations passed. Release lint passed with 94 warnings and no errors; there are no remaining `UseTomlInstead` findings in this report. Existing code/API/resource warnings were not suppressed. The release AAB was inspected and contains no signing certificate blocks; signing configuration and credentials were not changed.

The credential-free build (no Google Services file in the isolated export) passed above. The conditional plugin branch also passed in a **separate** export containing a generated, clearly synthetic `google-services.json` matching the application package:

```sh
./gradlew --project-dir "$firebase_export" :app:androidApp:processDebugGoogleServices --console=plain
```

The fixture used dummy project/client/key values, was not committed or installed, and never replaced the local real configuration. This verifies plugin application/resource generation, not delivery through a real Firebase project.

Native/common runtime verification passed: 144 core + 552 shared iOS simulator tests (696 total), plus 520 shared Android emulator tests, zero failures/errors/skips. Combined host/native KMP test executions: 2,032. The configured iOS device target compiled; tests ran on a simulator, not a physical iPhone.

```sh
xcrun simctl boot 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4
xcrun simctl bootstatus 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 -b
/Users/muhirwagabooreste/.local/bin/android emulator start Sequo_References_API_36_1 --headless
ANDROID_SERIAL=emulator-5554 ./gradlew :core:iosSimulatorArm64Test \
  --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
  :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
  :app:shared:connectedAndroidDeviceTest -Pandroid.injected.device.serial=emulator-5554 --console=plain
```

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --cached --check
```

Passed: 405 Kotlin source/test files satisfy package/platform/module checks; design resources consistent; 19 Python and 84 JavaScript tests passed; staged whitespace check passed. No additional static-analysis plugins were added.

## Remaining follow-ups

1. Consider mobile-only dependency locking and artifact verification with explicit coverage of Native configurations and CI update procedures. Review trust metadata separately; do not blindly generate root-wide trust for an unrelated backend.
2. Treat Material3 alpha adoption, serialization platform alignment and upstream Firebase/DataStore runtime cleanup as separately verified dependency work, not automatic latest-version updates.
3. Existing Android Lint, package/source-set/module-DAG checks and script tests remain the static analysis baseline. No detekt/ktlint/coverage/dependency-analysis plugin is configured or added; absence of those tools is not a claim that all Kotlin style or unused-dependency issues have been detected.
4. Record/revalidate the actual Xcode/JDK/SDK environment when changing the toolchain. Preserve the Kotlin/Compose compiler pairing and keep AGP upgrades separate from routine catalog cleanup.
