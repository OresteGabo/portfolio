# KMP packages and modules

INZIRA uses a pragmatic feature-oriented app structure with shared infrastructure and financial rules. It does not require a Gradle module per feature.

## Modules

| Owner | Responsibility |
|---|---|
| `:core` | Shared financial/value types and HTTP infrastructure, separated by package. Android/JVM/iOS implementations provide native engines and decimal primitives. |
| `:app:shared` | Compose presentation, session/lifecycle orchestration, feature workflows and native capability adapters. |
| `:app:androidApp` | Android entry point, Firebase delivery, widgets, intents and native host integration. |
| `app/iosApp` (Xcode) | SwiftUI/UIKit host, share import, widgets and Live Activity extensions. |
| `:server` | Optional Ktor starter; not a mobile dependency or the production Spring backend. |

The Gradle graph stays `androidApp → shared → core`, with the optional server also using core. No new modules, plugins or runtime dependencies were added. Split a module later only for demonstrated reuse, build isolation or an independently enforceable dependency boundary.

## Where to look

All packages start with `dev.orestegabo.portfolio`:

| Package / module | Contents |
|---|---|
| `network` / core | `PortfolioApi`, `PortfolioDataApi`, origin selection, public equity quote transport/DTO validation and native HTTP engines. |
| `features.transactions` / shared | Internal account cash-ledger workflow/state. |
| `features.shares` / shared | Internal pending share-order workflow/state. |
| `features.documents` / shared | Internal statement evidence/reconciliation workflow/state. |
| `investments` / core | Existing financial rules, snapshots, JSON compatibility values and diagnostics. These shared values do not depend on app feature implementations. |
| `investments` / shared | Existing session/model composition, screens, authentication, imports, reports, settings and other legacy owners awaiting cohesive feature extraction. |
| `design` / shared | Existing reusable theme, colour and native design adapters. |

The two `investments` rows are different module owners despite the same Kotlin namespace. Dependency reasoning must retain that distinction:

```text
shared:investments (composition)
    → shared:features.{transactions,shares,documents}
        → core:network
            → core:investments (shared values/diagnostics)
```

There are no peer dependencies between the three migrated feature workflows. Keep feature-owned code with its feature; extract a shared component when real consumers justify it. Do not move financial policies into a generic UI utilities package. The remaining legacy package is a migration boundary, not a recommended home for every new feature.

## Source sets and visibility

- Mirror package paths in `commonMain`, platform `*Main`, `commonTest` and platform tests. HTTP expects and Android/JVM/iOS actuals now all live in `network`; move an expect and all actuals together.
- Network tests mirror that package. Cross-feature workflow tests live in shared `features`; session/model integration tests retain their existing owner.
- Workflow implementations remain Kotlin `internal` (module visibility, not package-private). Session cookies remain private. The platform engine factory is now internal because only core uses it; app-facing data/client types and the Android host's `PlatformBuild` remain public where cross-module access requires it.
- Package moves change Kotlin qualified names; all repository callers are updated. App IDs, manifest entry points, storage keys, database/backup formats, URLs and backend payloads are unchanged. No compatibility aliases hide the new owners.

## Checks and incremental migration

Run `python3 scripts/check-kmp-packages.py`. CI runs it beside the existing colour/engine checks. It checks project-owned source/test package paths, the current Gradle module DAG, internal workflow visibility and the migrated packages' import rules. It forbids core investment rules importing transport and feature workflows importing the composition root or unrelated features. This scoped source check is not a complete Kotlin semantic dependency analyzer.

Move bonds, authentication, import or settings slices when their ownership is independently characterized. Update all imports and platform counterparts, run affected KMP targets/tests, and keep mechanical moves separate from behavior changes. Existing audit documents describe their recorded baselines; use this guide for current package locations.

[Verification and move scope](../quality/KMP_PACKAGE_ORGANIZATION.md).
