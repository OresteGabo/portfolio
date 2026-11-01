# Struct Capital — Security test and release plan

Reviewed **31 October 2026** against actual opaque-cookie authentication. Read alongside [report](SECURITY_AUDIT_REPORT.md) and [checklist](VULNERABILITY_CHECKLIST.md). Use only synthetic accounts/documents and disposable localhost databases. No production scanning, bulk reads, brute force, destructive fuzzing or third-party attacks are authorized by this plan.

## Results of this execution

| Gate / exact command | Actual result / evidence |
|---|---|
| `./gradlew :core:jvmTest --rerun :app:shared:testAndroidHostTest --rerun` | PASS: core 58 + shared Android host 240 tests, zero failures/errors/skips. XML under respective `build/test-results/` directories. |
| `node --test tests/*.test.mjs` | PASS: 78 parser/portable-engine/security regression tests. |
| `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` | PASS: five tests, including two new Android backup-policy checks. |
| `./gradlew :app:androidApp:lintRelease :app:androidApp:bundleRelease` | PASS after backup fix: zero lint errors/fatal, 55 warnings (version/icon/style etc.); backup-policy warning removed. Release bundle produced. Not a Play signing/provenance approval. |
| `./gradlew :app:shared:iosSimulatorArm64Test -PiosTestDevice=<temporary iPhone 16 UUID>` | PASS: 256 tests, zero failures/errors/skips, iOS 26.2 simulator; temporary simulator deleted afterward. Not a physical biometric test. |
| `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release -destination generic/platform=iOS -derivedDataPath <temporary path> CODE_SIGNING_ALLOWED=NO build`, then `python3 scripts/check-ios-release.py <Portfolio.app>` | PASS: Xcode 26.3 unsigned device Release build and packaged privacy/version/transport checks. Distribution signing not verified. |
| Backend `./gradlew --rerun-tasks test bootJar` | PASS: 55 tests, including two new migration configuration tests. Fresh loopback PostgreSQL 18 cluster, `struct_test_*` DB, test-only datasource guard, synthetic market provider, production env cleared; cluster stopped afterward. Default CI uses Testcontainers instead. |
| Next `npm test`, `npm run lint`, `npm run build` | PASS: 42 tests; no lint errors, one existing unused-variable warning; production build succeeds with Node 24.22.1. |
| Nuxt `npm run check` | PASS: typecheck/lint, 17 tests, 34 contrast checks. See exact breakdown in audit report. |
| Nuxt `NITRO_PRESET=node-server NUXT_PUBLIC_SITE_INDEXABLE=true NUXT_PUBLIC_CONTACT_ENABLED=false NUXT_DESIGN_REVIEW_ENABLED=false npm run build`, then `NUXT_PUBLIC_SITE_INDEXABLE=true npm run test:generated`, `npm run test:server` | PASS: Node production build; 10 generated-content tests + four localhost HTTP/contact tests. No real contact delivery. |
| Fresh Gradle/npm/vendored OSV inventory, `npm audit --json` | COMPLETED WITH FINDINGS: see `DEPENDENCY_ADVISORIES.md`. npm moderate-threshold gates fail; no high/critical returned in npm results. |
| Limited standard-library tracked-text/last-200-commit secret pattern scan | COMPLETED: no confirmed secret; two backend hits were README placeholder and PEM delimiter parsing, not key material. Not equivalent to a dedicated scanner. |
| Non-secret Cloud Run metadata / revision read | FAILED to obtain metadata; no current IAM/runtime setting verified, raw error withheld. Sanitized attempt saved with evidence; no production mutation. |
| Container image CVEs/SBOM; dedicated SAST/secret scanner | NOT RUN: Docker available, but no image scanner/gitleaks/semgrep installed; no container security assurance inferred. Android lint and manual source review are narrower checks. |

All logs from this execution are local-only in `/private/tmp/struct-security-20261031-audit/`. Durable sanitized advisory evidence is committed-ready under `docs/security/evidence/2026-10-31/`. Temporary logs may expire; retain only redacted evidence with access controls if needed for release sign-off. No commit/deploy was performed by this audit.

## Critical behavior matrix

