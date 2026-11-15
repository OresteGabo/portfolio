# PDF import architecture review and refactor

Reviewed against `27c0737` on 15 November 2026. Scope: the KMP mobile application and its checked-in embedded parser/runtime. No backend API, website, user document, financial formula, persisted record or parser grammar is modified. Existing unrelated unfinished changes are excluded from this commit.

## Actual pipeline and responsibility boundaries

Paths below are relative to the repository; Kotlin names are in `dev.orestegabo.portfolio.investments` unless indicated otherwise.

| Stage | Existing owner and contract |
|---|---|
| Acquisition | `PdfPicker`, platform `PlatformPorts.android.kt` / `.ios.kt`, `IncomingPdf` and `IosPdfReceiver`: system document picker/share/open-in handoff, bounded reads, cancellation and local URL handling. Android streams close with `use`; iOS streams close and security-scoped access is released in finally. Receiver reads must not delete originals or fetch arbitrary remote URLs. |
| Selection lifecycle | `PdfImportPipeline.readInvestmentPdf`: opening/extracting/checking feedback, cancellation checks across picker/parser/validation callbacks, cleanup of progress callbacks and minimum feedback duration. It returns a proposed review rather than saving an investment. `PurchasePdfImport` supplies account-specific admission. |
| Android extraction | `LocalInvestmentEngine.android.kt` dispatches PDF bytes through the local WebView bridge. Shipped `assets/engine/pdf-import.js` uses PDF.js, page policy and text-row reconstruction, then bounded Tesseract OCR where needed. No remote document execution/annotation UI is introduced. The renderer/JS lifecycle is owned by the host. |
| iOS extraction (new explicit boundary) | `IosPdfTextExtractor`: PDF envelope/catalog/page policy, PDFKit text, BK statement coordinate reconstruction, bounded Vision OCR and `(text, usedOcr)` output. Documents, text lists and OCR counters are local to each call; progress is passed per call. This component has no API, repository, journal, financial parser or JSContext. |
| Native operation / JS execution | `IosInvestmentEngine` owns mutex serialization, timeout, active job cancellation, destroy state and JavaScriptCore evaluation. It delegates extraction then invokes the unchanged shared parser. The Android and iOS engines still implement the existing `InvestmentEngine`; `EngineDocumentParser` remains the PDF-only capability adapter. |
| Format identification / field interpretation | `web-logic/source/equity-pdf-parser.ts::parsePurchasePdfText` dispatches existing statement, issuance, equity and investor-result parsers. `assertSingleDocumentFamily`, `uniqueCapture`, `pdfNumber`, broker/layout checks and parser identity metadata stay authoritative. Generated ESM/portable assets and source are untouched. |
| Normalization / financial admission | Parser output carries `kind`, fields, missing fields, warnings, OCR marker and parser identity. `PdfReviewPolicies` validates workflow/statement compatibility; `importedPurchaseInput`, `importedIssuanceInput`, pending contract matching and guest/account save boundaries validate actual writes. Parser interpretation does not authorize a transaction. Incomplete results remain proposals to review, not fabricated zeros ready to save. |
| Preview and user intent | `PurchaseImportScreens`, import conversation/review UI and `GuestPdfImport` display the original PDF/proposed fields. Guest import requires explicit confirmation before saving. Selection, recognition and an existing document do not themselves confirm a personal purchase. |
| Duplicate handling | `ImportDuplicates` distinguishes metadata candidates, exact content and completed investment links. `PortfolioImports` checks a fresh authorized document list and compares SHA-256 of actual bytes; matching name/date alone is insufficient. A retained file can be reused while restoring a missing purchase. Guest workflow uses digest plus retained bytes and a linked purchase. Public issuance identity additionally checks security/settlement/terms and rejects conflicting reports. |
| Persistence / evidence | `PortfolioImports` orchestrates existing document chunk upload, linked purchase/statement/issuance requests, retries and account journal. Guest importer retains a private PDF through its protected repository. `ImportJournal` / history codec store bounded receipts/digests/IDs/status rather than a new extraction-text cache. Original values/PDF evidence and existing fee/tax preservation rules remain at their current boundaries. |

The system is shared parser/domain logic with platform extraction and workflow orchestration, rather than a new Clean Architecture import subsystem. Introducing another repository/interface chain for each row would duplicate boundaries already present. The embedded Android extractor and native iOS extractor have genuinely different APIs; they are not forced into one platform implementation.

## Supported format matrix

These are explicit current parser identities, not a promise to support every financial PDF or every version of a broker template.

