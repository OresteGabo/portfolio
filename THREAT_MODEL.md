# Struct Capital — Threat model

**Reviewed 31 October 2026.** Verified source and synthetic local tests, not a production assessment. See [audit](SECURITY_AUDIT_REPORT.md), [checklist](VULNERABILITY_CHECKLIST.md) and [advisories](DEPENDENCY_ADVISORIES.md). Struct Capital tracks investments; no custody, trade execution, settlement or broker integration was found.

## Scope and architecture facts

| Component | Verified implementation / evidence |
|---|---|
| Mobile `AndroidStudioProjects/portfolio` | Kotlin 2.3.21 / Compose Multiplatform 1.11.1; shared PortfolioModel, Ktor 3.6.0 client; Android SDK 36/min 24; native Swift host on iOS. `server/` is an unused Ktor sample, not the real backend. |
| Backend sibling `AndroidStudioProjects/gabo-portfolio-api` | Spring Boot 4.1.2, Java 21; Spring Security stateless filter chain, JdbcTemplate SQL, PostgreSQL, Flyway V1–V9; JPA is present as a dependency, not an alternative financial persistence engine. |
| Investor website `WebstormProjects/gabo` | Next.js 16.4.2 / React 19.2.3. `/api/bonds/**` rewrites to Spring before file routes; no legacy Next API handlers remain. |
| Marketing website `WebstormProjects/struct-capital` | Nuxt 4.6.3 / Vue; educational content compiled from reviewed Markdown with raw HTML disabled. Optional contact delivery disabled by default; no investor API connection. |
| External services | Fixed RSE market reads, optional Firebase/APNs push, Cloud Run deployment/Secret Manager, Neon Postgres and Vercel referenced in deployment docs. A non-secret Cloud Run metadata read failed; current live IAM, grants, region, encryption, logs and retention are **not verified**. |

## Assets, actors and boundaries

Sensitive assets: credentials, opaque session cookies, account email, private contracts/statements/PDFs, holdings, financial movements and fees, source/reconciliation decisions, import filenames/history, push registrations, server/signing keys and backups. Shared issuance results and official market observations are distinct from private purchase evidence.

Actors: anonymous network clients; account holders; malicious account holders; designated owner/admin; developers and CI; document senders; device users/OS; infrastructure and upstream providers. There is account isolation, **not institutional multi-tenancy**.

1. OS user-presence prompt → local credential vault. Android uses auth-bound Keystore AES-GCM key wrapping; iOS uses passcode-set-this-device-only Keychain plus user presence. A biometric success is not a server authorization credential.
2. Vault/session memory → Ktor → HTTPS Spring API. Redirects disabled; release rejects non-HTTPS origins. Cookies restored only for their configured origin. Session validation remains authoritative before remote financial operations.
3. Controller → service → parameterized owner-scoped SQL. UUID supplied by a client never establishes ownership. Owner-only invite/account administration and shared issuance publication use server role checks.
4. Untrusted PDF → bounded native/JS extraction → normalized, reviewed import → backend validation → source document and transaction persistence. Server stores PDF bytes as base64 in Postgres; it does not independently certify document authenticity or run OCR. A PDF envelope check is not malware detection.
5. API → shared market data/push. Fixed market upstreams, separate scheduler secrets, authenticated force refresh. Push permission, device registration and notification data require separate privacy review on real devices.
6. Investor web → proxy → same Spring authorization. Marketing contact → optional approved private HTTPS recipient; Origin/MIME/schema/body/time/response limits apply. No contact delivery to a real recipient was attempted.
7. Git/CI → Gradle/npm build/cache → artifact → cloud runtime. Reviewed pinned actions and least-privilege YAML do not prove effective repository protection or deployment IAM.

## Critical flows

- **Login:** bounded JSON + allowed Origin → persistent hashed-key rate limits → password verification → random opaque token, only its SHA-256 stored server-side. Production cookie is `__Host-bonds_session`, HttpOnly, Secure, SameSite=Strict, Path=/; 12-hour lifetime. Session checks reject expiration, disabled account and changed credential hash. No JWT access/refresh pair exists. APNs provider JWT is unrelated to user authentication.
- **Unlock/resume:** PortfolioModel requests native vault authorization; successful local auth restores the origin-bound session, checks server state and may use saved credentials through the normal login endpoint. Slow/unavailable backend is not converted into authenticated financial data. Lock/logout/account switch clear sensitive in-memory data and vault access; monotonic background timing controls the short resume grace period.
- **Registration/recovery:** owner issues hashed, expiring single-use invite; account management is role-gated. No public email/OTP recovery implementation was found; recovery/account identity procedures remain an operator/product decision, not an invented endpoint.
- **Documents/import:** private account ID comes from authenticated principal. At most 20 chunks, 512 KiB decoded per chunk, 10 MiB complete PDF, eight unfinished uploads per account; stable metadata, owner-scoped digest deduplication, transactional completion, source ownership and retries. Client parsing has page/text/OCR/time limits and rejects active PDF features. Completed PDF storage is not subject to an account byte/lifetime quota.
- **Reconciliation:** source evidence and explicit decisions retained; strong identity matching and manual review for ambiguity; statement rows do not automatically become holdings. Independent bond instruments, account locks and business-identity uniqueness prevent a second transaction when equivalent evidence is imported. General holding edits lack a comprehensive immutable revision trail.
- **Deletion/export:** password-confirmed self-deletion revokes sessions and deletes private server records; mobile clears account-specific import history/target/vault state. Shared public issuance and already-delivered exports have different ownership/retention. Backup erasure and provider retention are unverified.

