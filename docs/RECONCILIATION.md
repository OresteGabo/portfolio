# Statement reconciliation and source evidence

Updated 31 October 2026. Extends the existing BK statement parser, `StatementService`, statement-items table, import chat and missing-document screens. No second transaction ledger or reconciliation engine was created. Financial calculations are covered separately in [FINANCIAL_CALCULATIONS.md](FINANCIAL_CALCULATIONS.md).

## Existing components retained

- BK statement purchase-debit extraction, personal BNR/equity contract imports and reviewed financial saves.
- Existing statement reconciliation/reminder service and `/api/bonds/statement-items` operations.
- Owner-scoped queries, account locks, transactions and purchase/contract uniqueness constraints.
- Exact-PDF SHA-256 detection, private original uploads, resumable chunks and saved-source retry.
- Existing document library/PDF viewer, chat results and missing-document entry points.

The important prior gap: a bond debit could be marked recorded solely from settlement proximity and execution amount. That is now a candidate requiring review when the statement lacks instrument/reference identity. Matching a statement never executes `saveBond` or `saveShare`.

## Matching policy

| Evidence | Automatic outcome |
|---|---|
| Equity contract reference + ticker + quantity + price + payable + trade date + BK broker context, uniquely matching in the authenticated account | Matched |
| Bond instrument identifier or explicit transaction reference + execution amount + settlement/posting window + BK broker context, uniquely matching in that account | Matched |
| Bond with similar amount/date but no reliable instrument/reference identity | Review required if a candidate exists; never automatic |
| Multiple compatible records or contradictory figures for an identified event | Review required; no financial mutation |
| No compatible record | Unmatched; retained for confirmation. A sourced bond entry with compatible saved terms can be explicitly reviewed and added as described below |
| Cash receipt, separate fee, coupon, dividend or other statement movement | Evidence only; never a holding, cash balance or verified income receipt |

Bond cost rows are compared with face value × executed price / 100 + accrued interest paid. Separate fee rows are not added to that debit or charged twice. Equity payable already includes its itemized buying fees. The existing two-cent tolerance is retained for comparison; amounts use BigDecimal and numeric scale differences do not change identity.

Broker-account identifiers are not transaction references. The current authoritative account boundary is the authenticated owner; the caller cannot choose another owner. Different broker/currency/status/instrument contexts are excluded. Bond codes with the existing `/MN` suffix normalization are compatible. Existing supported BK bond-cost rows usually lack ISIN/reference details, so investor review is expected. No unseen broker layout or reference field was guessed into the parser.

## Statuses and review interactions

`reconciliationStatus` is `matched`, `unmatched`, `review-required` or `evidence-only`. Compatibility `state` remains `recorded` for matched and `missing` for unmatched, with additive `review-required`/`evidence` states. Review-required purchase rows remain in the existing reminder list.

After a statement import, **Review statement records** opens its results in the existing document flow. The same action is available while viewing a statement PDF from Library, including on another device. Users can inspect the original statement, purchase proof where linked, candidate details and previous decisions.

A manual link requires selecting a compatible existing purchase, explicitly confirming it and entering a reason. Conflicting quantities/prices/amounts cannot be overridden by a link: correct the financial data/evidence first. Linking associates evidence; it does not add a holding or alter amounts. Clearing/replacing a link appends another decision. Concurrent stale decisions receive 409 and require refresh; identical retries return the existing result.

The selected financial record is fingerprinted. A material later change or deletion reopens review instead of silently carrying forward an old confirmation. A restored equivalent record may again agree with its recorded fingerprint; this is not complete financial mutation history. General append-only investment-edit auditing remains separate debt.

## Persistence and APIs

Flyway [V8](../../gabo-portfolio-api/src/main/resources/db/migration/V8__statement_evidence_and_decisions.sql) adds two small tables around the existing `bond_statement_items`:

- `bond_statement_sources`: account-owned original statement PDF plus normalized source occurrence. Original PDFs and source proposals are not overwritten when decisions change.
- `bond_statement_decisions`: ordered append-only link/clear decisions, authenticated actor, time, reason, purchase fingerprint and optional purchase-proof PDF.

