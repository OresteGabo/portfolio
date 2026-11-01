# Struct Capital — Security audit report

- **Audit date:** 31 October 2026 (local execution; OSV snapshot 15:13:45 UTC).
- **Auditor/tool:** Codex-assisted source/configuration review and synthetic local test execution. No independent certification or production penetration test.
- **Baseline revisions:** mobile `0c99a4f80595c9ed88b5002f99371498e7560309`; Spring `5f291bee9c666143e22650d4fc14948f7094dcfe`; Next `6eb09c2aa5d5eab46b54323fb54d762dc1ab45ed`; Nuxt `f2d7b989d84feb0a531e0bb567921322f2b741fa`. Results include the uncommitted audit changes below. The six already-staged security policy/template files were preserved in the index; their working copies were updated as requested. No commit/push/deployment performed.
- **Scope:** accessible security-critical mobile, real backend, two websites, migrations, release/deployment and CI source; local test/runtime checks. Paths below are relative to their named repository. Full platform/toolchain/cloud penetration coverage is explicitly excluded.
- **Environments:** macOS, Xcode 26.3, temporary iPhone 16/iOS 26.2 simulator; Android JVM/release build tools (no physical device); Java 21 Gradle builds; isolated local PostgreSQL 18; Node 24.22.1 websites; Docker 28.5.1 available.
- **Dynamic authorization:** local synthetic test fixtures and disposable localhost services only, under this mission. No real user documents/accounts or live service probes, changes, secret rotations, migrations or data reads.
- **Recommendation: CONDITIONAL GO for continued controlled testing. Public beta/production approval is withheld** until the operational and release gates below are verified and open findings triaged by accountable owners. This does not claim a confirmed current production breach or a critical exploit.

## Executive summary

The established architecture already contains material protections: opaque hashed server sessions with expiry/revocation; platform user-presence vaults; server-side account isolation; role-gated administration; Origin/JSON mutation defenses; bounded and idempotent PDF uploads; private document ownership; source-aware financial reconciliation; generic errors; TLS release restrictions; non-root backend container; pinned CI actions and deploy-only OIDC. These were reused, not replaced.

No new confirmed exploitable critical/high defect was demonstrated in the reviewed source and synthetic tests. That is a limited result, not evidence that none exists. The earlier high-impact private-price/role/transaction issues documented on 30 October remain covered by passing regressions; they are not reclassified as new findings. This audit makes three small hardening changes: Android transfer exclusions, optional independent migration credentials and a verified official Gradle distribution checksum pin. Four new regression tests cover the first two.

Open risks include moderate build dependency advisories, absence of account-wide completed-document quotas, incomplete revision history for general holding edits, and unverified effective cloud/DB/backup/device/signing controls. A fresh scan is **not clean**, even though no npm high/critical was returned. No production changes were attempted to make this report look complete.

## Architecture and data-flow evidence

Repository roots:

- Mobile: `/Users/muhirwagabooreste/AndroidStudioProjects/portfolio`.
- Real API: `/Users/muhirwagabooreste/AndroidStudioProjects/gabo-portfolio-api` (not mobile `server/`).
- Investor web/proxy: `/Users/muhirwagabooreste/WebstormProjects/gabo`.
- Marketing web: `/Users/muhirwagabooreste/WebstormProjects/struct-capital`.

