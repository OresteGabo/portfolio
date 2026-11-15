# KMP abstraction review

Reviewed against `f0bbef0` and the working tree on 15 November 2026. Scope: mobile Kotlin Multiplatform application; no backend, website, dependency or build configuration changes. Earlier unfinished refactors are excluded from this commit.

## Existing architecture and decisions

The module direction is Android host → shared app → core; iOS embeds the shared framework. Shared Compose presentation and a session ViewModel coordinate feature collaborators. Wiring is manual through constructors, callbacks, composition locals and `expect`/`actual` factories, with no Koin/Dagger container or dedicated DI integration task. Core contains financial rules and transport, in separate packages; it is not an entirely infrastructure-free domain module.

| Boundary inspected | Evidence and decision |
|---|---|
| Guest persistence | `GuestPortfolioRepository`, `GuestSnapshotStorage`, `GuestDocumentStorage` already describe durable capabilities. The Android/iOS `GuestSnapshot` factories construct `LocalGuestPortfolioRepository` with native storage. Keep these contracts and the atomic write/recovery owner. No second repository interface or filesystem facade is needed. |
| External account data | `PortfolioCashLedger`, `PortfolioShareOrders`, `PortfolioStatements` already receive a lazy `PortfolioDataApi` provider, with real/mock implementations exercised by `AccountWorkflowDataBoundaryTest`. Keep it; client replacement and session lifecycle stay with the model. This remains a JSON/path-oriented application port, not a typed domain repository. |
| PDF parsing | `InvestmentDocumentParser` is already independent of persistence and the general engine command protocol. `EngineDocumentParser` translates parsing into the existing `pdf` command without owning engine destruction. Keep this adapter and the engine-taking compatibility overload; native rendering/OCR/JavaScript machinery remains behind it. The new change narrows the *selection* dependency, not the parser output schema. |
| Financial calculations | `BondMath`, payment/window rules, share acquisition costs and valuation are concrete pure functions/value types with explicit inputs. Polymorphic calculator services would not improve their current substitutability. Keep functions and native decimal `expect`/`actual` operations; do not introduce interface-per-calculator wrappers. |
| Environment | Guest repository constructors already accept time/date/ID functions and a dispatcher. `GuestMarketData` already accepts fetch, wall-clock and monotonic-time dependencies. Keep function injection and standard library contracts. Import progress used a hard-coded monotonic source; inject the standard `TimeSource` there, without inventing an app clock interface. |
| Platform | `NativeActions` combines unrelated device capabilities. PDF workflows need only selection, filename and a private-selection policy. Introduce `PdfPicker` and let existing native actions implement it by inheritance. Keep platform factories, password/email/share/export methods and their ownership unchanged. |

## Implemented boundaries

### PDF selection capability

New `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PdfPicker.kt` exposes only:

- `pickedPdfName`: optional filename after reading.
- `pickPdf(onSelected)`: selected bytes or null for cancellation.
- `pickPrivatePdf(onSelected)`: selection avoiding optional export/cache copies; defaults to ordinary selection when there are no such copies.

These declarations move out of `NativeActions`; their signatures and defaults are preserved through inheritance. Both `readPurchasePdf` overloads and `GuestPdfImport.select` now accept `PdfPicker`. The guest workflow's private-path adapter delegates only this contract, rather than all native actions. A document-only adapter can participate without stubbing URL navigation, sharing, export or password saving. Existing Android/iOS adapters, `ReceivedPdfActions` and UI callers still supply the same objects. The parser test fixture now implements only `PdfPicker`.

The private override remains observable and tested: guest imports invoke `pickPrivatePdf`, retain its filename, validate/review the result, and write only after confirmation. Default forwarding remains covered by existing guest workflow tests. No new abstract base class, factory layer, service locator or Gradle module is added.

### Standard monotonic time injection

Both `readPurchasePdf` overloads accept an optional `TimeSource`, defaulting to the original `TimeSource.Monotonic`. The engine overload forwards it to the parser pipeline. Only the origin of the selection time mark changes; the 100 ms rendering interval, 1,000 ms minimum feedback period, cancellation handling, parser cleanup and stage ordering remain unchanged. A coroutine-test scheduler time source proves the exact minimum duration without relying on elapsed host time.

No existing abstraction was removed. The broad selection dependency and guest delegation were simplified. `NativeActions` remains the composition-level aggregate for existing UI/platform wiring.

## Compatibility and remaining boundaries

