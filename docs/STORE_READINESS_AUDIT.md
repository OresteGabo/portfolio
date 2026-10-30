# Store readiness audit — 27 October 2026

## Follow-up — 30 October 2026

This remains a historical audit below. Use [the current release checklist](RELEASE_CHECKLIST.md) for the latest gates and commands. Release preparation now includes a bundled iOS privacy manifest, shared version configuration, optional environment-based Android upload signing with a required-signing beta command, and mobile CI checks. Notification permission/API-level fixes already present now pass full Android release lint (zero errors, 56 warnings). Private contract price history has since been scoped by account in the backend source; the shared-history recommendation below is superseded by the security audit, not a request to introduce contribution consent. Deployment of these source fixes has not been verified.

Public URL recheck: the legacy origin's `/privacy` and `/delete-account` still return 404. `https://structcapital.net/privacy` returns 200 but explicitly identifies itself as a **website privacy draft**, not the app policy. Its `/delete-account` returns 404. A valid app policy and deletion-request path remain release gates. Store declarations, provider facts, distribution credentials, release-device tests and the undocumented iOS capture workaround remain unresolved. This preparation does not make the app submission-ready.

## Verdict

**Do not submit the current build unchanged.** Google Play looks achievable after release preparation. Apple has an additional material risk from the screenshot workaround and financial-app classification. This is a source/configuration audit plus release builds and tests, not an approval guarantee or a legal compliance certification.

Audited mobile revision: `3c77a41`, including the shared Compose UI, Android/iOS platform integrations, API transport, bundled PDF engines, legal content, release configuration and tests. Read relevant Spring account deletion and market-history code and website routes. No production records were changed. No application functionality was changed during this audit.

Not verified: store-console settings or developer membership, signed distribution archives, every screen interactively on every device, production authenticated workflows, accessibility with real assistive technology, backend penetration testing, processor contracts or regulatory authorization. A successful build does not prove those things.

## Verification performed

| Check | Result |
| --- | --- |
| Android `:app:androidApp:bundleRelease` | PASS; generated release AAB, but it has no signing certificate. |
| Android `:app:androidApp:lintRelease` | FAIL: 1 error, 57 warnings. Notification permission finding below. `lintVitalRelease` passes, so this is a release-quality finding rather than proof Play will reject the binary. |
| Shared `:app:shared:testAndroidHostTest --rerun` | PASS: 132 tests, zero errors/failures/skips. |
| Core `:core:testAndroidHostTest --rerun` | PASS: 15 tests, zero errors/failures/skips. |
| Core `:core:allTests` | PASS using existing up-to-date results, including JVM and iOS simulator suites; no new simulator interaction claimed. |
| iPhone Release, Xcode 26.3, device SDK, signing disabled | PASS, twice; packaged SDK `iphoneos26.2`, minimum iOS 18.2. No signed archive or App Store Connect upload validation performed. |
| Packaged iOS privacy resources | No `.xcprivacy` files anywhere inside the resulting `Portfolio.app`. |
| Android native ELF load segments | All eight bundled `.so` files have 16,384-byte LOAD alignment. This supports 16 KB readiness; a 16 KB runtime/device test and final delivered APK packaging check remain. |
| Production anonymous session endpoint | `/api/bonds/auth/session` returns HTTP 200 JSON. This is availability evidence, not authenticated feature verification. |
| Public legal URLs checked | `/privacy` and `/delete-account` return 404. `/bonds/privacy` redirects to `/privacy`. No dedicated public privacy/deletion route found in the inspected website source. Another URL could exist outside this source; none is configured in the app legal UI. |

Local build logs: `/private/tmp/gabo-store-audit-build-tests.log`, `/private/tmp/gabo-store-audit-fresh-tests.log`, `/private/tmp/gabo-store-audit-android.log`, `/private/tmp/gabo-store-audit-ios-final.log`. Logs/build artifacts are not committed.

## Confirmed submission and release gaps

### 1. iOS required-reason privacy manifest — submission blocker

`app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/PlatformPorts.ios.kt:32` and the Swift push delegate use UserDefaults. The built app contains no privacy manifest. Add and bundle `PrivacyInfo.xcprivacy`, declare the app's actual required-reason APIs, and inventory runtime/dependency use too. App-only UserDefaults commonly uses `CA92.1`; verify all reasons against actual use rather than adding blanket declarations. Reinspect the final archive and privacy report.

Apple rejects uploads that omit required API reasons. [Apple required-reason API guidance](https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api), [UserDefaults category and reasons](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitype).

### 2. Public privacy/deletion pages and updated disclosure — submission preparation blocker

