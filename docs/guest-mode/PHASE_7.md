# Phase 7 — Consent-based guest-to-account transfer

2 November 2026. Implements the transfer boundary in [the architecture plan](ARCHITECTURE_PLAN.md), after [Phase 6](PHASE_6.md). The plan describes the original audit, not today's feature inventory. Both repositories began clean on `feature/guest-mode`. No merge, push, production migration, deployment, credential use or real customer-data operation occurred.

## Entry and consent

Normal account authentication remains mandatory. Account **Settings → Transfer Guest Data** explicitly opens a review. Login, biometric unlock, session restoration and background refresh never start a transfer or inspect guest originals for upload. Review fetches only the signed-in account's previous acknowledgments and loads the separate protected personal guest repository locally.

The review identifies the destination email and server. Bonds, share purchase lots, receipt states and original PDFs are listed independently; every selection initially is unchecked, even after an interrupted transfer. Financial details can be expanded. Selected originals require an actual document date checked from the PDF and can be previewed locally. The repository's upload time is never invented as the official document date.

A separate consent checkbox enables transfer. Changing a selection/date clears consent. **Not now**, Back and Stop close/cancel the workflow without deleting anything. An in-flight request may have committed before cancellation; stopping is not a rollback. A new review/retry always requires new selection and consent. PDFs are sent in full, so the review explicitly warns that a selected original may contain other investments or personal information.

Sample view records are not in the personal repository. Both client and server reject `sample:` identities. No fictional records are eligible for transfer.

## Existing components reused

- `GuestPortfolioRepository` provides protected snapshots, stable source IDs, revision checks and hash-verified PDF bytes. Migration never writes or deletes from it.
- `PortfolioModel` supplies normal authenticated `PortfolioApi`, account identity, origin and security-generation checks. Its account clear/lock/mode-switch cleanup also cancels and clears transfer state/preview buffers.
- Existing Material theme, settings/navigation, financial fact components, native protected preview and English/French message conventions are reused. `GuestMigrationScreen.kt` adds a review, not another portfolio/dashboard.
- Backend `currentAccount()`, existing private-route authentication, Origin/JSON mutation protection, account transaction lock, `PurchaseValidation`, `PortfolioService`, document chunk upload/hash deduplication and owned evidence resolution remain authoritative.
- Existing holdings and coupon receipt tables remain the financial source of truth. No second financial ledger, parser, broker integration or migration account identity is introduced.

## Data flow and authorization

```text
verified account → explicitly open review
  → authenticated GET status(accountId), no guest records/filenames
  → protected local snapshot → unchecked review → explicit selection + consent
  → selected private original chunks → selected financial records → selected receipts
  → per-item acknowledgment → normal account refresh
  → keep all original guest data unchanged
```

Every request is bound to the selected account ID; the server compares it with the authenticated account, never trusts it as ownership authority. Client origin, current ID, generation, lock/mode and closed-review checks prevent subsequent sends after an account change. Server financial/document queries are owner-scoped. Receipt parent targets are resolved through this owner's migration acknowledgments, never arbitrary client-provided cloud holding IDs.

The financial DTO whitelist excludes local IDs, file paths, revision and workspace metadata. Only explicitly selected original bytes are read/sent; generic document labels replace original filenames in upload metadata. Account status contains no guest payload. Transfers use the existing authenticated HTTPS client; no new public route, private key or credential mechanism exists. Authenticated requests retain existing server validation. No guest HTTP behavior or public quotation permission gate changed.

## Atomicity, matching and recovery

`V11__guest_migration_receipts.sql` adds an owner-scoped acknowledgment table, **not** a financial transaction ledger. Each row records stable local source ID, kind, normalized input, target/status and server acknowledgment time. Its `(owner_id, source_id)` primary key and existing account advisory transaction lock serialize concurrent retries. Financial mutation and acknowledgment commit together. JSONB comparison treats JSON numeric/key ordering equivalently.

