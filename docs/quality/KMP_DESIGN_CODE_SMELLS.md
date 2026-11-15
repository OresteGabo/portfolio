# Design patterns and remaining code smells

Reviewed against `f00b42b` on 15 November 2026, including the existing working tree. Scope: KMP migration/import coordination and related architectural boundaries. Backend and website are excluded. Earlier unfinished changes are not included. This is a targeted review with concrete corrections, not a repository-wide assertion that all smells are resolved.

## Findings and corrections

| Priority | Evidence | Simplest correction / decision |
|---|---|---|
| High | `GuestMigration.prepare`: each receipt used `purchases.firstOrNull` to resolve a parent title. With P purchases and R receipts, preparation performed up to P×R comparisons. The existing review cap is 40,000 rows. | Extract presentation `migrationRows(GuestPortfolio)` into `GuestMigrationRows.kt`. Build an ID→name index once, then project rows in one pass per collection. Keep first-match semantics for duplicate IDs, empty names for missing parents, source references and order. |
| High | `GuestMigration.prepare`: each undated document searched prior migration items with `firstOrNull`. With D documents and H history items this can perform D×H comparisons. | `migrationDocumentDates` builds one history index. Preserve local date precedence, the first historical entry even if its date is blank, duplicate row overwrite behavior, and the existing date validation later in transfer. |
| Medium | `GuestMigration.prepare`: record-shape construction and display metadata were embedded in lifecycle/account authorization coordination. Several mapped lists were concatenated into intermediate lists. | Move only review projection to a small mapper file and use `buildList`. Keep fetching, authorization checks, row count/ID checks, revision, selection, consent, failure state and uploads in `GuestMigration`. No service interface or factory is needed for in-memory projection. |
| Medium | `validatePurchasePdfReview` in `PdfReviewPolicies.kt`: compound `||` and `&&` expressions mixed statement bypass, bond-only admission and statement-target matching. Changes required re-deriving precedence. | Explicit ordered guards: statements use their own review; enforce bond-only admission; return when there is no target; allow public issuance for a bond target; otherwise apply existing financial/identity matching. Preserve both error messages and their priority. |
| Medium, retained | `InvestmentApp.kt` route `when` combines modal/document/security/guest/detail state precedence and nullable assertions. This file is already modified by unfinished work. | A future route-state change needs navigation/security characterization first. Do not replace the chain with a State hierarchy in this isolated migration/import refactor or overwrite unfinished work. Its branches encode real precedence, not interchangeable algorithms. |
| Medium, retained | `GuestMigration.kt`: `MigrationRow.kind` and `MigrationOutcome.status` use strings also exchanged with the migration API and UI. | Avoid a broad enum/value-class migration here: it would require deliberate unknown-value and wire mapping policies across the API adapter and review UI. Keep explicit supported-value validation and existing DTO fields. |
| Low, retained | `readPurchasePdf` has eight arguments, including three callbacks, parser/engine compatibility overloads and an injectable clock. | These are cohesive import hooks with defaults and named callers. No request/context bag is introduced just to shorten the signature. Revisit only if callers repeatedly pass the same independently evolving configuration. |
| Low, retained | `ImportJournal.begin/update` use `Clock.System` and random IDs internally. | They are concrete environment dependencies, but receipt metadata is not financial policy. Clock/ID injection may help future deterministic journal tests; no new factory is justified for this change. |

Cash labels retain `displayDate` and its existing app-language context. The row mapper is presentation code with no data I/O, not a locale-independent domain conversion; the date-default mapper only selects stored strings.

The indexed projections use O(P + R + holdings + documents + ledger) work for review rows and O(H + rows) for date defaults, with O(unique purchase IDs + unique historical IDs) extra index storage. This describes traversal structure, not measured timing. Do not sort or deduplicate review rows while building indexes; their order and source identities are observable. Duplicate lookup IDs keep the first entry; duplicate document-row keys retain the original map's last-write behavior.

## Pattern decisions

