# Persistent private Guest Mode — Phase 1 architecture audit

Audited 2 November 2026. **Planning only: no Guest Mode, storage, schema, dependency or application-behavior change.** Future interfaces below are proposals, not existing APIs or permission to implement later phases.

## Git scope and evidence

Both worktrees were clean before branch setup. Mobile base: `main` at `c69389f`; companion Spring base: `main` at `9e796f9`. Created `feature/guest-mode` in each checkout. No main/develop commits, merges or pushes. Only this document is intended for the Phase 1 commit. Backend branch has no file changes.

Mobile repository: `/Users/muhirwagabooreste/AndroidStudioProjects/portfolio`.
Backend repository: `/Users/muhirwagabooreste/AndroidStudioProjects/gabo-portfolio-api`.
No applicable AGENTS.md was found in inspected ancestor/repository paths. No production service, credential, user document or database was accessed. Versions/configuration below describe checked-in source, not deployed-state verification.

Build on the existing [architecture](../ARCHITECTURE.md), [current inventory](../CURRENT_STATE.md), [target architecture](../architecture/TARGET_ARCHITECTURE.md), [security audit](../security/SECURITY_AUDIT.md), [parser guide](../DOCUMENT_PARSING.md), [import lifecycle](../DOCUMENT_IMPORT_CONVERSATION.md), [shared-document policy](../SHARED_DOCUMENTS.md) and [unlock flow](../UNLOCK_PERFORMANCE.md). This plan covers Guest Mode deltas rather than replacing those audits.

## Verified architecture and dependency map

Unless qualified as backend, mobile investment filenames below are rooted at `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/`; core filenames at `core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/`. Native investment adapters have the same package under `app/shared/src/androidMain` or `iosMain`. Backend Kotlin filenames are relative to the companion repository’s `src/main/kotlin/dev/orestegabo/portfolio/`.