## Threat/risk register

Likelihood is qualitative given current code, not a penetration-test score. All open items have proposed owners; none is a founder-approved risk acceptance. Review by **14 November 2026 or before beta**, whichever is earlier.

| ID | Threat | Evidence / current controls | Residual likelihood / impact | Owner / next action |
|---|---|---|---|---|
| T01 | Guessed investment/document/source ID accesses another account | Controller principal + SQL owner predicates; synthetic two-account holding/PDF/source/notification tests pass | Low under tested paths / high if regressed; not a proof of every future endpoint | Backend: keep negative authorization tests mandatory |
| T02 | Replayed stolen session | Opaque high-entropy token, hashed at rest, expiry, logout/deletion/credential-change revocation; no refresh-token model | Medium if live token/device compromised / high; no per-session remote UI revocation found | Backend/mobile: protect vault; plan device/session controls if justified |
| T03 | Local Face ID accepted as server authority | Separate local vault and authenticated API session; failure/expired/offline model tests | Low in covered code / high; real biometric/device tests outstanding | Mobile: physical Android/iOS gate |
| T04 | Hostile PDF exhausts/compromises parser | Bounded bytes/pages/OCR/text, timeout, JS isolation, native parser, active-content policy, malformed fixture tests | Medium / high; no full decompression-bomb or native parser exploit campaign | Mobile/backend: OS updates, hostile-fixture testing, quota policy |
| T05 | Logs/screenshots/metadata expose private data | Generic backend errors, timing route labels, no token/body HTTP logger; Android secure flag and iOS cover/redaction code | Medium / medium; native capture behavior, external logs, NSDefaults metadata retention not verified | Mobile/operator: device and provider-log review |
| T06 | Exposed credential or privileged CI | No confirmed secret in limited pattern scan; pinned actions, PR caches read-only, deployment OIDC | Unknown / high; no dedicated secret scanner/full history/binary assurance; effective IAM unknown | Maintainer/operator: secret scanning + IAM review |
| T07 | TLS downgrade/interception | Release HTTPS policy, redirects off, no trust-all implementation found; JDBC docs require verify-full | Low in source / high; live TLS/JDBC settings not checked | Operator: verify deployed origins/certificates and datasource TLS |
| T08 | Duplicate/tampered financial evidence | Server normalization, decimal calculations, source checks, idempotency/account locks, reconciliation decisions | Medium / medium; documents are user-supplied evidence, not institution-signed truth; general edits not fully audited/versioned | Backend: revision/concurrency plan before larger use |
| T09 | Abuse inflates storage/load or enumerates accounts | Bounded requests/market cache, rate-limited login, generic errors | Medium / medium; no completed-document quota; list responses can grow, proxy IP/rate behavior unknown | Backend/operator: measured per-account quotas/pagination/abuse policy |
| T10 | Dependency/cache/build compromise | Fresh scan, lockfiles, non-root container, backend wrapper checksum now pinned | Medium / high; Kotlin cache advisory and transitive build advisories open; container CVEs unscanned | Build owner: compatible patches, trusted caches, exact-image scan |
| T11 | Backup/transfer or lost device exposes metadata | Android explicit cloud + device transfer exclusion added; auth-bound vault, lock cleanup; iOS ThisDeviceOnly credentials | Low/medium / medium; OEM transfer test and iOS metadata backup review pending | Mobile: validate physical transfer and retention |
| T12 | Cloud database/backups misconfigured | Fail-closed cloudrun datasource; optional separate migration credentials now supported | Unknown / high; grants, encryption, backups/restore, RPO/RTO and deletion not proved | Operator: role split, restore rehearsal, incident runbook |
| T13 | Website/contact or rendered article abuse | Nuxt disabled default, bounded Origin/schema transport; reviewed Markdown compiler; Next API forwards to Spring | Low in tested local paths / medium; current Vercel headers/config and contact recipient unverified | Web/operator: keep contact opt-in and verify real deployment before enablement |

## Explicit unknowns

Live public exposure is documented in earlier deployment records, but current production revisions/settings were not independently verified here (read-only Cloud Run metadata attempt failed). The source has release and production deployment paths; treat internet threats as applicable, not as proof of a current live vulnerability. Cloud backups/restore, DB grants/extension privileges, secrets and IAM, WAF/rate limits, egress, provider log retention, privacy registrations/transfer permissions, signing-key custody and store artifacts remain external gates. No broker's or customer's data was inspected to test these hypotheses.