| Boundary / sensitive flow | Evidence and actual control |
|---|---|
| Startup, unlock, session restore, logout, account switch | Mobile `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PortfolioModel.kt`, `PortfolioApi.kt`, `SecureSignIn.*.kt`, platform MainActivity/Swift host. Ktor HTTPS/origin/no-redirect policy and bounded responses; native user presence protects saved credentials/session; server validation remains separate. Shared SecureSessionTest/PortfolioModelTest cover cancellation, expired session, backend delay/offline, deduplication and cleanup. |
| Native vault/storage | Android Keystore auth-bound AES-GCM wrapping; iOS passcode-set-this-device-only Keychain with user-presence access control. Portfolio data is held in private in-memory state; account-scoped import journal metadata/targets/preferences persist separately. Journal deliberately excludes raw PDFs/extracted financial values but filenames can still identify people. New Android rules exclude cloud/device-transfer storage. iOS metadata backup protection and physical-device behavior remain unverified. |
| Auth/roles | Spring `src/main/kotlin/dev/orestegabo/portfolio/{SecurityConfig,AuthService,AuthController}.kt`. 32-byte random opaque sessions, SHA-256 server token storage, 12-hour expiry; account/credential status checked. PBKDF2 600,000 iterations plus legacy scrypt compatibility. Persistent hashed identity/IP/global login limits. Owner-only invitation/account administration and shared issuance. No user JWT or refresh-token subsystem. |
| Private objects / APIs | Spring PortfolioController, notification controllers/services, DocumentService, StatementService, PortfolioService. Account comes from principal, SQL predicates restrict owner. Nested sources and reconciliation actions verify account access. Private document IDs are not authorization. Shared public issuance has an explicit different scope. |
| Endpoint surface | Auth: session/login/register public under validation, logout/private account/invite administration protected; portfolio purchases CRUD/confirm, equities, overview, documents/chunks/download, statement reconciliation/review, coupon receipts, targets and notifications private. Market quotes/market/history public; private-derived history only for current account. Daily/snapshot jobs require separate server secrets. Health public; unmatched routes denied. Review the API repository's `docs/API.md` for exact contract, not an invented parallel API. |
| Web authorization / CSRF | Next `next.config.ts`, `lib/portfolio-api-origin.ts`, `proxy.ts`: all investor API paths rewrite to Spring, no legacy handler fallback. Spring stateless secure Strict cookie plus strict allowed Origin and JSON on mutations; CORS exact allowlist. Framework CSRF is disabled with those compensating checks, not an unprotected cookie endpoint. |
| PDFs / extraction | Mobile LocalInvestmentEngine implementations and bundled logic; native PDFKit/Vision on iOS and isolated asset-only Android WebView/JS engine; file size 10 MiB, extraction page/text/OCR/time limits, active-content rejection. Spring DocumentService/PdfUploadPolicy bounds chunks/count/size/envelope, account-locks completion, digest-deduplicates within owner. Postgres base64 JSON content is **encoding, not encryption**. Server does not run a second parser or authenticate institution-issued financial truth. |
| Financial integrity | Existing BigDecimal backend normalization/calculations, transaction/account locks, uniqueness, independent instruments, source documents, statement rows and reconciliation decisions; manual confirmation for ambiguous matches. Regression coverage includes synthetic bond coupons/equity fees, retry/source isolation and pending-allocation duplicates. General mutable holdings still lack full versioned history. |
| SQL / DB / errors | JdbcTemplate parameters and controlled identifiers; Flyway V1–V9, no Hibernate schema generation; bounded connection/query timeouts. ApiSupport returns generic validation/internal errors and logs exception class, not SQL/body. New cloudrun credential settings allow migration role split without changing current grants. |
| External calls | MarketService uses fixed exchange URLs/cache; optional push uses configured provider credentials, not client-supplied URLs. No arbitrary user URL fetch or broker integration found. Live push delivery and provider configuration not tested. |
| Marketing contact/content | Nuxt `server/api/contact.post.ts`, `server/services/contact.ts`, `nuxt.config.ts`: disabled by default, exact Origin/JSON/stream size/time/schema limits, in-process hashed-IP throttling, explicit approved private HTTPS endpoint, redirect denial, bounded acknowledgment. Raw article HTML disabled in compiler; structured data escaped. Local HTTP tests never deliver to a real recipient. |
| Build/deploy | Mobile `.github/workflows/mobile-release-checks.yml`, backend workflow/Dockerfile/cloudrun config, Nuxt quality workflow: pinned actions, read-only defaults, release/debug separation; non-root runtime. Deployment source uses tested commit digest/candidate health before traffic. Live metadata read failed; actual runtime IAM, branch protections, secrets and backup settings not verified. |