| Layer / actual source | Current responsibility | Guest readiness / dependency |
| --- | --- | --- |
| `app/androidApp/src/main/kotlin/dev/orestegabo/portfolio/MainActivity.kt` | Android activity, PDF SEND/VIEW intents, push identity/registration and shared App host | Retain host; prevent guest device/account synchronization and inadvertent permission prompts. SDK initialization itself needs a privacy decision. |
| `app/iosApp/iosApp/iOSApp.swift`, `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/MainViewController.kt` | SwiftUI → Compose host, Open In receiver, optional APNs setup | Both platforms have real implementations; no full share extension should be assumed. |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/App.kt` | Creates settings/vault/PortfolioModel through lifecycle `viewModel`, foreground/background events, capture protection, shake masking | Guest selection must precede remote session startup; local lock must remain independent of server auth. |
| `InvestmentApp.kt` | Compose state-driven routes, saveable child state, onboarding, auth gate, navigation and import entry routing | `user == null` currently selects SignIn; many actions/observers require user and unlocked state. Guest must be explicit, not a fabricated user. |
| `PortfolioModel.kt` | ViewModel state, actions, synchronization, HTTP calls, financial writes, imports, receipt tracking, reports and caches | No broad repository/use-case abstraction currently interposes on all writes. Many direct `api.request` calls must be inventoried/moved incrementally behind a storage boundary. |
| `PlatformPorts.kt`, `SecureSignIn.kt` | Native settings/actions/engine and protected credential-store interfaces | Existing expect/actual/manual injection; no Koin/Hilt container or local SQL store found in inspected dependency manifests. |
| Core `PortfolioApi.kt`, native `HttpEngine.*.kt` | Ktor 3.6.0; Android, Darwin and JVM CIO engines, cookie storage, strict origin/path/redirect/response policy | Server adapter only. Do not instantiate/restore a signed-in session as the guest data source. |
| Core `PortfolioSnapshot.kt`, `BondMath.kt`, `InvestmentDates.kt`, `DecimalAmount.kt`, `InvestmentInsights.kt` | Common holdings, valuations, coupon schedule, explicit-date precedence, decimal money and outlooks | Reuse unchanged against local records. Market enrichment optional; unavailable quotes must not become zero-price losses. Native decimal implementations remain necessary. |
| `YearlyCoupons.kt`, `CouponReceiptPresentation.kt`, `AnnualReport.kt`, `PortfolioReport.kt` | Read-only coupon/receipt presentation and reports over supplied records | Local inputs feasible, but report orchestration currently fetches full server history/receipts/prices through the model. |
| `EngineDocumentParser.kt`, core `InvestmentDocumentParser.kt` | Existing parser adapter/port | Reuse for local extraction; preserve normalized review and validation. |
| `DocumentsHub.kt`, `PurchasePdfImport.kt`, `ImportJournal.kt`, `ImportDuplicates.kt` | One import conversation; receipt/retry metadata; review and duplicate helpers | Native parsing is local, but duplicate lookup, save and reconciliation currently use server records. Journal is not a local ledger or document store. |
| `DocumentPreview.kt`, native `PdfDocument.*.kt`, `DocumentThumbnail.kt`, `InvestmentEvidence.kt` | Local native rendering; authenticated download references; session-only image cache | Need local source resolution for guest IDs, not fake `/api/bonds/documents/...` URLs. |
| Backend `PortfolioController.kt`, `PortfolioService.kt`, `StatementService.kt`, `DocumentService.kt` | Owned purchases, documents, financial validation, deduplication, evidence and reconciliation | Keep authoritative for signed-in persistence. No anonymous owner/default account or weakened authorization. |
| Backend `BondInstrumentService.kt`, `PublicCouponTerms.kt`, `PendingIssuanceService.kt` | Public security facts and shared issuance propagation, separate personal holdings | Guest private imports must not implicitly contribute public terms or publish reports. |
| Backend `AuthService.kt`, `SecurityConfig.kt` | Spring Security, opaque cookie sessions and account ownership | No guest authentication endpoint required by this proposal. |

Mobile uses Kotlin 2.3.21, Compose Multiplatform 1.11.1, Material3 1.11.0-alpha07, lifecycle ViewModel, coroutines, kotlinx.serialization JSON and kotlinx.datetime (see `gradle/libs.versions.toml`). `JsonRecord`/`JsonList` are established wire/domain compatibility types. `settings.gradle.kts` includes `:core`, `:app:shared`, `:app:androidApp`, and `:server`; the in-repository Ktor `server/src/main/kotlin/dev/orestegabo/portfolio/Application.kt` is a hello-world sample, **not** the production Spring API.

Companion backend uses Spring Boot 4.1.2/Kotlin 2.3.21/Java 21, WebMVC, Security, Jackson, PostgreSQL and Flyway. JPA dependencies exist, but audited financial services use injected JdbcTemplate, bound SQL, account-scoped transactions/locks and explicit validation. Migrations V1–V10 contain the current schema (`src/main/resources/db/migration`). Configuration selects JDBC/Hikari; no runtime database inspection was performed. No microservice or new backend database is proposed.

## Current execution flow

1. Android MainActivity / iOS SwiftUI hosts shared App; native push setup may occur before sign-in. App constructs settings, secure vault and PortfolioModel; dependencies are supplied manually/default constructors and Compose locals.
2. PortfolioModel initialization loads bundled issuance calendar and education, then checks `/api/bonds/auth/session` when not locally locked. A saved vault entry determines secure-sign-in state. This occurs independently of the onboarding composition; merely adding a Continue as guest button would not prevent the existing startup request.
3. Onboarding is shown according to existing preferences; the protected scene otherwise chooses UnlockScreen, SignInScreen or authenticated screens. There is no guest route or persisted operating-mode selector today.
4. Unlock reads device-protected saved origin/email/password/session, restores cookies, validates the server session and may log in again. Only verified account state unlocks signed-in data; `startPortfolioSync` runs supplementary synchronization asynchronously. This is opaque session auth, not a JWT refresh-token architecture.
5. Private snapshot comes from `/mobile-overview`, with purchases/equities fallback, followed by documents, statements, notifications and public-term enrichment. Full history loads separately through purchases/equities APIs; `historyComplete` must not be set from a partial local view.
6. Current financial mutations (`savePurchase`, `saveEquity`, delete/confirm/import/statement operations) call HTTP directly. Notifications may initialize preferences, mark receipts or register a device. Guest mode must intercept all of these, not only login or dashboard loading.
7. `lock`, logout/account rejection and `clearPrivateData` cancel/reject stale work and clear private in-memory snapshots, active import bytes and preview caches. Background secure-lock grace is 30 seconds; data-cache window is 60 seconds. These are not persistent offline portfolio storage. Signed-in behavior must be preserved.

## PDF parsing is on-device, saving is server-dependent

**The existing mobile financial PDF parsing is not server-side.**

- Android `LocalInvestmentEngine.android.kt` executes the bundled engine inside a sandboxed WebView. Requests are limited to `appassets.androidplatform.net` local assets; arbitrary network navigation is blocked. Bundled `pdf-import.js` uses PDF.js text extraction and local Tesseract worker/WASM/English assets for limited scanned-page OCR.
- iOS `LocalInvestmentEngine.ios.kt` extracts PDFKit text, reconstructs recognized statement layout when needed, uses Vision for scanned-page OCR, and executes the shared field rules in JavaScriptCore. It does not need a cloud OCR service or hidden browser.
- Rules live in `web-logic/source/pdf-import-parser.ts`, `equity-pdf-parser.ts`, `bk-statement-parser.ts`, `document-parser-support.ts` and associated validation/error modules. `scripts/bundle-shared-logic.mjs` builds portable `logic.js`/resources; it currently depends on TypeScript tooling from the separate website checkout by default. Do not hand-edit generated bundles or assume a self-contained Node toolchain.
- BNR personal allocation/public issuance, BK Capital equity contracts and client statements already have adapters/regressions. PDF envelope, page/OCR/resource limits remain governed by `PdfPolicy.kt`, engine checks and `web-logic/source/pdf-file.ts`. Preserve ambiguity/review behavior and active-content rejection.
- After local parse, PortfolioModel queries server documents, may download candidates to establish SHA-256 equality, and saves original PDF chunks plus normalized financial records through authenticated APIs. Statement imports use backend matching/decisions; they cannot simply be declared saved locally without equivalent validation/provenance.
- Backend DocumentService validates uploads, hashes/deduplicates/stores bytes/metadata and validates supplied structured issuance facts. That is not a replacement PDF extraction engine. Private originals are owner-scoped; explicitly shared issuance documents and projected public facts have different publication paths.

Guest parsing can reuse the local adapter. Guest save, duplicate detection, IDs, evidence links and statement decisions need a real local implementation; a successful parse alone must never imply a persisted holding.

## Persistence and actual platform support

| Existing mechanism | Actual contents / constraints | Suitability |
| --- | --- | --- |
| Android `PlatformPorts.android.kt` SharedPreferences `connection`; iOS `PlatformPorts.ios.kt` NSUserDefaults | UI preferences, origin, account-scoped target and import journal | Not encrypted financial storage. Do not add holdings/PDF Base64/credentials to these preferences. |
| Native `SecureSignIn.android.kt` | AES-GCM with Android Keystore wrapping/authentication policy | Credential vault; its current payload is origin/email/password/cookie snapshot, not a guest repository. |
| Native `SecureSignIn.ios.kt` | Keychain item with user-presence ACL and WhenPasscodeSetThisDeviceOnly protection | Credential vault; guest encryption keys need separate names/service and lifecycle. Keychain items can survive reinstall; validate cleanup/key-loss policy. |
| PortfolioSnapshot/model, thumbnails | Memory; cleared on security/account transitions | Useful view state, not a durable offline store. |
| Native PDF reader/share adapters (`PdfDocument.*.kt`, `PlatformPorts.*.kt`) | Android private temporary renderer/share files and FileProvider grants; iOS temporary share directories | Temporary handling only; not durable ownership or backup guarantees. Crash leftovers/share recipients require separate handling. |
| Backend PostgreSQL | Private originals stored as Base64 content with metadata, upload chunks, owner-scoped hash uniqueness; public originals stored separately | Existing signed-in source of truth. A guest must not upload here implicitly. |

`app/shared/build.gradle.kts`: Android library plus iosArm64 and iosSimulatorArm64 static framework. `core/build.gradle.kts` also has JVM for tests/tools. Android launcher uses minSdk 24, compile/target SDK 36; iOS Xcode deployment target is 18.2. There is no configured desktop/web application target for Guest Mode; JVM core does not imply a desktop product. Android SEND/VIEW and iOS Open In are implemented. Free Personal Team/full share-extension restrictions remain as documented in the existing sharing guide.

Android application backup is disabled in `app/androidApp/src/main/AndroidManifest.xml` and extraction rules. That does not establish iOS file-backup policy. New iOS files need explicit Data Protection/backup exclusions and real-device checks. No Room, SQLDelight or persistent portfolio SQLite layer was identified in the audited manifests; platform storage must be chosen deliberately.

**Storage options to validate, not implement now:** (A) a versioned encrypted snapshot plus private original files and an atomic manifest, small dependency footprint but custom concurrency/recovery/migration work; (B) a shared SQL store with suitable encryption/native key management, better transactional/query support but dependency, native build and migration costs. Plain SQLite alone is not at-rest encryption. Prefer the smallest option that proves crash-safe financial writes and document provenance. Do not reuse an auth-gated credential payload as an unbounded financial database. Neither option has been selected in code.

## Target flow and trust boundaries (proposed)

```text
startup -> restore persisted operating-mode metadata (no financial fields)
        -> Guest -> open separate local encrypted workspace under its local-lock policy
                 -> repository snapshot -> existing calculations/screens
                 -> local parse -> explicit review -> atomic local save + original/evidence
        -> Account -> existing secure session validation -> existing remote repository/sync
