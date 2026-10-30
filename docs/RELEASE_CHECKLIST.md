# Struct Capital public beta and release checklist

Source/configuration review: **30 October 2026**. This extends [the existing store audit](STORE_READINESS_AUDIT.md), [current functionality](CURRENT_STATE.md), [domains](DOMAINS.md), [email setup](EMAIL.md) and [security audit](security/SECURITY_AUDIT.md). Keep those as the detailed inventories; do not treat historical deployment notes as proof of the current production revision.

## Decision

Local release compilation is supported; **store submission and a public financial-data beta remain blocked** by the unchecked gates below. Nothing was published, signed with a distribution identity, deployed, or enrolled in a paid service during this work. Existing UI, application identifiers, storage keys and APIs are preserved. No production database or account was accessed.

## Verified baseline and repository changes

| Area | Actual implementation / status |
| --- | --- |
| Android | KMP shared Compose UI, AGP 9, API 36 target/compile, minimum 24, Java 11 bytecode/desugaring. `dev.orestegabo.portfolio` retained for update compatibility. Release disables development API override and debug hidden-API capture experiment. Minification stays disabled; changing it requires separate native/serialization/PDF testing. |
| iOS | Native Swift host + static KMP Shared framework, Xcode 26.3/device SDK 26.2 locally, minimum iOS 18.2; iPhone and iPad supported by configuration. ID `dev.orestegabo.portfolio.Portfolio` retained. No distribution archive validated. |
| Versions | Root `Version.xcconfig` is consumed by Android Gradle and iOS Config.xcconfig. Existing 1.0/build 1 retained; this is preparation, not a release bump. Installed About version already reads OS metadata. |
| Android signing | Optional four `STRUCT_UPLOAD_*` environment variables create the upload signing configuration. Partial configuration fails. `scripts/android-beta.sh` requires all four and a signed artifact. No keys generated, stored, or selected. Unsigned CI builds remain useful compile checks. |
| iOS privacy | `iosApp/PrivacyInfo.xcprivacy` bundles through the existing synchronized Xcode resource group. UserDefaults `CA92.1` covers app-only preferences; boot-time `35F9.1` covers monotonic timers; file timestamp `C617.1` covers app-container resources/files. The linked Shared static binary references `stat`, `fstat`, `mach_absolute_time`; these are not permission to fingerprint users. Collection entries describe linked email/account identifiers, financial data, uploaded content and optional push device identifiers for app functionality, without tracking. |
| Mobile CI | New `.github/workflows/mobile-release-checks.yml`: PR/main Android tests + Node parser tests + package-validator tests + full release lint + unsigned AAB, seven-day artifact retention, read-only permissions, no credentials or publishing. Optional manually dispatched macOS job pins Xcode 26.3, uses the checked-in shared scheme, builds the unsigned device Release app and inspects the packaged plist/manifest. Runner availability and the first hosted run still require verification. |
| Backend/CD | Existing sibling Spring repository already has pinned GitHub Actions, PostgreSQL integration tests, container build, workload identity, candidate health gate, traffic promotion and traffic rollback. Do not replace it. Pushing its main branch can deploy; review local security/calculation changes before pushing. This task changed no backend schema, deployment workflow or infrastructure. |
| Database updates | Flyway V1–V8 in current source; startup migrations use the configured direct connection and runtime queries use JDBC/pooling. Existing disposable PostgreSQL tests cover migration startup. No live schema/version or restore was checked here. Candidate startup can migrate the shared DB **before traffic changes**; traffic rollback does not undo schema changes. Require additive/backward-compatible migrations and an older-client test for each release. |
| API | Default production transport remains working HTTPS `https://bonds.orestegabo.dev`; public session endpoint rechecked as 200 JSON, no authenticated data fetched. New domain cutover remains a separate tested operation; redirects are not valid API migration for the current client. |
| Local/offline | Secure native saved-sign-in vault; Android backup disabled. Preferences/import-journal metadata persists locally. No persistent offline portfolio database exists: cold unlock needs server authorization; some in-memory data survives the existing warm grace. Do not market full offline access or change this security policy for release. |
| Error/crash handling | Existing bounded network requests, user-facing import/HTTP errors, section-level retry/loading and safe native failure handling. No new crash SDK/global exception swallowing added. Use Play Android vitals and TestFlight diagnostics with consent/access review; collect version/build/device/OS/error code, never credentials, raw PDFs or balances in support tickets. |
| UX/accessibility | Existing branded onboarding, library/chat imports, document viewer, About/version, English/French, reduced motion, financial masking, receipts and empty/error states retained. Tests do not establish VoiceOver/TalkBack, large-text, small-screen or iPad readiness. |
| Feedback | Existing Settings/legal email-draft contact and no-mail-app fallback are sufficient for beta. Mailboxes are source-configured, **not delivery-verified**. No new feedback service required. |
| Account/document lifecycle | Invited-account password-confirmed erasure and authenticated private document access already exist. Internal/legacy account exceptions, provider backups, retention and restored-backup erasure still need an operational decision. See security operations, not a blanket “all data immediately deleted” claim. |