| Pattern | Decision and evidence |
|---|---|
| Mapper | **Introduced as mapping functions**, not a mapper interface/class hierarchy. `migrationRows` and `migrationDocumentDates` isolate display projection from remote/account lifecycle. `migrationRecord` remains the separate outbound whitelist: review metadata is not an upload DTO. |
| Strategy | **Retained as existing function injection** in `readInvestmentPdf`: `validateReview` selects admission policy without duplicating picker/parser/cancellation handling. Reject a class registry for the small closed set of document kinds. |
| Adapter | **Retained** `EngineDocumentParser`: its small size is not a Middle Man smell. It maps `parse(bytes)` to the engine's `run("pdf", pdf = bytes)` while preserving engine-owned cancellation/progress/lifecycle. `ApiGuestMigrationRemote` likewise translates account-bound operations into HTTP routes/chunks; it does more than forward an identical API. |
| Decorator | **Retained** anonymous delegation in `GuestPdfImport.select` to choose private picking and validate the envelope before parser execution. No reusable decorator framework is added for these two workflow-local behaviors. |
| Repository | **Retained** guest read/import capability boundaries. Native storage and account HTTP remain separate; projection does not fetch data or own storage. |
| State / sealed models | **Retained** existing `SessionState` sealed operating modes. **Rejected** a new State pattern for migration: its consent, revision, account ownership and cancellation checks remain explicit and tested. A pattern substitution would expand regression scope. |
| Observer | **Retained** Compose state observation in the reviewed workflows. No parallel Flow/event-bus layer is added solely to demonstrate Observer. |
| Factory | **Rejected** for `MigrationRow`: construction is simple record projection. A factory interface would add indirection without hiding complex lifecycle or interchangeable construction. |
| Generic ID / DTO wrapper | **Rejected** for this slice. Do not rename persisted string variants or merge bond/share fields into a flag-driven universal financial model. |

No existing abstraction was removed merely because it is small. The removed design burden is nested linear searching, intermediate concatenation and opaque boolean composition, not a useful repository/adapter. No concrete evidence in the reviewed boundaries justified introducing inheritance, a plugin system, new DI framework or one-interface-per-class structure.

Feature-specific JSON reads in the mapper are intentional knowledge of the existing representation; inventing methods on generic `JsonRecord` for migration titles would spread migration policy into a shared transport model. Likewise, the call chain `snapshot.portfolio.purchases` is straightforward input traversal, not a reason for forwarding wrappers. The remaining multi-role navigation state is documented rather than disguised by another facade.

## Regression evidence

Two preparation characterization tests ran before row-mapping changes. They freeze all five row kinds, ordering, synthetic cash IDs, parent links, retained record references, unchanged source data, duplicate bond lookup IDs and orphan receipt labels. Existing migration tests cover selection/consent, cancellation, account switches, revision changes, transfer order, PDF retention and acknowledgment retries.

Before date indexing, an additional preparation test freezes local-date precedence, first duplicate historical date, and a first historical entry without a date. Two bounded-list-read tests then ensure receipt and document projection cannot silently return to per-row scans; they assert traversal bounds rather than wall-clock timing. Existing PDF admission cases cover all supported kinds, unknown/empty kinds, guest admission and financial matching; one added test freezes bond-only error priority when both checks could fail.

## Verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`; Android SDK: `/Users/muhirwagabooreste/Library/Android/sdk`.

Before row projection changes:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*MigrationPreparationTest' --tests '*PdfReviewPolicyTest' --tests '*PdfImportPipelineTest' --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 14s`; log `/tmp/inzira-smells-characterize.log`. After the row extraction, the same selectors plus `*GuestMigrationTest` and `*MigrationTransferCharacterizationTest` pass in 10s; log `/tmp/inzira-smells-focused.log`.

Before date-indexing changes:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*MigrationPreparationTest' --tests '*PdfReviewPolicyTest' --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 3s`; log `/tmp/inzira-smells-date-characterize.log`.

Full working-tree verification:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 1m 5s`; **700 host test executions** (107 core JVM, 108 core Android host, 485 shared host), no failures/errors/skips. Android build/lint and both iOS compilations pass. Log `/tmp/inzira-smells-working-build.log`.

The staged six-file tree was exported with `git write-tree` / `git archive`, excluding prior unfinished work. The full command above was repeated with `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk` and `--project-dir "$smells_export"`.

Export `/tmp/inzira-smells-index.KPR6eA`: exit 0, `BUILD SUCCESSFUL in 1m 33s`; **689 host test executions** (107 core JVM, 108 core Android host, 474 shared host), no failures/errors/skips. Android build/lint and both iOS compilations pass. Log `/tmp/inzira-smells-index-build.log`. Eleven extra working-tree shared tests belong to excluded earlier work.

Shared native verification:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$smells_export" :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 1m 5s`; **493 shared native tests**, no failures/errors/skips. Log `/tmp/inzira-smells-index-native.log`.

Static/script commands in working and isolated trees:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Both pass package ownership/module DAG and design-color checks, **16 Python and 84 Node tests**. Final whitespace and staged-source/export comparison checks run before commit; only this report receives final result updates afterward.

Baseline compiler/lint warnings remain. No configured Detekt/ktlint task was found. No runtime performance benchmark, physical-device UI run, connected instrumentation, live transfer, account mutation or deployment was performed. No full core-native execution is claimed; the separately documented pre-existing share-cost exact floating-point equality issue is outside this change. Financial arithmetic, persistence formats, API routes, matching semantics, transfer acknowledgments and test tolerances remain unchanged.
