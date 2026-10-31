# Struct Capital: current state

Reviewed: 30 October 2026. This is the canonical feature inventory. Read [TECHNICAL_DEBT.md](TECHNICAL_DEBT.md) for prioritized gaps and [PRODUCT_ROADMAP.md](PRODUCT_ROADMAP.md) for sequencing. [Architecture assessment](architecture/ASSESSMENT.md) owns the stack/API description; [source inventory](architecture/SOURCE_INVENTORY.md) records the repository-wide inspection.

The roadmap now covers five flexible years with proposed validation milestones, business economics and stop criteria. Current usage, retention, import accuracy in the field, paying customers and financial results are not measured by this source inventory. Its historical test counts are technical verification records, not business metrics; consult later entries in VERIFICATION.md for subsequent engineering work.

## Scope and status meanings

The KMP checkout is `portfolio` (HEAD `5952c80`); the actual Spring backend is the sibling `gabo-portfolio-api` (HEAD `348ed14`). This inventory includes the current working tree, including the prior audit's **uncommitted** core/parser separation and Android fixes. It does not mean these changes are committed, deployed or installed on a phone. The Ktor `:server` here is a sample, not the financial backend.

Source, database migrations V1–V7, services/JDBC persistence, models, native adapters, navigation, tests, documentation and recent Git history were inspected across both repositories. No production database, credentials, real user PDFs or live deployment was accessed in this phase. The sibling website is represented by copied TypeScript and proxy documentation, not a complete website audit.

| Classification | Meaning |
|---|---|
| **Fully implemented** | Complete for the explicitly stated scope, with reachable flow/service and supporting tests. Does not claim universal formats, production deployment or store approval |
| **Partially implemented** | Working building blocks exist, but a known coverage, contract or workflow gap prevents the broader capability being complete |
| **Implemented but needing validation** | Code exists; a material integration, runtime, policy or production dependency remains unverified |
| **Missing** | No functional end-to-end implementation found for the named capability. Educational text, a status flag or a similarly named form is insufficient evidence |
| **Deferred for the future** | Deliberately outside the present free individual-investor product; not a feature to implement in this audit |

Compound product areas are split into rows so, for example, implemented unrealized returns are not confused with a complete performance system.

## Imports and documents

