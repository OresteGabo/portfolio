# Financial document parsing

Updated: 30 October 2026. This documents the existing ingestion path and the targeted hardening of its supported layouts. [CURRENT_STATE.md](CURRENT_STATE.md) owns the wider feature inventory; [TECHNICAL_DEBT.md](TECHNICAL_DEBT.md) owns remaining production gaps.

## Inspected components and reused boundaries

| Layer | Existing implementation / decision |
|---|---|
| Native selection and routing | `InvestmentApp`, `DocumentsHub`, `IncomingPdf`, `PurchasePdfImport`, `PortfolioModel`: Library/picker/shared-file entry points continue into the same import chat. Preserved |
| Android extraction | `LocalInvestmentEngine.android.kt`, bundled `pdf-import.js`, PDF.js and Tesseract. Reconstructs rows from glyph coordinates; local OCR for sparse text, with optional coupon-table second pass. Preserved |
| iPhone extraction | `LocalInvestmentEngine.ios.kt`: PDFKit text first, Vision accurate English recognition for sparse pages, `layoutOcrText` for columns. JavaScriptCore runs the shared financial parsers. Preserved |
| Supported field parsers | `pdf-import-parser.ts` (personal and public BNR results), `equity-pdf-parser.ts` (BK equity purchase), `bk-statement-parser.ts` (BK reconciliation rows). Improved in place, not replaced |
| Parser adapter | Existing `InvestmentDocumentParser` and `EngineDocumentParser` isolate the native engine from the import use case. Adequate for additional adapters; no registry/service/plugin framework added |
| Normalized review | Existing `fields`, `missingFields`, `warnings`, `usedOcr`, `kind`, or statement `rows/fromDate/toDate`. Optional identity metadata added outside financial fields; JSON review compatibility retained |
| Confirmation | `PurchaseReviewState`, `importedPurchaseInput`, `completePendingPurchase`, `importedIssuanceInput`, statement matching and conversation review. Persist only after the existing user confirmation and server validation. Preserved |
| Authoritative API | Companion Spring `PortfolioController`, `DocumentService`, `PurchaseValidation`, `PortfolioService`, `StatementService`, `BondIssuance`; existing `/documents`, `/purchases`, `/equities`, `/statement-items` endpoints. One additive upload-category fix, no new endpoint/schema |
| Duplicates/recovery | SHA-256 exact-byte checks, account-authorized lists/downloads, `ImportDuplicates`, `ImportJournal`, backend owner/hash and purchase identity constraints, chunk resumption and saved-file reuse. Adequate for current duplicate handling; server-wide import idempotency/audit remains separate debt |

Reviewed related Node parser/reader fixtures, KMP adapter/import/review/statement/duplicate/upload/model tests, native PDF/OCR tests, and Spring validation/compatibility/Testcontainers tests. Native OCR test existence is not a claim they ran during this change.

## Supported formats and identity

Every supported result now includes `parser: { institution, documentType, format, version }`. These describe Struct Capital's adapter contract, not an institution's official file version and not verification of document authenticity.

| Institution | Document type | Format / adapter version | Normalized data and limits |
|---|---|---|---|
| BNR | `bond-confirmation` | `investor-results` / 1 | Bond identity/terms, personal allocated principal, dates, explicit coupon schedule; whole-issuance amount never becomes personal principal |
| BNR | `bond-issuance` | `summary-market-results` / 1 | Public auction identity/terms, offered/applied/sold amounts, bids/yield and dates; no personal purchase date/principal |
| BK Capital | `equity-purchase` | `rse-purchase-contract-note` / 1 | RSE/RWF purchase quantity, security, trade/settlement dates, price, itemized fees and payable total. Current ISIN mapping remains BOK/IMR; unknown securities need support rather than guessing |
| BK Capital | `statement` | `rwf-client-statement` / 1 | Bounded period and bond/equity purchase debits for reconciliation. Receipts, bank fees, sales and transfers never become investments; missing terms are not invented |

Existing entry points remain. `parsePurchasePdfText` now owns statement dispatch too, so ESM/browser and portable native bundles make the same classification decision. Each direct parser rejects mixed document families. This removes precedence-dependent detection rather than introducing another ingestion path.

