# Target architecture: grow the existing modular monolith

## Constraints

One Spring Boot deployment, one existing PostgreSQL database and the current KMP clients remain the default. No new microservices, message broker, DI framework, auth provider or storage service is needed for this foundation. Keep the existing package roots, routes, IDs, Flyway history and synthetic fixtures.

## Logical boundaries

These are ownership boundaries, not instructions to create a Gradle module for every row.

| Boundary | Current home | Responsibility and allowed dependencies |
|---|---|---|
| Financial domain | Existing `core` financial files | Terms, positions, per-currency valuation, scheduled cash flows, explicit rounding policy; no UI, broker SDK, file picker or authentication globals |
| Mobile application | `PortfolioModel` and app-side import/report coordinators | User intent, validated review, retry/cancellation, refresh and presentation state; calls ports rather than embedding extraction algorithms |
| Document adapters | Native engines, TypeScript parsers, `EngineDocumentParser` | PDF/text/OCR → proposed record and warnings. Interchangeable behind `InvestmentDocumentParser`; never persist an investment themselves |
| Transport adapters | `PortfolioApi`, native HTTP engines | Origin, cookies, bounded responses, errors and wire DTO compatibility |
| Platform UI | `app:shared`, Android/iOS hosts | Screens, accessibility, localization, secure vault, permissions and native file/notification actions |
| Identity/account access | Existing Spring auth/security services | Trusted account context and authorization. No client-supplied owner/tenant may override it |
| Financial application services | Existing Spring portfolio/pending/receipt services | Authoritative validation and state transitions, transactional persistence, idempotency and eventual durable audit |
| Documents/provenance | Existing Spring DocumentService | Owner/hash access, public issuance identity, original bytes and future parser/version linkage |
| Broker integrations | Future adapters inside the same Spring app | Broker-specific import/contract mapping into the same validated commands; no broker-specific arithmetic in the domain |
| Market/reference data | Existing market services | Public reference data kept separate from private holdings and private-derived observations |
| Reporting | Current client reports, later server snapshots when justified | Read models with explicit date/scope/provenance and valuation assumptions; not a new write source |

The existing `core` module also contains Ktor transport and mutable JSON compatibility values. Moving its financial files does not make the entire module a pure domain library. Split a dedicated published financial artifact only when a second real consumer needs it; the separate Spring checkout must not import source through a developer's absolute path.

## Incremental financial contract

1. Preserve current wire fields and add fixtures comparing backend canonical values with KMP/JS results.
2. Define units and policies explicitly: currency scale, gross/net coupon, buying fees, accrued interest, percentage versus fraction, rounding stage/mode and date zone.
3. Introduce typed financial inputs at the edges, replacing JSON maps one use case at a time. Persist/return canonical backend values. Treat parser output as untrusted proposals.
4. Keep received cash separate from a schedule and from an automatic due-date assumption. A future statement-verified receipt needs an explicit evidence type, not a renamed boolean.
5. Resolve month-end/non-divisor schedule parity using published-date fixtures before changing formulas. Never replace confirmed BNR coupon dates with an inferred schedule.
6. Keep uncertainty visible: missing quote ≠ zero price; unavailable history ≠ no transactions; public bond terms ≠ personal allocation.

A future parser result should carry parser ID/version, document kind, original document digest, warnings/confidence and proposed financial records. The current `JsonRecord` output is retained for compatibility; broker-specific parsers should implement the new port rather than dispatch arbitrary simulation commands.

## Server-side import and audit evolution

Keep the current upload endpoints while adding a versioned, additive import operation when backend work is undertaken:

- An account-scoped idempotency key and source digest identify an import attempt durably.
- Upload completion establishes a stored source, not a completed investment.
- The confirm command validates the target record/version and writes the financial mutation, provenance link and audit event in one DB transaction.
- A retry returns the stored result. Network timeouts must not require guessing whether a financial write succeeded.
- Audit records capture actor/account, request/import ID, action, affected entity/version, timestamp and the necessary changed financial fields. Exclude credentials, raw PDFs and OCR text from logs. Set access and retention deliberately.
- Prefer optimistic version checks for edits, keeping existing duplicate constraints as a second defence.

This is a small transactional audit/import mechanism, not a requirement for event sourcing, a double-entry ledger or distributed sagas. A full accounting ledger becomes a separate domain need if cash movements, custody or execution are actually introduced.

## Future tenant boundary without implementing enterprise tenancy now

Today `owner_id` means an individual authenticated account. Do not overload it with a club name or accept an organization ID from a client as authorization.

When a real paid team pilot justifies it:

1. Add a personal workspace per existing account with an additive Flyway migration and deterministic backfill.
2. Add membership/roles and a trusted `AccountScope`/`WorkspaceScope` resolved by the server for each request.
3. Scope repositories, uniqueness constraints, document access and audit reads by that scope. Verify account A cannot access B through list, get, download, mutations, reports or background jobs.
4. Keep public issuance/market data explicitly public; private-derived prices need a separate sharing decision and permission.
5. Preserve old API behaviour for each account's personal workspace during rollout and test rollback after new writes.

Do not create workspace tables, enterprise policy engines, SSO or billing during this audit. Documented boundaries and trusted owner scoping are enough for today's individual investor app.

## Low-cost operation

Keep local deterministic parsing, existing bounded native renderers, small DB pools and cached public quotes. Measure per-account document bytes, query count/latency and scheduled-job cost before buying infrastructure. PDF base64 in PostgreSQL is workable at current scale but increases storage/backup overhead; introduce private object storage only when measured growth justifies migration and retention/access semantics are designed.

Use existing CI/Testcontainers patterns and synthetic golden fixtures. Prove restore and access isolation before expanding distribution. Do not send financial PDFs to an LLM or third-party parsing service by default.

## Product staging

The canonical [product roadmap](../PRODUCT_ROADMAP.md) sequences current reliability work and records gates for individual-investor, institutional and partner capabilities. It does not schedule a rewrite or implement those products in this audit. The boundaries above describe how to evolve the existing application when those gates are met.