No parser assets, investment formulas, HTTP paths/payloads, persisted data, JSON schemas, native storage policies or DI factories change. This is an internal source API refactor: all Kotlin consumers and native framework targets must rebuild together; binary compatibility with previously compiled external clients is not claimed. Neither interface is a separately published artifact. Call sites in both working and independently staged trees are compiled.

Remaining deliberate limits:

- `JsonRecord` remains the parser/account compatibility representation. A typed review contract needs a separate migration across import variants, not an additional wrapper hiding the same JSON.
- The model and older screens still coordinate broad `NativeActions`, `InvestmentEngine` and concrete session ownership. Narrow another capability when a real consumer benefits; do not fragment the composition root merely for uniformity.
- `ServerSettings` is broad, and `ImportJournal` consumes it for two history operations and uses system time/random IDs. A history-specific store/time seam is a candidate when changing journal behavior; it is not bundled into the PDF selection change.
- Session-cookie expiry uses real wall time inside Ktor cookie storage. Testing expiry boundaries would benefit from coordinated clock injection into both bookkeeping and Ktor's expiry behavior. Injecting only one would give a misleading test seam.
- Calendar/UI entry points still obtain current dates directly. Core date/financial rules accept explicit dates; no clock-service rewrite or changed rollover behavior is included.

## Tests and verification

Four added behavioral tests cover selection cancellation without parsing, parser-failure callback cleanup, guest private-path selection plus persisted filename, and the exact feedback timing boundary with injected time. Existing tests exercise parser cancellation, engine command adaptation, incoming shares, duplicate imports, financial matching, storage recovery and default private-picker forwarding. No assertions or financial tolerances are weakened.

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`. SDK: `/Users/muhirwagabooreste/Library/Android/sdk`.

Focused command (working tree):

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*DocumentParserAdapterTest' --tests '*GuestPdfImportTest' --console=plain
```

Passes, exit 0 (`/tmp/inzira-abstraction-focused.log`). Initial test compilation exposed a nullable-list inference error and an existing native-action fixture used by the incoming-file adapter; both were corrected before the passing run.

Full host/build/lint command:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Working tree: exit 0, `BUILD SUCCESSFUL in 38s`, **100 core JVM + 101 core Android host + 432 shared host tests**, zero failures/errors/skips. Log: `/tmp/inzira-abstraction-working-build.log`.

The seven staged source/test/report files were exported using `git write-tree` / `git archive` to `/tmp/inzira-abstraction-index.wQ11tz`. The same command was run against that tree with `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk` and `--project-dir "$audit_export"`. Exit 0, `BUILD SUCCESSFUL in 1m 1s`; **100 core JVM + 101 core Android host + 421 shared host tests**, zero failures/errors/skips. Log: `/tmp/inzira-abstraction-index-build.log`. The 11-test difference belongs to excluded earlier refactoring. Both iOS targets, Android app/instrumentation APK compilation and release lint pass. All staged Kotlin files match the isolated export byte-for-byte.

Native shared-suite command against the export:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$audit_export" :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 33s`; **444 shared native tests**, zero failures/errors/skips. Log: `/tmp/inzira-abstraction-index-native.log`. This includes native PDF/engine integration, received-document handling and all common capability/workflow tests.

Package and existing static/engine checks were run in both trees:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

All pass: package ownership/module DAG, design colors, **16 Python tests and 84 Node tests**, and whitespace checks. Logs: `/tmp/inzira-abstraction-{working,index}-{packages,colors,python,node}.log`. Source comparison confirms the two import workflow bodies are unchanged after normalizing the narrowed dependency and time-source parameters.

Existing account data-boundary tests cover lazy construction and provider replacement; guest repository/import tests exercise injected stores and valid writes. Android/iOS factory callers compile unchanged. No DI framework/container task exists, and runtime invocation of every Compose factory is not claimed. No Detekt/ktlint is configured; release lint retains baseline warnings. No connected Android instrumentation execution, physical-device UI check, live-account write or backend deployment occurred.

The full core-native suite was not rerun: no core source changed. Its previously documented separate `ShareCompanyRowsTest.remainingSharesAndTheirCostAreUsedAfterSales` floating-point equality failure remains outside this slice. Full core JVM/Android host and shared host/native results are reported separately; this is not an all-tasks/all-targets green claim.

Commit only this slice as `refactor(kmp): improve abstraction boundaries`. Earlier unrelated work remains uncommitted.
