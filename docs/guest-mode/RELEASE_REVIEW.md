# Guest Mode release review — Phase 8

2 November 2026. Scope: KMP `feature/guest-mode` after `b7f0316`, companion Spring Boot branch after `3d8ff49`. Both worktrees were clean before this phase. [Architecture audit](ARCHITECTURE_PLAN.md) is historical; [Phases 3](PHASE_3.md), [4](PHASE_4.md), [5](PHASE_5.md), [6](PHASE_6.md) and [7](PHASE_7.md) describe the implemented boundaries. No production credentials, customer PDFs, production database, deployment, push, merge or publishing operation was used.

## Recommendation

**NO-GO for an unrestricted public release.** The code can progress to a supervised internal beta after the backend V11 deployment is separately reviewed. Guest storage/import/UI and explicit account transfer have automated evidence, but physical-device protection/restore checks, signed distribution checks, permission/disclosure verification and the outstanding dependency risk decision below are not complete. This is a scoped engineering assessment, not a penetration test, legal opinion or OWASP certification.

| Gate | Decision / remaining action |
| --- | --- |
| Authenticated account safety | Preserve normal verified server sessions and owner-scoped APIs. No anonymous investment/migration account was introduced. Backend V11 must precede distribution of migration-capable clients; it was tested locally, not deployed. |
| On-device protection and recovery | Physical Android/iPhone locked-device, backup/transfer, reboot, uninstall/reinstall and capture checks require human sign-off. Simulator file attributes/Keystore tests cannot certify hardware protection. Guest Mode has no separate app PIN/biometric lock; it relies on device sandbox/protection and must not be marketed otherwise. |
| Quotes | `GUEST_RSE_DISTRIBUTION_APPROVED=false` remains unchanged. No permission has been supplied. Guest retrieval stays disabled; purchase cost is not a live quote. Existing backend public routes are not evidence of redistribution rights. |
| Dependency risk | Fresh OSV scan has one **moderate** Kotlin build-cache advisory. Release owner must validate a supported fix or explicitly accept a time-bounded exception with trusted-cache controls. No beta compiler, advisory suppression or acceptance was introduced. |
| Push privacy | Automatic Android token/analytics initialization disabled. Real configured-FCM/APNs network QA remains required before enabling push in a release. Switching to Guest Mode does not revoke an already registered account device at the server. OS-rendered notifications from an earlier account remain a conditional privacy concern; keep optional push disabled until tested/resolved for that configuration. No guest server call was added to revoke devices silently. |
| Release operations | Store privacy forms, privacy policy/guest-retention wording, account deletion/export journeys, distribution signing and exact release artifact testing need human/developer-account verification. No store upload performed. |

No unremediated critical/high vulnerability was demonstrated by this scoped audit. That is not proof that none exist. A failed or missing gate remains a release blocker regardless of advisory severity. Do not infer public-release approval from successful compilation or internal-test results.

## Findings fixed in this phase

### Android cancellation crash — high availability impact

The old Android Ktor transport used `HttpURLConnection`. A focused synthetic loopback test cancels a stalled response from `Dispatchers.Main`. **Before the fix the test process crashed** with `CompletionHandlerException → RawSourceChannel.closeSource → BufferedInputStream.close → Http1xStream.FixedLengthSource.close → NetworkOnMainThreadException`. This reproduces the unresolved Phase 4 thumbnail failure without using a real account or backend.

`core/src/androidMain/.../HttpEngine.android.kt` now uses Ktor CIO, already used in the project's JVM implementation, at the same Ktor version. Only the Android engine dependency changes; cookie storage, endpoint/origin/redirect/body limits, timeouts, server validation and iOS Darwin transport remain unchanged. No StrictMode exception, authentication bypass, unsafe retry or unbounded IO workaround was added. `HttpCancellationTest.kt` retains the focused regression; authenticated import-chat/document journeys provide broader checks. This is the only dependency substitution in this phase.

### Guest snapshot repopulation while backgrounded — medium privacy/lifecycle issue

The new native lifecycle test exposed that clearing a guest snapshot could trigger the calendar's incomplete-history effect, starting another read while backgrounded. A pending guest operation could also publish its result after backgrounding. The app privacy cover still guarded visible content, so this is not evidence of a remotely exposed portfolio; unnecessary decrypted-state retention was real.