| Capability | Classification | Actual coverage, evidence and limit |
|---|---|---|
| Supported BNR personal allocation/confirmation PDFs | Fully implemented | `pdf-import-parser.ts`, `PendingPurchases.importedPurchaseInput`, `readPurchasePdf`, `ConversationReview`; numeric/date checks, review, original upload and purchase confirmation. Parser/model/issuance tests cover supported fixtures; not every BNR PDF layout |
| Public BNR issuance results | Fully implemented | `parseBondIssuancePdfText`, app `BondIssuance.kt`, Spring `BondIssuance`/`DocumentService`/`PendingIssuanceService`, migrations V3/V4/V7. Shared public terms enrich pending applications; personal allocation remains separate. Multipart and conflicting-term tests exist |
| Supported broker equity purchase notes | Fully implemented | `equity-pdf-parser.ts`, `equity-validation.ts`, `PurchaseValidation.equity`, `saveImportedPurchase`; extracts quantities, dates, unit price and itemized fees, checks totals and rejects sale notes |
| General broker/security coverage | Partially implemented | Equity parser recognizes one purchase-note layout, recognizes BK Capital, and auto-maps only two ISINs (BOK/IMR). Other layouts/securities may produce missing fields or fail. An interchangeable parser port exists; that does not mean all brokers are supported |
| BK Capital statement parsing | Fully implemented | `bk-statement-parser.ts` and statement fixtures extract supported investment debits and dates. This is not a general bank-statement/cash-ledger parser |
| Statement-to-investment reconciliation | Fully implemented | `StatementScreens.statementConfirmationMatches`, Spring `StatementService.matches/reconcile/missing`, `/statement-items`. Unmatched/ambiguous entries become missing-document reminders, not invented principal |
| Original statement PDF save plus reconciliation | Fully implemented | Existing private upload category aligned; source-linked reconciliation, duplicate/retry and ownership regressions pass against disposable PostgreSQL. Production deployment remains unverified. See TD-01 and RECONCILIATION.md |
| OCR and document safety | Implemented but needing validation | Android PDF.js/Tesseract, iOS PDFKit/Vision; size/page/text/scan limits, cancellation and active-content policy exist. Native/signed-widget fixtures exist, but broad real-format/device validation remains. Signature-widget acceptance is not cryptographic signature verification |
| Library, search/filter, metadata and PDF reading | Fully implemented | `DocumentsScreen`, `DocumentsHub`, `PdfViewerScreen`, native PDF renderers; reachable from Documents, with zoom, scrolling and original-byte sharing. Generated annual cards do not require a stored document row |
| Direct upload into chat | Fully implemented | `InvestmentApp` opens Library by default; floating Add document launches picker, selection routes into chat, cancel preserves the source screen. Pending/missing/shared-file entry points use the same import workflow |
| Import progress, retry, duplicate completion | Fully implemented | `ImportJournal`, `ImportDuplicates`, model save/retry paths and UI journey. Exact-byte checks and backend hash constraints prevent normal repeated file saves; saved details and existing record links are shown. Truly pending confirmation reuses the file |
| Original upload timestamps across devices | Partially implemented | UI can use server `createdAt`/`uploadedAt` or the earliest successful local receipt. Current document list omits those server fields; old cloud files without local history cannot show a trustworthy original time |
| Cross-device import-chat history | Missing | Saved backend files are accessible on other sessions, but receipts/history are device-local preferences. This is not a synchronized chat or a durable financial audit trail |

## Investments, fees and performance

