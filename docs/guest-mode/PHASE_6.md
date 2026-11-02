# Phase 6 — One shared portfolio experience

2 November 2026. Follows [the architecture plan](ARCHITECTURE_PLAN.md), [private imports](PHASE_4.md) and [gated public quotes](PHASE_5.md). Both worktrees started clean on `feature/guest-mode`; the backend remains unchanged. No push, merge, deployment, credential use or customer-data operation is part of this phase.

## Audit and implementation decision

The guest repository and financial calculations already worked, but `InvestmentApp.kt` selected a parallel `GuestContent` list for every guest tab. That prevented guests from reaching the existing dashboard, detail screens, payment calendar, history and document library. Rebuilding those screens would have duplicated both UI and financial presentation.

The shared screens already read `PortfolioModel`. It now has separate account backing state and **mode-aware read properties** for portfolio, history completeness, receipt states, quotation availability, target and loading. Personal guest reads come from `GuestPortfolioRepository`; sample reads come from a distinct fictional snapshot. Neither is copied into account caches, and neither creates a backend user/session. The existing guarded `api` accessor remains unavailable in Guest Mode. Normal account getters/setters and server authority remain intact.

`InvestmentApp` routes both modes through the existing screens. `GuestScreens.kt` now contains only guest entry and the necessary local PDF review/preview overlay using existing purchase forms; its separate dashboard/list/settings implementation was removed.

## Existing screens reused

| Existing screen/component | Guest behavior |
| --- | --- |
| `PortfolioDashboard.kt` | Same branded hero, allocation/income summaries, targets, filters and investment tiles. Compact local/sample status, personal manual-add actions and existing PDF-add FAB. No account notification bell or statement reminders. Missing quotations remain purchase-cost/principal estimates with existing valuation-basis disclosure. |
| `InvestmentDetails.kt` | Same bond identity, coupon/net/gross/tax presentation, maturity timeline, receipt ledger, provenance, edit and confirmed deletion. Personal guest receipts use the local repository; sample editing/receipt mutations are disabled. Deleting an individual holding retains its PDF as before. |
| `ShareDetailsScreen.kt` | Same valuation/cost distinction, position and purchase ledger. Adds local lot edit and confirmed delete actions only for personal guests. Missing market prices never turn purchase cost into an available quote. |
| `CalendarScreen.kt`, `YearlyCouponsScreen.kt` | Existing current-month calendar and yearly coupon navigation/totals. Uses local purchases/receipt states. Guest calendar does not inherit account-loaded public subscriptions or expose their account-only bid actions. Coupon and maturity events still come from the existing portfolio event generator. |
| `InvestmentHistoryScreen.kt` | Same complete-history search, asset/year filtering and links to details, with local load/refresh routing. |
| `DocumentsScreen.kt`, `DocumentDetails.kt` | Existing library, search/categories, genuine preview, concise details and investment links. Local source resolution, original sharing and confirmed PDF-only deletion. Original creation time is labelled first-added, not invented as an official document date. Server-generated annual report entries are not advertised to guests. |
| `SettingsScreen.kt` | Same appearance/language/chart-style, masking/capture/shake controls, support and legal/about UI. Local-storage panel replaces account identity/session settings. Account devices, push preferences, account security/admin actions and server-dependent tools are not presented as guest capabilities. |
| `LibraryScreens.kt` | Existing market/position view without guest historical-price HTTP requests or fictitious charts. Public retrieval remains disabled under the Phase 5 permission decision. |

Theme tokens, typography, existing financial formatters, touch components, navigation/back behavior and native document viewer are reused. No design-system replacement, new animation/dependency or financial calculation engine was introduced. English/French guest messages use the existing translation map. No new RTL policy/language support is claimed.

## Local actions and document boundary

- Local mutations use the existing guest action/repository boundary; busy/ready/history/receipt state reflects durable local operations. The dashboard does not wait for auth or market HTTP.
- Monthly targets are stored in the protected guest manifest's optional metadata, not under an account preference key or remote notification preferences.
- `InvestmentEvidence` and document-to-investment navigation follow persisted `documentIds`. Local references never manufacture `/api/...` URLs. `documentBytes` rejects a guest reference after switching out of Guest Mode, before any account-client fallback.
- Library thumbnails, preview and explicit user-selected sharing use the same native rendering/sharing components with locally hash-verified originals. Account sharing retains its existing implementation. Source PDFs are not automatically uploaded, synchronized, published or contributed to shared instruments.
- Common previews are closed and owned mutable buffers cleared when switching guest/account modes. Guest import preview retains its own existing cleanup. No promise of forensic RAM erasure is made.
- Back navigation preserves a PDF review when closing its original preview; navigation out of an active durable write is disabled. Local-storage links clear the current nested route and open the actual guest storage settings.
- Personal imports still require Phase 4's review/consent and duplicate rules. Unsupported guest statements/public issuance imports remain unsupported; no parallel ingestion workflow or cloud fallback was introduced.