The privacy manifest is an executable-bundle disclosure, **not** a substitute for a published app policy or store privacy questionnaires. Reconcile uploaded-document contents (including incidental names/account details), actual enabled SDKs, infrastructure diagnostics and final archive privacy report before submission. Do not copy its limited inventory blindly into store labels. No false “no data collected” entry is supplied.

Explicit `GaboPushEnabled`/`GaboPushEnvironment` keys were added to the custom Info.plist after the built app proved the existing `INFOPLIST_KEY_*` settings were absent. Default is still `NO`/sandbox; this does not enable APNs or add entitlements. The release checker rejects an enabled push configuration pointing to sandbox, in addition to rejecting a missing capability flag. Real production signing/delivery remains a manual gate.

## Repeatable local checks

Run from this repository, with Android SDK, JDK 21, Node 22+ and Python 3 available:

```sh
node --test tests/*.test.mjs
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
./gradlew :core:jvmTest :app:shared:testAndroidHostTest :app:androidApp:lintRelease :app:androidApp:bundleRelease
```

Android output: `app/androidApp/build/outputs/bundle/release/androidApp-release.aab`. **Unsigned unless upload signing was configured.** CI artifacts are explicitly named unsigned. Do not distribute these as Play candidates. Full lint currently reports 56 warnings; triage accessibility/platform findings rather than disabling lint or adding a blanket baseline.

For a signed Android candidate, use an existing securely backed-up upload key outside Git. Set `STRUCT_UPLOAD_STORE_FILE` (prefer absolute path), `STRUCT_UPLOAD_STORE_PASSWORD`, `STRUCT_UPLOAD_KEY_ALIAS`, `STRUCT_UPLOAD_KEY_PASSWORD` through a secure local environment/credential manager, then:

```sh
sh scripts/android-beta.sh
```

This disables the configuration cache for the signing invocation, requires credentials, builds/lints, checks for a signing certificate and verifies the bundle signature. It never uploads. Do not paste passwords in command arguments, enable shell tracing, check in keystores, or replace an already distributed signing identity. Enroll/verify Play App Signing and upload-certificate fingerprint in Console manually. Test a store-delivered APK's signing/update behavior; a valid upload-key signature alone does not prove Play delivery.

For iOS, on a Mac with Xcode 26.3 selected:

```sh
xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' -derivedDataPath /tmp/struct-release-ios CODE_SIGNING_ALLOWED=NO build
python3 scripts/check-ios-release.py /tmp/struct-release-ios/Build/Products/Release-iphoneos/Portfolio.app
```

The checker reads **built** binary/XML plists, enforces unchanged ID/shared versions, no development API override or unrestricted ATS, and packaged privacy reasons. It does not validate a provisioning profile or App Store approval. The app uses a static KMP framework, so required-reason declarations belong in the final app bundle; inventory any dynamic SDK bundles added later too.

Existing iOS regression tests require a booted simulator (`:app:shared:iosSimulatorArm64Test -PiosTestDevice=<UDID>`); core uses `:core:iosSimulatorArm64Test`. Reuse existing device-test setup; no remote device farm required. Run the sibling backend `./gradlew build` using Testcontainers/disposable PostgreSQL, **never the production database**.

## Store/account gates — human verification required

