# Open/Closed Principle review

Reviewed against `d11457b` and the working tree on 15 November 2026. Scope: KMP mobile application. Backend, website, parser assets, financial formulas, persisted schemas and build configuration are unchanged. Earlier unfinished work is excluded from this commit.

## Actual variation points

| Area inspected | Existing variation and decision |
|---|---|
| Parser adapters | `InvestmentDocumentParser` already permits another native/broker parser without exposing the engine's command protocol. `EngineDocumentParser` remains the compatibility adapter. Retain these contracts; no parser registry or automatic format fallback chain is needed. |
| Supported review kinds | Personal bond allocation, equity contract, statement and public bond issuance have different review/confirmation semantics. Native extractors and bundled parser algorithms produce the existing `kind`/fields contract. Do not pretend that a new financial document can safely be supported just by registering a string. |
| Account versus guest workflows | Account admission can restrict bond uploads and match a statement target; statements must route to their own review. Guest imports accept only personal bond/equity proposals and enforce private ownership/cancellation checks. Separate these existing policies from stable parsing lifecycle orchestration. **Implemented here.** |
| Native extraction and picking | Android WebView/PDF machinery and iOS PDFKit/Vision/JavaScriptCore have substantive platform differences. Existing parser/picker/engine ports isolate their implementations. Leave native safety limits, threading and lifecycle ownership intact. |
| Financial instruments | Bond scheduling, coupons and tax calculations differ from share acquisition/sales/valuation. Typed terms and pure functions already express these inputs explicitly. Retain separate rules; do not introduce a speculative instrument superclass or strategy per formula. |
| Discriminator branches elsewhere | `ImportDuplicates`, statement matching, pending confirmation and account loading inspect document kind for distinct evidence/status/endpoint rules. These are not interchangeable copies of one dispatcher. Retain auditable checks rather than making a generic handler responsible for unrelated persistence, navigation and calculations. |
| Closed state/date sets | Session states, subscription phases and retry bookkeeping have meaningful bounded variants and explicit decisions. Keep sealed/exhaustive or small conditional rules; OCP does not mean hiding every new business decision behind polymorphism. |

## Implemented boundary

### Stable pipeline: `PdfImportPipeline.kt`

Internal `readInvestmentPdf` owns selection, byte-size bounds, progress stages/minimum timing, parser invocation, selected-byte publication, cancellation and callback cleanup. It no longer knows about `bond`, `equity`, `statement`, public issuance, purchase targets or guest restrictions.

An ordinary `(JsonRecord) -> Unit` review-validation callback allows a workflow to apply its existing policy before selected bytes are published. There is no new interface/class hierarchy: validation is a single synchronous capability, so function composition is sufficient. The internal raw pipeline defaults to returning a parsed proposal; it does not authorize a financial write. It is not exposed as a replacement public purchase API.

### Existing purchase API: `PurchasePdfImport.kt`

Both public `readPurchasePdf` overloads preserve all signatures/defaults, including injected time, picker/parser/engine and callbacks. The parser overload delegates to the stable pipeline and supplies `validatePurchasePdfReview(review, bondOnly, expectedStatement)` at exactly the original validation point: after checking-stage progress and before selected-byte publication.

### Explicit workflow rules: `PdfReviewPolicies.kt`

- `validatePurchasePdfReview` contains the original account conditions, messages, ordering and existing `statementConfirmationMatches` call. Statements bypass purchase-target matching and keep their separate review. Public issuance is accepted for a bond context, not used as an equity statement confirmation. Normal purchase matching remains unchanged.
- `validateGuestPdfReview` contains the original two guest checks: personal bond/equity kind and a fields object. Incomplete personal fields remain reviewable; downstream financial save validation is not weakened or replaced.

`GuestPdfImport` now calls the raw shared pipeline instead of an account-purchase wrapper with default arguments. It still validates **after** the returned review has been copied and the coroutine cancellation check has run, exactly where the original guest checks were. Private picking, envelope validation, error copy, hashing, duplicate detection, zeroing and save confirmation are unchanged. Guest workflow ordering is deliberately not forced into the account callback just for uniformity.

## What can extend without editing stable orchestration

