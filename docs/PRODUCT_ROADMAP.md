# Struct Capital: product roadmap and implementation gates

Reviewed: 30 October 2026. This is a sequence of decisions, not a promise of dates or authorization to implement new features. The current priority remains a reliable, secure, low-cost, free application for individual investors in Rwanda.

[CURRENT_STATE.md](CURRENT_STATE.md) owns feature classifications; [TECHNICAL_DEBT.md](TECHNICAL_DEBT.md) owns findings and acceptance criteria. [Target architecture](architecture/TARGET_ARCHITECTURE.md) describes boundaries and compatible evolution. [Business ideas](BUSINESS_IDEAS.md) records commercial possibilities, not committed scope.

## Preserve the working product

Keep supported BNR personal/public issuance imports, supported equity purchase contracts, statement parsing/reconciliation, bond and equity positions, coupon estimates/acknowledgements, fee-aware unrealized equity returns, dashboards, goals, reports, document library/chat and Android/iPhone hosts. These are implemented capabilities to maintain, not a backlog to build from scratch. General broker coverage and total portfolio performance remain narrower than those individual features.

Keep Spring Boot, PostgreSQL/Flyway, existing session authentication, KMP modules, constructor/platform injection and native parsing/rendering. Keep the backend authoritative for financial writes. Retain IDs, wire compatibility, originals and stored financial values; any future migration needs additive rollout and data-preservation tests. Do not deploy the local Ktor sample as the financial API.

## Recommended next implementation steps

Each step is a separate reviewable change. No product implementation was performed in this inventory phase.

| Order | Work / reason | Completion gate |
|---|---|---|
| 1 | **Operationally verify the aligned statement contract (TD-01).** Supported statement upload and source reconciliation now pass isolated PostgreSQL tests; coordinate API/mobile rollout and verify deployment | Supported statement PDF upload, owner access, reconciliation, duplicate/retry and invalid-category tests pass against the real Spring API using disposable PostgreSQL. Existing bond/equity uploads remain compatible |
| 2 | **Resolve private-derived market-data sharing (TD-05).** Current public history can include prices derived from private contracts; direct upload bypasses an older disclosure | Explicit policy, server-enforced contribution/access behaviour, accurate disclosure and isolation/deletion tests. Confirm deployed behaviour before claiming the issue resolved |
| 3 | **Validate and fix scheduled-job transaction boundaries (TD-06).** Internal proxy bypass can weaken transaction-scoped locking | PostgreSQL tests prove lock/transaction lifetime and consistent concurrent daily/manual coupon updates, rollback and repeat execution |
| 4 | **Establish calculation agreement (TD-07 and P2 financial policy).** Working estimates must agree on supported frequencies, month ends, dates and rounding | Synthetic published-date, leap-year, maturity, secondary-purchase, non-divisor and Kigali-boundary fixtures compare KMP/JS/Spring outputs. Document units, tax/fee applicability and rounding. Preserve explicit BNR dates; do not rewrite saved records silently |
| 5 | **Make financial mutations durably explainable (TD-02–TD-04).** Existing constraints and local retry do not reconstruct edits or uncertain saves | Minimal server audit/provenance, durable import result/idempotency and versioned edits; tests for lost responses, concurrent retries, stale edits, rollback, deletion and account scope. Compatible old-client rollout, bounded retention and no raw PDFs/secrets in logs |
| 6 | **Close release and metadata gaps.** Validate current distribution dependencies (TD-15); return authoritative document timestamps/digests where authorized | Signed/device checks for PDF zoom/readability, small-screen onboarding, Open In/share, privacy, biometrics and notification settings; provider delivery and operational restore checks. Additive document metadata tests avoid invented timestamps and preserve duplicate detection |

Steps 1–3 are the most immediate known integration/privacy/consistency gaps. Calculation fixtures can be developed independently. Audit/concurrency work precedes promises of immutable historical reporting or institutional financial records. Use the existing test stacks and small backend changes, not a replacement application.

## Strengthen supported formats before widening coverage

The existing parser port and native engines are the starting point. For each additional broker/layout, obtain authorized representative examples, create redacted/synthetic golden fixtures, document supported fields and rejection cases, and implement an adapter that proposes the same validated financial commands. Keep broker-specific extraction separate from domain calculations and persistence.