- [ ] Resolve developer legal identity and store financial classification before assuming personal enrollment is enough. Product tracks existing investments and documents; it does not execute trades, custody funds or integrate broker settlement. Do not imply BNR/broker affiliation or investment guarantees.
- [ ] Apple paid Developer Program membership and App Store Connect access are absent according to the user's latest answer (free Personal Team). Distribution signing/TestFlight require the appropriate membership/profiles. No account was created or purchased.
- [ ] Confirm Google Play account identity/type, organization requirements if applicable, account age, testing access, country availability and current Console forms. Complete financial features declaration accurately, including portfolio management.
- [ ] Reserve/check the **existing** identifiers in the intended accounts. Do not rename IDs for branding: that creates a separate app and can strand preferences, Keychain credentials, push topics and installed updates.
- [ ] Resolve iOS screenshot implementation review risk: `CaptureShieldViewController.swift` still inspects an undocumented UIKit secure-text container. Do not claim this is a supported screenshot-protection API. No privacy behavior was silently removed here; choose and verify a supported fallback before submission.
- [ ] Publish a final **app** privacy policy and accessible external account-deletion request page with verified support/privacy contact. Legacy-host routes remain 404. `structcapital.net/privacy` is a website-only draft; `/delete-account` is 404. Update app/store links to tested final URLs. Mail aliases need receiving/reply tests.
- [ ] Reconcile English/French disclosures with actual deployed security behavior, controller identity, providers/regions, retention, backup expiry, incident contact and account exceptions. Confirm Rwanda registration/transfer/storage questions with counsel using [existing legal questions](security/RWANDA_DATA_PROTECTION.md). Technical safeguards do not establish legal compliance.
- [ ] Complete Play Data safety and Apple privacy labels, age/content ratings, encryption/export questionnaire and distribution-region/trader declarations for actual practices. No advertising or paid digital features are currently implemented; do not invent those disclosures.
- [ ] Validate production backend revision/migrations, TLS, allowed origins, proxy/no-store behavior, deployment of security fixes and rollback compatibility. Review old shared issuance provenance before opening access. Complete backup/restore and erasure-on-restore rehearsal; see [operations](security/OPERATIONS.md).
- [ ] Choose push scope. Disabled/unconfigured push may be omitted from beta promises while in-app notifications remain. If enabled: configure own Firebase/APNs production credentials/topics/entitlements, inspect final plist keys, test foreground/background/terminated delivery, permission denial/revocation, account switch and unregister/logout. No push was sent here.
- [ ] Confirm rights to exchange data, institution marks, Kigali photograph and bundled runtimes/educational material. Attribution alone is not proof of license.

## Internal testing → closed beta → production

1. **Freeze a candidate:** review all pending changes in both repositories, record commits, dependency review, version/build and tested backend revision. Increment root `CURRENT_PROJECT_VERSION` for every store upload; never reuse an uploaded build number. Keep marketing version numeric. Do not change signing identities. Re-run checks on that exact candidate, not only a debug build.
2. **Private internal testing:** manually upload the signed AAB to Play internal testing; with paid Apple membership, archive Release in Xcode Organizer using the intended team/distribution settings, inspect entitlements and privacy report, validate archive, then manually upload to TestFlight internal testing. These are future operator actions; no automatic upload job is included.
3. **Dedicated fictitious account:** use existing invitations/admin tools, not the real owner account. Prepare synthetic PDFs/holdings and steps for biometric/password unlock, BNR allocation, BK contract/statement, duplicate/reconciliation review, private PDF viewer/zoom/share, annual report, coupon receipt and deletion. Ensure reviewer/tester access will remain valid through review. Do not seed real examples as user investments automatically.
4. **Release-device matrix:** Android API 24/25 and current Android; iPhone minimum supported OS/current OS; supported iPad; small display/rotation; French/English; dark/light; large fonts, TalkBack/VoiceOver; keyboard and focus; system document pickers, denied notifications, no mail app. Verify fallback financial masking in the actual Android release build. Exercise offline, backend unavailable, expired session and warm/cold biometric paths separately. No requirement is checked merely because compilation succeeds.
5. **Upgrade rehearsal:** on dedicated devices install the previous build then the candidate **over it without uninstalling**. Verify preferences, onboarding completion, vault invalidation/fallback, import journal, no duplicate records, private documents, reports and compatibility with both old/new server versions. App-store signing can differ from sideload/debug signing; verify store-delivered updates as well. Android 16 KB checks must include final store APK packaging/runtime, not only ELF segment alignment.
6. **Closed/external beta:** start with a small invited cohort, no real financial documents until privacy/security gates pass. TestFlight external testing requires Apple's beta review; Play production-access testing depends on account type/date. Capture consented minimal bug reports and issue triage. Do not collect testers' passwords or statements through support email.
7. **Promotion decision:** require no unresolved data-isolation/calculation/data-loss issues, reliable import recovery, verified deletion/support, acceptable crash/ANR trends and device timing evidence. Define an observation window/cohort before beginning; no crash-rate or performance improvement is fabricated here. Manually approve any wider rollout.
8. **Rollback:** stop a bad rollout, keep compatible backend available, and ship a higher-build-number fix. Stores do not guarantee binary downgrades. Backend traffic rollback does not reverse financial writes/migrations; preserve evidence and reconcile deliberately. Do not delete/recreate user records to recover from a release error.

## Current policy references — checked 30 October 2026

