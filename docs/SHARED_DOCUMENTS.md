# Private documents and shared issuance results

Policy selected by the founder on 1 November 2026: during the small beta, any authenticated user may explicitly contribute a public BNR issuance report. A staff-review gate/official-source verification is deferred, not claimed to exist.

## Visibility

| Evidence | Scope | Duplicate identity |
|---|---|---|
| Investor-specific BNR allocation/confirmation | Private to the account | Account + PDF SHA-256 |
| Equity purchase contract | Private to the account | Account + PDF SHA-256 |
| Broker/client statement | Private to the account | Account + PDF SHA-256 |
| Generated personal annual report | Generated privately from that account's records | Existing generated-report identity |
| Public BNR issuance/auction market results | Shared with authenticated users | Canonical security code + auction settlement date |

Private PDF existence is never checked against another account. Identical private bytes in two accounts remain separate owned records; one account cannot list/download the other copy. The server chooses storage scope from its supported document category and validates the upload; a client visibility flag cannot turn a private category into shared storage. The library labels server-reported scope, and generated reports are labelled private.

New shared uploads require an explicit public-report declaration in the review. The app distinguishes the public issuance parser from personal confirmation/contract/statement parsers. The server validates issuance identities, numeric terms, dates/schedules and upload policy; obvious personal investor fields are rejected in public issuance data. The first contributing account ID is recorded in server-owned `_publishedBy` metadata for later source review and is removed from shared document responses. Duplicate contributions never replace that attribution or the saved PDF/terms. Older reports may lack attribution.

These checks are **not** independent PDF-content extraction, BNR origin verification, signature verification or malware scanning. A malicious/mistaken contributor could still misclassify a PDF or submit plausible false terms. This is a deliberate beta trust tradeoff, superseding the earlier owner-only publication restriction (SEC-02). It must be revisited before wider untrusted registration. The sharing checkbox is a declaration, not an authenticity certificate. No new legal-compliance claim is made.

## Reuse before saving

The app compares a parsed public report with already-published metadata by security and auction identity, plus all material terms and coupon dates. A match immediately produces the existing-file receipt without downloading/reuploading another saved PDF or requiring another save. This works across accounts and PDF encodings. Conflicting figures require review.

The backend independently repeats the identity/terms check. If another user published the report concurrently, it returns the existing document ID and `duplicate=true` from the first received chunk. The app stops further upload chunks and shows the already-existing completion. A new complete report retains the existing transactional/global-uniqueness boundary; incomplete/invalid multipart uploads never publish terms. Different settlement dates represent separate reopened auctions, even with the same ISIN.

## Publication and linkage

Within the existing upload transaction:

1. Save one public report and canonical instrument terms, preserving conflicting-source checks.
2. Link compatible confirmed historical holdings by security, settlement and maturity. Add only public source/instrument references; do not rewrite their saved financial figures.
3. Enrich matching pending applications across accounts with public coupon terms, dates, maturity and the shared source link. Leave principal, investor fees/cash and submitted status unchanged.
4. Create deduplicated per-account notifications and queue the existing push delivery mechanism. Pending users open their exact purchase; confirmed-holding users open the exact shared PDF. No uploader identity or another investor's amounts enter the notification.

Matching pending records created after publication also receive the existing report through the established refresh path. Uncertain or contradictory identities remain unmatched. A public report does not prove any investor's allocation, bank receipt or principal redemption. Personal confirmation still uses the existing personal-PDF or explicit user-attestation workflow.

Notifications respect the existing category preferences: pending-application updates use `pending`; linked historical evidence uses `documents`. One event key per purchase/report prevents repeat uploads or reads from generating duplicate alerts. In-app alerts persist server-side. Push uses the existing device/provider/worker setup and is not claimed delivered instantly. Other devices see persisted updates on their next normal refresh/resume/push-triggered sync; no realtime subscription service was introduced.

## Rollout, verification and future gate

Deploy the updated Spring backend before distributing the rebuilt mobile app. There is no new schema migration or separate ingestion system. Old backend versions still reject ordinary shared publication. Old apps lack the explicit new sharing flow/notification text.

Regression coverage includes cross-account public reuse, same-byte private isolation, ordinary-user multipart publication, all-account pending enrichment, owned notification destinations, active-holding source linkage without financial rewriting, duplicate alert suppression, explicit consent, conflicting/public-personal fields and semantic public-report reuse. Updated bilingual privacy copy describes the beta policy and source-review attribution. See [verification log](VERIFICATION.md).

Before broader adoption: restrict publication to approved staff or implement a reviewed/official-source-verified contribution workflow; review old shared sources, correction/versioning procedures and metadata retention. Do not treat the current first-valid-submission rule as an institutional trust model. No moderation queue or automatic BNR authenticity verifier was implemented.