| Family | Parser identity (version 1) | Workflow scope / important distinction |
|---|---|---|
| BNR personal investor results | BNR / bond-confirmation / investor-results | Personal bond proposal; a personal allocation is distinct from offered or applied auction totals. |
| BNR summary market results | BNR / bond-issuance / summary-market-results | Public terms/auction report, not a personal allocation. Account publishing permission and a separate confirmation path apply. Guest import does not support this family. |
| BK Capital equity purchase note | BK Capital / equity-purchase / rse-purchase-contract-note | Existing RSE/RWF purchase layout and recognized security mappings. A sale note or unknown broker cannot inherit purchase defaults. |
| BK Capital client statement | BK Capital / statement / rwf-client-statement | Reconciliation rows and movements; deposits are not purchases and bond consideration is not automatically principal. Guest import does not support this family. |

`tests/document-parser-regression.test.mjs` verifies these identities and normalized ESM/portable-bundle output in normal/OCR modes. Existing sanitized/synthetic text fixtures cover all four families; `bond-issuance-import` also covers previously captured PDF.js/PDFKit text layouts. The shipped PDF.js reader is exercised with synthetic signed/table PDFs by `signed-pdf-import.test.mjs`. Native tests cover generated text/scanned bonds, statement table layout, equity guest imports, passive signature widgets and rejected/unrecognized documents. No personal Downloads PDF was opened or altered for this task.

## Focused refactor

`app/shared/src/iosMain/.../LocalInvestmentEngine.ios.kt` combined two independent change reasons: JavaScript operation lifecycle and PDFKit/Vision extraction/security/layout. It was 148 lines, so its responsibility mix, rather than an arbitrary file-length threshold, justified the extraction.

- Engine: **148 → 51 lines**. Keeps JSContext, projection/PDF command dispatch, mutex, job, timeout, cancellation and sanitized JS error envelope.
- New internal `IosPdfTextExtractor`: **112 lines**. Owns native document validation, page/word text reconstruction and Vision recognition. No retained document, result, callback or OCR counter is stored in fields.
- No new public API, interface, constructor injection framework, format registry, shared mutable cache or financial model is added. The engine still forwards its current progress callback and owns request lifetime.

Extraction, catalog/page policy, OCR rendering/recognition and dictionary-helper method bodies were mechanically compared with the pre-refactor source: unchanged apart from the extraction method's visibility/signature accepting its progress callback. Envelope/page/text/OCR/image limits, dispatchers, statement layout heuristic, permitted passive signature widgets, forbidden active/embedded content, error strings, parser invocation and byte-to-text result shape are preserved. Moving this code does not make synchronous PDFKit/Vision/JavaScript operations preemptible; existing between-stage/page cancellation semantics remain.

## Malformed documents, traceability and privacy

Existing boundaries reject malformed envelopes, locked/oversized/over-page PDFs, unsupported active document content, mixed families and conflicting labels. Numeric parsing validates complete tokens; unrecognized/missing financial fields and inconsistent totals remain missing/warnings and fail the existing strict purchase-admission path rather than being silently accepted. A parser identity identifies supported interpretation, not authenticity or cryptographic verification of a PDF signature. Accepting a passive signature widget does not certify its signer.

No validation is weakened to accommodate tests. The first new fixture used `ReportPdf.finish`, which embeds a file attachment; all three new tests correctly failed the existing catalog policy. An intermediate UIKit fixture also failed marker assertions with wrapped text. Final fixtures use the existing native tests' line-by-line drawing style and plain ASCII markers, with attachment-free UIKit PDFs; the focused three-test run passes. Production security code remained unchanged. The resolved failure is recorded here rather than counted as a passing run.

No document content logging, raw JS exception logging, remote upload during parsing or extraction-text persistence is added. Existing bridge/engine errors remain fixed/typed messages. Original selected bytes are verified unchanged by the new native test. Review normalization and imported command defaults are still distinct from original document evidence; this refactor does not claim every displayed/defaulted field came from the PDF.

## Regression coverage and limits

Three new `IosPdfTextExtractorTest` cases exercise the actual native boundary:

1. Generic nonfinancial text is extractable independently of format classification; original bytes stay intact, OCR is false for text PDFs, and consecutive calls have separate text/progress.
2. Empty/damaged/oversized/over-page input fails, followed by successful extraction on the same component.
3. Cancellation thrown at the progress boundary propagates with the same exception; the next extraction does not invoke a retained callback.

Existing shared tests continue to cover valid/incomplete/wrong-kind review admission, duplicates across all four kinds, retained/deleted documents, retry upload failure, pending targets, cancellation after non-cooperative parsers, local-only guest import and sensitive error redaction. Shared tests use fake API/storage and do not prove deployed backend transaction atomicity. Parser regressions cover malformed quantities, dates, charges, conflicting/repeated labels, mixed families and ESM/bundle equivalence. Existing native suites cover actual PDF/OCR/JS integration rather than only parser mocks.