Guest -> optional public market adapter only -> cached/source-dated quotations
Guest -> explicit migration decision + successful account authentication -> reviewed transfer
```

- Use an explicit mode/state type (provisional `Undecided`, `Guest(workspaceId)`, `Account(origin, accountId)`), separate from local `Locked/Unlocked` and server `Verifying/Authenticated` state. Do not set a synthetic user or bypass authenticated server gates to make UI visible.
- Guest owns a device-local namespace and encryption key. No email/account identifiers are required; no account cookies, registration, private API, push-device synchronization, shared PDF publication or public-term contribution is triggered by guest financial activity.
- Signed-in data remains server-owned and server-authoritative. Guest data stays locally authoritative until a separately consented, verified migration. Logging in must not silently upload, merge, overwrite or delete it.
- Reuse read models/calculators and visual components; resolve identity/capability at the data boundary. Prevent direct API fall-through in screens, the model, reports, native PDF inbox and background observers. Separate local/public/remote clients; existing cookies must not accompany guest public-market requests.
- A private imported PDF remains private, even if its instrument terms are public. Existing authenticated publication behavior must never execute automatically for a guest import. Local provenance links reference local document IDs, not server IDs/URLs.
- Locking may clear decrypted view state and cancel work, but must preserve committed guest files. Switching mode must not accidentally run current `clearPrivateData` as durable guest deletion. Key loss, explicit reset and account deletion are distinct flows.
- Device sandbox/key storage protect at-rest data, not against a compromised unlocked device. Masking/capture protection are presentation safeguards, not encryption. Never log document text, amounts, accounts, decrypted snapshots or keys.

## Market data and offline boundary

Bundled `issuance-calendar.json`/`education.json` and local financial calculations can work without the server. Current RSE quotes/board/history come from backend `MarketController.kt`, `MarketService.kt` and `MarketHistory.kt`, not a direct mobile RSE integration. SecurityConfig permits `/rse/**` at the filter level, but controller-level authorization still restricts administrative refresh/cron/snapshot operations; do not treat the entire prefix as unrestricted. `/coupon-terms`, documents and private endpoints require authentication today.

Proposed guest public market access is optional and isolated: no uploaded holdings/tickers derived from private data without a stated privacy policy; preserve source/as-of/stale indicators and unavailable-price behavior. Full private offline operations must not depend on market reachability. Persistent quote caching is a separate proposed store, not an implemented capability. Clarify whether privacy means “no personal-data upload” or “no network at all”; default financial workflows should remain local either way. Changing server authorization/public dataset distribution requires its own reviewed scope.

## Phase-by-phase proposed contracts and acceptance gates

Only Phase 1 was supplied as an executable mission. The following phase allocation is **provisional** and must yield to the user's later phase instructions. No interface or feature below was added.

| Phase | Proposed boundary / interface (names illustrative) | Required proof before advancing |
| --- | --- | --- |
| 1 — audit | This plan + safe Git branches | Accurate source references; no behavior change. |
| 2 — mode/lifecycle | ModeStore, WorkspaceContext, capability policy; local lock separate from server session | Restart restores choice; undecided/guest startup does not invoke account synchronization; existing auth flow unchanged. |
| 3 — durable local storage | GuestStore load/transaction/reset; versioned schema; DocumentStore read/write/delete, local ID namespace | Atomic financial/evidence writes, key loss/corruption recovery, disk-full handling, backup policy and process-death tests on both platforms. |
| 4 — repository routing | PortfolioRepository snapshot/mutations with remote adapter preserving current API and local adapter | No direct private HTTP in guest flows; identical financial results; complete-history and error semantics retained. |
| 5 — imports/documents | Existing InvestmentDocumentParser + ImportCoordinator using the selected repository/document resolver | Offline native parse/review/save, digest deduplication, retained-PDF restore, statement matching/review and no shared publication. |
| 6 — investor views/public enrichment | Existing portfolio/calendar/yearly coupons/reports over local records; optional isolated MarketDataSource | No fake prices or received income; local receipt persistence; incomplete/stale data labeled; export requires user action. |
| 7 — consented account transition | MigrationPlanner dry-run, identity/provenance map, explicit confirmation and resumable idempotent apply | No credentials until authorized, no automatic transfer; ambiguous duplicates require review; original guest evidence retained until verified completion and explicit cleanup. Backend changes/migrations need separate approval. |
| 8 — release verification | Platform/security/recovery matrix and rollout/rollback documentation | Guest persistence across restart/update, account/guest isolation, incoming PDF routing, capture/masking, accessibility, artifact/build and physical-device acceptance. No automatic store publication. |

Contracts should expose suspend operations/state flows and the existing normalized records where useful, rather than inventing a second financial engine. Start with narrow boundaries around actual call sites; avoid universal repositories, guest microservices or full multi-tenancy. Any remote-adapter extraction must have signed-in regression parity before being used as a guest entry point.

## Migration and integrity risks

1. UUID/server aliases versus local IDs: keep a separate namespace and explicit mapping. Preserve document fingerprint, original bytes, classification and receipt/provenance; never overwrite an unrelated server record by identifier coincidence.
2. Duplicate bytes are not duplicate financial events. Reuse existing evidence/matching concepts, distinguish retained files from deleted purchases, and require user review for ambiguous contract/statement matches. Port validation rather than weakening it for offline saves.
3. One operation may write a PDF, holding, receipt and journal. Design transaction/commit markers and crash recovery so neither orphan records nor “saved” receipts appear before durable completion. Handle storage exhaustion and cancellation.
4. Current financial rules exist in Kotlin, shared JS and backend validation. Reuse each at its existing boundary and test parity; do not introduce rounding, tax, coupon/yield or date-default divergence in local persistence.
5. Guest imported terms must not silently alter canonical shared instruments. Account migration must use existing ownership/canonical-validation APIs, preserving original evidence and uncertainties.
6. General financial edits do not already provide a complete immutable audit ledger. Plan local revision/provenance sufficient for corrections; do not claim institutional-grade auditability.
7. User-scoped preference keys, origin changes, stale jobs, journal IDs, incoming PDF staging and thumbnail caches all need mode/workspace scoping. A completed remote response must not populate the guest workspace after a mode switch.
8. Uninstall, OS restore, device migration and key invalidation have different semantics. Do not promise recovery when encrypted files survive but keys do not, or deletion when recipients/backups retain exports. Durable guest deletion and export require explicit confirmation in later phases.

## Decisions requiring validation before implementation

- Guest default/entry/re-entry UX and what happens when saved account credentials already exist; no automatic credential use in this phase.
- Storage engine, serialization/version format, key provisioning, encryption/authentication scheme, key rotation and local biometric/passcode fallback. Keep guest and credential keys separate.
- Device backup exclusion versus opt-in encrypted export, recovery expectations, and iOS reinstall Keychain behavior. No cross-device sync is assumed.
- Which investor features must be fully offline, statement cash movements/receipt decisions to persist, and whether any guest feature must be unavailable until parity is proven.
- Privacy policy for public price requests, telemetry/crash reporting and SDK startup. A guest screen alone does not prevent current native push initialization.
- No automatic upload/public contribution; any later account transfer UX, duplicate conflict rules, rollback/partial-transfer recovery and deletion policy need explicit agreement.
- Minimum OS/device coverage and accessibility of local unlock; emulators do not prove secure hardware, backup or physical-device behavior.

## Baseline verification and limits

See the Phase 1 verification record at the end of this file. No production credentials, schema operations, application startup against a real database, destructive fixture cleanup, user-data transfer or Guest Mode implementation is part of this phase. Later work must remain on `feature/guest-mode` and stop on unrelated worktree changes as instructed.

### Phase 1 verification record

- `./gradlew :core:jvmTest :app:shared:testAndroidHostTest :app:shared:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL. Shared Android host: 286 tests, no failures/errors/skips. Core JVM task was UP-TO-DATE, with its existing 58-case zero-failure report; this was not a newly executed core suite. iOS simulator compilation was UP-TO-DATE.
- `node --test tests/document-parser-regression.test.mjs tests/bond-pdf-import.test.mjs` — 44 passed, zero failures/skips. These are local parsing regressions, not a fresh native OCR/device acceptance test.
- Companion `./gradlew compileKotlin` — BUILD SUCCESSFUL, task UP-TO-DATE. No server was started.
- Backend integration tests were not run: their setup truncates tables on its configured test datasource, so there was no need to provision or authorize that operation for a documentation-only phase. No database connection, schema migration or credential use was performed.
- No Android APK/release build, Xcode build, native simulator test execution, physical-device test, production metrics check or Guest Mode behavioral test was performed in Phase 1. Guest Mode is not implemented.
- Local logs: `/private/tmp/struct-guest-phase1-checks.log`, `/private/tmp/struct-guest-phase1-parser-tests.log`, `/private/tmp/struct-guest-phase1-backend-compile.log`.
- Markdown references checked against the workspace; only this Markdown file is staged for the phase commit. Both repositories remain on `feature/guest-mode`; backend has no pending file changes.