| Capability | Classification | Actual coverage, evidence and limit |
|---|---|---|
| Bond application and owned-position tracking | Fully implemented | `PendingPurchases`, `PendingConfirmationAction`, calendar purchase status, Spring `PortfolioService` and `/purchases`. Submitted applications are separate from active totals; uploaded personal proof/manual confirmation have distinct paths |
| Bond edits/deletion and filtered investment history | Fully implemented | Reachable PurchaseDetails → PurchaseEditor, model PUT/DELETE, Spring owner-scoped operations; `InvestmentHistoryScreen` requests complete history rather than using only the dashboard preview. Audit/version limitations are separate debt |
| Equity purchase lots and positions | Fully implemented | `/equities` create/list, core `PortfolioSnapshot.sharePositions`, Portfolio/holdings/history screens. Multiple active lots aggregate by ticker/currency without double counting |
| General equity editing/deletion | Missing | `EquityEditor` exists but is an Add share purchase form; `saveEquity` POSTs. Current navigation has no non-null assignment to `equityEditor`, and Spring exposes no equity PUT/DELETE. Do not infer edit support from the class name |
| Purchase fees and cash cost | Fully implemented | `PurchaseValidation`, `PendingPurchases`, core cost helpers and equity validation track buying costs/fees and validate totals. Treasury defaults and rounding policies still need dated policy/parity validation |
| Sale/disposal proceeds and realized gains | Missing | A `sold` status and holdings exclusion exist, but no complete sale transaction/proceeds/fee/lot-allocation workflow was found. Equity sale PDFs are explicitly rejected |
| Coupon dates and contractual estimates | Implemented but needing validation | Core `BondMath`, published-date precedence, calendar events and tests are present. Inferred month-end, non-divisor frequencies and backend count/rounding parity are known gaps (TD-07 and P2 financial policy) |
| Manual/automatic coupon acknowledgements | Fully implemented | Spring `LifecycleNotifications`, migration V6 `coupon_receipts`, `/notifications/coupons`, app notifications/reporting. Automatic due-date acknowledgement is a supported assumption, not evidence of bank receipt |
| Statement-verified coupon cash arrival | Missing | No evidence state linking coupon cash arrival to a verified bank/broker statement was found. Existing statement reconciliation handles investment purchases, not this receipt-verification workflow |
| Dividend ledger/imports/receipts | Missing | No dividend model, migration, API or reachable accounting workflow found. Portfolio/annual report code explicitly excludes dividends; education/legal mentions do not implement them |
| Per-currency dashboard and investment goals | Fully implemented | `PortfolioDashboard`, core `PortfolioSnapshot`, `PortfolioValuation`, `MonthlyInvestmentGoal`, compact overview aggregates and tests; keeps currencies separate and pending applications out of confirmed progress |
| Fee-aware unrealized equity return | Fully implemented | `ValuedSharePosition` computes current quote value minus recorded buying cash cost, plus return ratio; dashboard/holding cards/reports expose scope. Missing quotes fall back to cost with unavailable gain, not zero profit |
| Quote charts and contract-derived price points | Implemented but needing validation | `StockPriceHistory`, `LibraryScreens`, Spring `MarketHistory`; observed quote dates and source types are distinguished. Private-derived public sharing lacks a contribution control (TD-05); deployment/source freshness are not verified here |
| Complete portfolio performance | Partially implemented | Current valuations, unrealized gains, scheduled coupon estimates and investment contributions exist. No complete cash-flow/dividend/disposal history or immutable historical valuations underpin total return |
| TWR, MWR/XIRR and benchmark-adjusted total return | Missing | No implementation found in functional models/services. Simulator/educational calculations and a closing-price chart do not establish personal portfolio performance |
| Cash accounts, FX, liabilities and corporate actions | Missing | Reports explicitly exclude cash/liabilities/dividends; no full cash/FX/split/rights workflow or ledger found. Multiple currency labels are not FX conversion |
| Investment simulation | Fully implemented | Bundled `calculations.ts`, portable JS engine and SimulatorScreen support assumptions, contribution periods, coupons, reinvestment and fees. These projections do not execute trades or promise returns |

## Reporting, identity and platform support

| Capability | Classification | Actual coverage, evidence and limit |
|---|---|---|
| Portfolio PDF export | Fully implemented | `PortfolioReport`/`ReportPdf`, complete authorized records, original report scope and native sharing. Tests inspect PDF structure, private-data exclusions and missing-price behaviour |
| Calendar-year/year-to-date generated annual report | Fully implemented | `AnnualReport` filters year boundaries, cuts current year at today, handles carry-forward and unavailable prices. `AnnualReportTest` covers these rules; Library creates a virtual report card |
| Immutable historical statements/audited returns/tax reports | Partially implemented | Annual reconstruction exists and warns that current statuses/quantities may not reproduce historical state. Frozen snapshots, complete event history and tax-grade calculations are absent |
| Authentication and basic account management | Fully implemented | Spring `AuthService`/`AuthController`, DB opaque sessions, invite registration, owner account administration and invited-account deletion; KMP sign-in/unlock/logout state handling and tests. This is invite access, not public self-service registration |
| Biometric vault and screenshot/privacy controls | Implemented but needing validation | Keystore/AES-GCM, Keychain user presence, capture masking and lifecycle locking exist. Secure-canvas workaround and Android release/debug differences need device/store validation |
| MFA, account recovery and organization identity | Missing | No MFA/recovery/SSO implementation found. Device biometric unlock does not provide server-side MFA; owner/admin flags do not implement organization membership |
| In-app notifications and category preferences | Fully implemented | Inbox/read state, reminders, targets, price alerts, receipts and per-switch saving logic exist in app/backend and tests. Cron transaction-path correctness remains TD-06 |
| Remote push and production scheduled delivery | Implemented but needing validation | Firebase/APNs/device-token adapters and protected cron routes exist; credentials, permissions, scheduling and real delivery must be validated. SDK presence is not a configured production channel |
| Bounded local/offline behaviour | Partially implemented | Bundled lessons/parsers, in-memory data reuse, on-device preferences/import receipts and an already loaded PDF can work locally. Unlock/session verification, fresh data, persistence and server saves still depend on connectivity |
| Full offline portfolio database, queued financial writes and conflict sync | Missing | No Room/SQLDelight/SQLite account store or durable write queue found. Vault session snapshots are credentials/session state, not a local portfolio database |
| Android application | Fully implemented | Shared Compose host, native file/open/share, notification adapters and app/test builds exist (minimum API 24). Physical/device/provider coverage and signing are validation gates, not missing app implementation |
| iPhone application | Fully implemented | SwiftUI host, shared Compose screens, PDFKit/Vision/JavaScriptCore and native file/sharing adapters exist (minimum iOS 18.2). Compilation and native test sources exist; native runtime execution and distribution readiness are separate |
| Dedicated iPhone Share extension | Deferred for the future | PDF Open In already works. A separate extension and entitlement/signing work remain deferred; do not label all iPhone file sharing missing |
| Onboarding, themes, English/French, education | Fully implemented | Reachable shared screens, bundled educational assets, localization catalog and native settings. Does not imply every translated/assistive-technology scenario was manually validated |
| Signed store distribution and complete operational readiness | Implemented but needing validation | Release build paths exist, but store identity/signing, privacy resources/policy URLs, provider setup and capture behaviour need the release audit gates. See STORE_READINESS_AUDIT and TD-15 |