The identity travels with transient review output. It is not yet durable backend parser provenance; server audit/provenance is documented separately (TD-02–TD-04). Financial payload builders continue sending their existing fields.

## Changes and safety rules

- Added shared whole-token number validation: decimal point, conventional comma grouping, nonnegative bounded amounts and safe scaled units. Purchase money accepts at most two fractional digits. It rejects negative signs (including separated signs), scientific notation, malformed grouping, parentheses and excess precision instead of accepting a plausible substring.
- BNR rate tokens retain up to six fractional digits; percent-to-fraction conversion remains unchanged. Auction amounts retain glyph-spacing normalization but cannot consume a suffix of malformed/negative amounts. Invalid totals/yield remain missing rather than becoming a guessed number.
- BK quantity/price/ISIN extraction no longer accepts valid prefixes of malformed values. Quantities still require integral shares and totals must still reconcile with existing financial validation. Long trade identifiers remain strings; optional absent identifiers remain empty.
- BK dates use explicit supported month spellings and anchored calendar validation. BNR dates cannot be borrowed from the next labelled row. Existing PDFKit/PDF.js spacing repairs and narrowly scoped OCR date corrections remain supported; security codes are not OCR-corrected by guessing.
- Conflicting repeated labels and mixed statement/contract/BNR documents are rejected. Multiple contracts and concatenated statements cannot silently select the first document. Identical repeated scalar labels remain compatible.
- BK statements require opening and closing boundaries, one bounded statement, valid period/row dates and complete trailing debit/credit tokens. An unsupported/truncated ledger fails rather than returning a partly fabricated result.
- Spring `DocumentService` now accepts the app's existing `Statement` category through the same private, owner/hash-scoped upload path, using `broker-statement.pdf`. Other categories and wire fields are unchanged. Uploading a statement still does not create a bond/equity transaction; reconciliation can create missing-document reminders only.

Existing review guards reject missing/inconsistent purchase figures. OCR warnings remain visible and confirmation stays explicit. Parsing is not signature verification, confirmation of cash arrival or protection against a convincingly forged document. Existing JSON/Double estimate boundaries and BigDecimal backend validation remain; this is not a full financial decimal-model migration.

## OCR and PDF policy: already adequate boundaries

No OCR provider, model, network service or paid dependency was added. Both native paths already support text and image-based PDFs. They enforce 10 MB / 10 pages, at most five OCR pages, bounded extracted text and bounded rendering; cancellation/timeouts, password/active-content restrictions and passive-signature policy remain in place. Android's OCR assets stay local; iPhone uses Vision. Signed-widget acceptance is not signature authenticity verification.

The sparse-text threshold can miss a document with a useful-looking but inaccurate hidden text layer. Recognition quality also depends on scan quality, platform and table layout. Do not silently merge conflicting OCR/native numbers or auto-correct financial amounts. Collect authorized examples and validate both extraction paths before changing thresholds. Physical-device OCR quality was not re-evaluated here.

## Regression fixtures and verification

[Fixture provenance](../tests/fixtures/README.md) distinguishes synthetic private financial documents from public BNR auction data. No customer PDF or account identity was added.

- `document-parser-regression.test.mjs`: normalized identities and exact equity DTO, optional fields, long IDs, malformed amounts/shares/ISINs/dates, inconsistent totals, repeated labels, mixed families, truncated/concatenated statements and BNR personal/public separation. Runs supported outputs against ESM and the shared portable bundle, including OCR flags.
- Existing bond/issuance/statement/calculation tests remain; PDFKit and PDF.js text fixtures keep their extraction-specific spacing coverage.
- `signed-pdf-import.test.mjs`: shipped PDF.js reader now also opens synthetic BNR personal, BK purchase and BK statement PDFs; passive signature acceptance and active/non-signature rejection remain covered.
- Spring integration regression covers statement upload, original bytes/name/category, same-file reuse, another account's denial, reconciliation without investment creation and unsupported categories. It compiles, but requires an isolated Docker/PostgreSQL runtime to execute.

Executed results and limitations are in [VERIFICATION.md](VERIFICATION.md). The pre-change bundle reproduced silent acceptance of a negative BK fee and a fractional quantity as valid purchases; it also selected a statement from a mixed statement/contract. The new regressions block those cases.

