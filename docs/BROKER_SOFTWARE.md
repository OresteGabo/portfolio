# Broker software opportunities and validation gates

Reviewed **30 October 2026** against the existing KMP repository and companion Spring checkout `../gabo-portfolio-api`. This is architectural readiness documentation, not a broker product launch. No partnership, customer, supported broker API, workflow agreement, institutional approval or willingness to pay has been established.

## Evidence and assumptions

**Verified** means inspected source behavior, not deployment certification. **Proposed** means a design or experiment requiring validation. Existing parser support identifies a document layout; it does not prove an institution licensed, endorsed or authenticated that document. Business labels such as the existing `broker` field are not regulatory classifications.

The inspected application is an individual investment tracker: shared Compose/KMP screens, Kotlin financial rules, local native PDF/text/OCR extraction, TypeScript field parsers, Ktor mobile transport, and Spring Security/JDBC/PostgreSQL/Flyway persistence. The Ktor `server` sample is not the production backend. Existing [feature inventory](CURRENT_STATE.md), [parsing](DOCUMENT_PARSING.md), [calculations](FINANCIAL_CALCULATIONS.md), [reconciliation](RECONCILIATION.md) and [security audit](security/SECURITY_AUDIT.md) remain authoritative for their detailed scopes.

## Reusable components, without overstating readiness

| Existing capability and source evidence | Potential reuse | Present limitation |
| --- | --- | --- |
| `InvestmentDocumentParser`, `EngineDocumentParser`, native PDFKit/Vision and PDF.js/Tesseract extraction | Contract/statement interpretation and document-quality review | Runs through the mobile host; not a headless broker service or published parser SDK. OCR/layout limits remain. |
| `web-logic/source/{pdf-import-parser,equity-pdf-parser,bk-statement-parser,document-parser-support}.ts` | Institution-specific extraction into normalized review proposals | Four BNR/BK layouts, version 1; BK purchase security map has two ISINs. Supported documents are not generic institutional coverage. |
| Import chat/review, `ImportDuplicates`, `ImportJournal`, private PDF viewer and original-document links | Investor-assisted document collection and correction | User-oriented workflow; no staff queue, client assignment, approval chain or bulk ingestion. Local journal is not the authoritative server audit. |
| Spring `DocumentService`, account/hash uniqueness, resumable uploads and bounded PDF envelope validation | Original evidence storage/reuse and document retrieval | PDFs are base64 in PostgreSQL; upload and financial save are separate requests. Parser identity is transient, not durable backend provenance. No malware scanner/authenticity verification is implied. |
| `PortfolioService`, `PurchaseValidation`, account locks, BigDecimal validation and duplicate constraints | Validated bond/equity acquisition recording | Primarily individual acquisition records, not a full transaction/cash ledger. No complete disposal/dividend accounting or general immutable mutation history. |
| `StatementService` and Flyway V8 source/decision tables | Evidence matching, duplicate prevention, explanations and reviewed links | Matcher/validation explicitly target BK Capital/RWF. Other movements are evidence-only, not posted cash, received dividends or settlement. Reads retain individual-scale limits. |
| `BondMath`, portfolio valuation/aggregation and `InvestmentInsights` in `core`; Spring coupon schedules/receipts | Coupon outlook, maturity tracking, acquisition-fee analysis and investor summaries | Schedules/automatic markings are not bank-confirmed cash; prices can be missing/stale. Core still includes JSON/Double/transport boundaries. Not an institutional valuation artifact. |
| `PortfolioReport`, `AnnualReport`, `ReportPdf` | Explanatory investor reporting and report-layout reuse | Client-generated reports require complete history and disclose limitations. Not broker-issued statements, official tax certificates or reproducible signed server snapshots. Historical disposals cannot be reconstructed from absent history. |
| Current authenticated APIs, ownership checks, secure mobile vault and isolation tests | Existing validation/persistence boundaries and test patterns | Opaque end-user sessions; no service principals, delegation grants, institution memberships or scoped machine API. `ROLE_OWNER` is a platform-administration role, not a broker role. |