`PortfolioModel` now rejects new guest actions while backgrounded, always clears the guest snapshot on ordinary backgrounding and publishes local operation results only while foregrounded. Foreground is set before reloading local data. An already-started durable write can complete atomically; committed files are not discarded. System document-picker interactions preserve their existing special treatment. Shared and native tests cover late completion, rejected background refresh, durable target preservation and foreground recovery. Account auth/lock behavior is preserved.

### Optional Android FCM initialization — medium conditional privacy issue

The native guest preference guard ran after Firebase's manifest provider could initialize. Firebase documents that auto-init can generate/validate a registration token before application-level checks. The manifest now sets `firebase_messaging_auto_init_enabled=false` and `firebase_analytics_collection_enabled=false`; the host also forces auto-init off to override an older retained SDK preference. Existing account-mode code explicitly requests its token. No Firebase credential/configuration was created or used. A packaging-policy regression checks these defaults. This mitigates automatic registration; it does not certify every optional SDK/network path or revoke older account subscriptions.

### Additional fixes from native/disclosure checks

- **Native rendering resilience:** repeated sharing runs exited with a native `RenderThread` SIGSEGV; a subsequent WebView renderer exit was not proof that WebView caused that SIGSEGV. The debug build also unconditionally installed an unsupported private SurfaceControl capture experiment. That experiment is now explicitly opt-in (`experimentalCaptureLayer`, default false), preserving standard masking/FLAG_SECURE. The sharing and standard capture checks passed with this default. Release artifacts already exclude the experiment/bypass library; packaged checks confirm this. Physical-device graphics regressions still require QA.
- **Contained PDF renderer exit:** separately, the local engine lacked `onRenderProcessGone` handling. It now fails pending parser/chunk work with fixed guidance, removes/destroys the failed view and requests a fresh private sandbox only on an explicit next operation. The Compose host keys the view by engine/generation, so a lost view is not reused across retries or guest/account adapters. It never retries a financial save or uses cloud parsing. The real-host fault-injection regression verifies replacement and reinitialization; it is not a GPU/OS fuzzing test. Engine/view construction is lazy until an operation needs it. No isolated startup speedup is claimed for this structural change.
- **Guest privacy disclosure gap:** `docs/legal/content.json` and its generated offline English/French content now distinguish guest local storage from authenticated uploads, disclose no guest cloud backup, explain device-loss/uninstall and separate app-lock limits, describe selected/full-PDF transfer consent and distinguish guest deletion from account deletion. Policy draft version is 2026-11-02. Regression tests cover both languages. These are implementation-matched draft disclosures, not evidence of legal approval, publication or acceptance recording.

## Threat model and data flow

Threat actors: another app or document provider; a sender of hostile PDF bytes; someone using an unlocked/lost phone; a malicious authenticated API user; a stale task from another account/mode; an interrupted write/upload; and a malicious build-cache producer. Remote broker integrations, production infrastructure attacks and compromised/rooted OS resistance are outside the test environment.

