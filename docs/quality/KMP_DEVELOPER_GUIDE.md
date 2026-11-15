# KMP developer guide and review checklist

Scope: the committed INZIRA mobile application. Start with the [final quality review](KMP_FINAL_MAINTAINABILITY_REVIEW.md) for open risks, and the [pending-change integration review](KMP_PENDING_CHANGES_REVIEW.md) for the subsequently verified responsibility extractions.

## Dependency and ownership conventions

Android host → shared Compose app → core; the Swift host embeds the shared framework. Core contains financial/value rules **and** a separate transport package, so it is not advertised as a pure domain module. Mobile modules must not depend on the optional server project or website implementation.

- `core/.../investments`: deterministic calculation, validation and record projection; no Compose/lifecycle, Android SDK or network implementation imports. Network contracts live in `core/.../network`, depending only on the permitted shared value/diagnostic types.
- `app/shared/.../features/{transactions,shares,documents}`: existing account workflow/state owners. Keep feature internals `internal`; use the existing data/session capability boundaries and explicit constructor injection. Do not create an interface for every concrete helper.
- `app/shared/.../investments`: existing screens, model/session composition, import/report/guest adapters and presentation. This broad legacy package remains; new cohesive workflows can use feature packages incrementally. Do not perform a package-wide move merely to satisfy this guide.
- Native source sets: filesystem, protected storage, file access, authentication and native PDF/OS services. Reusable financial rules belong in commonMain; ordinary injected capabilities or existing expect/actual ports are preferred over new global service locators.

`PortfolioModel` owns the session/action lifetime. Feature owners expose read-only observable state and forward commands through the existing session/guest gate. No secondary global scope, independent authenticated client or detached write path. Preserve generation/account checks, cancellation, write completion versus refresh failure, and explicit resource cleanup.

Compose displays state and forwards intentions. Pure business decisions belong in tested functions, not composables. Hoist state to the lifetime that owns it; keep local editor state local. Use stable list identity where records have IDs. Do not add `remember`, caching, reducers or inheritance without a demonstrated need. A large translation/vector table is not a God Class; review independent reasons to change, state coupling and side effects rather than imposing a line cap.

## Adding a feature

1. Identify its presentation, deterministic rules, data access and genuine platform capabilities. Find the nearest existing workflow instead of introducing a new architecture framework/module.
2. Put reusable calculations/admission rules in commonMain; keep HTTP/JSON/persistence adapters at existing boundaries. Preserve wire identifiers, status strings and stored schema unless a migration is explicitly part of the feature.
3. Give any new state/workflow a clear lifecycle owner and the smallest useful dependencies. Do not give a data-only feature cookie/session destruction or unrelated feature access.
4. Add behavior tests for success, rejection, cancellation, account switch and post-write refresh failure where applicable. Prefer injected storage/parser/clock/dispatcher capabilities and synthetic records over broad mocks.
5. Verify shared host/native targets plus any changed native adapter. Compile Swift hosts/extensions when their integration changes. New modules need a concrete ownership/build reason and an update to the boundary allowlist.

## Changing financial rules safely

- Identify the authoritative rule and supported units first: whole recorded shares versus order-entry increments; rate fractions versus display percentages; original purchase cost versus remaining cost; currency-separated balances; scheduled coupons versus confirmed cash receipts; estimates versus executed purchases.
- Write expected-value tests **before** changing arithmetic. Use independent hand calculations or an independent oracle, including cent boundaries, repeated sales/final remainder, tax/charges, zero/negative states where valid, large values and reporting-date boundaries. Do not compute the assertion's expected amount using the implementation under test.
- Preserve the existing decimal boundary and admission rules across JVM and Native. Do not silently replace Double, change rounding/tax/day-count conventions or infer an FX conversion. Ambiguous policy needs a documented decision, not a guessed formula.
- Trace parsing → normalized input → persisted/API records → projection → UI/report formatting. Keep raw evidence where supported. Test serialization and replay/duplicate behavior, not only a displayed formatted total.
- Run financial/domain tests on JVM/Android and iOS Native. Record any intentional numeric/behavior change separately, with evidence. Review [precision risks](KMP_FINANCIAL_PRECISION_AUDIT.md) and [domain conventions](KMP_DOMAIN_MODEL_REVIEW.md).