- Same owner/source/payload retries return the original target. If the response was lost after commit, another review reads the acknowledgment and permits a user-confirmed retry without duplication.
- Existing investment matching reuses established strong bond/equity keys and additionally compares all normalized financial, instrument and account-context fields. Dates and amounts alone are insufficient. Ambiguous/mismatching records produce a review conflict.
- Notes and source links on matched cloud records are retained; new share lots preserve validated local notes. Existing different private proof, changed source payload or changed/removed cloud target produces a conflict. Nothing is silently overwritten or resurrected.
- A selected PDF uses bounded existing chunk upload, private owner/hash deduplication and full digest validation. Repeated chunks/completion resolve safely. Source acknowledgment follows completion; already-owned identical bytes reuse the file. Private categories only: Bond confirmation or Share contract. Migration does not publish public issuance reports or contribute private coupon terms to shared instrument data.
- Selected documents transfer before their selected investments. A failed selected original blocks its dependent record; independent selected records can continue. Receipts require a successfully transferred/matched parent bond, either in this attempt or a previous acknowledgment.
- Receipts preserve received/not-received, manual/automatic method and recorded amount. Automatic marking does not become verified payment. Existing inconsistent receipts are not replaced. Dates must belong to the saved bond schedule and be due; currency/status and existing whole-unit amount constraints are validated.
- Validation/conflict/size failures mark the affected item for review. Authentication, connection or unexpected failures stop further sends with fixed guidance; raw HTTP bodies/private payloads are not displayed or logged by this workflow. Already committed items remain committed.
- A changed local revision invalidates the review before sending. Guest originals and holdings are retained regardless of success, error, interruption or cancellation. Account deletion removes this account's transfer acknowledgments using the existing deletion transaction.

Progress/recovery lives in the authenticated acknowledgment table. No new insecure local progress journal is needed; acknowledgment loss is recovered by the stable source ID. Original local `recordedAt` for a receipt is retained in the acknowledgment payload; the existing receipt row schema is unchanged.

## Deployment and operational limits

Deploy reviewed backend code and additive Flyway V11 **before** distributing the client. V11 has no financial data rewrite or destructive migration. Existing authenticated APIs preserve defaults/contracts. A client against an older backend safely explains that the migration update is required; no transfer occurs. This phase does not deploy that change.

- Current support is personal guest bond purchases, active share lots, coupon receipt states and locally supported private originals. Unsupported statements/public issuance PDFs still require the existing authenticated workflows.
- Backend validation can be stricter than permissive manual guest input: an instrument ISIN, valid official schedule, recognized fields and whole-unit receipt amounts are required. Correct untransferred local data in Guest Mode, then reopen review. Missing facts are not fabricated.
- Once acknowledged, an edited local source is a conflict, not permission to overwrite the account. Use normal account correction/attachment actions after review. Similarly, adding an extra PDF to a source already transferred without that PDF is not an automatic update.
- The existing financial record supports one primary proof. The first selected compatible proof is linked there; all selected proof IDs are retained in transfer provenance. Additional selected originals are available in the account document library.
- A completed original's date can be restored from account acknowledgment. If interruption preceded acknowledgment, recheck/reenter its date; stable upload identity and owner/hash checks still prevent duplicate originals.
- Status is bounded to the latest 10,000 acknowledgments per account. Very large portfolios require a future paginated review; no unbounded institutional ingestion promise is made.
- No automatic local deletion, complete cloud backup, cross-account merge, rollback of committed items, source mutation reconciliation or institutional product is claimed.

## Verification

Synthetic fixtures only. Backend integration used a task-owned Postgres 18 cluster at `127.0.0.1:55479`, database `struct_test_guest_phase7`, blank test password and explicitly disabled optional friend/push/web-import configuration. No configured production database or customer files were used. The test harness rejects non-test database URLs.

Exact final commands, results and inspected screenshots are recorded below after execution. Phase 8 release/security certification is not part of this phase. Prior Phase 4 Android 16 **account-mode** thumbnail cancellation validation remains unresolved; local guest preview tests do not resolve it. Real-device secure-storage/backup/biometric regression, iOS rendered accessibility and production deployment remain manual checks.

### Final execution record

```sh
./gradlew :app:shared:testAndroidHostTest :app:shared:compileKotlinIosArm64 :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest
./gradlew :core:iosSimulatorArm64Test :app:shared:iosSimulatorArm64Test -PiosTestDevice=<temporary-iPhone-16-UUID>
# Synthetic debug host, MockEngine transport, temporary Android 16 emulator:
adb -s emulator-5588 shell am instrument -w -r -e class dev.orestegabo.portfolio.investments.GuestMigrationUiTest dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5588 shell wm size 640x1280
adb -s emulator-5588 shell wm density 320
adb -s emulator-5588 shell settings put system font_scale 1.3
adb -s emulator-5588 shell am instrument -w -r -e theme dark -e class dev.orestegabo.portfolio.investments.GuestMigrationUiTest dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
# Backend isolated-DB command is in companion docs/GUEST_MIGRATION.md.
git diff --check
```