Method: use [OWASP MASVS](https://mas.owasp.org/MASVS/) categories and [MASTG](https://mas.owasp.org/MASTG/) verification techniques for storage, network, platform and privacy; [ASVS 5.0.0](https://asvs.owasp.org/) for server input/auth/session/authorization expectations; and [API Security Top 10 2023](https://api-security.owasp.org/editions/2023/en/0x11-t10/) for object/property/function authorization and resource exposure. This is a risk-oriented mapping, not a claim to have executed every requirement.

| Boundary / abuse case | Verified controls and evidence | Residual / manual check |
| --- | --- | --- |
| Account/guest/sample confusion; stale coroutine (MASVS-AUTH/PRIVACY, API1/API2) | Explicit SessionState/guest path, separate account backing state, no synthetic user, guarded account client, generation/cancellation and migration binding. Shared session/model/migration regressions. | Native secure sign-in/biometric UX on physical devices; OS app-lock policy remains distinct from guest device protection. |
| Read another user's holdings/PDF (ASVS authorization, API1/API3) | Backend `currentAccount`, owner-scoped purchase/doc queries and owned evidence resolver; expected migration account checked per chunk/item; wrong-owner tests. | No production pen test; authorization tests use synthetic local accounts. |
| Silent document upload or term publication (MASVS-PRIVACY/NETWORK) | Guest actions use only local repository and on-device parser. Account API accessor rejects guest. Private engine blocks Android network and restricts assets; iOS uses PDFKit/Vision/JavaScriptCore without URLSession/fetch. Migration only via explicit account review/selection/consent. Migration does not contribute private terms to shared instruments. | OS document providers may download a user-selected cloud file; explicit user export/share hands a copy to another app. Neither is a Struct server upload. No production packet-capture claim. |
| Portfolio leaking through quotes (API3/API9) | Separate fixed-source cookie-free all-market DTO/cache; disabled distribution gate constructs no client or cache read. Public/history tests ensure private contract observations remain absent anonymously. | Licence remains unresolved; existing public backend market routes have caching but no dedicated per-client ingress rate limiter in this repository. Verify deployment quotas/abuse controls separately. |
| Lost phone, tampered/restored snapshot (MASVS-STORAGE/CRYPTO) | Android noBackupFilesDir + device Keystore AES-GCM + fresh IV/AAD + atomic ciphertext; missing/key-loss/corruption fail closed. iOS Complete Data Protection + atomic Foundation writes + excluded Application Support directory. Separate auth key/store. Native and shared corruption/isolation tests. | No custom cryptography added. Android guest key does not require an additional biometric per read. iOS simulator may not expose protection-class attributes; hardware/device transfer verification required. |
| Malicious/oversized PDF (MASVS-PLATFORM/CODE, API4) | Bounded selection/envelope, 10 MiB/10 pages/5 OCR pages/200,000 text characters, resource checks/150-second processing timeout, ambiguity rejection and explicit financial review. Native parser and malformed/password/scan fixtures. No cloud OCR or AI dependency. | Native OS/PDF parsers need patched supported OS versions. Timeout cannot prove preemption of every synchronous native parser call. Exhaustive PDF fuzzing/bomb tests not performed. |
| Temp-file/provider leak (MASVS-STORAGE/PLATFORM) | Android preview unlinks temporary PDF before writing plaintext; FileProvider exposes only pdf-share subtree with read grants. iOS private picker avoids an automatic imported copy; native PDFKit preview uses memory. Explicit share/preview tests and backup policy checks. | User-exported/recipient copies are outside deletion control. Abrupt power loss/OS provider behavior needs real-device QA; no forensic erasure guarantee. |
| Interrupted deletion/import (MASVS-STORAGE/RESILIENCE) | Versioned manifest, bounded pending-file cleanup journal, atomic commits, confirmed bulk deletion, resumed cleanup and fail-closed corruption tests. Holding deletion retains original as designed; PDF-only deletion removes links. | Test schemas 1/2 → 3, not arbitrary future downgrades. Do not downgrade while a deletion intent is pending. |
| Duplicate/partial migration or wrong account (ASVS business integrity, API1/API3/API6) | Default unchecked selection and separate consent, account ID+origin+generation binding, strict DTO keys, owner/source acknowledgment PK, account lock, atomic financial save/ack, conservative normalized matching, selected private PDFs only, hash dedup, receipt parent/status validation. Local originals always retained. | Edited acknowledged sources/deleted cloud targets require review, not silent replacement/recreation. Existing whole-unit receipt schema may reject fractional local receipt amounts. Only one primary proof is linked; all selected proof IDs remain in audit provenance. See Phase 7 limits. |
| Sensitive logs/build compromise (MASVS-CODE/PRIVACY) | New measurements are test-only type/stage/duration data; no amounts, filenames, text, credentials or tokens. Runtime diagnostics use sanitized route/timing fields. Fresh bounded secret/advisory scans. | Not a complete secret scanner/SBOM/container scan. Build-cache moderate advisory remains open; never consume untrusted PR caches in privileged releases. |

### Verified guest data path

```text
OS-selected PDF → bounded private reader → local parser → explicit review
  → local normalized holdings/receipts + protected original + atomic manifest
  → existing calculators / shared UI / native local preview
  × no account request, term publication, telemetry payload or cloud OCR
Optional public quote path → disabled by permission gate
Normal authentication → no automatic guest access/upload
Explicit transfer review → selected original chunks / financial items / receipts
  → authenticated owner-scoped validation + ack → retain every local source
```

Evidence combines source tracing, a fail-on-construction account client in native/shared guest journeys, a loopback request canary in the real guest startup journey, MockEngine request audits, Android private WebView network blocking, native iOS parser fixtures and actual backend owner/isolation tests. It does not assert that the OS, an optional third-party SDK or a user-selected cloud provider makes no network traffic. No guest document/text/filename/financial values are sent to advisory services: the dependency scan submits only public coordinates.

## Verification and performance

Final command/result table, measurements and QA notes follow below. All PDF/account/portfolio fixtures are synthetic or the existing sanitized fixtures. No supplied investor statement or personal allocation PDF was opened. Tests ran with disabled external connectivity on a temporary Android 16 emulator; loopback test servers remain available. Backend uses a fresh task-owned Postgres 18 cluster, not application configuration.

### Final results

| Check | Actual result |
| --- | --- |
| Shared Android host | **350 passed**, zero failures/errors/skips |
| Core JVM / Android host | **65 / 66 passed**, zero failures/errors/skips |
| Shared iOS simulator | **370 passed**, zero failures/errors/skips |
| Core iOS simulator | Existing **65-test** successful result reused (UP-TO-DATE), core financial code unchanged |
| Spring Boot full suite | **70 passed**, zero failures/errors/skips, fresh isolated PostgreSQL 18 only |
| Node parser/financial/source tests | **81 passed**, zero failures/skips |
| Python release/backup/push policy checks | **6 passed** |
| Android release lint | **0 errors, 55 warnings**; warning IDs/paths retained, not silently called warning-free |
| Android debug APK / test APK / unsigned release AAB | Builds successful; packaged manifest backup/debug/push defaults verified; AAB has no signature blocks or HiddenApiBypass classes |
| iOS device Release | **BUILD SUCCEEDED**, unsigned; built-package version/origin/ATS/privacy checks passed |
| HTTP UI-thread cancellation | **1 passed** after CIO; original transport demonstrably crashed the focused test process |
| Real Compose-host renderer fault/retry | **1 passed**, correct sandbox replacement and retained network/file-access restrictions |
| Native guest PDF import | **1 passed**, actual local text/OCR, review/save/duplicate/preview and malformed rejection, offline |
| Native guest entry / shared experience / migration / lifecycle | **1 passed each**; actual entry canary has zero account-server requests, local mode/data retained, consent/selective transfer unchanged |
| Authenticated import chat / document library / sharing | **1 passed each** on final relevant implementations; synthetic loopback server only |
| Standard capture protection | **1 passed**, private SurfaceControl experiment off |
| Android encryption/tampering/key-loss CRUD | **1 passed**; staged persistence method is intentionally skipped without an explicit argument |
| Protected persistence stages | **Write → force-stop/read → power-cycle/read → cleanup passed** with the same temporary userdata and key |
| Both Git whitespace checks | Passed; no production/schema deployment or Git remote operation |

Native class names: `HttpCancellationTest`, `LocalEngineResilienceTest`, `GuestPdfNativeTest`, `GuestEntryTest`, `GuestExperienceUiTest`, `GuestMigrationUiTest`, `GuestReleaseReadinessTest`, `ImportConversationUiTest`, `DocumentsLibraryTest`, `PdfSharingTest`, `CaptureProtectionTest` and `GuestPersistenceTest`. The guarded `GuestStartupFixtureTest` prepared only nonfinancial preferences for the launch samples. See the small-screen execution record below; experimental private-capture tests were not represented as production requirements or passed gates.

Source/tests: two new shared regressions cover background publication/write preservation and guest consent/retention disclosures in both languages. Four new Android test classes provide focused cancellation, real-host renderer recovery, native protected-store/lifecycle timing and a guarded empty-startup fixture. Existing native PDF tests gained monotonic type/duration-only measurements, including iOS. Existing authenticated, financial, import, deletion, quote and isolation suites remain intact. No financial calculation engine changed.

### Measured performance — no production extrapolation

Android 16 / API 36, temporary headless emulator, debug build, no external network, no market quotes. Launch samples use **empty** personal guest state; the composed-host test uses one synthetic bond, one share lot and one protected original. These datasets differ and are identified rather than combined into one latency number.

| Metric | Measurement | Interpretation |
| --- | --- | --- |
| Actual app force-stopped process launch, OS TotalTime | 1,161 / 1,047 / 1,090 / 1,052 / 1,046 ms; **median 1,052 ms**, n=5 | OS reports COLD; activity draw, not proof of full dashboard TTI or biometric/server-session performance |
| Home/background resume, OS TotalTime | 1,395 / 426 / 475 / 648 ms; **median 561.5 ms**, n=4 | Four HOT results. One additional sample reports UNKNOWN with no TotalTime (WaitTime 336 ms); it is not assigned a fabricated draw time |
| First composed populated host | **1,582.771 ms** | First host in an existing instrumentation process, not a force-stopped app |
| Warm populated host recreation | **984.785 ms** | Guest data ready and real shared dashboard visible, no account-client construction |
| Populated foreground recovery | **1,001.153 / 754.846 ms** | Includes ActivityScenario, Home click and accessibility polling/harness overhead |
| Protected native manifest loads | 4.218 / 5.741 / 4.514 / 3.414 / 6.243 ms; **median 4.514 ms**, n=5 | Synthetic populated store, Android Keystore decrypt/JSON validation, no remote access |
| Android text PDF parser, equity | **239.013 / 95.375 ms** | Actual synthetic text fixture, first/repeated parse |
| Android text PDF parser, bond | **100.573 / 85.536 ms** | Actual synthetic personal allocation fixture |
| Android local scanned-equity OCR | **2,192.303 ms**, n=1 | Actual bundled local OCR, not a cloud request; too few samples for a percentile claim |
| iOS PDFKit text parser, bond | **363.193 / 15.418 ms** | Synthetic iOS fixture; first/repeated initialization |
| iOS PDFKit text parser, equity | **22.557 ms**, n=1 | Different native fixture/setup from Android, not a cross-platform speed comparison |

Monotonic clocks: Android `elapsedRealtimeNanos`, iOS `TimeSource.Monotonic`; app launch values are OS-reported `am start -W` fields. Parser numbers exclude the picker, human correction, save and the existing one-second minimum feedback presentation. The full scripted test duration is not treated as startup or HTTP latency. No physical-device, iOS UI-launch, production backend/auth-session/Face ID or percentile benchmark was performed.

Preliminary loaded-host samples during concurrent native release compilation were slower (first/warm host 7.393/2.126 s). Final samples above were taken after that compilation load settled; this is **not** a controlled before/after optimization comparison. Raw final timing evidence is retained. Warm local UI approaches the initial one-second goal in this synthetic setup, but the first resume outlier and lack of real-device release measurements prevent a blanket “instant on all devices” claim. The verified architectural result is that guest rendering does not wait for account, portfolio or quote HTTP.

### Scans, warnings and assurance limits

Fresh OSV result at **2026-11-02T09:19:13.859182Z**: **596 unique public coordinates, 596 responses, zero batch errors**, one matched coordinate/advisory: `org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21`, [GHSA-r937-wjx7-w2jp](https://osv.dev/vulnerability/GHSA-r937-wjx7-w2jp), moderate unsafe build-cache deserialization. No high/critical match was returned **within this inventory**. Android's final CIO graph was included. Website repositories, OS/WebView/PDFKit/Vision components, full compiler/native toolchain and final container are not covered by that coordinate scan. No automatic beta compiler upgrade or false clean gate was introduced.

Bounded high-confidence pattern scan: current tracked/untracked intended text files plus the latest 200 relevant commits in each repository; binaries/large/invalid-UTF8 files skipped, ignored environment files not read. Mobile: zero pattern findings. Backend: two reviewed matches, a documentation placeholder JDBC URL and the PEM marker used to parse a supplied key, not embedded credentials. No verified secret finding in this bounded scope. This is not a full-history secret audit or proof of absence; standalone secret/SAST/container tools were unavailable and not installed. Evidence retains only paths/rule classifications, never matched secret values.

Evidence: [test counts](evidence/2026-11-02/test-summary.json), [OSV inventory](evidence/2026-11-02/advisory-inventory.json), [OSV summary](evidence/2026-11-02/advisory-summary.json), [bounded secret scan](evidence/2026-11-02/secret-scan.json), [lint summary](evidence/2026-11-02/android-release-lint-summary.json), [packaged artifact checks/hashes](evidence/2026-11-02/artifact-checks.json), [launch samples](evidence/2026-11-02/startup-measurements.json), [native local UI timings](evidence/2026-11-02/local-ui-measurements.txt), and [Android](evidence/2026-11-02/android-pdf-measurements.txt) / [iOS](evidence/2026-11-02/ios-pdf-measurements.txt) parser timings. Optional operational tools/credentials were not needed.

### Intermediate failures and remaining uncertainty

- The cancellation test deliberately reproduced a real baseline crash before the transport substitution. The final focused test and account-document journeys pass; the prior Phase 4 thumbnail-cancellation blocker is closed for this tested Android environment, not certified on every physical device.
- The first new lifecycle test failed because the background snapshot really repopulated; the model guard and shared/native regression resolve that behavior.
- Native sharing repeatedly hit a `RenderThread` SIGSEGV while the debug private capture experiment was installed. With the final opt-in default, sharing and standard protection pass. This does not establish a universal graphics-driver root cause or certify the experimental path.
- The first renderer test started during severe emulator load and exited before test execution. Its next version used an unattached replacement view and timed out; the real Compose-host regression corrected that host assumption, and its Kotlin/JUnit signature also needed an explicit Unit return. Final real-host test passes; no failing test was relabelled as a success.
- One document-library attempt timed out waiting for an optional thumbnail during concurrent build/native graphics problems. Final library journey passes with the final debug defaults. Optional previews retain their existing bounded timeout/placeholder behavior.
- An initial iOS release attempt was cancelled after being mistaken for a hang. Thread inspection showed active LLVM optimization. The bounded retry and the final rebuilt unsigned package succeeded; the quiet log was not a measured application bottleneck.
- `adb reboot` ended the task-owned emulator with GL-context errors. Restarting that emulator with **the same userdata**, without wipe/snapshot restore, passed the persisted-data read and cleanup. This is an emulator power-cycle check, not proof of physical reboot/backup behavior.
- No iOS UI/screen-reader, configured push-provider packet capture, physical storage/backup/uninstall, signed distribution/update or production deployment test was performed. Those remain explicit gates, not implied passes.

## Manual QA and release gates

Use disposable synthetic data first. Record device model, OS/security patch/WebView version, app hash and result; never attach real PDFs or financial screenshots to public issues.

- [ ] Android physical device at minimum supported API and a current device: guest cold process start, Home/background resume, normal close versus force-stop, offline reads/writes, screen lock/unlock and low-memory recreation.
- [ ] iPhone physical device: Complete Data Protection while locked, foreground recovery, background app-switcher cover, secure preview, file-picker cancellation and local deletion. Test with and without device passcode according to the published guest protection policy.
- [ ] Reboot, OS backup/device-transfer, uninstall/reinstall and key-loss tests: guest data must not unexpectedly restore, migrate to another device, reset silently on corruption or be mistaken for cloud backup. Emulator reboot tests are not equivalent to physical backup/restore.
- [ ] Import supported sanitized BNR personal allocation and equity contracts offline; correct missing facts; reject ambiguous/public issuance/statements, protected/malformed/oversized/page-heavy inputs safely. Confirm no account client/remote fallback. Check cancellation, storage-full and interrupted cleanup on both platforms.
- [ ] Local PDF preview/share and incoming cold/warm intents on actual iPhone/Android. Confirm user-selected recipient, bounded reads, read-only grant and cleanup; exported copies must remain explicitly outside app deletion guarantees.
- [ ] TalkBack/VoiceOver, 320 dp/enlarged text, French and both themes: complete selection, review, local storage explanation and destructive-action confirmation without exposing concealed financial values.
- [ ] Authenticate normally after using Guest Mode; decline transfer and observe zero mutations. Transfer only selected investment/receipt/PDF to the displayed account, interrupt after an acknowledged item, retry with fresh consent, compare counts/figures and retain local originals. Repeat with another account and expired/revoked session.
- [ ] Existing cloud holdings, conflicting receipts, different source proofs, removed targets and changed local source: review required, no silent overwrite/recreation. Never include fictional samples. Verify manual/automatic receipt semantics separately.
- [ ] Deploy V11/backend through normal reviewed operations; verify authorization, Origin policy, private evidence access, migration account deletion and rollback compatibility in a dedicated staging environment. This phase does not authorize production access/deployment.
- [ ] Keep guest RSE retrieval disabled until written redistribution/cache permission is verified. Separately review existing account/public market terms and abuse controls.
- [ ] If enabling optional push, capture configured-FCM/APNs cold startup traffic and account→guest transitions, test OS-rendered notifications from an older subscription, and confirm no guest token/identifier registration. Otherwise leave push disabled for beta.
- [ ] Resolve or explicitly accept the moderate compiler build-cache advisory with an owner, expiry and trusted-cache controls. Refresh resolved inventory/OSV results before release; scan the actual signed/container artifacts. A fresh scan can discover new advisories.
- [ ] Verify signed Android/iOS update installation over the previous release, migration consent/account retention, account deletion/export, no debug origins/private API experiments in the release artifact and no obsolete saved-session auto-entry after selecting Guest Mode.
- [ ] Confirm public privacy/support/deletion URLs and working contact inboxes. Have the policy drafts, Rwanda controller/processor and cross-border/retention questions reviewed by a qualified adviser; complete accurate store privacy forms. Do not advertise technical tests as legal compliance.

## Scope limits and rollout decisions

Supported source targets: Android minSdk 24, configured compile/target SDK 36; iOS device arm64 and simulator arm64, app deployment target 18.2. Runtime checks here use the specified Android emulator and iOS 26.2 simulator. There is no desktop/web Guest Mode target. Compilation does not establish runtime coverage of every supported OS/device.

The Android test image's WebView/graphics stack is not a substitute for an updated physical device. Unsigned device/package checks do not test App Store signing/provisioning, entitlements, review approval or OTA/store updates. Runtime timings are debug/synthetic emulator observations, not production latency promises or biometric benchmarks.

Existing financial calculators, rounding, market-price availability labels, financial APIs and guest storage schema stay unchanged. No microservice, broker ingestion, billing, new cryptography, analytics infrastructure or institution integration was introduced. The new code is limited to evidenced lifecycle/transport/parser resilience, privacy defaults and matching disclosures/tests.

Remaining operational limits include no guest cloud recovery, no cross-device synchronization before explicit transfer, whole-unit backend receipt constraints, one primary account proof reference, bounded migration status history and no automatic reconciliation of edited acknowledged sources. Those are documented Phase 7 limitations, not silently completed features.

## Reproduction commands

Commands were executed in the repository indicated, with local output under `/private/tmp/struct-guest-phase8`. Run destructive fixture stages only on fresh disposable emulator/test-database state. No production connection string belongs in these commands.

```sh
# KMP: financial/shared/auth regressions and release checks
node --test tests/*.test.mjs
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest \
  :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest \
  :app:androidApp:lintRelease :app:androidApp:bundleRelease
# Final Android-only package changes were rechecked with assembleDebug/assembleDebugAndroidTest/lintRelease/bundleRelease;
# the last JUnit signature correction was rechecked with assembleDebugAndroidTest and all three host/JVM test targets.

# Temporary iPhone 16 / iOS 26.2 simulator; runner removes it afterward
./gradlew :core:iosSimulatorArm64Test :app:shared:iosSimulatorArm64Test -PiosTestDevice=<temporary-simulator-UUID>
# Unsigned final iOS device package, no identity/credential use
GRADLE_OPTS='-Dorg.gradle.configuration-cache=false -Dorg.gradle.workers.max=2' \
JAVA_HOME=<installed-JDK-21-path> \
xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' -derivedDataPath /private/tmp/struct-guest-phase8/ios-release \
  CODE_SIGNING_ALLOWED=NO build
python3 scripts/check-ios-release.py /private/tmp/struct-guest-phase8/ios-release/Build/Products/Release-iphoneos/Portfolio.app

# Backend: NEW disposable local Postgres 18 cluster, loopback only
# initdb -D /private/tmp/struct-guest-phase8/postgres -U struct_test -A trust --no-locale
# pg_ctl -D ... -o '-h 127.0.0.1 -p 55480 -k /private/tmp/struct-guest-phase8' start
# createdb -h 127.0.0.1 -p 55480 -U struct_test struct_test_guest_phase8
JAVA_TOOL_OPTIONS='-Dstruct.test.local-postgres=true -Dstruct.test.database-url=jdbc:postgresql://127.0.0.1:55480/struct_test_guest_phase8' \
GABO_DATABASE_URL='jdbc:postgresql://127.0.0.1:55480/struct_test_guest_phase8' \
GABO_DATABASE_USER=struct_test GABO_DATABASE_PASSWORD= ./gradlew test --rerun-tasks
```

Android instrumentation used `adb -s emulator-5590 shell am instrument -w -r -e class dev.orestegabo.portfolio.investments.<CLASS> dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner`. Class names/results below identify each run. Airplane mode/Wi-Fi disable were applied; external Internet is not needed. Synthetic loopback servers and MockEngine handlers are intentionally available.

Persistence stages invoke `GuestPersistenceTest#guestInvestmentsSurviveProcessRestartAndReboot` with `-e guestPersistenceStage write|read|cleanup`. Force-stop the test app between write/read, reboot **only the task-owned emulator**, wait for boot, read again, then cleanup. `GuestStartupFixtureTest` requires `-e isolatedGuestBenchmark true`, an emulator and no existing guest choice/personal manifest. It writes nonfinancial mode preferences only. Launch measurements use `am force-stop dev.orestegabo.portfolio` followed by `am start -W -n dev.orestegabo.portfolio/.MainActivity`; Home/resume uses `input keyevent KEYCODE_HOME` then `am start -W`, without force-stop. Do not describe those as equivalent execution paths.

Dependency inventory was refreshed using the existing audit Gradle-init approach: resolve release/runtime/JVM/iOS compile configurations and declared build plugins, include backend npm lock and vendored PDF/OCR coordinates, then POST public coordinate batches to `https://api.osv.dev/v1/querybatch`. Full coordinate inventory, checked-at time, response count, errors and advisory IDs are retained in the evidence directory. This does not scan signed/native OS/container binaries or source reachability. No standalone gitleaks, semgrep, trivy or osv-scanner executable was installed; the bounded pattern scan is not one of those tools.

## Final small-screen and change record

Final `GuestMigrationUiTest` at **320 dp width, 130% system text, dark mode passed** (16.678 s scripted journey, not an application-latency metric). The [actual synthetic review preview](evidence/2026-11-02/review-small-dark.png) was inspected: destination and explanation remain readable, values/controls are reached by ordinary vertical scrolling and no automatic selection appears. This does not replace iOS visual QA or a full screen-reader review. [Native test outcomes](evidence/2026-11-02/native-test-summary.json) distinguish the intentionally skipped default persistence stage from its separately executed write/read/power-cycle/cleanup checks.

Changed source groups:

- `core/build.gradle.kts`, Android `HttpEngine`: reuse existing CIO version for safe cancellation; no shared/public API contract changes.
- `PortfolioModel`, `GuestExperienceTest`: foreground-only guest view publication and preserved durable writes.
- Android `LocalInvestmentEngine` / `PlatformPorts`, new `LocalEngineResilienceTest`: bounded renderer cleanup/replacement, generation-keyed host and lazy view startup; unchanged parser rules and private asset/network restrictions.
- Android manifest / `MainActivity`, debug `ExperimentalCaptureLayer`, Python backup/push policy test: no automatic token/analytics initialization; private capture experiment opt-in, standard protection retained; release artifact excludes the debug bypass.
- `docs/legal/content.json`, generated `LegalContent`, `LegalContentTest`: implementation-matched bilingual guest retention/transfer/deletion disclosures.
- New `HttpCancellationTest`, `GuestReleaseReadinessTest`, guarded `GuestStartupFixtureTest`; existing Android/iOS native PDF tests: focused regressions and monotonic synthetic measurements only.
- This review and reduced evidence: current threat model, measurements, exact executed checks, risk/deployment/manual gates. Backend source/schema unchanged in Phase 8; its full suite was rerun on a new isolated database and its worktree remains clean on `feature/guest-mode`.

Local execution logs remain under `/private/tmp/struct-guest-phase8`: `mobile-final.log`, `android-package-final.log`, `final-regressions.log`, `ios-tests.log`, `ios-release-final.log`, `ios-package-final.log`, `backend-tests.log`, `node-tests.log`, `python-tests.log`, `android-final-stages.log`, `android-focused-final.log`, `pdf-native-final.log`, `small-dark-final.log`, `advisory-query.log` and per-class instrumentation logs. Successful final outcomes supersede recorded intermediate failures without deleting that history. Temporary simulators were removed by their runner; task-owned Android/Postgres processes were stopped after verification.

Exact dependency/secret inspection invocations used in this workspace:

```sh
./gradlew --no-configuration-cache -I /private/tmp/struct-guest-phase8/dependencies.init.gradle \
  :core:securityAuditDependencies :app:shared:securityAuditDependencies \
  :app:androidApp:securityAuditDependencies :server:securityAuditDependencies
# Companion backend directory:
./gradlew --no-configuration-cache -I /private/tmp/struct-guest-phase8/dependencies.init.gradle securityAuditDependencies
python3 /private/tmp/struct-guest-phase8/query-advisories.py
python3 /private/tmp/struct-guest-phase8/scan-secrets.py
```

These temporary helpers adapt the existing 31 October audit method documented in `DEPENDENCY_ADVISORIES.md`; public inventories/reduced outcomes are versioned here. They are local inspection commands, not a newly installed security service/CI gate. Independently refresh the inventory and credentials-free advisory query before a real release. No signed release, production go-ahead, legal approval, push, merge or deployment is implied by the Phase 8 commit.
