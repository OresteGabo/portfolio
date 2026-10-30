# Struct Capital: five-year product and technical roadmap

Reviewed **30 October 2026** against the existing KMP application, companion Spring backend, tests and documentation. This is a decision framework, not a delivery promise, revenue forecast, authorization to spend or instruction to implement every candidate. Built by GABO.

## Direction and planning clock

Build a trustworthy free investment-management app, learn from investors, then sell additional software only where repeated use and willingness to pay are demonstrated. The founder's personal use supplies real problems and fast feedback; it does not establish demand from other investors. A profitable software company can remain the destination. Owning a licensed brokerage is an optional later business decision, not the next technical milestone.

**Year 1** is the first twelve months of stabilization and initial release preparation, starting from this baseline. Years 2–5 describe subsequent approximately twelve-month phases; record actual start/end dates in quarterly reviews. If release or validation takes longer, move the phases rather than compressing security work or assuming revenue starts on schedule. Useful institutional conversations may happen earlier, but do not trigger speculative product construction.

The long-term ambition is **RWF 2–5 million monthly net business profit**. It is an objective to evaluate, not a guaranteed Year-5 outcome. Never count investment principal, assets tracked, coupons, share gains, unpaid invoices or founder cash injections as software revenue/profit.

## Current maturity: substantial individual app, public release not yet proven

Source-backed capability is separate from deployed behavior, real-device quality and market demand. The detailed inventories remain in [CURRENT_STATE.md](CURRENT_STATE.md), [technical debt](TECHNICAL_DEBT.md), [financial calculations](FINANCIAL_CALCULATIONS.md) and [verification](VERIFICATION.md). Do not use older counts/limitations in historical audit sections without their later follow-ups.

| Already implemented | Evidence and practical limit |
| --- | --- |
| BNR personal bond results and public issuance imports; BK equity purchase contracts and client statements | Existing native PDF/text/OCR engines, shared TypeScript parsers and `InvestmentDocumentParser` adapter. Four identified supported layouts, not all institutions/securities/documents. Extraction is not signature/authenticity verification. [Parser coverage](DOCUMENT_PARSING.md). |
| Bonds, equity acquisitions, costs/fees, coupon schedules, maturity calendar, manual/automatic receipt markings and quotes/charts | Existing `core` rules, Spring validation/portfolio/coupon services and shared screens. Scheduled/automatically marked/manual income is distinguishable; none alone proves bank receipt. Fee-aware unrealized equity gains are not total portfolio return. |
| Import chat/library, original PDF access, reviewed confirmation, duplicate prevention, statement source links and correction decisions | Existing `PortfolioModel`, `DocumentService`, `StatementService`, V8 and import tests. Statements do not create a holding per row. Other cash/fee/coupon/dividend movements remain evidence-only. [Reconciliation](RECONCILIATION.md). |
| Portfolio and calendar-year/YTD PDF reports, contribution goals and simulator | Existing `PortfolioReport`, `AnnualReport` and tools. Complete-history guards and uncertainty disclosures remain necessary; these are not audited broker/tax statements. |
| Monthly scheduled bond cash-flow/maturity outlook and completed-acquisition fee summaries | Already added to Settings → Tools; reuse saved data and explicit financial assumptions. These are not missing future features. [Investment tools](INVESTMENT_TOOLS.md). |
| Android/iOS shared UI, branded onboarding/About/version, English/French, themes, settings, session/biometric sign-in, invited-account deletion and in-app notifications | Substantial functioning product with platform tests. Full offline financial access, every account-deletion case, production push, store distribution, real-device accessibility and upgrades are not established. |
| Spring Boot/PostgreSQL/Flyway backend, ownership checks, tested persistence and existing deployment pipeline | One modular monolith, not a greenfield backend. Internal REST APIs are not a supported institutional API. Preserve stored IDs, data, wire compatibility and secure defaults. |

Actual business metrics were **not inspected or supplied**: non-founder active/retained users, current import success/accuracy in the field, paying users, revenue/MRR, operating expense, net profit and signed partners are **unknown**, not zero and not inferred from test accounts, Git history or test counts. No analytics SDK or commercial billing system was found; current privacy copy says there is no advertising/analytics tracking software. The founder reports personal use; no cohort conclusion follows from that.