These tables represent evidence and reconciliation decisions, not a cash ledger. Account erasure removes the account's evidence/decisions consistently with existing deletion semantics. No immutable-retention promise beyond the account's lifetime is made.

Existing POST `/statement-items` accepts the unchanged purchase `items`, plus optional evidence-only `movements` and an owned statement `documentId`. The new app sends the document ID after saving the original. Legacy calls without a document remain accepted but cannot claim PDF traceability; the UI explicitly identifies unlinked sources. Invalid movement arrays/source identifiers are rejected, not silently dropped.

GET `/statement-items` retains unresolved purchase reminders. Optional `documentId` filters the full source-linked results; `all=true` provides the account's available records. POST `/statement-items/{id}/resolve` adds explicit link/clear decisions. The server validates item, purchase and proof ownership and checks optional `expectedDecisionAt` before a different correction.

New equity imports attach their verified private `contractDocumentId` in the existing holding JSONB. Existing bond confirmation links remain unchanged. A matching decision can cite those original purchase proofs. No new endpoint or schema is needed to create an equity purchase.

Source/decision reads are batched for the returned items, not a separate query per reminder during refresh. The existing 500-item reminder bound remains; complete institutional pagination and back-office queues are deferred.

## Idempotence and preservation

- The same original PDF is stored once per account hash; public issuance identity remains separate from private allocation evidence.
- Existing event duplicate keys still protect bond/equity creates. A statement entry never calls a purchase create operation.
- Statement amounts/quantities use canonical decimal strings for identity: `1000`, `1000.0` and `1E3` do not create distinct purchase-reminder identities merely because of JSON representation.
- Source links are unique per item/document. Repeating a statement import returns the same records and links. Unambiguously identical legacy reminders can gain their missing source without creating another reminder; conflicting legacy candidates are not guessed together.
- Unidentified bond/other occurrences retain source-document/row context. Two genuinely indistinguishable statement lines are not silently collapsed into a confirmed investment.
- On another device, an existing statement PDF counts as completed only when source-linked reconciliation records exist (or completion was already known locally). An uploaded-but-unprocessed file is reused for checking; it is not uploaded again or incorrectly declared fully reconciled.
- Corrections append decisions; originals and normalized source occurrences remain accessible. Reimporting different figures for the same contract raises review rather than overwriting the holding.

The upload and later financial/reconciliation request are still separate HTTP operations. Existing retry/source reuse handles that boundary; a general durable server import command and financial-edit history remain future foundations, not claims made by this change.

## Examples

1. **BNR allocation + BK statement:** allocation creates one confirmed bond. A later unidentified Bond Cost debit proposes that bond for review. The investor checks the original allocation and links it. Portfolio count and principal remain unchanged; the statement and decision are traceable.
2. **BK contract + statement:** 400 IMR shares, RWF 36,615.60 payable, same contract/quantity/date/price/broker automatically match the existing lot. Reimporting the statement does not create another 400 shares or add RWF 615.60 in fees again.
3. **Conflicting contract:** same reference but 401 shares or a different payable is review-required. It cannot be manually labelled matched while the figures disagree, and it cannot overwrite the original purchase.
4. **Deposit and custody/broker fees:** retained separately as credit/debit evidence. They neither increase holdings nor create purchase principal. Coupon/dividend movement labels likewise do not mark bank-verified income automatically.
5. **Same file after interruption:** saved PDF bytes are reused. Missing reconciliation work can continue; completed results are returned without another document or transaction. A repeated manual decision appends no duplicate audit event.

## Verification and rollout

Pure matcher regressions cover identity, broker/currency context, scale-independent decimals, ambiguous candidates, inconsistent quantities/amounts and fingerprint changes. KMP tests cover explicit reasons/revisions, no purchase writes, status compatibility, logout cleanup and saved-source reuse. Parser tests separate purchase rows from other movements.

The full Spring suite ran against a fresh disposable PostgreSQL 18 cluster, including Flyway V1–V8, real HTTP/security, owner isolation, source bytes, retries, manual link/clear history, stale decisions, changed financial records and legacy reminder reuse. No production database was used. Android/iPhone tests and build results are in [VERIFICATION.md](VERIFICATION.md).