Backend evidence: [controller](../../gabo-portfolio-api/src/main/kotlin/dev/orestegabo/portfolio/PortfolioController.kt), [portfolio service](../../gabo-portfolio-api/src/main/kotlin/dev/orestegabo/portfolio/PortfolioService.kt), [statement matcher/service](../../gabo-portfolio-api/src/main/kotlin/dev/orestegabo/portfolio/StatementService.kt), [document service](../../gabo-portfolio-api/src/main/kotlin/dev/orestegabo/portfolio/DocumentService.kt), [security](../../gabo-portfolio-api/src/main/kotlin/dev/orestegabo/portfolio/SecurityConfig.kt). These links require the companion checkout; no backend source is copied into this repository.

## Opportunities to investigate beside existing broker systems

All rows below are hypotheses. Do not describe the proposed inputs, staff responsibilities or outputs as observed broker practice.

| Opportunity | Small experiment / questions to validate | Boundary to preserve |
| --- | --- | --- |
| Document extraction quality assistant | With authorized synthetic/redacted samples, compare supported-field extraction and correction time against a demonstrated existing task | Output a review proposal; do not issue contracts or write to the broker system. |
| Missing-evidence/reconciliation assistant | Demonstrate a contract and statement referring to the same acquisition; ask who resolves discrepancies and what counts as reliable identity | Reuse matching/evidence rules; no inferred holding per row and no settlement assertion. |
| Investor document organization | Test whether an investor can find the original confirmation and explain a matched result more easily | Investor controls their own collection; a broker logo/referral does not grant staff access. |
| Bond allocation and coupon exception view | Investigate verified allocation sources, reopened issues, payment-date changes and how actual receipt evidence is established | BNR public issuance totals are never a client's allocation; scheduled/marked/verified cash remain different. No coupon disbursement. |
| Report preparation/export assistant | Compare an existing investor report with a broker-approved sample of the information needed for a real task | Supplemental draft/export until completeness, valuation and approval requirements are agreed. Do not relabel it an official client statement. |
| Parser maintenance/support | Test whether reliable support for an agreed document format is useful without a broker API | Institution owns its source documents; Struct does not gain reuse/training rights by maintaining a parser. |
| Broker-sponsored investor access | Investigate referral/support value with ordinary investor accounts and zero staff access by default | No customer directory, silent account linking or mandatory sharing. Sponsorship is not data consent. |

Client statement generation, staff-managed portfolios, white-label portals and institutional APIs are later opportunities. They need demonstrated requirements and controls beyond reskinning existing reports/screens. None requires replacing a broker's system of record as a starting premise.

## Small next foundations that also benefit individuals

These are **recommendations, not implemented changes in this pass**. Existing boundaries are adequate for documentation readiness; no current-user defect required a code change.

1. **Durable import provenance and retry result (highest value):** extend the existing upload/save boundary with account-scoped import identity, original digest/document link, parser identity/version, normalized proposal, reviewed changes and final outcome. Reuse existing source/decision tables where appropriate; do not create another document store or holdings ledger. Acceptance: retry after response loss returns one financial result; corrections remain traceable; old imports without metadata remain valid. Needs an additive API/migration and compatibility tests, not an invented provenance backfill.
2. **Financial edit history and conflict checks:** extend existing services with transactional audit records and expected-version checks for mutations that lack them. Current statement decisions already append history and detect stale decisions; don't duplicate that mechanism. Acceptance: two devices cannot silently overwrite a changed acquisition; original evidence and changed fields remain distinguishable.
3. **Broker-account context where a real case requires it:** current owner boundary is not a verified external securities-account identity. Review multiple-account reference collisions before adding a second institution/account format. Acceptance: same reference in two external accounts cannot collapse into one acquisition; no email/name-based ownership inference. Preserve existing records and current duplicate protections during any migration.
4. **Report evidence/completeness contract:** document/report date, covered period, quote date/source, full-history status and scheduled versus received evidence consistently. Existing annual-report guards already require complete history; preserve them. Acceptance: missing history/prices or disposal evidence is disclosed, not presented as a reconciled institutional balance.