## Future products: deliberate boundaries

| Capability | Classification | Readiness rather than assumed implementation |
|---|---|---|
| More broker-specific parsers | Deferred for the future | `InvestmentDocumentParser`/`EngineDocumentParser` provide a current adapter seam. No complete registry, versioned parser provenance or broad supported broker catalog exists |
| Institutional/club shared portfolios | Deferred for the future | Existing `owner_id` scopes individuals; no workspace/membership/tenant model, approval workflow or team entitlement system exists |
| Premium subscriptions and billing | Deferred for the future | Business ideas are documented, but there is no billing/entitlement implementation to audit as working |
| External financial/partner APIs | Deferred for the future | Internal financial REST APIs already work; a public partner contract, API keys/scopes, quotas, versioning and integration lifecycle are not implemented |
| Customer-authorized broker/bank integrations | Deferred for the future | No third-party grant/consent/resource scope/revocation or partner credential lifecycle found. Reusing a customer's app session/password would not provide this architecture |
| Brokerage, orders, custody and payments | Deferred for the future | Record keeping is implemented; execution/custody business domains and operational/licensing dependencies are not. Do not build these in the inventory phase |

## Evidence map and validation confidence

Client sources are under `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments`, `core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments` and `web-logic/source`. Spring symbols above are under the companion checkout's `src/main/kotlin/dev/orestegabo/portfolio`; schema evidence is in its `src/main/resources/db/migration`.

Key sources:

- [Native import pipeline](../app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PurchasePdfImport.kt), [equity parser](../web-logic/source/equity-pdf-parser.ts), [statement parser](../web-logic/source/bk-statement-parser.ts), [parser port](../core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentDocumentParser.kt).
- [Navigation](../app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentApp.kt), [application coordinator](../app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PortfolioModel.kt), [domain valuations](../core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PortfolioValuation.kt), [annual reports](../app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/AnnualReport.kt).
- [Spring APIs](../../gabo-portfolio-api/src/main/kotlin/dev/orestegabo/portfolio/PortfolioController.kt), [validation](../../gabo-portfolio-api/src/main/kotlin/dev/orestegabo/portfolio/PurchaseValidation.kt), [document service](../../gabo-portfolio-api/src/main/kotlin/dev/orestegabo/portfolio/DocumentService.kt), [statement service](../../gabo-portfolio-api/src/main/kotlin/dev/orestegabo/portfolio/StatementService.kt).