## Adding another broker or format

For future broker-account identity, integration scope, durable provenance and authorization contracts, see [broker adapter onboarding gates](architecture/BROKER_INTEGRATION.md#broker-specific-parser-onboarding). The checklist below remains the canonical parser implementation process; adding a parser alone does not broaden Spring's current BK-specific statement reconciliation or grant institution access to investor data.

1. **Collect samples with permission.** Obtain multiple versions, securities, dates, fee variants and multi-page examples, plus text and scanned copies where applicable. Record the issuing institution and actual format date/version. Keep raw private samples out of Git, logs and diagnostics. Recreate identifying fields with synthetic data and remove hidden PDF metadata/signatures before committing fixtures.
2. **Define recognition.** Require institution plus document-type/layout markers; test near matches, unrelated PDFs, sales, contradictory currencies and concatenated documents. Do not broaden BK recognition to another broker merely because labels look similar. Recognition must be independent of dispatch order.
3. **Map to normalized proposals.** Reuse the existing financial fields and statement rows. Document units, percent/fraction conversion, currency, price basis, settlement/trade dates, gross/net amounts, fees, optional fields and missing-value behaviour. An adapter extracts proposals; domain validators check relationships; Spring owns persistence/authorization. A missing required amount never defaults to zero.
4. **Identify/version the adapter.** Add its institution/documentType/format/version metadata beside the existing identities. Version recognition/mapping changes deliberately; distinguish a new layout from a fix to version 1. Add an explicit branch to the existing dispatcher and ambiguity guard. Only introduce a registry if actual adapter growth makes this dispatch hard to maintain.
5. **Test extraction and arithmetic separately.** Add anonymized extracted text and expected normalized values, malformed/ambiguous examples, invalid/leap/year-boundary dates, grouping/precision/rounding cases, optional-field cases and missing fees. Add genuine PDF reader/native OCR tests for layout-dependent changes, not only text pasted directly into a parser. Require equivalent ESM/portable results and native extraction fixtures on both platforms.
6. **Validate the persistence contract.** Test existing review confirmation, target matching, upload metadata, account isolation, duplicates, response-loss retry and no writes on rejection against disposable PostgreSQL. Do not disguise a new category as a supported category just to bypass validation. Coordinate mobile, Spring and website compatibility.
7. **Generate and release deliberately.** Edit `web-logic/source`, then run `node scripts/bundle-shared-logic.mjs [toolchain-directory]` to regenerate the checked-in common ESM and portable `logic.js`. This script currently resolves TypeScript from the existing sibling website installation by default. Do not run `sync-web-logic.mjs` casually: it copies sibling sources over local parser work. Pin/self-contain that toolchain before depending on regeneration in CI. Re-run Node tests, parser type checks, shared import tests and native extraction tests appropriate to the changed layer.

## Documented future work only

| Future capability | Gate / decision required |
|---|---|
| Direct broker integrations | Broker-supported interface/agreement, customer consent and scoped revocable grants, mapping/reconciliation, protected credentials and audit. Keep adapters separate from financial rules; never reuse a customer's app password/session |
| Paid cloud OCR | Demonstrated local failure/quality need, cost budget, explicit consent, retention/location/privacy policy and deletion guarantees; retain local default and validate output through existing review |
| AI document interpretation | Explicit need and permission, private-data handling, bounded structured proposals, uncertainty/abstention evaluation and deterministic financial validation. No silent auto-save or model-invented amounts |
| Institutional ingestion APIs | Real customer requirements, trusted tenant/resource scope, versioned API/authentication, quotas, retention, idempotency, audit and cross-account isolation tests before exposing ingestion externally |

None of these future services/APIs was implemented. No new parser framework, OCR service, financial product screen or parallel document store was created.

## Follow-up reconciliation

The [existing reconciliation extension](RECONCILIATION.md) retains purchase `rows` and adds separate nonpurchase `movements` (cash receipts, fees, coupon/dividend/other labels). They are evidence only, never inferred holdings or verified income. Backend statement upload and reconciliation integration tests subsequently passed on disposable PostgreSQL; the earlier Docker-only execution limit above describes the parser phase. Native test results and financial changes are recorded in VERIFICATION.md.