| Scenario | Coverage / expected result | Status |
|---|---|---|
| Anonymous private API; opaque invalid/expired/revoked session; credential change | SecurityConfig/AuthService + `sessions are opaque revocable and invalidated by credential changes` backend regression | PASS locally |
| User A guesses B's holding/PDF/source/notification IDs for read/write/confirmation | `accounts cannot access or mutate each others purchases and PDFs`, source/reconciliation and sign-in activity tests; no data/mutation | PASS for covered routes |
| Invited account calls owner administration/shared publication | Owner-only publication/admin tests, no privileged write; account derived server-side | PASS locally |
| Caller supplies owner/role or another user's document as source | Normalization/server principal, ownership and source tests | PASS for reviewed paths |
| JWT algorithms/claims; refresh-token rotation/replay | No user JWT or refresh-token system exists; do not replace opaque-cookie tests with irrelevant tests | NOT APPLICABLE |
| Local biometric cancel/failure, expired session, slow backend, offline, duplicate unlock, logout/switch/resume | Existing shared model/API/vault lifecycle tests; no authenticated remote fallback | PASS model tests; native prompt/OS protection NOT TESTED on devices |
| Cross-origin / non-JSON mutation; streaming oversized body; malformed dates/internal exception | Existing backend negative HTTP tests; no sensitive stack/SQL response | PASS locally |
| Malformed/oversized/password-protected/active-content PDF; chunk mismatch/retry | JS/core/platform fixtures, PdfUploadPolicy/backend chunk tests; bounded safe failure | PASS fixtures; decompression/native exploit campaign NOT RUN |
| PDF filename/path or hostile content URI | Sanitized generated export paths and bounded content stream source reviewed; no server filesystem path selected by user | Source review; physical intent/provider adversarial test NOT RUN |
| Duplicate statement/contract/bond import; uncertain match; rollback | Backend/source/statement tests and portable engine tests; one financial event, explicit review | PASS locally |
| SQL injection-like input | Parameterized queries + validation source review; no live scanner | Manual review; broad dynamic injection suite NOT RUN |
| Login abuse | Synthetic throttling test, bounded requests; no load campaign | PASS scope; real proxy IP behavior NOT TESTED |
| Backup/transfer and lost device | New manifest/XML policy tests; release merged manifest verified | PASS configuration; actual OEM D2D/backup restore and iOS metadata backup NOT TESTED |
| Export/account deletion | Existing private source and password-confirmed deletion/session revocation tests | PASS server/model; provider backups/recipient-held exports outside test scope |

## Reproducible release procedure

1. Record revisions and dirty state in all four repositories. Keep real .env, credentials, private PDFs and customer exports out of fixtures/build context/logs.
2. Run the commands above. Force test execution at least once rather than counting UP-TO-DATE tasks as fresh. Check XML totals and failures; a zero exit status from a shell sequence must not hide an earlier failed step.
3. Backend: prefer CI's isolated Testcontainers PostgreSQL 18. If using local PostgreSQL, explicitly opt in with `-Dstruct.test.local-postgres=true` and `-Dstruct.test.database-url=jdbc:postgresql://127.0.0.1:<port>/struct_test_<name>` using only the synthetic `struct_test` role; never reuse production connection variables. Disable outbound push and seed import; use CountingMarketTestPages.
4. iOS: create/boot a temporary available simulator, run shared tests, stop/delete it in a trap. Separately build the unsigned device app using the workflow's `xcodebuild ... CODE_SIGNING_ALLOWED=NO` command and `python3 scripts/check-ios-release.py <Portfolio.app>`. Inspect the signed store package separately before distribution.
5. Run fresh advisory/secret checks, record versions/date/feed errors and triage. No blanket suppressions or unreviewed forced major upgrades. See advisory document for snapshot re-query and unresolved packages.
6. Review account/role route matrix whenever routes change; add negative cross-account tests for the new nested resource. Ensure shared public issuance remains explicitly separated from private purchase/statement documents.

## CI gates and remaining manual gates

Existing GitHub Actions already enforce mobile parser/model/release checks, backend PostgreSQL build/test before deployment, Nuxt type/lint/unit/build/generated/HTTP checks and npm moderate advisory gate. Checkout/tool actions are pinned, credentials not persisted; deploy-only OIDC and read-only PR cache policy are present. This audit adds backup checks to the existing Python discovery step; it does not add competing CI or silently disable Nuxt's failing advisory gate.

Before beta, build maintainer should add a reviewed secret scanner over PR changes plus scheduled full-history scans; exact-image SBOM/advisory scanning; dependency checks with explicitly expiring, justified exceptions and a new high/critical review gate. Keep untrusted PR jobs without secrets or deployment authority. Verify effective branch/environment protection and cache trust; source YAML alone cannot prove them. Resolve compatible build-chain updates rather than forcing glob/minimatch/Kotlin majors blindly.

Manual operator/mobile/security gates, all **NOT RUN** here:

- Two physical devices per platform if feasible: native biometrics cancel/fallback, passcode removed, reinstall, process death, 20-minute background, offline, expired/revoked session, screenshots/recording, lock-screen notifications, account switch and device-transfer behavior.
- Exact live runtime revision and release origin; TLS hostname verification; private-data cache headers through real proxy; no debug configuration; effective CORS and trusted proxy/IP rate limiting.
- DB runtime cannot perform DDL/alter grants; separate migration role, TLS, backup encryption/retention, successful isolated restore rehearsal and agreed RPO/RTO.
- Cloud least privilege, secrets/rotation, no public bucket/dump, logs free of sensitive payloads, monitoring/budget/incident escalation and signed artifact provenance.
- Publish a monitored private security-reporting channel and supported versions. Confirm privacy/retention notices, controller/processor registration and cross-border hosting questions with appropriate legal advice.
- Independent scoped penetration review before significant public customer exposure; no authorization to scan production is implied by this document.

## Release decision rules

**NO-GO:** unresolved confirmed high/critical exposure or failed critical behavior gate. Owner acceptance must be explicit, time-bounded and recorded; acceptance does not fix a vulnerability.

**CONDITIONAL GO:** only for the defined environment and documented noncritical residuals with named responsibility and deadlines. This audit supports continued controlled testing, not unrestricted public production sign-off.

**GO:** required behavior, deployment, operational, privacy and signing checks complete with accountable sign-off. No status means zero vulnerabilities.