## What previous phases improved

These are local source/test results unless separately deployed and verified. They must not remain in the backlog as if untouched.

| Completed work | Remaining verification or boundary |
| --- | --- |
| Financial rules moved into existing `core`; parser-specific port wraps existing native engines | Core still contains transport/JSON boundaries; do not create a published financial SDK without a real second consumer. |
| Parser validation, institution/type/format/version identity, ambiguity/malformed-document regressions and supported statement upload | Broader coverage, held-out real-device accuracy and durable server parser provenance are not established. No new cloud OCR/AI service added. |
| Coupon date/inference alignment, tax/rounding and acquisition-fee regressions using synthetic BNR/BK examples | Official dates must stay authoritative; broader cash/dividend/disposal/performance accounting remains incomplete. Regression examples are not real user investments. |
| Identity-aware statement matching, evidence-only movements, private sources, append-only link/clear decisions, stale-decision/retry protection | General investment edit history, optimistic conflict checks and atomic durable import outcomes remain gaps. |
| Unlock gate separated from background portfolio synchronization; safe opt-in timing/correlation | Controlled delay tests improved; they do not establish the founder's physical-device cold/warm timing or hosting root cause. Server authorization and biometric policy remain required. |
| Clearer scheduled/recorded income labels, direct original-document access and two standalone investor tools | No UI redesign or second financial engine; validate real usability before adding further tools. |
| Account-scoped private-derived price history, owner-only shared issuance publication, real daily-job transaction boundary, PDF envelope checks, anonymous cache protection and dependency patches | Verify deployed fixes, old shared-report provenance, backup/restore, actual provider handling and legal notices before broader access. Envelope validation is not malware scanning. |
| Shared mobile versioning, Android signing guard, packaged iOS privacy manifest, explicit push settings, shared Xcode scheme and mobile release CI | Unsigned local release builds/package checks pass; signing/store/account/device gates remain. Backend CI already existed. [Release checklist](RELEASE_CHECKLIST.md). |
| Broker reuse/adapter/authorization/pilot documentation | Documentation only: no partner, broker API, tenant model, institutional system or commercial validation created. [Broker opportunities](BROKER_SOFTWARE.md), [integration contracts](architecture/BROKER_INTEGRATION.md). |

## Next 90 days: use the existing product before widening it

Work in small changes with acceptance evidence; dates depend on available founder time. Keep one active product experiment alongside reliability work.

1. **Reconcile the working release baseline.** Review pending changes in both checkouts, choose candidate revisions, run appropriate existing tests and coordinate backend/mobile/privacy rollout. Verify production owner isolation, source access, daily jobs and supported imports with dedicated synthetic accounts. No production deployment is authorized by this plan.
2. **Close the public-beta gates.** Final app privacy/deletion pages and verified contacts, developer identity/store classification/signing, iOS screenshot workaround decision, provider/retention facts, backup/restore/erasure rehearsal, physical-device cold/warm unlock, PDF readability, denied permissions, accessibility and upgrade checks. The current branded privacy URL is a website draft, not a final app policy. See the dated release checklist; recheck URLs at rollout.
3. **Finish small financial reliability foundations.** Prioritize durable server import provenance/idempotent results, then financial edit audit/version checks. Reuse current transactions/hash constraints/evidence rather than creating a second ledger. Add authoritative private document digest/upload timestamp where justified; do not infer an original upload date from the document date. Use additive migrations and old-client compatibility tests.
4. **Run a small invited investor cohort once safe.** Target 10 non-founder testers and 5 structured feedback sessions first; watch supported imports with permission, record failures/corrections and test finding originals/understanding receipts. Do not request private PDFs through support email or record screen balances for analytics.
5. **Establish a private baseline scorecard and spending limits.** Populate actual metrics only from permissioned measurements/invoices. Agree experiment budgets before spending. No analytics service, payment system, advertising infrastructure or institution product is needed for this step.

## Flexible five-year phases