Prioritize the existing [technical debt](TECHNICAL_DEBT.md) and [release/security gates](RELEASE_CHECKLIST.md) before institution-specific functionality. A customer request alone does not justify weakening personal-account isolation or skipping those gates.

## Data ownership and access choices

Software licensing, document authorship, custody of a copy, data-subject rights and processing responsibilities are separate questions. A firm buying Struct software gains no automatic right to existing investor accounts, originals, other brokers' transactions or aggregate private-derived prices. A user's upload proves neither broker endorsement nor authority to share other clients' data. Receiving a user-authorized copy does not make a broker the owner of the user's complete personal workspace.

| Possible arrangement — not new deployed modes | Data access principle / validation needed |
| --- | --- |
| Broker-sponsored ordinary investor app | Accounts remain personal. No broker directory, dashboard or access grant is created merely by sponsorship or an invitation. |
| Local parser evaluation with synthetic data | Existing parsing algorithms are local, but the current full app persists through Spring. A standalone local SDK/tool is not implemented; do not promise an offline institutional installation. A sample demonstration need not include real clients or server persistence. |
| Future broker-controlled installation | A separate deployment/packaged tool could keep customer records under broker-controlled infrastructure. Software support/license need not include remote DB access. It still needs validated packaging, updates, authentication, backups and a contractual support model; not available today. |
| Future managed processing | Explicitly authorize purpose, input subset, retention, subprocessors and operator access. Tenant isolation separates customers but does not make the host unable to read plaintext. No zero-knowledge/end-to-end encryption claim is supported. |

Current operator privileges must be disclosed honestly: `AuthController.accounts` uses `currentOwner()` and `PortfolioService.accounts` exposes cross-account administrative portfolio summaries. Database/service operators also have operational access. Ordinary users do not thereby gain cross-account APIs. Never give a broker the current platform owner role or reuse a personal `owner_id` as an institution scope. Operational administration must be reviewed/minimized before hosting institutional records; this pass does not silently remove working administration.

Future delegations must be separate from product purchase and default to none. An investor may choose to share a specific report without authorizing continuous synchronization or access to all originals. Broker-provided records and investor annotations should retain distinct provenance; resolving a mismatch cannot silently overwrite either source. Revocation stops future retrieval and broker access through Struct; previously exported copies and legally retained records require separate, disclosed handling.

## Security and Rwanda regulatory questions

Reuse the current [security findings](security/SECURITY_AUDIT.md), [operations](security/OPERATIONS.md) and [Rwanda legal questions](security/RWANDA_DATA_PROTECTION.md); no compliance certification is made. Before real client data, identify controller/processor roles per workflow, legal basis/authority, confidentiality, support access, retention, rights handling, subprocessors, incident responsibilities and backup restoration/deletion. A future product grant is a technical permission, not by itself a legal basis for every processing activity.

Rwanda's official law describes processing principles, security/processing records and circumstances requiring impact assessments. Map the actual proposed activity with qualified counsel rather than declaring every software workflow either exempt or automatically compliant. [Official controller/processor obligations](https://dpo.gov.rw/dpp-law/obligations-of-the-data-controller-and-the-data-processor).