- Google requires a Financial features declaration, including on closed/open/production tracks; portfolio management is a listed category. [Declaration guidance](https://support.google.com/googleplay/android-developer/answer/13849271?hl=en-en). Its financial-products account-type requirements need classification/identity verification; do not assume the app is exempt merely because free. [Account types](https://support.google.com/googleplay/android-developer/answer/13634885?hl=en).
- New/updated phone apps currently need API 36; this app targets 36. [Target API policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-EN). For applicable personal accounts created after 13 November 2023, production access requires at least 12 continuously opted-in closed testers for 14 days and an access application; internal testing does not replace this. [Testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).
- Apps with in-app account creation need an in-app deletion path and an accessible external request resource for Play, with retention disclosed. [Google deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en). Apple also requires in-app initiation for account-creating apps. [Apple deletion guidance](https://developer.apple.com/help/app-review/guideline-reference/5-1-1-account-deletion/).
- Apple Guideline 3.2.1(viii) addresses financial trading/investing/money-management providers and necessary permissions; its applicability to this tracker requires an App Review/entity decision. Guideline 2.5.1 restricts API use to public intended purposes. [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/). No approval or regulatory exemption is inferred.
- Apple's upload minimum since 28 April 2026 is Xcode 26 with iOS/iPadOS 26 SDK or later; local Xcode/SDK meet that minimum. Recheck at submission. [SDK requirements](https://developer.apple.com/news/?id=ueeok6yw).
- Verify required-reason declarations and collection against actual code and final archive. [Apple API reasons](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitype), [KMP privacy guidance](https://kotlinlang.org/docs/multiplatform/multiplatform-privacy-manifest.html). App-level C617.1 covers container files; SDK-only 0A2A.1 is not blindly copied into the app manifest.

Policies and Console forms can change. Recheck these official sources at candidate submission, especially account classification; source inspection cannot supply developer-account approvals.

## Verification recorded for this preparation

| Check | Actual result |
| --- | --- |
| Android `bundleRelease` | PASS, ~48 MiB AAB. Packaged manifest preserves ID, version 1.0/build 1, min 24/target 36. Zero signing-certificate entries: **unsigned**, as expected without credentials. |
| Android full `lintRelease` | PASS, zero errors, 56 warnings. Prior notification permission/API-level blockers no longer fail lint. |
| Core JVM / shared Android host tests | PASS: 58 / 226 tests respectively, zero failures/errors/skips. Gradle reused up-to-date test outputs where applicable; no rerun or device execution is claimed. |
| Node document/financial regressions | PASS: 74 tests, fresh execution. |
| Release-package validator tests | PASS: 3 tests, including missing manifest, version/ID drift, development transport, unrestricted ATS, missing push flag and enabled sandbox push rejection. |
| Required-signing guard | PASS negative test: requesting a beta bundle without upload credentials fails at `validateBetaSigning` before packaging. No real signed build tested. |
| iOS device Release | PASS with signing disabled, including final incremental rebuild using checked-in shared scheme and explicit push plist keys. SDK iphoneos26.2, minimum iOS 18.2. |
| iOS packaged checks | PASS: actual app contains PrivacyInfo.xcprivacy, expected API reasons, ID/version, empty development origin; push remains NO/sandbox. No archive upload, App Store validation, certificate/profile or APNs delivery verification claimed. |
| Workflow/shell/source hygiene | `actionlint`, `sh -n scripts/android-beta.sh`, `plutil` manifest validation and `git diff --check` PASS. Hosted workflow not run. |
| Backend | Inspected existing deployment/health/Flyway configuration; no changes in this release phase. The preceding local security pass recorded 48 backend tests/bootJar on disposable PostgreSQL; not rerun against production or recharacterized as a deployment check. |
| Real-device release/upgrade/accessibility | NOT RUN in this phase; remain checklist gates. Prior shared iOS tests are documented separately, not claimed as fresh hardware verification. |

Local logs: `/tmp/struct-release-android.log`, `/tmp/struct-release-ios.log`, `/tmp/struct-release-ios-final.log`, `/tmp/struct-release-signing-guard.log`, `/tmp/struct-release-node.log`. Logs/artifacts are not committed. Production health and policy-page HTTP probes were anonymous and read-only.

## Deferred launch work

After the gates pass, test a free, invitation-led Rwanda investor beta. Explain holdings tracking, verified-document imports and scheduled/received income accurately; avoid trading/advice claims. Start with a small, measurable experiment such as an English/French store description or onboarding help copy, using voluntary qualitative feedback and store-provided aggregate metrics before buying analytics infrastructure. Prepare genuine app screenshots, a working support URL and accessible listing text; no fabricated ratings or bank affiliations.

Record acquisition channel, activation/import completion, recurring useful usage and support burden using privacy-reviewed metrics before deciding whether to widen distribution. Future premium analytics/planning, institutional subscriptions, broker-branded interfaces and APIs remain roadmap items; no paywall, billing, advertising, broker execution or paid service is introduced for beta. Reassess store billing and legal terms only if those products are actually selected.