## Adding a supported PDF format

Supported account dispatch currently includes bond confirmations, equity contracts, statements and bond-issuance documents. Guest admission is narrower; a parser classification does not authorize a save or publication.

1. Create sanitized/synthetic text fixtures and representative generated native PDFs. Include valid, malformed, incomplete, ambiguous and duplicate cases; never check in a user's document or log extracted account text.
2. Add format identity/field interpretation in `web-logic/source` using the existing parser support, leaving document acquisition/OCR/platform adapters separate. Classification identifies a layout, not authenticity. Keep source document values, missing-field/warning semantics and conservative rejection behavior.
3. Update dispatch and Kotlin review/admission policy only where the new format requires it. Preserve explicit review, permissions, duplicate financial-link checks, original-file identity and cancelled-result ownership. Test same content/new filename, same metadata/different bytes, retries, incomplete fields and rejected saves.
4. Rebuild and review **both** ESM and portable shipped artifacts, then run Node goldens and Android/iOS native extraction tests. Current `bundle-shared-logic.mjs` still obtains TypeScript from an external package directory; `sync-web-logic.mjs` additionally copies external sources/assets. There is no clean-checkout locked mobile regeneration gate yet (F03). Establish/review that toolchain before claiming reproducible new-format delivery; do not silently sync the entire website or overwrite mobile-specific fixes.
5. Retain native size/page/text/OCR limits and stream/security-scope cleanup. A logical cancellation check cannot preempt a synchronous OCR call. Verify the actual changed adapter as well as common dispatch tests.

## Maintained checks and limitations

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 scripts/check-kmp-ui.py
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest \
  :app:androidApp:lintRelease :app:androidApp:bundleRelease
```

The package checker rejects forbidden mobile module edges (even acyclic ones), core presentation-framework imports, common platform imports, package/path mismatches and migrated-feature reverse/peer imports. It is a source-text check for literal imports/project declarations, not full Kotlin/Gradle semantic analysis; fully qualified calls, reflection or dynamic build logic still require review. Tests cover allowed and rejected boundaries.

`.editorconfig` provides mobile-only four-space UTF-8/newline editing defaults, matching `kotlin.code.style=official`. It does not reformat existing files or constitute a formatter/lint pass. Generated build trees are not scanned by the package checker; declarative resources/vectors/translations have no complexity/length budget. No detekt/ktlint plugin or arbitrary file/function limit is introduced. Android Lint warnings remain visible; do not suppress them broadly to make reports look clean.

For native/device commands and actual results use the [final review](KMP_FINAL_MAINTAINABILITY_REVIEW.md). CI runs host/build/script checks; it does not automatically execute connected-device or native simulator suites. The device gate must validate executed critical journeys with `python3 scripts/check-kmp-ui.py --results app/androidApp/build/outputs/androidTest-results/connected/debug`; source inventory alone is insufficient. iOS simulator tests require an available simulator; see the integration review for the exact locally executed commands. Physical-device, VoiceOver/TalkBack and iOS visual verification remain separate manual work. Passing compilation does not replace native monetary/PDF/OS tests.

## Reviewer checklist

- One coherent change reason; no arbitrary splitting, unnecessary interfaces, peer-feature coupling or hidden environment dependencies.
- Authoritative business rule and independently calculated expectations identified; financial policy changes explicit.
- State, client, jobs and private bytes have owners; cancelled/old-account work cannot publish; retries cannot duplicate writes.
- Persistence/wire compatibility, duplicate evidence links and original purchase facts preserved.
- Shared/native boundaries and caller-specific failure/refresh behavior covered by tests.
- Shipped parser artifacts reviewed against their source; generated/toolchain limitations disclosed.
- Relevant host/native/UI tests, compilation, lint and static checks actually run; unavailable checks and remaining warnings recorded.