The numerical milestones below are **proposed learning targets**, not current metrics, market-size estimates, promised growth or safety exemptions. Review them quarterly against available investors, data quality and founder capacity. Use the metric definitions below. Report denominator and cohort size beside every percentage; small cohorts cannot establish production-wide accuracy or retention.

### Year 1 — reliable free release and evidence of usefulness

**Product:** stabilize the existing import → review → record → original-document/report loop. Validate coupons, dates, taxes/fees, duplicate/retry behavior and pending allocation confirmation. Strengthen security and operational recovery, complete release gates and make a free release when ready. Keep functioning reports, tools and imports available.

**Technical:** existing KMP/Spring/PostgreSQL stack and one deployment. Maintain cross-runtime golden tests and authorized held-out format samples. Verify secure local storage/account switching, API/database compatibility, release updates, slow/offline errors, crash handling and accessibility. Obtain actual device timing; keep nonessential API work off the global unlock gate.

**Proposed milestones:** 30 activated non-founder users; 15 meaningful monthly active users; 50 distinct supported-document imports with completed reviewed outcomes; 20 structured feedback sessions/findings; first R90 cohort with at least 20 eligible users, aiming for at least 8 returning users. Initial parser acceptance corpus: at least 20 independent authorized/synthetic document examples per supported layout, with scan/text/platform variants tracked separately. All supported critical fields must agree with independently checked ground truth on the accepted release corpus; known unsafe/mixed cases must reject. Broaden samples after every failure rather than treating corpus size as proof of universal accuracy.

**Advance when:** release/security/calculation gates are met; users other than the founder repeatedly complete a useful task without founder intervention; eligible users and import denominators are measurable; severe unresolved financial/data-isolation defects are absent. Record sample diversity and provenance; generated variations of one template are not independent evidence of broad layout coverage. If release is delayed, continue a safe small invited test rather than spending to acquire users. Missing the numeric target is a diagnostic trigger, not permission to fabricate activity or loosen safety.

### Year 2 — retention and willingness to pay

**Product:** investigate why people return or stop. Choose at most two missing capabilities from repeated needs: e.g. evidence-backed dividend history, auditable equity corrections/disposals, verified-term scenario comparison or concentration analysis. The existing cash-flow/fee tools and simulator stay; do not rebuild them. Financial prerequisites determine scope: a total-return chart must wait for complete dated cash flows/valuations.

**Monetization:** test premium concepts through interviews and clearly labeled prototypes/offers. Preserve ordinary tracking, original-document access, export/deletion, essential correctness and security as the free foundation. Candidate extras: reproducible saved scenario sets, deeper well-supported reports/analytics, optional higher-volume convenience and support. An expression of interest is not a paying user. Only after repeated use and explicit purchase commitments should a separately authorized phase choose a store-compliant payment/entitlement mechanism; no billing is implemented now. Do not use external invoices to bypass in-app digital-purchase rules.

**Proposed milestones:** 100 cumulative activated non-founder users; 50 meaningful MAU; 3 eligible R90 cohorts of at least 20 users with a provisional return target of 40% or better; at least 250 cumulative completed supported-document imports; 10 pricing interviews and 5 explicit paid-offer commitments; test first 5 actual paying users and observe at least 2 subsequent billing/renewal opportunities **if** a paid offering is authorized and available. Record actual collected revenue, refunds, MRR and operating expenses monthly even if revenue is absent; don't set a growth forecast from these thresholds.

**Marketing:** after recurring value is demonstrated, run at most two small, time-bounded channel/copy experiments with predefined loss caps, using existing store/support tools and voluntary source attribution first. Compare cost per activated and R90-retained investor, not installs alone. No campaign spend or tracking infrastructure is authorized here. If usage is too sparse, investigate whether this is episodic bond utility rather than forcing daily engagement.

**Advance when:** repeated customer value and affordable support are demonstrated; first buyers renew or give a clear repeat-use reason; spending produces retained users within the agreed cap. Pause premium construction after two failed offer iterations without commitments. Fix activation/retention before widening paid acquisition.