1. Another parser for the existing review contract implements the existing parser capability and is supplied by the caller; no pipeline format dispatch changes.
2. An actual new import context can supply its own acceptance function, or validate the returned proposal after its ownership checks, while retaining the picker/parser/progress lifecycle.
3. The existing account/guest policies can evolve in their policy file without modifying cancellation, timing, selection or parser cleanup.

A genuinely new document/instrument still requires explicit parser support, validated mapping, financial rules and appropriate review/persistence behavior. This change does not promise configuration-only additions of financial products, nor support hypothetical new variants. It narrows the area changed for existing workflow policy variations.

## Compatibility and tests

Source comparison reconstructs the original account pipeline body by substituting the moved validation block for the callback; it matches exactly. The guest source changes only its pipeline call and replaces the original checks with their named function. Public overloads, review JSON and exception messages remain compatible. Internal functions are newly added; no published binary API is being migrated.

Nine new tests cover account kind/context fallback, statement/public-issuance exceptions, exact equity identity/date/financial target matching, guest incomplete-review admission versus save validation, rejected guest kinds/fields, all four variants through the same pipeline, rejection before byte publication, guest post-return validation, and cancellation/late-callback cleanup. Existing native and common parser/guest/incoming-file tests remain. No financial tolerance or existing assertion is weakened.

## Verification

JDK: `/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home`; Android SDK: `/Users/muhirwagabooreste/Library/Android/sdk`.

Before extraction:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest --tests '*PurchasePdfImportTest' --tests '*DocumentParserAdapterTest' --tests '*GuestPdfImportTest' --tests '*IncomingPdfTest' --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 10s`; log `/tmp/inzira-ocp-characterize.log`. After extraction, this command plus `--tests '*PdfReviewPolicyTest' --tests '*PdfImportPipelineTest'` passes, exit 0, `BUILD SUCCESSFUL in 13s`; log `/tmp/inzira-ocp-focused.log`.

Full working-tree command:

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 45s`; **100 core JVM + 101 core Android host + 449 shared host tests**, zero failures/errors/skips. Android build/instrument APK compilation, release lint and both iOS compilations pass. Log `/tmp/inzira-ocp-working-build.log`.

The seven staged source/test/report files were exported using `git write-tree` / `git archive`; the same full command was repeated against the export with `ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk` and `--project-dir "$audit_export"`. This verifies committed callers without relying on earlier unfinished code.

Export `/tmp/inzira-ocp-index.rAJdwJ`: exit 0, `BUILD SUCCESSFUL in 1m 34s`; **100 core JVM + 101 core Android host + 438 shared host tests**, zero failures/errors/skips. Android build/lint/instrumentation compilation and both iOS compilations pass. Log `/tmp/inzira-ocp-index-build.log`. The 11-test working-tree difference belongs to excluded unfinished refactoring.

Shared native suite against the same export:

```sh
ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew --project-dir "$audit_export" :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

Exit 0, `BUILD SUCCESSFUL in 50s`; **461 shared native tests**, zero failures/errors/skips, including existing native PDF/receiver integrations and all nine new policy/pipeline tests. Log `/tmp/inzira-ocp-index-native.log`.

Existing static/engine checks ran in both working and isolated trees:

```sh
python3 scripts/check-kmp-packages.py
python3 scripts/sync-design-colors.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
node --test tests/*.test.mjs
git diff --check
git diff --cached --check
```

Both pass package ownership/module DAG and color checks, **16 Python tests and 84 Node engine/parser tests**, and diff whitespace checks. Logs `/tmp/inzira-ocp-{working,index}-{packages,colors,python,node}.log`. Source comparisons confirm unchanged account policy expressions and the original pipeline body after substituting that policy back for its callback, plus only the documented guest call/check changes. Staged Kotlin source/tests match the verified export byte-for-byte.

No Detekt/ktlint is configured; baseline compiler/lint warnings remain. No connected Android instrumentation execution, physical-phone UI run, live-account import/write, backend deployment or signed distribution was performed here. Instrument APK compilation is not claimed as test execution.

No core source changed. The separate full-core-native share-cost floating-point equality baseline failure documented in prior audits remains outside this slice; full core JVM/Android host and shared native results are not an all-core-native/all-task green claim.

Commit only this verified slice as `refactor(kmp): improve extensibility`; preserve earlier unrelated work.