Coordinate the Spring migration/API deployment with the new mobile interactions. Source changes/tests are not a deployment; older backend versions cannot supply the new decisions/evidence results. New metadata and operations are additive; original financial records and IDs remain intact.

## Reviewed missing bond purchases and independent terms (31 October)

Flyway V9 adds `bond_instruments`: a public definition keyed by ISIN plus settlement date (reopened auctions stay distinct), with no investor quantities or identities. It seeds the two existing public catalog entries and copies existing shared issuance terms. Future owner-approved issuance uploads register their public terms through `BondInstrumentService`; conflicting coupon/price/date schedules cannot silently replace the definition. `bond_purchases.instrument_id` references this definition. Existing purchase columns remain as compatibility/historical snapshots; the change does not delete them or recalculate existing amounts. V9 backfills only exact security/settlement references. This is an incremental relationship, not a complete replacement of the existing purchase schema.

`bondOptions` is additive metadata on an unmatched sourced bond entry. Candidate terms must fit its existing 0–7 day settlement/posting window and any supplied security identifier. “July Bond Cost” alone never selects a security, invents principal or posts a holding. **Add bond from saved terms** requires an explicit selection and confirmation of principal, actual price, accrued interest, separately charged fees and tax. The primary acquisition is recorded at the selected settlement date (disclosed in review); the original statement posting date remains evidence. Public terms supply coupon, official dates, maturity and schedule, independently of personal amounts. The editable 5% tax field follows the existing app default, not a new legal/tax determination.

Authenticated POST `/statement-items/{id}/bond` accepts `instrumentId`, those five numeric figures, `confirmed`, `reason` and `expectedDecisionAt`. The backend owns the terms and validates the statement source, dates, decimals and debit = principal × executed price / 100 + accrued interest. Fees remain additional and are not posted twice from evidence rows. It refuses creation if an existing/pending purchase may represent the event. In that case use the existing link/confirmation flow. Unknown bonds or uncertain identity/figures still require original confirmation; no invented schedule, coupon or price.

Creation, source link and append-only reconciliation decision share the existing account lock/database transaction. A retry returns the same equivalent creation; changed figures or stale decisions conflict. Overlapping statements may retain separate evidence occurrences but cannot create another matching holding. The new `statementDocumentId` opens the private original from the existing investment evidence actions; it is labelled **View source statement**, never presented as a BNR allocation certificate. No new ledger, cloud parser or trading integration.

The import wrapper also routes a statement selected from a purchase-specific picker to statement review, clearing that purchase target. This fixes a demonstrated context rejection. Separately, the exact native `not_bond_result` message was reproduced on iOS 18.6 after the original passed on iOS 26.2. The connected phone reports iOS 18.7.1, and the Android Studio iPhone product contained the current JS parser. Older PDFKit returned statement columns before its opening-balance boundary. The native reader now reconstructs recognized BK statements from positioned word selections using the existing `layoutOcrText` row helper; numerical tokens are not corrected or guessed, and no OCR/network service is added. Updating Spring cannot replace this client extraction fix; run a rebuilt iPhone app.

Rollout: deploy Spring with V9 before using the new creation action, then install the new mobile build. Older clients and APIs remain compatible; older servers simply omit `bondOptions`, so the mobile app retains upload-confirmation actions. No production migration/deployment was performed in this change. V9 is additive; application rollback can leave its tables/columns intact. Take the usual database backup and verify migrations before production rollout. Verification details are in [VERIFICATION.md](VERIFICATION.md).

## Deferred institutional and broker workflows

Shared institutional workspaces, approval chains, reconciliation assignment/SLA queues, broker cash/settlement/custody reconciliation, regulatory retention, bulk ingestion and back-office exports require a validated customer workflow and tenant/permission design. They are not implemented here. Direct broker integrations need supported interfaces and scoped customer consent; app passwords/sessions must not be reused. No brokerage execution, settlement or custodial integration was added.

Dividend/cash accounting, realized gains, partial disposals and statement-verified coupon receipt posting remain separate product/domain work. Recognizing those movement labels as evidence is not an implementation of those accounting systems.
