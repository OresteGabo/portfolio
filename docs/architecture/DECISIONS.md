# Implementation decisions — 30 October 2026

## ADR 1: reuse `core` for existing pure financial rules

**Problem:** Bond tracking was already in `core`, but position aggregation, quote valuation, monthly goals and JSON-to-terms mapping lived in the Compose module. They were hard to reuse/test without building the presentation layer.

**Implemented:** Moved `PortfolioSnapshot.kt`, `PortfolioValuation.kt`, `MonthlyInvestmentGoal.kt` and their existing tests into `core`. Added `InvestmentDates.kt` for shared date helpers and the existing `JsonRecord.terms()` bridge. Existing package names and call sites remain compatible. The small JSON calculation helpers used across modules are now public. Localization, device clocks, UI and annual-PDF rendering remain in `app:shared`.

**Preserved:** All formulas, status/currency filters, quote fallback, cash-cost conventions and monthly aggregate semantics for valid records. No backend/schema/wire/data change and no new module/dependency.

**Limit:** Financial projections still use mutable `JsonRecord` and Double. `core` also contains networking; it is not yet a standalone pure-domain artifact. Strong DTOs and decimal policy require a versioned compatibility plan, not a broad map-to-class rewrite.

## ADR 2: parser-specific port, legacy engine adapter

**Problem:** `InvestmentEngine.run(command: String, ...)` handles both parsing and projection. An import workflow should not need to know the simulation engine's command protocol.

**Implemented:** Added `InvestmentDocumentParser` in `core` with parse/progress/cancel. `EngineDocumentParser` wraps the existing platform engines using their unchanged `pdf` command. `readPurchasePdf` now has a parser-specific entry point; its original engine overload delegates through the compatibility adapter. Tests exercise a parser without a simulation engine and verify cancellation/progress/lifecycle ownership.

**Preserved:** Native PDF/OCR policy, same TypeScript rules, broker detection, review validation, limits, upload/storage API and UI engine lifetime. The adapter never destroys a UI-owned engine. Alternate broker/native parsers can be supplied to the pipeline without replacing the whole engine.

**Deferred:** A broker parser registry, typed parse-result/provenance schema, model-wide service extraction and separate projection port. Only add adapters for actual supported documents; the port does not claim those future formats work today.

## ADR 3: reject malformed bond terms before schedule loops

**Evidence:** Legacy month interval calculation rounds `12 / frequency`; very large frequencies can make the interval zero and prevent a loop from advancing. Arbitrarily long terms also allow excessive work. JSON integer conversion silently truncated fractional frequencies.

**Implemented:** `BondTerms` rejects frequencies outside 1–12, nonpositive/reversed or over-100-year terms, unbounded first-coupon anchors, non-finite/negative principal and invalid rate/tax ranges. The JSON bridge rejects malformed/fractional frequencies and returns unavailable terms through its existing nullable boundary. Tests verify these boundaries and normal results.

**Compatibility:** Zero principal remains allowed for calculation compatibility; persisted purchase validation remains stricter on the server. The existing backend-accepted frequency range is retained. Confirmed-date precedence and normal inference formulas are unchanged. This prevents malformed client inputs from hanging rather than reinterpreting persisted records.

**Not resolved:** Non-divisor frequencies and repeated month-end stepping still differ across implementations; explicit published schedules remain preferable. Changing those rules requires agreed business semantics and cross-runtime golden fixtures.

## ADR 4: scheduled coupons are not received cash

Added `BondTracking.scheduledCouponsToDate` and switched visible Scheduled to date usages to that name. The existing `couponsPaid` field is retained as a compatibility value with the same numerical result. No stored receipt is created by this calculation. The existing manual/automatic receipt model is preserved and its verification limitations are documented.

## ADR 5: small Android production fixes, no dependency upgrade sweep

Release lint reproduced a MissingPermission error for notification delivery and API-26 notification settings constants on supported API-24 devices. Added an explicit Android 13+ POST_NOTIFICATIONS check while retaining delivery failure handling. Notification settings now use the modern action only on API 26+, with application-details fallback below it.

These are platform boundary fixes. Lint validates permissions/API reachability; no physical notification delivery was claimed. Existing provider configuration, tokens, account routing and permission prompts were not replaced.

## ADR 6: assessment first; no backend deployment or data rewrite

Spring backend source was inspected and its pure parser/compatibility tests executed, but no backend source, Flyway migration, account record, secret, live database or deployment was changed. Priority backend findings have explicit acceptance criteria in [RISKS_AND_DEFERRED.md](RISKS_AND_DEFERRED.md).

No microservices, message bus, object-storage migration, auth replacement, billing, organization model or brokerage functionality was added. Pre-existing working-tree changes were preserved; this audit does not implicitly commit or publish them.

## Verification boundary

See [VERIFICATION.md](../VERIFICATION.md) for executed commands/results. Existing financial aggregation tests moved with their implementation, so per-module test counts change even when coverage is preserved. New tests cover parser substitution/cancellation and malformed schedule inputs. TypeScript bundle fixtures remain unchanged.
