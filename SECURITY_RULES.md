# Struct Capital — Mandatory security rules

Status: reviewed against the implementation on 31 October 2026; operational controls still require verification. Applies to KMP clients, Spring Boot APIs, databases, CI/CD, deployments and related websites.

## Non-negotiable principles
1. **Server is the authorization authority.** UI checks are usability only. Every resource operation must enforce authenticated user/tenant ownership and required role on the backend. Default deny.
2. **Never trust client inputs.** Validate type, size, format, ownership and permitted fields server-side. Use parameterized queries; never construct SQL from untrusted strings.
3. **No secrets in repositories or mobile binaries.** Public mobile apps cannot keep a shared secret confidential. Store server secrets in a managed secret store or environment configuration; rotate any exposed secret using an approved process.
4. **Secure authentication and sessions.** For the current opaque-cookie sessions, enforce secure random issuance, hashed server storage, expiry and revocation. Verify signatures/claims/issuer/audience and refresh replay rules if such token formats are introduced; do not pretend they exist today. No auth bypass in production. Biometric unlock is local user presence, not API authorization.
5. **Least privilege everywhere.** Separate database accounts and environment credentials; restrict service permissions, CI tokens and cloud IAM; never use admin database credentials for routine API requests.
6. **Protect sensitive data.** Minimize collection; encrypt in transit; use platform secure storage for tokens; protect backups; avoid sensitive values in logs, crash reports, analytics, screenshots and notifications. Document retention and deletion.
7. **Fail safely.** Do not silently expose cached data after logout or account switch. Handle offline mode, invalid sessions and backend failures without leaking information. No insecure TLS fallback.
8. **Secure document ingestion.** Treat PDFs and parsed text as hostile. Apply file size/type/resource limits, safe storage, parsing timeouts and dependency patching; avoid path traversal and untrusted file names.
9. **Protect financial integrity.** Enforce ownership, validate amounts/currencies, use appropriate decimal representations, handle duplicate imports and concurrent updates safely; audit sensitive changes without logging confidential content.
10. **Secure-by-default releases.** Disable debug-only endpoints, verbose logging, test credentials, cleartext transport and unnecessary exported components in release builds. Keep signing credentials protected.
11. **Patch with evidence.** Track dependencies and known advisories, maintain reproducible builds and an SBOM where feasible; update vulnerable dependencies with compatibility tests.
12. **Test adversarial cases.** Include negative tests for unauthorized access, malformed inputs, session expiry, duplicate requests and hostile documents; retest after fixes.
13. **No unapproved production actions.** No live penetration tests, destructive migrations, key rotations, firewall changes, bulk data reads or account manipulation without explicit authorization and rollback plans.
14. **Honest assurance.** Distinguish verified, suspected, not tested and not applicable. No claims of zero vulnerabilities or certification without supporting evidence.

## Required engineering workflow
- Every security-sensitive change includes a threat/abuse-case review and a regression test.
- PRs should run unit/integration tests, secret detection, dependency advisory checks and appropriate static analysis.
- Critical/high vulnerabilities require triage before release; documented risk acceptance requires an owner, expiration date and mitigation.
- Security reports must redact tokens, PII, account IDs, document contents and exploit-enabling production details.
- Maintain a vulnerability intake route and incident response contacts without publishing personal addresses by default.

## Rules for AI coding agents
- Read this policy before changing authentication, authorization, crypto, networking, storage, uploads or deployment.
- Verify actual code paths and dependencies before suggesting fixes; do not invent findings or claim unrun tests passed.
- Prefer established platform/framework security primitives over custom cryptography.
- Never weaken security to improve biometric unlock speed or bypass authorization to make the UI load faster.
- Keep changes minimal, explain tradeoffs, and preserve unrelated user work.

## Review cadence
Revisit after major authentication changes, new integrations, public beta, security incidents and at least each major release.

## Architecture-specific rules — 31 October 2026
- Keep private purchase/statement PDFs distinct from intentionally shared owner-published issuance reports; public market history must never aggregate private contract data.
- Preserve Origin + JSON mutation checks alongside the current stateless secure-cookie model; disabling framework CSRF is not permission to remove those checks.
- Keep Android cloud/device-transfer exclusions and iOS device-bound credential protection; validate real backup/transfer behavior before release.
- Build caches are trusted code inputs. Do not promote artifacts or caches from untrusted PR execution into privileged releases; triage the open Kotlin build-cache advisory in DEPENDENCY_ADVISORIES.md.
- Use optional separate migration credentials only with reviewed database roles/grants; configuration support is not proof of least privilege.