Supporting tests include core PortfolioSnapshot/PortfolioValuation/MonthlyInvestmentGoal/BondMath tests; shared Pending/Issuance/Statement/ImportDuplicates/DocumentPreview/AnnualReport/PortfolioReport tests; Android ImportConversationUiTest/DocumentsLibraryTest/native renderer tests; Node broker/BNR/signed-PDF tests; and Spring parser/compatibility/Testcontainers tests. Test existence is distinguished from execution.

The preceding audit executed 33 core JVM tests, 197 shared Android-host tests, 29 Node tests, six pure Spring tests and one sample-server test successfully. Android app/test assembly, iPhone shared/test-source compilation and release lint passed (0 errors, 54 warnings). Full PostgreSQL integration, current native runtime/device/provider flows, store submissions and live restore/isolation were not verified. These checks were **not re-run for this documentation-only phase**; see [VERIFICATION.md](VERIFICATION.md) for commands and historical run dates.

## This phase

No product code, schema, configuration or dependencies were changed. Existing implementation and pending working-tree changes were preserved. The deliverable is a more precise inventory and prioritized next steps, not another implementation pass.

## Subsequent parser hardening

The later [document parsing changes](DOCUMENT_PARSING.md) improve the existing parsers and align the statement upload category in the companion Spring working tree. They supersede the original TD-01 source mismatch; full database/runtime validation remains outstanding. The inventory-only phase above made no source changes itself.

## Subsequent calculation and reconciliation changes

[Financial calculations](FINANCIAL_CALCULATIONS.md) documents configurable tax, decimal monetary boundaries, preserved official dates, anchored inferred schedules and corrected RWF presentation. [Reconciliation](RECONCILIATION.md) documents strengthened identity matching, manual review, separate nonpurchase evidence, source links and correction history. Spring now includes additive Flyway V8 in its working tree; both prior V1–V7 inspection statements and inventory-only no-code statements describe the earlier phase. Dividend/sales/cash accounting and full financial mutation audit remain missing; reconciliation evidence does not implement them.


## Investor UI review — 30 October 2026

This follow-up inspected shared navigation (`InvestmentApp`), its direct lifecycle ViewModel (`PortfolioModel`), the core API/domain helpers, screen/component families, native ports and the companion Spring controller/service routes. There is no separate repository/use-case/DI layer to recreate. The older inventory above is a dated architectural snapshot; current source and the later verification entries take precedence over its baseline/test counts.

