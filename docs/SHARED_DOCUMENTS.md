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

## 1 November 2026 — Public security facts extracted from a private confirmation

Private file visibility and public fact reuse are separate. Saving an owned personal bond confirmation can contribute an explicit projection of security facts: normalized ISIN, government issuer/RWF currency, maturity, coupon rate/frequency, tenor and explicitly documented coupon dates. The projection excludes the source PDF ID, investor/account identity, principal, fees, purchase/settlement dates, executed price and withholding-tax choice. No artificial public PDF is created. Another account still receives 404 for the original private PDF and cannot list it.

Flyway **V10** adds `bond_security_coupon_terms`, keyed by security/maturity. This is separate from the existing auction-specific `bond_instruments`: using a private purchase's settlement as a shared auction date would disclose acquisition context and fabricate auction identity. The authenticated `GET /api/bonds/coupon-terms` returns the allowlisted public projection only. Publicly published instrument terms take precedence. Estimated/generated dates are not contributed as documented dates; unknown zero coupons are omitted. Compatible partial schedules can extend an existing schedule only when one is a subset of the other. Conflicting rates/frequencies or shifted date variants suppress the contributed entry rather than silently choosing or creating extra coupons. Conflicts do not reject or rewrite the investor's legitimate private purchase.

Only saved document-backed holdings contribute, after existing owned-confirmation validation; a manual purchase or staged upload alone does not publish these facts. Existing private history is not scanned/backfilled automatically. Newly saved/re-attached private confirmations contribute; the public auction catalog is already available through the existing instrument registry. The extracted source is identified as `document-extracted`, not independently authenticated by BNR.

The app fetches these public facts in background, uses unambiguous identity hints for calendar coupons/reopenings, and can fill missing rates/tenor/documented future dates in import review. Exact security/maturity is required for review enrichment. Known read rates/first-coupon information and private costs/allocations are preserved; inconsistent sources are not combined. Review identifies the shared source without revealing or linking the contributor's private document. Existing holdings' cash flows are not silently rewritten, and this does not confirm another investor's allocation.

The import review and bilingual privacy notice explain the projection. Public security facts can remain independently of a removed holding or account because no private ownership/source reference is retained in this registry. This does not change private PDF/account-deletion behavior. Independent authenticity/source review remains a beta limitation.

## Deleting and restoring a purchase

Purchase deletion and PDF retention are separate. A saved original remains in Library as evidence. Re-importing a personal confirmation queries current server purchases; an old local import receipt or an existing PDF does not prove that its purchase still exists. If the purchase was deleted, the app reopens review, reuses the owned saved PDF and saves a new purchase. A duplicate found during the final save check follows this same rule rather than ending at “already in Library.” If the purchase still exists, the import remains idempotent.

The retained-file race was reproduced with a failing-before regression. Tests also cover normal delete/reimport and a real backend DELETE followed by recreating a purchase from the same original PDF: new purchase ID, one holding, one file, idempotent repeat. The user confirmed that their reported PDF is a personal confirmation containing the amount. A public issuance report alone still cannot reconstruct a deleted personal allocation.

Deploy V10 and the new backend before distributing the corresponding app. The migration is additive and preserves existing records. Application rollback may leave the new table in place; do not drop live terms/purchases to roll back application code. No production migration or deployment was performed.

## Legacy saved-PDF confirmation compatibility — 1 November 2026

A follow-up reproduced a separate failure after the retained-file restoration fix. Migrated private files are listed/downloaded under legacy text IDs and retain categories such as `Bond result`. The purchase endpoint previously required a UUID reference and the exact modern category `Bond confirmation`; attaching a perfectly retained legacy file returned `400 Confirmation PDF not found`, which the app reduced to “Check the details and try again.”

The server now resolves a submitted bond-evidence reference within the authenticated account, accepts the known old category only on a migrated legacy row, and stores its internal UUID in the existing foreign key. Purchase responses retain the file's stable public alias so library matching/deduplication remains consistent. Shared issuance files, another user's files, a statement category or a modern row merely labelled with the old category are not accepted as private allocation proof. No file content/category/financial figures are rewritten and no migration is required for this compatibility fix.

A safe exact-message mapping makes missing/unlinkable saved confirmations actionable without displaying arbitrary backend diagnostics. The 7-year public catalog rate/reopening mapping also has a direct regression independent of personal holdings. Deleting a purchase removes its personal payment entries; it does not prove a retained PDF is unreadable or erase public security facts.

Read-only deployment inspection during this investigation identified the live backend as `f51a7ec`; that predates this compatibility patch. Two recent coupon-terms requests returned HTTP 200, which establishes endpoint reachability, not the contents of a particular user's response or their installed app version. The new backend patch still requires deployment. No production user records, PDF contents or credentials were read or modified; local legacy metadata inspection was limited to category/identifier-format counts.