Introduce parser identity/version, source digest, warnings and record linkage incrementally when adding a real adapter. Make regeneration of copied TypeScript self-contained and reproducible first. A generic parser marketplace, plugin runtime or new parsing service is unnecessary. Do not send financial PDFs to external AI services by default.

Review the unreachable Add share purchase form separately from a genuine equity edit/delete capability (TD-09). Confirm user need and navigation before wiring a form; edits need backend commands and history safeguards. Do not treat POST as an update.

## Individual-investor capabilities requiring a product decision

These are genuinely absent or partial, but not implementation scope for this audit. Prioritize from user evidence after the reliability work.

| Candidate | Prerequisites / scope boundary |
|---|---|
| Equity sales, disposals and realized gains | Dated proceeds, selling fees, lot-allocation policy and auditable changes; the existing sold flag is insufficient |
| Dividends and verified coupon receipts | Separate declared/expected/received states, currency/tax policy and evidence-based reconciliation; preserve manual/automatic acknowledgements as distinct from verification |
| Cash accounts, redemptions, FX and corporate actions | Define cash and holdings reconciliation, dated movements, exchange-rate provenance and corporate-action policy before expanding net-worth claims |
| Full portfolio performance, TWR/MWR/XIRR and benchmarks | Complete dated contributions, distributions, sales and valuation history. Keep existing fee-aware unrealized equity return; another chart cannot supply missing cash flows |
| Immutable year-end/tax-grade reports | Durable financial events/snapshots, explicit valuation/correction policy and applicable reporting requirements. Existing calendar-year/YTD PDF reports remain available with their stated limits |
| Fuller offline use | Start with validated read-only needs and account-protected storage. Queued financial writes require durable server idempotency, stale-edit/conflict policy and secure logout/account switching; no incidental financial database in preferences |
| Account recovery/MFA and synchronized import history | Assess access/support requirements separately. Keep functioning sessions/biometric unlock; a cross-device chat transcript is not a substitute for server financial audit |

Do not bundle all these into a single release. No new formulas, sale/dividend endpoints, local database or reporting promises were introduced by this phase.

## Deferred products and readiness gates

| Product / capability | Why deferred; gate before implementation |
|---|---|
| Institutional or investment-club workspaces | Need a real pilot, ownership model, membership/roles and reporting/approval requirements. Then add personal-workspace backfill and trusted server scope without changing existing individual ownership; test every read/write/document/job across scopes |
| Public financial APIs | Internal REST endpoints already exist. A supported external API needs a consumer contract, versioning, scoped credentials, rate/usage controls, revocation and audit. Do not expose app sessions as a partner API |
| Customer-authorized broker/bank integrations | Need broker agreement and supported interfaces, explicit consent, resource scopes, expiry/revocation, protected credentials and reconciliation. Customer passwords/session reuse is not an authorization model |
| Premium/institutional subscriptions | Validate a paid use case and support cost first; define entitlements before selecting billing infrastructure. Existing free investor functionality remains the baseline |
| Dedicated iPhone Share extension | Existing PDF Open In/native sharing remains. Separate extension UX/capability/signing work is optional; validate the developer account and current platform requirements when scheduled |
| Brokerage, execution, custody and payments | A separate operational/licensing/security and reconciliation domain. Portfolio tracking does not establish readiness to execute orders or hold customer money |

## Infrastructure deliberately deferred

No microservices, Kafka, Redis, event-sourced rewrite, auth replacement, enterprise tenant engine or new DI framework is justified by this audit. Stay with the existing modular monolith. Measure document storage/backup size, full-read query cost, pool saturation and latency before introducing private object storage, caches or asynchronous infrastructure. Test backup/restore and account isolation rather than assuming hosting configuration proves them.

Do not implement a complete enterprise schema now. Future scope should resolve trusted account/workspace context server-side; broker integrations stay adapters; financial rules remain independently testable; documents remain evidence rather than an alternative source of persisted truth.

## Risks and dependencies

The Spring source is a separate checkout; API-contract changes must be coordinated with both mobile and website consumers. Copied parser assets currently depend on sibling tooling. Operational credentials, signing, provider delivery, production schema/version, backup recovery, applicable financial policies and broker permissions were not verified by source inspection. None should be marked complete from a compile or a documentation change.

Use [TECHNICAL_DEBT.md](TECHNICAL_DEBT.md) to close findings with evidence and [VERIFICATION.md](VERIFICATION.md) to distinguish executed tests from tests that merely exist. This roadmap does not authorize deployments, production-data changes or collection of additional customer financial information.