The [threat model](THREAT_MODEL.md) expands actors, flow sequence, data retention and residual scenarios. [MASVS](https://mas.owasp.org/MASVS/), [ASVS](https://owasp.org/www-project-application-security-verification-standard/) and [OWASP API Security](https://owasp.org/www-project-api-security/) are used as review categories, not certification claims. Selected CWE mappings follow below.

## Findings

| ID | Severity / confidence | Finding and evidence | Remediation / status |
|---|---|---|---|
| S01 | Medium, confirmed configuration gap; exploit not demonstrated | Android `allowBackup=false` lacked explicit Android 12+ device-transfer rules. Import journal/preferences can contain private filenames/metadata even though vault ciphertext is device-bound. | **Fixed in source:** explicit legacy backup opt-out and both cloud/device storage exclusions; two tests and release manifest/lint/bundle verification pass. Physical transfer gate remains. |
| S02 | Medium, confirmed configuration limitation; production privilege unknown | cloudrun Flyway user/password were coupled to runtime credentials. Operators could not configure a separate migration role through those existing settings. Does not prove runtime was using an admin role. | **Configuration support fixed:** optional separate migration credentials with compatible defaults; two synthetic configuration tests. Actual role/grant separation **not performed**, operator gate open. |
| S03 | Low, confirmed supply-chain hardening gap | Backend Gradle wrapper omitted `distributionSha256Sum`, unlike mobile. HTTPS alone did not pin expected archive bytes. | **Fixed:** official Gradle 9.8.2 archive SHA-256 retrieved and pinned; wrapper command succeeds. Existing cached distribution was not independently re-downloaded/re-authenticated. |
| S04 | Medium, confirmed structural resource-control gap | DocumentService caps individual files/staging, but no completed-document lifetime byte/count quota. Owner lists can grow without pagination. Authenticated abuse or ordinary growth can exhaust the small DB/response budget. | **Open:** no destructive load reproduced; define per-account/storage policy, then implement byte/count accounting and bounded listing with compatibility tests. Do not silently delete documents. |
| S05 | Medium, confirmed auditability limitation | Reconciliation has evidence/correction history, but general holding update/delete has no complete immutable revision chain or optimistic version conflict protocol. | **Open:** retain current behavior; design revision evidence/conflict handling before wider use. Not evidence of a duplicate ledger or a trading vulnerability. |
| S06 | Moderate advisory severity; applicability varies | Kotlin build-cache and npm build/packaging advisories; Nuxt range ambiguity and unpatched low deepmerge. Fresh saved feed evidence, not a static guessed CVE list. | **Open / triaged:** see dependency review. No untested compiler/breaking parent upgrades or blanket exceptions introduced. |
| E01 | Severity not assigned, external evidence missing | Effective IAM, production DB/TLS/encryption/grants/backups, real device/signing controls and monitoring cannot be established from repository source. | **NOT TESTED**, explicit before-beta/production gates. Do not mislabel unknown settings as confirmed vulnerabilities. |

### S01 — Android transfer policy

**Prerequisite/impact/likelihood:** OS/vendor transfer implementation copies app metadata despite allowBackup=false; an authorized transfer or compromised destination can move filenames/preferences. Device-bound encrypted credentials remain protected by their separate key policy; no plaintext token transfer was demonstrated. Medium priority privacy defense, not a biometric bypass. [Android's official backup guidance](https://developer.android.com/identity/data/autobackup) documents manufacturer-dependent transfer behavior. Mapping: MASVS-STORAGE, CWE-312 (metadata exposure category).

**Safe reproduction/evidence:** inspect pre-fix manifest for absent dataExtractionRules; baseline release lint emitted DataExtractionRules warning. After fix, both backup/transfer XML domains are excluded, merged release manifest references policy; warning absent (56 → 55 total warnings, zero errors). Synthetic policy tests protect those invariants. No actual customer backup/transfer attempted.

**Owner/deadline/residual:** proposed mobile maintainer; source fix complete, physical Android OEM backup/transfer check before beta. iOS metadata backups need a separate review; XML cannot control them.

### S02 — Migration/runtime credentials

**Prerequisite/impact/likelihood:** runtime and migration role are configured identically; if that role has DDL/ownership privileges, a future SQL/in-process compromise has a larger blast radius. Effective grants are unknown, so elevated production privileges are not alleged. Mapping: ASVS least privilege, CWE-250 (conditional impact).

**Safe reproduction/evidence:** read pre-fix `application-cloudrun.properties`; Flyway's user/password resolved directly from GABO_DATABASE_USER/PASSWORD. New optional GABO_MIGRATION_DATABASE_USER/PASSWORD can differ while runtime settings remain unchanged. Tests use only synthetic strings and no real role changes; existing deployments retain defaults.

**Owner/deadline/residual:** proposed backend/cloud operator; review a restored/synthetic branch, create required least-privilege roles/default grants and test all operations, then separately approve deployment. Before beta. Config alone does not fix overprivileged live grants. No schema migration was added.

### S03 — Wrapper checksum

**Prerequisite/impact/likelihood:** substituted distribution delivered through compromised distribution path/cache; low likelihood, build compromise impact. Mapping: CWE-494, software supply chain. Official checksum source: `https://services.gradle.org/distributions/gradle-9.8.2-bin.zip.sha256`; stored value `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`.

**Verification/owner/residual:** `./gradlew --version` succeeds after the pin; backend build/tests also pass for the other changes. Proposed build maintainer should require wrapper integrity review and trusted clean caches at release. A checksum in source does not independently authenticate a compromised wrapper JAR; no full supply-chain assurance claimed.

### S04/S05 — Limits and financial correction history

These are source-confirmed design limits, not attacks executed against production. S04 mapping: API4/CWE-400; S05: CWE-778 audit completeness and CWE-362 stale-edit risk. Existing per-request bounds and atomic reconciliation reduce exposure. No disruptive quota, pagination contract, new ledger or audit schema was introduced during security review. The backend maintainer should specify behavior and add boundary/concurrency/history tests before beta expansion. A low-cost provider/storage budget is not an application quota or reliable abuse defense.

### S06 — Dependency findings

See [DEPENDENCY_ADVISORIES.md](DEPENDENCY_ADVISORIES.md) for all five actual advisory IDs, exact installed versions, fix ranges, dependency parents, reachability limits and feed discrepancy. Proposed build/web maintainers must schedule compatible patch testing **within 14 days or before beta**, whichever is earlier. No risk acceptance has been granted. Do not promote caches/artifacts from untrusted contributors to privileged builds; existing read-only PR cache policy helps but does not patch unsafe deserialization.

## Dependency advisories

The fresh OSV scan covered 1,775 coordinates; npm results include moderate findings in both websites. **No dependency-clean sign-off.** No OSV match was returned for the inventoried Spring runtime or vendored JS PDF/OCR runtime, but native OS/compiler/container coverage remains incomplete. Full triage table and durable evidence live in the linked dependency document to avoid duplicating an independently changing CVE list.

## Checks and test results

All counts below were read from fresh execution logs/XML, not copied from the historical audit:

| Command/check | Environment | Actual result |
|---|---|---|
| `./gradlew :core:jvmTest --rerun :app:shared:testAndroidHostTest --rerun` | KMP JVM / Android host | PASS: 58 + 240 tests, zero errors/failures/skips |
| `node --test tests/*.test.mjs` | Mobile portable logic/parser | PASS: 78 tests |
| `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` | Repository release-policy tests | PASS: five tests, two added |
| `./gradlew :app:androidApp:lintRelease :app:androidApp:bundleRelease` | Android release | PASS: bundle built; lint 0 errors/fatal, 55 warnings. Transfer warning removed. |
| `./gradlew :app:shared:iosSimulatorArm64Test -PiosTestDevice=<temporary UUID>` | iPhone 16 / iOS 26.2 simulator | PASS: 256 tests, zero errors/failures/skips; simulator cleaned up |
| `xcodebuild ... -configuration Release -destination generic/platform=iOS CODE_SIGNING_ALLOWED=NO build` + `scripts/check-ios-release.py` | Xcode 26.3 device package | PASS: unsigned build and packaged release metadata/privacy/transport checks; signing not tested |
| Backend `./gradlew --rerun-tasks test bootJar` | Fresh synthetic local PostgreSQL 18 | PASS: 55 tests, two added; cluster stopped; outbound market mocked, push disabled |
| Next `npm test`, `npm run lint`, `npm run build` | Node 24.22.1 | PASS: 42 tests, build; one existing unused-variable warning, no lint errors |
| Nuxt `npm run check` | Node 24.22.1 | PASS: typecheck/lint, 17 tests, 34 contrast checks |
| Nuxt Node build, `npm run test:generated`, `npm run test:server` | Production-mode localhost, synthetic contact inputs | PASS: build, 10 generated tests, four HTTP tests |
| OSV public-coordinate query; `npm audit --json` | Public advisory services only | COMPLETED WITH FINDINGS, no request failures; moderate npm threshold fails |
| Limited tracked-text + last 200 commits per repository secret scan | Standard-library reviewed local script | No confirmed secret. Two backend pattern hits reviewed as README sample URL and PEM delimiter parsing. 896 tracked text files scanned; binary/large files skipped. Not a full secret-scanner pass. |
| Android lint/manual security source review | Local source | Completed with scoped findings above; no Semgrep/enterprise SAST claim |
| Container CVE scan, dedicated secret scanner, whole-history scan, live TLS/IAM/database review, physical device tests | Not available/run in scope | NOT RUN; Docker exists but scanner tools not installed. No live targets tested. |

Exact commands/safety conditions and scenario mapping are in [SECURITY_TEST_PLAN.md](SECURITY_TEST_PLAN.md). First Gradle invocations hit cache permissions or UP-TO-DATE tasks; approved cache access and forced test reruns supplied the actual evidence above. Such initial failures were not counted as passed checks. Local logs are in `/private/tmp/struct-security-20261031-audit/`; retain redacted summaries rather than publishing raw build logs. Source review is not equivalent to exhaustive dynamic testing of every endpoint.

Structured local result counts are retained in [verification.json](docs/security/evidence/2026-10-31/verification.json).

## Changes made

| Repository / file | Change / rationale | Verification |
|---|---|---|
| Mobile `app/androidApp/src/main/AndroidManifest.xml`, `res/xml/data_extraction_rules.xml` | Explicit legacy/cloud/device-transfer exclusions for private app state | Two new `scripts/tests/test_android_backup.py` cases, merged release manifest, lint/bundle |
| API `src/main/resources/application-cloudrun.properties` | Optional independent migration user/password; preserve runtime settings/defaults | Two new `MigrationCredentialConfigurationTests.kt` cases; full 55-test suite |
| API `gradle/wrapper/gradle-wrapper.properties` | Official archive checksum pin | Official published SHA retrieved; wrapper command succeeds |
| API `docs/CLOUD_RUN.md` | Safe role-split operator steps, fallback and limits | Reviewed against config/test behavior; no live grants changed |
| Mobile root security policies/report/test plan/threat model/checklist; `DEPENDENCY_ADVISORIES.md` and sanitized evidence | Replace unverified baseline with current, scoped facts and outstanding gates | Cross-checked logs, XML, source and fresh feed; report template preserved |
| Mobile `docs/security/SECURITY_AUDIT.md` | Link historical audit to current follow-up | Existing historical evidence retained |

No authentication bypass, changed financial algorithm, dependency replacement, new parser, new security infrastructure, public API contract or production migration was introduced. Websites were inspected/tested, not modified.

## Outstanding external checks

**Not established by this audit:** current Cloud Run revision/ingress/service account and IAM, Vercel team/deployment protections, Neon grants/network/encryption/retention, database backup completeness or restore success, billing/abuse alerts, effective session cookie headers through the deployed proxy, live Firebase/APNs permissions, signing-key custody and App Store/Play package contents, actual physical-device secure storage/capture/transfer, complete history/binary secret absence, actual incident response capability.

A read-only `gcloud run services describe` non-secret metadata attempt failed, including a simpler revision-only retry; no current cloud setting was verified and no production data was read or changed. Raw errors/credentials were withheld. The sanitized attempt result is in `docs/security/evidence/2026-10-31/cloud-metadata-attempt.json`. No Neon/Vercel connector was available in the discovered tool inventory, and neither Neon nor Vercel CLI was on PATH. Re-establish authenticated read-only operator access for those gates; this audit did not log in, request broader permissions or buy tooling.

Repository deployment notes describe live use and earlier verified configurations; they are historical evidence, not fresh console verification. Encryption requirements in docs do not prove actual JDBC/provider settings. Metadata, delivered exports, shared public issuance and backups have separate retention/deletion questions. No production document/account totals are needed in this report.

Rwanda legal verification must cover the actual controller identity/contact, registration, lawful basis, DPO responsibility, subprocessors, foreign hosting/storage/transfer permissions, retention/rights and breach procedure. [Official DPP guidance](https://dpo.gov.rw/faqs) describes registration/transfer and breach duties; [the authority's obligations chapter](https://dpo.gov.rw/dpp-law/obligations-of-the-data-controller-and-the-data-processor) specifies 48-hour notification and 72-hour reporting. These are issues for qualified verification, not a finding that technical controls establish legal compliance. See existing `docs/RWANDA_DATA_PROTECTION.md` rather than inventing a new legal policy.

## Remediation roadmap

| Priority / when | Proposed accountable role | Concrete exit evidence |
|---|---|---|
| Immediate, before next distribution | Founder/build maintainer | Review these diffs; triage S06 compiler/build advisories and schedule compatible patches; retain trusted cache boundaries. No founder risk acceptance implied. |
| Before inviting wider beta, no later than 14 Nov 2026 review | Backend/cloud operator | Read effective IAM/grants/TLS, validate separate runtime/migration role on isolated restored data, perform backup restore; document retention/RPO/RTO and incident owner. |
| Before wider beta | Backend/product maintainer | Specify completed-document byte/count quotas, pagination/error UX and general financial correction history/version behavior; then implement narrowly with regression tests. |
| Before public beta | Mobile/release maintainer | Physical biometric/lock/resume/capture/transfer tests; signed APK/IPA inspection and supported OS policy; verify clean-cache builds and publish supported versions. |
| Before public beta | Founder/security maintainer | Configure monitored private reporting channel, effective branch/env protections, reviewed secret scanning and exact-image SBOM/advisory gates; record any exception with owner/expiry. |
| Before public production | Founder/operator plus legal adviser | Complete privacy/registration/transfer/retention verification, recovery/monitoring drill; independently scoped penetration review where feasible. |
| Every release / weekly advisory review | Engineering maintainer | Refresh dependency evidence, scan exact image and artifacts, recheck account-boundary tests and provider security/OS updates; revisit after any auth/import change. |

Do not implement institutional multi-tenancy, broker CRM/execution/custody, enterprise certifications, a new identity platform or paid cloud OCR as a substitute for these concrete gates. Preserve the low-cost architecture.

## Decision and sign-off

**CONDITIONAL GO for continued controlled testing only. No public release security approval is granted here.** No newly confirmed unmitigated critical/high exposure was demonstrated; current critical synthetic behavior tests pass. Noncritical findings and unverified operational gates require review, not silent acceptance. A confirmed high/critical or failed critical gate changes the decision to NO-GO until remediated or formally accepted under project policy.

The founder must assign the proposed roles and approve any risk acceptance and production actions separately. **Next review: by 14 November 2026 or before public beta, whichever is earlier.** Static review, advisory queries and passing tests cannot establish absence of compromise or comprehensive security.