Offline English/French legal pages exist before login and in Settings. They do not supply public store URLs. Publish a public HTML privacy policy and an external account-deletion request page that works without installing/signing into the app. Registration is available through invitations inside the app, so invitation-only access should not be treated as an exemption.

The privacy content in `docs/legal/content.json:180` describes Vercel/Neon but omits the Spring service on Google Cloud. It also omits new push tokens, device identifiers and notification/receipt preferences. The policy explicitly leaves processing locations and retention periods unresolved. Confirm real practices and update both languages. Establish the responsible legal person/entity behind the service name and ensure the listing matches. These facts cannot be invented from code.

Complete accurate App Store privacy labels and Play Data safety declarations: email/account identifiers, investment information, uploaded PDFs and their contents, device/push identifiers and relevant security logs. Map SDK behavior, purposes, linkage, sharing exceptions and retention individually; “no advertising” does not mean “no data collected.”

[Google User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en-GB), [Google deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en), [Apple account deletion](https://developer.apple.com/support/offering-account-deletion-in-your-app/).

### 3. Distribution credentials — unprepared/unverified

The Android release AAB produced by this audit is unsigned. No release signing configuration is present in `app/androidApp/build.gradle.kts`. Configure a protected upload key and Play App Signing, outside Git. Version code is currently 1; increase it for later uploads.

The iPhone builds deliberately disabled signing. Paid Apple membership was previously reported absent, but its current status was not checked. Confirm membership, App Store Connect identity, distribution signing and profiles, then validate a signed archive. Existing development-team settings do not establish distribution readiness. Complete export-compliance questions for actual cryptography; missing `ITSAppUsesNonExemptEncryption` is not automatically a rejection because App Store Connect can collect the answers.

### 4. Android notification permission lint — release-quality blocker

`app/androidApp/src/main/kotlin/dev/orestegabo/portfolio/PriceMessagingService.kt:28` fails `MissingPermission` on `notify()`. Existing code checks notifications enabled and catches errors, so this is not evidence of an observed crash. Still, explicitly check `POST_NOTIFICATIONS` on Android 13+, preserve exception handling, and rerun lint.

`MainActivity.kt:30` uses notification settings constants introduced in API 26 while supporting API 24. Guard API level and provide an older-device settings fallback. The other 57 lint warnings include dependency/style notices; they are not 57 separate store rejection blockers.

## Material review risks and incomplete features

### 5. Screenshot protection — Apple review risk and Android release mismatch

`app/iosApp/iosApp/CaptureShieldViewController.swift:92` locates a UIKit internal view by the `TextLayoutCanvasView` class suffix and repurposes secure-text rendering for the Compose screen. This is undocumented implementation behavior, not an Apple-supported general screenshot-redaction API. Working on the user's phone is not evidence of approval. There is a material risk under Apple's public-API/intended-use rules, plus future OS breakage. Resolve this before submission; retain a supported privacy fallback and document any limitation accurately. No protection was removed in this audit.

Android deliberately excludes the hidden-API capture experiment from release: `app/androidApp/src/release/kotlin/dev/orestegabo/portfolio/ExperimentalCaptureLayer.kt` is a no-op; the hidden-api-bypass dependency is debug-only. Release therefore falls back to masking amounts on the display too while protection is enabled. Do not promise the debug capture-only behavior in a Play listing. Verify the actual release UX, including dialogs, refresh, navigation and recordings.

[Apple software requirements](https://developer.apple.com/app-store/review/guidelines/#software-requirements).

### 6. Financial-app classification — policy decision needed

The implemented product records existing holdings; the inspected paths do not place broker orders, hold funds or execute payments. Explain this accurately in listings/review notes. Avoid implying affiliation with BNR, the exchange or banks.

Google's declaration includes **Stock trading and portfolio management**. Do not declare “no financial features.” Apple may apply its financial-service/entity/licensing rules broadly; a record-keeping description does not guarantee an exemption. Confirm classification and supporting identity/permissions with Apple before assuming an individual developer account is sufficient. No code audit can settle regulatory status.

[Google financial declaration](https://support.google.com/googleplay/android-developer/answer/13849271?hl=en-419), [Google financial-services policy](https://support.google.com/googleplay/android-developer/answer/9876821?hl=en), [Apple financial-services and privacy rules](https://developer.apple.com/app-store/review/guidelines/).

### 7. Shared trade-price history — disclosure/consent risk

Spring `MarketHistory.kt:41` aggregates ticker/date/unit-price observations across users' contract-backed purchases. It excludes names, PDF contents and quantities, but a rare trade can still be inferred. There IS an inline notice and privacy link on `PlanningScreens.kt:39`; this is better than a policy-only disclosure. However, there is no affirmative contribution consent or in-app opt-out. Direct PDF selection from existing purchases does not necessarily pass that screen.

Assess the lawful basis and whether Google/Apple consent requirements apply to this secondary use. Prefer a clear optional contribution control, honored by the backend, with disclosure before every affected import path. Existing disclosure is not proof of consent or guaranteed anonymization. Do not claim all financial information remains exclusively private while this feed exists.

### 8. Push notifications and Password AutoFill — not production-enabled

`google-services.json` is absent; Android only installs its Google services plugin if the file exists. Firebase push is unavailable in this checkout.

`app/iosApp/Configuration/Config.xcconfig:18` disables push; APNs defaults are sandbox/development. Associated-domain password sharing is disabled too. In the audited built iOS plist, `GaboPushEnabled` was absent, so enabling capabilities alone needs verification of the generated configuration key. Configure production credentials and entitlements, test delivery with the app closed, denial/revocation, logout and account changes. Otherwise leave unavailable controls hidden and exclude remote-push/automatic password-saving promises from the listing. Disabled push itself is not a mandatory rejection if the app works and is accurately described.

### 9. Deletion scope — invited accounts implemented; exceptions need resolution

`PortfolioModel.kt:79` exposes deletion for invited UUIDv4 accounts only. Spring verifies the password and deletes their investments, statements, PDFs, sessions, notification preferences and device records transactionally. This is a good implementation pattern. It was inspected, not exercised against a real user's production account during this audit.

Owner and environment-managed friend accounts cannot self-delete. The owner may be an internal administration exception, but a legacy friend who is an ordinary end user still needs an effective deletion path. Confirm public deletion-page coverage, retention/backup rules and complete production erasure. Do not describe this as universal self-service deletion until all end-user account types are covered.

### 10. Reviewer access, data permissions and final device testing — unverified

Prepare a dedicated reviewer account with fictitious holdings, coupons, pending records, documents and notifications. Supply safe sample PDFs and review instructions; never give reviewers the real owner account. No reviewer account was created or inspected. Ensure the published Spring API includes the endpoints/migrations used by the submitted app.

Verify rights to institution logos, the photograph, educational content and RSE data reuse. Credits are present but do not prove permission. Keep third-party licence inventory current.

The iOS target advertises both iPhone and iPad. Test iPad layouts, rotation, document export/popovers, keyboard and long translated text, or intentionally restrict supported device families if that reflects the product. Test Android API 24/25 and current Android, small screens, large text, TalkBack/VoiceOver, offline/expired sessions, biometric fallback, denied notifications, imports and deletion. Existing automated coverage is useful but is not an interactive store-review rehearsal.

## Positive findings

- Native shared Compose screens with substantial portfolio, calendar, documents, stock charts, settings and report features; not just a remote website wrapper.
- Production transport defaults to HTTPS at `https://bonds.orestegabo.dev`; the checked Release configurations have no local development API override.
- Android targets API 36, meeting the currently published new-app requirement. [Google target API rules](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-en).
- Xcode/device SDK used meets Apple's April 2026 SDK minimum. [Apple SDK requirement](https://developer.apple.com/news/?id=ueeok6yw).
- Minimal declared Android permissions: Internet, biometrics and notifications. System document pickers avoid blanket storage access.
- Saved sign-in uses authenticated platform secure storage; passwords are not Compose saved-instance state. HTTPS/session limits and controlled API paths are present.
- On-device PDF parsing/OCR, size/page limits, import review, and user-facing error mapping exist. The Android hidden WebView serves bundled algorithms and blocks external requests; this is not remotely downloaded executable functionality.
- English/French legal screens, app icons, reduced-motion support, exports, real invited-account deletion and native permission fallbacks provide a strong starting point.
- No advertising, in-app purchases or social sign-in were found in first-party source; the inspected functionality does not itself require Sign in with Apple or store billing. Transitive SDK behavior still needs its disclosure inventory.

## Recommended order

1. Add the iOS manifest; fix Android lint and signing; validate signed release artifacts.
2. Update/publish legal pages and deletion request access; prepare truthful store data/financial declarations.
3. Decide the store-safe screenshot behavior and clarify Apple financial/entity classification.
4. Cover every end-user account type and the optional shared-price contribution policy.
5. Configure/test push and password sharing if included in the advertised release.
6. Run a real release-candidate device review with a dedicated demo account and safe sample documents.
7. Complete listings, privacy labels, age/content ratings, support URL, screenshots and developer verification in both consoles.

If the Google Play personal developer account was created after 13 November 2023, production access normally requires at least 12 continuously opted-in closed testers for 14 days, followed by an application for access. Account status is unknown. [Google testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

Invitation-only distribution can use Play closed testing while stabilizing. Discuss Apple's appropriate reviewed distribution route if the app will remain limited to invited users. This audit does not equate a small invited audience with an exemption from store policies.
