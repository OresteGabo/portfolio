# Small-business security operations

Source controls are documented in SECURITY_AUDIT.md. These operational items are not claimed complete merely because a local test passed. No production backup, account, logs or infrastructure was accessed in this audit.

## Before deploying these fixes

- Deploy the updated Spring backend before/together with app and generated privacy notices. Confirm anonymous market history contains no private contract observations, signed-in accounts see only their own contract-derived prices, and invited users cannot publish shared issuance. Preserve ordinary private imports and access to owner-approved public reports. Anonymous market refresh parameters must follow the normal cache policy; signed-in refresh remains available.
- Inventory existing shared issuance PDFs/terms: older rows may have been created by an invited account, and historical uploader provenance is incomplete. Verify against original public BNR reports and review affected pending purchases before deciding on corrections. Preserve original evidence and use a documented correction process; do not silently rewrite confirmed investments.
- Check actual proxy/cache/no-store behaviour. The privacy fix stops new cross-account query results; it does not recall old downloaded data or establish whether a production incident occurred.
- Record deployed versions, database migration level and changed API permissions. No schema migration is required for this pass. Repeat isolated tests for any actual deployment-specific overrides.

## Backup and recovery verification

1. Record real hosting/database/backup/log locations, subprocessors, access roles, encryption/TLS settings and retention windows. Do not commit credentials or real connection strings. Use separate production and disposable-test identities with least privilege.
2. Choose measurable RPO/RTO and enabled backup/PITR retention appropriate to the current small service. Provider feature availability is not evidence that a backup is enabled or restorable.
3. Restore a backup to an isolated, access-restricted environment without production push/cron/provider credentials. Avoid exposing real financial data to routine developer environments; use sanitized data when possible. Record actual restoration time, schema compatibility, account/document isolation and sample integrity checks without logging customer values.
4. Maintain a minimal deletion/retention process sufficient to prevent restoring deleted accounts/records back into service. Define how authorized erasure requests are reapplied after a restore and how backup expiry works. Keep any deletion marker access-controlled/minimal and have its lawful retention verified; no new registry was implemented in this pass.
5. Document controlled cutover, rollback, emergency contacts and how to disable exports/push/jobs in a restored environment. Never treat an exported portfolio PDF/JSON attachment as an automatic restore backup.

## Retention and logs

- Decide actual active-account, financial evidence, import staging, operational/security log, local metadata and backup retention periods with legal review. The current public notice leaves several periods unresolved; do not invent statutory periods for a tracking app.
- Current unfinished-upload expiry is checked on later uploads for that owner; eight incomplete uploads and ten MiB each bound staging, not lifetime completed storage. Operational scheduled cleanup and account-volume budgets are follow-ups. Avoid claiming timed automatic deletion until it exists and is tested.
- Keep request body/cookie/authorization/SQL parameter logging disabled. Use allowlisted error codes and short-lived opt-in timings. Restrict access to infrastructure logs; do not paste user statements, PDFs, password hashes, push tokens or full database URLs into support reports.
- Treat filenames, account IDs, source digests and event timestamps as potentially personal metadata even when amounts/PDF bytes are excluded. Inventory iOS backup/defaults behaviour and device file-protection classes.
- Explain that recipient copies, downloads, screenshots, password-manager entries and old external caches cannot be recalled by account deletion. Verify active-store deletion and backup expiry independently.

## Incident triage

Restrict access, preserve minimal relevant evidence and assess affected versions/accounts/data/time windows with authorized personnel. Do not label a source finding a confirmed breach without evidence. Consult the Rwanda-specific notification/reporting requirements promptly; do not assume a generic seventy-two-hour-only rule applies. Use the official sources and legal questions in RWANDA_DATA_PROTECTION.md. This audit did not perform incident response against production.

## Later, when justified

Budget for an independent scoped penetration test, stronger build dependency verification, broader recovery drills and institution-specific audit/tenant controls as exposure grows. Certifications and broker regulatory programs are separate decisions, not required infrastructure for fixing today's source vulnerabilities.