Overseas access/storage requires specific analysis of the transfer, contract and storage provisions, including the certificate authorizing storage abroad; a consent checkbox alone must not be treated as resolving every obligation. Verify actual provider/log/backup locations and agreements. [Official transfer/storage provisions](https://dpo.gov.rw/dpp-law/sharing-transferstorage-and-retention-of-personal-data).

Verify a prospective institution's current status and the scope of any proposed activity with the appropriate authority; a directory listing is not a partnership or authorization for Struct. Software that helps licensed firms does not confer their licenses on its supplier. Do not assert a licensing exemption for a yet-undefined service. [CMA licensee directory](https://www.cma.rw/licensing/licensees).

## B2B pricing hypotheses — no validated prices

| Hypothesis | What to test before quoting |
| --- | --- |
| Fixed-price, bounded evaluation/pilot | Agreed layouts/sample count, deliverables, acceptance tests, correction/support effort and end date. Discovery may show no paid opportunity. |
| Monthly software/support fee with included document allowance | Actual useful recurring task, active usage, support response expectations, storage/retention cost and willingness to renew. |
| Transparent usage tier above allowance | Measure pages, retries/OCR and successful reviewed documents; define failed/duplicate handling so billing does not reward errors or repeated uploads. No metering/billing implemented. |
| Annual broker-controlled deployment/support license | Only after demand for that deployment model and security/update responsibilities are demonstrated; maintenance can outweigh hosting savings. |
| Later branded portal/API subscription | Requires a validated customer and identity/delegation/operating model first. Logo changes alone are not a B2B value proposition. |

No market benchmark, revenue forecast or broker price has been established. The older club arithmetic in [BUSINESS_IDEAS.md](BUSINESS_IDEAS.md) is an illustrative club scenario, not broker willingness-to-pay evidence. Estimate engineering, onboarding, legal/security, updates, support, storage/OCR and export/offboarding costs before testing an offer. Avoid commissions tied to securities execution or assets managed without separate commercial/regulatory assessment.

## Pilot validation before building or accessing customer records

1. **Discovery:** ask a willing institution to demonstrate one existing task with synthetic/redacted material. Record input/output, roles, source of truth, frequency, pain, exception handling and costs as observations; label all unverified assumptions. Do not contact anyone or obtain customer data automatically.
2. **Choose one bounded non-executing task:** prefer extraction-quality or reconciliation-evidence evaluation. Define what remains in the institution's current system and who verifies output. No CRM, customer-master import, order execution, custody, settlement or full tenancy.
3. **Agree permissions/data terms first:** authorized signatories, sample-use rights, parties' roles, locations, staff/support access, retention/return/deletion and incident contact. Start synthetic; obtain specific authorization and safeguards before any real documents. Broker approval alone must not be assumed sufficient for every customer's data.
4. **Create a held-out test set:** multiple authorized layouts/dates/scans, fees, repeated references, multiple accounts, malformed/mixed documents and corrections. Keep development and acceptance samples separate. Record ground truth approved by a named responsible reviewer; never commit real client samples.
5. **Agree numerical go/no-go targets before testing:** financial-field exactness, false automatic matches, ambiguity rejection, review/correction time, no duplicate financial writes, access denial, retry recovery, operating cost and deletion/export success. No mandatory safety failures: an unauthorized read, silent wrong financial write or wrong-client match blocks progression regardless of average accuracy. Record corpus size/coverage; a small sample is not a production guarantee.
6. **Evaluate with read-only proposals:** compare to the demonstrated baseline, obtain reviewer decisions, and document limitations. No automatic writes back into broker systems. Recheck public-API/source-data rights and financial assumptions.
7. **Commercial/operational decision:** identify budget owner, willingness to pay, support burden and repeat usage. If useful, propose a scoped paid pilot with a stop date and acceptance criteria. If not, stop; no integration is justified by speculative interest.
8. **Exit rehearsal:** demonstrate export, revoke any access, delete/return samples as agreed, document residual backups/legal retention and remove test credentials. Only a separately authorized next phase can add real synchronization or institutional users.

Capture findings in a pilot record: institution/authorized contact (private), problem observed, evidence/sample permission, hypotheses, scope/exclusions, data roles/access, baseline, agreed targets, results/errors, cost, willingness to pay, risks and go/no-go decision. No fictional pilot results are included.

## Deliverable boundary

This pass changes documentation only. [Conceptual integration contracts](architecture/BROKER_INTEGRATION.md) define how to extend the existing boundaries when evidence justifies it. No schema, parser behavior, UI, endpoint, credential, customer account, broker integration or infrastructure was added. Existing release/security changes in the working tree remain intact and are not represented as deployed institutional capability.