Compatibility is verified against the existing fixture corpus and available simulator/emulator runtimes only. The opt-in iOS real-statement test returns early when `STRUCT_TEST_STATEMENT_PDF` is unset; no customer document was configured. That test is not counted as real-file compatibility evidence. Arbitrary encrypted PDFs, unsupported brokers/templates, every OEM/file-provider variant and every scan quality are not claimed. A new template requires sanitized fixtures and parser identity/version decisions; this task adds no format.

## Remaining focused follow-ups

- Review payloads still use JsonRecord/string kinds, and `importedPurchaseInput` recognizes blocking warnings by message phrases. Preserve the current payload while moving toward typed internal diagnostics only with compatibility tests; changing warning wording must not accidentally make inconsistent figures save-ready.
- `PortfolioImports` is still a sizeable account workflow coordinator with selected/retry/upload/journal state. Its mutable state is owned by the existing serialized host action. Further decomposition must characterize resume/reuse/concurrent-save behavior; no partly finished extraction from the unrelated working tree is included here.
- Native and PDF.js extraction enforce the same broad envelope/page/text/active-content policy through different platform APIs. Native dictionary/annotation checks and PDF.js output are not assumed structurally identical. New signed/annotated examples need regressions on both runtimes.
- Parser regexes and BK coordinate layout are necessarily template-specific. Preserve full-token parsing, conflicting-label rejection and sanitized captured-text fixtures as formats evolve. This refactor adds testability rather than claiming OCR or template inference is solved.

## Verification

Verification uses JDK 21 (`ms-21.0.9`) and the local Android SDK. The final isolated tree is `/tmp/inzira-pdf-index.MMpuAj`, exported from HEAD plus only these four staged files. All three Kotlin files are compared to that tested tree before commit; only this report's results are finalized afterward.

- Baseline before production edits: `node --test tests/*.test.mjs` — 84 passed; `./gradlew :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain` — BUILD SUCCESSFUL against the existing working tree. Logs: `/tmp/inzira-pdf-before-node.log`, `/tmp/inzira-pdf-before-native.log`.
- The two preliminary fixture runs failed as described above; no production policy change was made to resolve them. Final focused run: `./gradlew :app:shared:iosSimulatorArm64Test --tests '*IosPdfTextExtractorTest*' -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain` — BUILD SUCCESSFUL, all three new tests pass. Log: `/tmp/inzira-pdf-extractor-focused.log`.
- Final isolated compilation/host/lint: `./gradlew --project-dir /tmp/inzira-pdf-index.MMpuAj :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :core:allMetadataJar :app:shared:allMetadataJar :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintRelease :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain` — BUILD SUCCESSFUL. **759 host test executions** (126 core JVM, 126 core Android host, 507 shared Android host), zero failures/errors/skips. Android app/instrumentation APKs assemble; both iOS targets and shared metadata compile; release lint passes with existing compiler/deprecation warnings. Log: `/tmp/inzira-pdf-isolated-build.log`.
- `ANDROID_SERIAL=emulator-5554 ./gradlew --project-dir /tmp/inzira-pdf-index.MMpuAj :core:iosSimulatorArm64Test --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 :app:shared:iosSimulatorArm64Test -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 :app:shared:connectedAndroidDeviceTest -Pandroid.injected.device.serial=emulator-5554 --console=plain` — BUILD SUCCESSFUL. XML results: **654 iOS test executions** (125 core + 529 shared) and **501 Android shared device test executions**, zero failures/errors/skips. Log: `/tmp/inzira-pdf-isolated-runtime.log`.
- `ANDROID_SERIAL=emulator-5554 ./gradlew --project-dir /tmp/inzira-pdf-index.MMpuAj :app:androidApp:connectedDebugAndroidTest -Pandroid.injected.device.serial=emulator-5554 -Pandroid.testInstrumentationRunnerArguments.class=dev.orestegabo.portfolio.investments.LocalEngineTest --console=plain` — BUILD SUCCESSFUL; two Android app device tests passed, including actual WebView/PDF.js synthetic RSE contract import and local projection. Log: `/tmp/inzira-pdf-android-engine.log`.
- Working and isolated static checks: `python3 scripts/check-kmp-packages.py` — passed (417 working / 394 isolated source/test files); `python3 scripts/sync-design-colors.py --check` — passed; `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` — 19 passed; `node --test tests/*.test.mjs` — 84 passed; `git diff --check` and final `git diff --cached --check` — passed.

No unresolved test failure is included in the final commit. Verification does not claim physical-phone/production-backend behavior, arbitrary format compatibility, new OCR accuracy or improved runtime performance. No live import or actual customer file was used.