| Check | Actual final result |
| --- | --- |
| Shared Android host suite | **348 passed**, zero failures/errors/skips |
| Shared iOS simulator suite | **368 passed**, zero failures/errors/skips |
| Core iOS simulator | Existing **65-test** successful result reused (UP-TO-DATE); core calculations unchanged |
| Android debug app + instrumentation APK | Build successful |
| Shared iOS device-arm64 Kotlin compilation | Successful; no signing/device deployment |
| Backend full suite | **70 passed**, zero failures/errors/skips; isolated local Postgres only |
| Native consent/decline/selected-transfer journey, light, 360 dp | **1 passed** |
| Same journey, dark, 320 dp, 130% text | **1 passed**, 17.503 s scripted test execution; not startup/request latency |
| Both repository whitespace checks | Passed |

Eleven new shared tests cover missing consent/decline, unselected PDFs, actual document-date review, selected original-before-investment linking, document dependency failures with independent progress, lost acknowledgments and recreated review, receipt-parent/method preservation, simultaneous transfer attempts/account changes, closing an in-flight review, changed local revision/cancellation, fixed-path whitelisted HTTP payloads, and actual normal-login model behavior without automatic migration. Seven backend integration tests cover authorization/consent/account/source/field validation, source and strong existing-record deduplication, conflicts/deleted targets/non-overwrite, concurrent share lots, private original ownership/hash/category, interrupted chunk upload, preserved/conflicting receipts, account-specific history and deletion. Existing auth, financial, guest-isolation/import and other tests remain in these suites.

The native journey uses a synthetic in-memory guest store and an in-process HTTP mock: normal sign-in, explicit review, select then decline (zero migration mutations), reopen with unchecked choices, select one bond, separately consent, transfer (one item POST, zero original POSTs), verify acknowledgment and unchanged local revision/PDF. It does not send files or credentials to a real backend. Backend HTTP integration uses the real controller/security/services against the disposable test database, not that UI mock.

Actual inspected synthetic previews:

- [Review, light](previews/phase7-review-light.png)
- [Acknowledgment, light](previews/phase7-saved-light.png)
- [Review, dark / small / enlarged text](previews/phase7-review-small-dark.png)
- [Acknowledgment, dark / small / enlarged text](previews/phase7-saved-small-dark.png)

The first visual pass exposed awkward email/server wrapping in a two-column layout; full-width account identity corrected it. Receipt rows also identify their bond, not just a potentially shared payment date. Final images show readable full values with normal vertical scrolling. Native accessibility-tree navigation reaches selection, decline, consent, transfer and results at enlarged text; this is not full TalkBack/VoiceOver certification or iOS visual QA.

Intermediate checks: one test assertion needed a Kotlin generic type correction; the initial non-escalated Gradle attempt could not access its cache. Emulator setup initially lacked a telephony service, and two UI attempts were obscured by a **System UI** ANR dialog (captured in the failure accessibility tree). Restarting that temporary emulator system component allowed the unchanged journey to pass. Final runs above were performed on the final implementation. Existing unrelated compiler deprecation/interop warnings remain.

Local evidence: `/private/tmp/struct-guest-phase7/mobile-final.log`, `ios-tests.log`, `backend-all-tests.log`, `android-ui-final.log`, `GuestMigrationUiTest.log`, `android-small-dark.log`, and Gradle result XML in each repository. Temporary simulators are removed by the runner; the task-owned emulator and Postgres cluster are stopped after verification.

### Changed files

KMP: new `GuestMigration.kt` coordinator/HTTP adapter and `GuestMigrationScreen.kt`; `PortfolioModel.kt` explicit entry, binding, lifecycle and normal post-ack refresh; `InvestmentApp.kt` navigation; `SettingsScreen.kt` entry; `App.kt` protected-document cover; `FrenchStrings.kt` translations. New `GuestMigrationTest.kt` and Android `GuestMigrationUiTest.kt`; Android build adds the existing Ktor MockEngine version **only to instrumentation tests**. This document and four inspected synthetic previews complete the phase record.

Backend: new `GuestMigration.kt`, additive Flyway V11 and `docs/GUEST_MIGRATION.md`; `PortfolioService.kt` narrowly scoped migration save options with existing defaults preserved; `SecurityConfig.kt` bounded private request bodies; `AuthService.kt` acknowledgment deletion; existing integration suite extended and optional external-service test settings explicitly disabled.

Phase 7 is implemented and committed on `feature/guest-mode` in both repositories. Backend deployment/V11 rollout remains a reviewed operational action; physical-device/iOS visual verification and the earlier account-thumbnail issue remain outstanding. No Phase 8 audit, merge, push or release readiness approval is implied.