| Area | Current user-facing status and evidence |
|---|---|
| Portfolio dashboard | Implemented: per-currency valuation, buying-cost-aware unrealized share gains, allocation, upcoming payment/maturity cards, contribution targets and recent purchases (`PortfolioDashboard`, `PortfolioValuation`). The preceding unlock change supplies initial loading/retry states; this pass retains them |
| Bond details | Implemented terms/costs, dates, schedules, confirmation, edit/delete and receipt acknowledgements (`PurchaseDetails`, `BondPurchaseFacts`). This pass adds direct access to explicitly attached personal allocation and issuance PDFs, and separates manual receipt totals from automatic markings |
| Equity details | Implemented inline purchase-lot disclosure (`SharePurchaseTile`) and aggregate holding cards/charts. Itemized buying fees and total payable already exist. This pass adds direct access to the saved contract from purchase rows. General equity edit/delete and sale/realized-gain workflows still lack end-to-end backend support; no decorative replacement detail page was added |
| Coupon calendar | Implemented published windows, personal coupon/maturity dates and purchase-aware issuance guidance (`CalendarScreen`, `CalendarEvents`, `CalendarPurchaseStatus`). Past coupon entries now show existing receipt evidence rather than remaining labeled only as expected. Future coupons remain expected; maturity remains an expected principal repayment, not receipt evidence |
| Dividend tracking | No dividend accounting/receipt API or durable ledger found. Existing screens/reports disclose exclusion. A client-only dividend total would be misleading, so none was introduced |
| Investment history | Implemented full-history loading, asset/year/text filters and original lots (`InvestmentHistoryScreen`). New evidence actions reuse the existing original-PDF viewer; no additional navigation destination |
| PDF import | Implemented picker/shared-file routing, parsing/OCR, editable bond correction, review, target matching, duplicate handling, progress, retry and saved receipts (`ConversationReview`, `PurchaseReviewState`, `PurchaseForms`, `ImportJournal`, model). Current import receipts now label combined unmatched/review-required counts as “need attention,” not all “missing PDFs” |
| Document management | Implemented searchable/filterable library, generated annual cards, authenticated PDF viewing and native sharing (`DocumentsHub`, `DocumentsScreen`, `PdfViewerScreen`). Investment evidence follows persisted document IDs, with canonical authenticated download paths even while library metadata is still loading; no guessed match by ticker/date |
| Financial analytics | Implemented fee-aware unrealized equity returns and explicitly limited portfolio estimates. Complete total return, dividends, realized proceeds, TWR/MWR/XIRR remain missing pending authoritative cash-flow history. Existing value-scope disclosures preserved |
| Charts | Implemented selectable area/line/bar charts, month/year/all periods, dated observed/contract-derived prices, stale/error/retry states and existing simulator projections (`InvestmentChart`, `StockPriceHistory`, `LibraryScreens`, `PlanningScreens`). Not recreated |
| Search/filtering | Implemented in history, document library, bond market and education; dashboard asset/status filters exist. No new global search was justified by this review |
| Reports | Implemented portfolio and calendar-year/YTD PDFs with original scope limits (`PortfolioReport`, `AnnualReport`). They already distinguish manual/automatic coupon receipt methods. Historical/tax-grade performance remains limited by available data; no new reporting promises |
| Notifications | Implemented inbox, read state, preferences, price alerts, coupon acknowledgements and backend push adapters. Automatic coupon notifications now say “marked automatically” and permit explicit manual confirmation through the existing receipt endpoint. Runtime push delivery still requires provider/device validation |
| Settings and localization | Implemented themes, privacy/capture/number controls, language, goals, biometric settings, account controls, about/version/legal links. New evidence/receipt/reconciliation copy is localized in English/French; existing money/date conventions and number masking are retained |
| Offline functionality | Partial: packaged lessons/parsers, already loaded content and within-grace in-memory session reuse exist. Cold unlock, current authoritative records and financial writes require connectivity; no new offline authorization or financial database |

### Selected usability changes and boundaries

- Document access was missing directly beside saved investment facts although provenance and the private reader already existed. Explicit ID links now make the existing evidence reachable without searching the library. Public issuance terms remain clearly separate from personal allocation confirmation.
- The bond summary previously called every `received=true` row “Confirmed received,” including automatic acknowledgements. It now shows separate “Confirmed by you” / “Marked automatically” amounts from recorded receipt rows. Future dates, another bond/currency, invalid amounts and unreceived entries cannot inflate these display totals. An unavailable receipt service is displayed as unavailable, not a zero or an unreceived payment. Automatic rows can be manually confirmed using the existing backend behavior; no accounting engine or new receipt endpoint.
- Calendar and automatic notification text now describe the same existing receipt distinction. Coupon rows label the scheduled net estimate separately from the stored receipt amount, so later changes to bond terms do not disguise what was acknowledged previously. No bank verification is claimed. Existing schedules, coupon calculations, tax assumptions and maturity formulas are unchanged.
- The active chat's `missing` count includes both unmatched and review-required entries; “need attention” is accurate for both. Its existing Review statement records action already exposes the individual statuses. The older standalone `StatementReviewScreen` has no current navigation call site; it was not used to justify a new product flow.