### Year 3 — institutional discovery and one bounded paid pilot

**Product/business:** interview brokers and other appropriately eligible institutions about demonstrated operational tasks. Show existing document processing, original evidence, matching explanations and investor reports using synthetic/redacted samples. Ask which current task, source system, reviewer and output matter. Do not invent workflows or promise to replace their systems.

**Proposed milestones:** 5 structured institutional interviews across at least 3 distinct eligible institutions, subject to the actual reachable market; 2 observed task demonstrations; 1 written pilot scope with authorized sample rights and baseline/acceptance criteria; seek the first **paid pilot**, not just a free demo or nonbinding letter. Keep existing investor retention/import/cost scorecard. Track pilot cash, one-off revenue and support hours separately from MRR. No assumed broker customer or minimum revenue is booked in advance.

**Technical:** reuse current parser adapters, validations and evidence/reports for a read-only extraction or reconciliation-quality evaluation. Begin synthetic. If real data is necessary, agree roles/access/locations/retention and select an approved secure intake/deployment method first. Current personal-account admin access cannot be repurposed as a broker workspace. A synthetic paid evaluation need not introduce tenancy, bulk APIs or a customer database.

**Advance when:** a named buyer verifies a recurring problem, authorizes a bounded task/data use, accepts measurable results and pays. Use [pilot gates](BROKER_SOFTWARE.md#pilot-validation-before-building-or-accessing-customer-records). If discovery yields no budget owner or repeatable pain after the planned interviews/two narrowed proposals, stop institutional building and return to the investor product; conversations alone are not traction.

### Year 4 — expand only validated B2B software

**Product/business:** convert a successful pilot into a repeatable software/support offer. Candidate services: maintained document adapters, reviewed document processing, evidence/reconciliation assistance and supplemental client-report preparation. Secure institutional APIs, broker-controlled installation or customer-authorized read-only integration are options only where a real customer and supported interface exist.

**Proposed milestones:** seek 2 recurring institutional customers, at least 1 observed renewal and positive direct contribution per recurring contract over 3 months after allocating support work. Test a recurring break-even milestone: recurring revenue covers direct delivery costs plus recurring operating expenses, including the agreed founder-labor allowance, for 3 consecutive months; separately verify collections fund cash outflows. Track actual MRR, collection delays, refunds, churn, customer concentration, operating expenses and net result. Require an agreed recovery drill, revocation/offboarding/export test and staffed incident contact before increasing sensitive-data exposure. No arbitrary MAU growth target overrides a sustainable smaller business.

**Technical:** implement only the isolation/delegation and operating controls justified by the chosen validated model, using additive migration/backfill and all-resource isolation tests. Scope machine credentials, quotas, durable idempotency, source revisions and audit; verify consent before retrieval and commit. This is a future gate, not approval to implement full tenancy now. Improve monitoring/recovery and capacity from measured traffic/storage, not a microservice checklist.

**Advance when:** at least two customers can use substantially the same supported product, renewals and collections support its cost, and onboarding/support remain within founder capacity. If each sale is an unrelated custom project, price it as services and evaluate separately; do not call it scalable software MRR. Pause APIs/integrations without supported interfaces, permissions or economic benefit.

### Year 5 — profitability and business-model choice

**Evaluate:** paid investor retention, institutional renewals, collected recurring revenue, customer concentration, software gross contribution, all operating expenses, founder compensation, tax/reserves, recovery readiness and support capacity. Consider larger partnerships only when they improve these outcomes without unacceptable dependency or data access.

**Proposed milestones:** 6 consecutive monthly closed business scorecards; where annual contracts exist, at least one actual renewal cycle; positive recurring contribution and a documented path to sustained net profit without consuming investment principal. Assess the RWF 2–5 million monthly net-profit ambition using a six-month average and realistic downside cases. Missing the target is a reason to adjust scope, price or channel, not assume a brokerage license will fix economics.

**Decision:** compare staying a focused profitable software provider, expanding an adjacent validated market, remaining a deliberately small investor utility, or researching a separately licensed brokerage. Brokerage research needs a separate legal/capital/operations study covering licensing, governance, custody/settlement partners, controls, staffing, liability, conflicts and opportunity cost. No licensing workflow, order execution or custody system is in this roadmap's implementation scope. Remaining a software provider is a valid success outcome.

## Measurement definitions and honest scorecard

All current values below are **unknown / not measured in this planning pass**. Create a private monthly record with period, source, denominator, observed value, confidence/limitations, experiment cost, decision and next review. Never substitute targets for observations.

| Metric | Definition and measurement rule |
| --- | --- |
| Activated users | Unique non-founder/non-test users who complete a reviewed import or reviewed manual investment setup and subsequently inspect their portfolio/original evidence. Account registrations/installs alone do not count. Define and record any activation-rule change. |
| Meaningful MAU | Unique non-test users in 30 days completing a meaningful action such as importing/reviewing evidence, inspecting a schedule/position, recording a receipt or generating a report. Exclude background sync, automated pushes and mere process starts. Count users once; report actions separately. |
| Retained users | For an activation cohort, R30 returns during days 30–59; R90 during days 90–119; optional R180 for semiannual bond use. Numerator is unique measurable users performing a meaningful action in that window; denominator is all users in the declared measurement-eligible cohort old enough to complete it, including nonreturners. Report that cohort's coverage of all activated users and explain opt-out/deletion/unavailable observations; do not claim whole-product retention from a subset. Include absolute counts and separate pre-window returners. |
| Import completion | Distinct eligible selected documents resulting in the intended reviewed outcome and linked original, divided by all eligible selected documents in the period. Break down supported/unsupported, platform, extraction method and format; selection abandonment/parser errors/rejections remain visible. Classify eligibility against ground truth where possible, not only successful parser recognition. Deduplicate retries and exact files per account; reused documents count as recovery/reuse, not new acquisitions. Public issuance/statement outcomes are not purchases. |
| Import accuracy | On authorized independently verified samples: correct critical fields divided by critical fields evaluated **before user correction**, plus documents with every required critical field correct divided by supported documents evaluated. Include misclassified/rejected supported documents in coverage/completion reporting, not silently exclude them to improve accuracy. Track principal, quantity, price, fees, currency, rates, identifiers and official dates separately. User acceptance alone is not ground truth. |
| Safety/error rate | Wrong financial records created, wrong-client matches, unauthorized reads, duplicates and unsupported documents silently accepted, with attempts/exposure and incident severity. Target zero known unresolved severe cases; a zero count in a small test is not a general guarantee. Track correction and rejection reasons alongside accuracy. |
| Feedback | Number of structured sessions, unique non-founder participants, observed task blockers, severity, repeated requests and resolved/retested issues. Do not treat message count, compliments or friends' politeness as willingness to pay. |
| Paying users/customers | Distinct actual buyers who paid for delivered software/services, net of fully refunded trials; show active paid contracts, renewals and cancellations. Separate individuals, institutions, pilots, free sponsors and one-off consulting. Commitments and demo participants are not payers. |
| Institutional funnel | Interviews, demonstrated problems, authorized pilots proposed/accepted/paid/completed, recurring conversions and renewals. Record institution count separately from contact count; don't invent partnerships from directory listings. |
| MRR / cash / revenue | Normalize the recurring software portion of active contracts to monthly value; exclude taxes collected for authorities, one-off pilots/setup/consulting, investment flows and unpaid speculative deals. Report accounting revenue, actual cash receipts and overdue amounts separately. Annual prepaid cash is not all one month's MRR. Validate recognition/tax treatment with an accountant. |
| Operating expenses / profit | Track cash outflows and accrued liabilities for hosting/DB/storage/backups, stores/domains/email, support, development, legal/security, fees/refunds, sales, founder compensation and taxes as applicable. Define operating profit, after-tax net profit, cash movement and founder draw separately so none are double-counted. Report economic profit with a reasonable founder-labor allowance as well as any cash view. |
| Acquisition efficiency | Cost per activated user and per eligible R90-retained user, by small tested channel; exclude organic users from paid-channel claims. Separate spend-only CAC from fully loaded sales/support effort. Paid LTV/payback requires observed renewal/contribution data; do not extrapolate it from one month. |

**Collection policy:** begin with voluntary interviews, permissioned task observations, a minimal private cohort register, invoices and aggregate operational counts where authorized. No user behavior/amounts/PDF contents are to be added to raw logs. Existing app policy does not authorize a new analytics SDK by implication. Before any event measurement, define necessity, lawful basis/consent as applicable, notices, pseudonymous scope, access, retention/deletion and sampling bias. Users who decline optional measurement retain essential functionality. Do not claim cohort retention for users who cannot be measured; disclose coverage/limitations rather than silently counting missing data as either return or churn.

Monthly operating review: record actual metrics/costs and 1–3 repeated user problems. Quarterly strategy review: compare cohorts and experiments, decide continue/narrow/stop, review financial/safety gates and rebase year targets. User welfare/correctness overrides marketing conversion metrics.

## Monetization and document-processing services

Keep the free individual product useful. Test one paid job at a time, not a speculative menu/paywall. Candidate recurring revenue:

- **Investor premium:** saved/reproducible scenarios, validated deeper reporting/analytics and optional convenience/support, only after repeated use and a paid-offer test. Essential document access/export, erasure, accurate balances, security and correction of defects are not premium bargaining chips.
- **Document-processing software/support:** fees for agreed supported layouts, maintenance, reviewed normalized extraction and measurable quality. Start as a bounded authorized evaluation, then possibly recurring allowances/support. It is not currently a hosted public parsing service.
- **Institutional reporting/reconciliation assistance:** a repeatable subscription or support license for a demonstrated workflow; outputs supplement the broker's source system. No official statement generation or full broker platform promised.
- **Later institutional API or broker-controlled deployment:** scoped recurring license/usage/support where a validated customer needs that delivery model. White-label branding is a later packaging decision, not proof of value.

Document-processing-as-a-service stages: synthetic local demonstration → agreed paid evaluation/quality report → approved limited processing with human review and deletion/return terms → repeatable managed or broker-controlled product → API only if demand justifies it. Current Spring only validates/stores PDF envelopes; moving local parsing/OCR to a server would add a new untrusted-file execution/capacity boundary requiring isolation, limits and testing. Do not assume the current upload endpoint is already a safe headless parsing service. Reject training/resale/aggregation rights by default; never sell financial data as a monetization shortcut.

Pricing, included volume, duplicate/failed-job handling, correction responsibilities and support commitments require experiments, not copied market rates. The [broker assessment](BROKER_SOFTWARE.md#b2b-pricing-hypotheses--no-validated-prices) records hypotheses. Do not book a paid pilot as recurring software revenue until there is a recurring paid contract. Future consumer payments must respect applicable store rules; no payment system or paywall is added by this plan.

## Financial sustainability and protecting founder capital

Current costs, income, available runway and risk budget are not known. First establish actual business cash accounts/records and an affordable loss budget **separate from investment principal and personal commitments**. The target profit must come from customers paying for useful software/services, not portfolio performance. A free app can be worthwhile as a small utility even before commercial expansion, if its recurring cost is intentionally affordable.

Before each experiment specify: cash cap, founder-hour cap, maximum duration, evidence sought, a cheaper alternative and a stop condition. Do not sign annual tools, hire a team or buy paid acquisition to satisfy a calendar year. Maintain a monthly cost register; decide a recurring operating ceiling, one-off release/legal allowance and minimum business cash buffer from actual resources. If the buffer would be breached, stop discretionary spend and narrow the offering; do not automatically replenish it from bond/share principal.

**Conditional arithmetic only:** if monthly after-tax net margin after all relevant expenses and founder compensation is `m`, revenue needed for profit `P` is `P / m`. These margins are hypothetical, not observed, achievable, tax rates or industry benchmarks. Rounded figures below are RWF/month, not forecasts.

| Hypothetical net margin | Revenue required for RWF 2m net profit | Revenue required for RWF 5m net profit |
| --- | ---: | ---: |
| 20% | 10,000,000 | 25,000,000 |
| 40% | 5,000,000 | 12,500,000 |
| 60% | 3,333,334 | 8,333,334 |

Round required revenue upward when budgeting. At a validated collected monthly price `p`, a rough required payer count is `ceil(required recurring revenue / p)` before allowing for churn, noncollection, discounts and capacity limits. Test whether that many eligible reachable customers exist; no Rwanda market size or willingness to pay was measured here. The ambition may require a different customer mix or later geography, or may be unattainable on the selected model. Do not plan a regional expansion before validating its economics and obligations.

Per-customer contribution must include infrastructure/OCR/storage, payment costs and allocated onboarding/support labor. Reassess after volume grows; more PDFs can increase support/backups faster than revenue. Include tax/legal/accounting advice and provider/store fees using actual current quotes when purchasing, not invented percentages. Track founder labor and development time even if cash pay is postponed. Keep personal investment returns outside the business scorecard.

Do not scale advertising from a speculative lifetime value. Start with a fixed affordable loss cap, then compare acquisition cost with observed collected contribution/renewals. Require positive expected incremental contribution under a conservative case, with finite approved payback, before increasing a channel. No contribution data means a learning experiment, not a justified growth budget.

## Data ownership, consent and regulatory questions

An investor's account is personal by default. Buying/sponsoring a software license, an institution logo or importing a broker-issued PDF does not grant the institution access to that person's other records. Existing platform-owner administration exposes limited cross-account summaries; never give that role to a broker. Managed hosting does not imply the operator cannot read plaintext. Define controller/processor roles, sample rights, staff/support access and purposes per actual arrangement.

Any future synchronization needs a supported interface and separate scoped, revocable authorization for institution/accounts, data kinds, period, direction and recipients; read-only first. Do not collect broker passwords/OTPs or reuse Struct session cookies as partner credentials. Check grants before retrieval and commit, preserve source versions, route discrepancies through review, provide disconnect/export and explain retained/exported copies. No live integration or complete institutional tenancy is implemented. [Integration principles](architecture/BROKER_INTEGRATION.md).

Legal verification remains activity-specific: Rwanda controller/processor registration and responsibilities, lawful processing, rights/retention, actual provider/subprocessor locations, overseas storage/transfer authorizations/contracts, incident handling, confidentiality and permitted financial-service claims. Official sources describe obligations; code/tests do not establish compliance. [DPO obligations](https://dpo.gov.rw/dpp-law/obligations-of-the-data-controller-and-the-data-processor), [transfer/storage provisions](https://dpo.gov.rw/dpp-law/sharing-transferstorage-and-retention-of-personal-data). Detailed questions remain in [Rwanda assessment](security/RWANDA_DATA_PROTECTION.md).

Before institutional work, verify the counterparty's actual current license/activity scope and authority to provide each sample/data flow; a directory entry is neither a partnership nor approval of Struct. [CMA directory](https://www.cma.rw/licensing/licensees). Distinguish portfolio software, advice, execution and custody; get qualified advice on the proposed service instead of assuming a software exemption. Store financial classification, identity, deletion/privacy and later billing rules also require the [release checklist](RELEASE_CHECKLIST.md); recheck official policies at each submission/monetization decision. No licensing workflow or regulatory filing is created here.

## Technical prerequisites and deferred scope

| Build only when justified | Required evidence first |
| --- | --- |
| More document layouts/securities | Authorized representative corpus, independent recognition/mapping, supported-field tests and ambiguity rejection. Reuse the existing parser port; registry only if actual adapter growth warrants it. |
| Dividend history, equity corrections/sales/partial disposals | User need, explicit expected/received evidence, tax/fee/currency and lot allocation policy, auditable versioned backend commands. Evidence-only statement labels do not implement accounting. |
| Total return, TWR/MWR/XIRR, benchmarks, tax-grade reports | Complete dated cash flows/disposals, corporate-action/valuation history and validated conventions. Preserve existing fee-aware unrealized return and annual report limitations. |
| Ladder/reinvestment/scenario/concentration tools | Repeated investor use case, verified terms, explicit non-guaranteed assumptions and financial tests. Do not fabricate future instruments, prices, dividends or rates. |
| More offline functionality | Demonstrated read-only need, secure account-bound cache, stale labels and erasure. Offline writes require idempotency, conflict handling and session policy; don't store finances in plain preferences. |
| Institution workspaces, APIs, integrations or white-label portals | Paid/authorized pilot, trusted resource scope, lawful data model, revocation, audit, migration/isolation tests and support economics. Do not repurpose personal ownership IDs. |
| Hosted parsing / private object storage / queues / additional services | Real workload/cost/isolation measurements and tested security/retention/migration plan. Base64 PostgreSQL storage and local OCR remain until evidence justifies change. |
| Billing, paywalls, attribution/marketing infrastructure | Validated paid job/channel, consent/privacy/store rules, affordable operations and separately authorized implementation. Not part of this planning pass. |
| Brokerage execution, custody, settlement, licensing or trading terminals | A separate independently justified regulated-business study. Never a default Year-5 feature checklist. |

Retain the current KMP modules, shared branding/UI, Spring services, PostgreSQL/Flyway, authentication and direct constructor/platform patterns. Keep financial rules separate from document layout/UI and backend writes authoritative. No microservices, event-sourced rewrite, message broker, enterprise policy engine, new auth provider or full multitenancy is justified by this plan. Preserve existing records, original PDFs, IDs and old-client behavior through additive changes and data-preservation tests.

## Major risks and explicit continue / narrow / stop criteria

| Risk | Decision rule |
| --- | --- |
| Founder-only usefulness / feature accumulation | After 10–20 task observations, prioritize repeated blockers. If two small iterations do not improve independent task completion or repeat use, narrow the use case before adding tools. |
| Financial errors, privacy leaks or data loss | Stop affected imports/rollout, preserve evidence, investigate/correct transparently and verify recovery; get legal incident advice as needed. Safety cannot be offset by more users or high average accuracy. |
| Weak retention or episodic need | Inspect R90/R180 and task reasons, not daily streaks. If return is absent, reduce scope/cost or reposition as occasional document/report utility; don't manufacture notifications to create engagement. |
| No willingness to pay | After two time-bounded offers with engaged users and no commitments, stop payment-feature construction and revisit the job/segment/price. Real renewals outrank positive interviews. |
| Small reachable institutional market / slow procurement | Use actual discovery/budget cycles and eligible customer count. No written owner/scope/sample authority means no custom integration build. An inability to reach required payer counts invalidates the profit model. |
| Custom work disguised as SaaS / customer concentration | Track per-customer delivery/support and renewal economics. Reject or separately price work that cannot be supported within capacity. Stress-test losing the largest customer before hiring or increasing fixed costs. |
| PDF format drift / platform behavior / backend cold paths | Maintain held-out fixtures and release/device checks; measure errors/cost by layout/platform. Pause support claims for unvalidated formats; don't send files to AI/cloud OCR to hide uncertainty. |
| Runway, founder burnout or investment-capital erosion | Stop an initiative at its approved cash/time cap unless new evidence justifies an explicitly revised cap. Maintain a sustainable support schedule and security ownership before expanding commitments. |
| Regulatory/provider/data dependency | No real-data pilot or new regulated activity until the relevant permissions/roles/locations and operating responsibilities are verified. Preserve export/offboarding options; avoid single-partner dependence. |

## Quarterly decision record and verification boundary

Record: review date; phase/cohort; actual metrics and denominators; changes shipped/deployed versus source-only; interviews/task evidence; revenue/cash/expense/profit; founder hours; risks/incidents; budget remaining; continue/narrow/stop decision; the next smallest experiment; responsible person and next review date. Keep personal/customer details in approved private records, not Git. Unknown metrics remain unknown until measured.

This update changes planning documentation only. Existing code/tests were inspected; no feature, billing, marketing tool, broker API, trading/custody operation, tenant infrastructure, account, deployment or data collection was added. Prior local test/build evidence is in VERIFICATION.md and is not presented as a fresh production benchmark or commercial result. The next best outcome is a safe useful free release and trustworthy learning, not completion of a five-year feature list.