## Sample and personal isolation

Fictional sample holdings have `sample:` IDs, fictional names, an estimated coupon schedule and no market quotation or recorded receipt. Their dates are relative illustrative dates so existing recent-history and upcoming-coupon views can be explored. The hero and detail pages explicitly identify the sample. Samples are read-only; actual personal holdings/receipts/originals remain in the separate protected repository.

`Reset sample` regenerates those fictional view records and resets navigation/saveable-view keys. It does not clear personal guest data, contact an account, generate fake market prices or copy examples into real holdings. Switching to the personal workspace restores its unchanged local records. Signing in does not upload anything; consent-based migration remains Phase 7.

## Storage explanation and confirmed deletion

Guest settings explain protected local storage, lack of sync/cloud backup, and device-loss/uninstall risk. Sign-in is an optional settings action, not a blocker on local portfolio operations.

`Delete Guest Data` requires a separate confirmation naming investments, receipt states and saved originals. It never calls account deletion, touches exported copies, or deletes another workspace. The public quote cache is reconstructible public information, not personal portfolio storage.

Deletion uses existing schema-3 metadata and the existing bounded file-operation journal:

1. Read and validate the protected manifest. Corruption or an unsuccessful initial atomic commit cannot authorize file removal.
2. Persist a deletion-requested marker and remove personal financial records/receipts atomically.
3. Remove original references in batches of at most 100, committing each batch's opaque cleanup IDs before deleting native protected files/keys. Shared legacy local-file references are retained until their final reference is removed.
4. Keep the deletion marker until every original is removed; clear target/private metadata at completion. An interrupted cleanup resumes on the next **valid** read. Failures show fixed retry guidance, never a false successful deletion.

No financial schema version bump, database migration or new cryptography is needed: optional metadata and the existing v3 journal are reused. Existing v1/v2 migration is preserved. Older builds do not understand the bulk-deletion marker; do not downgrade while deletion is pending. Logical deletion/Android key removal is not a forensic secure-erasure guarantee. Existing native storage protection and backup exclusions remain unchanged.

## Verification and previews

Synthetic fixtures only. No supplied investor PDF, production database, real credential, physical-device storage or private account was used. The debug-only `GuestExperienceHostActivity` injects an in-memory test repository and an HTTP factory that fails if constructed. Its deletion test cannot erase a real guest portfolio. The existing startup test runs only on isolated emulator userdata.

Final commands/results follow below. Rendered normal-size previews from the successful Android journey (360 dp width, light/dark):

- [Personal bond details](previews/phase6-bond-light.png)
- [Personal share details](previews/phase6-share-light.png)
- [Fictional bond in dark mode](previews/phase6-bond-dark.png)
- [Yearly coupons](previews/phase6-coupons-light.png)

Images were actually inspected: names/values wrap, expected coupons remain distinct from received amounts, purchase costs are labelled when prices are missing, and the local/sample status uses existing theme surfaces. These screenshots do not certify all devices, font scales or iOS rendering.

### Final execution record

```sh
./gradlew :app:shared:testAndroidHostTest :app:shared:compileKotlinIosArm64 :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest
./gradlew :core:iosSimulatorArm64Test :app:shared:iosSimulatorArm64Test -PiosTestDevice=<temporary-iPhone-16-UUID>
# Isolated Android 16 emulator, airplane mode, Wi-Fi/mobile data disabled:
adb -s emulator-5586 shell am instrument -w -r -e class dev.orestegabo.portfolio.investments.GuestExperienceUiTest dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5586 shell am instrument -w -r -e class dev.orestegabo.portfolio.investments.GuestEntryTest dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
# Additional accessibility-size run of GuestExperienceUiTest:
adb -s emulator-5586 shell wm size 640x1280
adb -s emulator-5586 shell wm density 320
adb -s emulator-5586 shell settings put system font_scale 1.3
git diff --check
```