These changes preserve the existing layout, Struct Capital branding, native privacy controls and preceding performance work. They introduce no backend/schema changes, dependencies, decorative animations, persisted financial records or new institutional products. Relevant shared tests cover presentation boundaries, evidence-path safety, exact private PDF bytes and localization. See VERIFICATION.md for executed commands/results.


## Standalone tools follow-up — 30 October 2026

Added **Investment outlook** under Settings → Tools after reviewing all fourteen candidate capabilities. Its Cash flows section aggregates existing saved bond schedules into twelve calendar-month buckets, separating gross/net coupons, withholding and face-value principal repayments per currency. Its Buying fees section summarizes completed purchase charges over complete acquisition history, with explicit cost/denominator definitions and visible incomplete-record exclusions. Both reuse core financial helpers and existing account-scoped history APIs; no writes or broker integrations.

These extend existing individual coupon/maturity dates and fee disclosures, rather than duplicate the simulator, contribution planner, annual summaries or PDF exports. Scheduled cash flows are not receipt evidence or guaranteed returns, and purchase fees are historical acquisition costs rather than current valuations. See [candidate inventory, assumptions, scope and tests](INVESTMENT_TOOLS.md). New tool calculations run off the UI thread, with existing loading/retry states, original calendar/history navigation, financial-number protection and English/French copy.


## Security follow-up — 30 October 2026

The [source audit and verified regressions](security/SECURITY_AUDIT.md) distinguish existing account-scoped purchases/private documents from three reproduced high-risk paths: public cross-account derived contract prices, unprivileged shared issuance publication, and non-atomic daily coupon/reminder work. These are fixed locally, along with completed-PDF envelope checks, anonymous forced-refresh cache bypass and targeted dependency patches. Notices now describe own-account contract charts and owner-published shared public reports. No auth replacement or schema migration. Production rollout, historical shared-source review, backups, retention and legal compliance remain separately unverified.

## 31 October follow-up: statement imports and independent bond terms

The exact recognition failure was reproduced on iOS 18.6 (connected phone: iOS 18.7.1), while iOS 26.2 accepted the original. The native BK statement reader now rebuilds positioned word rows using the existing layout helper; purchase-specific pickers route statements to their own review. See [parser evidence](DOCUMENT_PARSING.md#ios-18-reading-order-correction-31-october-follow-up).

Backend V9 adds independent public bond definitions and purchase references without removing historical snapshot columns. Existing statement reconciliation now offers explicit, sourced missing-bond creation from compatible saved terms, with personal figures verified by the backend, idempotent retries, pending/existing-purchase duplicate protection and direct original-statement access. Parsing/reconciliation still never auto-posts deposits, fees, coupons or dividends as holdings/income. See [review and rollout](RECONCILIATION.md#reviewed-missing-bond-purchases-and-independent-terms-31-october). Source implementation and local verification do not establish production deployment.

## 31 October follow-up: global inbox and specific notification actions

Notifications open from the Portfolio header bell, including loading/error states, with an unread badge. The five bottom tabs are Portfolio, Calendar, Documents, Stocks and Settings. The notification inbox is a dedicated subpage with Back returning to Portfolio; its specific destinations and preferences remain available. Push taps open the same inbox. Empty/error inbox actions return to Portfolio rather than treating all notifications as stock events.

Legacy `action=settings` events resolve by category: security → exact sign-in activity, target → monthly-target editor, report → existing report options. Document reminders open their specific reconciliation record; calendar links retain their due date; completed pending-purchase reminders open details rather than requesting another confirmation. Unsupported/malformed destinations do not receive an unrelated generic Settings action. Notification preferences are directly reachable from the Inbox header.

The read-only activity screen uses existing owner-scoped device registrations and highlights the device associated with the selected alert. It displays first-recorded time and this-installation identity, and explicitly distinguishes recorded devices from active sessions. It does not claim remote session revocation. The backend endpoint requires deployment; no new schema or device identifiers are collected for this notification change.