| Check | Actual result |
| --- | --- |
| Final shared Android host suite | **337 passed**, zero failures/errors/skips |
| Final shared iOS simulator suite | **357 passed**, zero failures/errors/skips |
| Core iOS simulator suite | Existing **65-case** successful result reused (UP-TO-DATE); no new core calculation change |
| Android debug app / instrumentation APK | Build successful |
| Shared iOS device-arm64 Kotlin compilation | Successful; no signing/device deployment |
| Android shared-screen journey at 360 dp width / normal font | **Passed**, personal light-theme details, coupons, PDF preview, history, cancel/confirm deletion, fictional dark-theme details and sample reset |
| Android existing guest startup/restart/sign-in-return journey | **Passed** with zero local account-server requests |
| Final Android journey at 320 dp width / 130% system text | **Passed** (32.141 s test execution), both themes; below-fold content reached by scrolling |
| Whitespace check | Passed |

The six new shared regression tests cover bulk deletion including populated receipt/target/original state, interrupted native-file cleanup, initial-commit/corruption fail-closed behavior, recovery across the journal's 100-file batch boundary, shared read models/provenance/mode isolation, and plausible but explicitly fictional sample dates. The model regression also checks scheduled-coroutine deletion without self-cancellation and zeroing/closing a guest preview before account entry. Earlier phase tests that expected an intentionally empty account-facing read model were updated to expect the new local/sample projection while retaining account isolation assertions.

Initial Android instrumentation exited once without a crash trace during setup. A subsequent attempt reached the library but selected its category chip instead of the preview because both used the document label; the test now selects the preview's accessible description. At 130% text, the test initially waited for an off-screen thumbnail; explicit scrolling fixed the selector assumption. The final recorded runs passed. These intermediate attempts are not counted as successful checks or disguised as measured application performance.

Additional inspected small-screen previews:

- [Share details, 320 dp / 130% text](previews/phase6-share-small.png)
- [Fictional bond, dark, 320 dp / 130% text](previews/phase6-bond-small-dark.png)
- [Coupons, 320 dp / 130% text](previews/phase6-coupons-small.png)

Local execution logs: `/private/tmp/struct-guest-phase6-final-build.log`, `/private/tmp/struct-guest-phase6/ios-tests.log`, `android-native.log`, `GuestExperienceUiTest.log`, `GuestEntryTest.log`, and `small-ui-final.log`. Result XML counts were checked. Debug-host original files and deletion records are synthetic in-memory fixtures. The separate startup test uses disposable emulator userdata, not a physical phone or user workspace. Temporary iOS simulators were shut down/deleted by the runner.

### Changed source groups

- `PortfolioModel`, `InvestmentApp`, `GuestPresentation`, `GuestScreens`: mode-aware data/action boundaries, shared navigation, compact guest context and local import overlay.
- `GuestPortfolioRepository`: optional local target metadata and recoverable confirmed workspace deletion; existing protection adapters and financial normalization reused.
- `PortfolioDashboard`, `InvestmentDetails`, `ShareDetailsScreen`, `InvestmentHistoryScreen` (reused unchanged), `CalendarScreen`/`YearlyCouponsScreen` (reused unchanged), `LibraryScreens`, `SettingsScreen`: existing investor layouts and appropriate local capabilities.
- `InvestmentEvidence`, `InvestmentDetailComponents`, `PortfolioPurchaseTiles`, `DocumentsScreen`, `DocumentDetails`, `DocumentThumbnail`, `PurchaseForms`: local provenance/resolution/sharing and local edit details without account-client fall-through.
- `FrenchStrings`, three existing guest regression classes, new `GuestExperienceTest`, existing `GuestEntryTest`, new `GuestExperienceUiTest`, debug-only host/manifest, this document and seven synthetic screenshots.

### Remaining limits

- This completes Phase 6's shared local investor experience; it does **not** implement guest-to-account transfer (Phase 7) or release certification (Phase 8).
- Public RSE retrieval remains disabled under the founder's Phase 5 permission decision. Guest reports/server tools and account/push administration are not presented as working local features.
- Real-device iPhone/Android secure-storage, backup/uninstall behavior, iOS visual QA and TalkBack/VoiceOver interaction still require manual checks. Accessibility-tree reachability and enlarged-font screenshots are not a full screen-reader audit. French messages were added but not exhaustively visually tested; no new RTL language support is claimed.
- Existing Phase 4 Android 16 **account-mode** thumbnail HTTP-cancellation validation remains unresolved. Passing guest-local preview tests do not resolve that network path. Existing authenticated host regressions passed, but no real-account/biometric device regression or production test was performed here.
- No startup benchmark, financial advice, cloud recovery or signed public-beta readiness claim is made. The native journey's test duration measures the entire scripted journey, not startup latency.
- No backend, production, signing, account credential, schema deployment, push, merge or automatic publication change. Both checkouts remain on `feature/guest-mode`.
