# Kotlin investment engine migration

The shared Kotlin engine replaces runtime JavaScript for investment simulation and document parsing on Android and iPhone. There is no browser host, JavaScript bridge, JavaScriptCore context or packaged JS/Wasm processing asset.

## Ownership

- `core/.../InvestmentProjection.kt`: the established hypothetical model, actual holding conversion, coupon schedules, contribution overlaps, injections, auction tenor cycle/fills, fees, maturity proceeds, reinvestment and summaries. Floating-point operation order and positive half-up cent rounding are preserved for simulator compatibility; recorded-transaction decimal policy is unchanged.
- `core/.../DocumentParsing.kt`, `BondDocumentRules.kt`, `EquityDocumentRules.kt`, `StatementDocumentRules.kt`: whole-token bounded numbers, dates, recognition identities, document-family ambiguity, BNR personal/public separation, BK contracts/statements, warnings/missing fields and reconciliation-only statement semantics.
- `KotlinInvestmentEngine`: serialized cancellable work, bounded timeout, background processing, private error messages, cancellation and final disposal.
- Android `AndroidPdfTextExtractor`: PDFBox Android 2.0.27.0 reads text and checks structure; Android PdfRenderer rasterizes scans; bundled ML Kit Latin 16.0.1 recognizes text on device. iPhone `IosPdfTextExtractor` retains system PDFKit/Vision. Native adapters do not calculate financial terms or save records.

Both keep the 10 MiB / 10-page / five OCR-page / 200,000-character policies and explicit review before persistence. Passive signature widgets remain accepted without claiming cryptographic signature verification. Active actions, embedded files, XFA, non-signature fields and unsupported annotations are rejected. Android scan rendering uses an unlinked private descriptor, not a named plaintext PDF. OCR bitmaps/recognizers and document handles have explicit lifetimes. If cancellation arrives during a non-cancellable native recognition task, its bitmap stays owned until completion, then the result is discarded before parsing or publication.

ML Kit uses a bundled model, not a cloud OCR API or a downloaded model. Library/runtime behavior and device OCR quality are separate from parsing arithmetic; the provider's SDK has its own notices/terms. PDFBox is Apache 2.0. Current notices are bundled in the legal hub. No paid AI service, production data import, backend deployment or provider-plan change is introduced.

## Independent regression references

The previous engine was recorded before switching callers. The JVM comparison checks **357 distinct parser cases**, including invalid/ambiguous inputs, normalized object shape, optional fields, exact strings and long identifiers. It checks **17 complete simulator scenarios**, comparing every monthly field, per-lot coupon result and summary, with a small floating-point comparison tolerance. Fixture provenance is in `tests/fixtures/README.md`.

`tests/reference-engine` contains the retired source/runtime and the existing 85 Node regressions; it is outside mobile source sets. The optional pinned Node compiler only rebuilds these test references (`npm run generate:reference` / `check:reference`). It cannot generate app resources. New native regressions cover cancellation, disposal, failure recovery, signed document types, physically offset/wrapped statement rows and active-content rejection. Existing native guest PDF/review/storage and iPhone PDFKit/Vision tests remain.

Package guards reject JS/MJS/Wasm assets in Android/iPhone release artifacts and require the Android bundled OCR model. Fresh packaging after resource removal is essential; stale intermediate Compose resources are not evidence of intended app contents.

## Verification

Verified 18 November 2026:

- Core JVM: 147 tests; core Android host: 145; shared Android host: 565; shared iPhone simulator: 591. All passed, none skipped. This includes the frozen 357 parser cases / 17 projection scenarios and the native iPhone PDFKit/Vision, signature, scan, statement and cancellation suites.
- Dedicated Android 36.1 emulator, airplane mode enabled and Wi-Fi disabled: six complete tests passed under Android Test Orchestrator. They cover native simulator/PDF execution, all signed document types and full normalized reference output, offset/wrapped statement rows, active-content rejection, failure/disposal recovery, local guest review/storage/preview/duplicates, and exact scanned-share quantity/price/payable values. No real account or customer PDF was used.
- All 85 archived Node regressions and 42 Python script tests passed. Package/source-set/module checks and UI test inventory checks passed.
- Android Debug and optimized unsigned Release assembled. Release lint passed with zero errors and six warnings (three version advisories and three trust-manager warnings inside PDFBox's transitive Bouncy Castle library; the app does not use those classes for HTTP). Release package guard verified bundled OCR models and no JS/MJS/Wasm engine assets. Universal release APK: 53.23 MiB, including all packaged native ABIs; this is not a per-device store download size.

The first native statement test incorrectly expected eight movements from the shorter synthetic fixture; the archived engine produces three. Replaced that mistaken assertion with a stronger full-object comparison against independently generated original-engine output for every document fixture. No parser expectation was changed to match a native defect.

The manual orchestrator installation initially could not bind its service on Android 36.1. Reinstalling its test-only APKs with Android 11+ `--force-queryable` fixed discovery/binding; the final six-test orchestrated run executed and passed. Zero-test runs and infrastructure crashes were not counted as success.

R8 initially reported PDFBox's optional `JP2Decoder` / `JP2Encoder` classes. These are not used by this integration (PDFBox only reads text/structure; Android PdfRenderer handles images). Added exactly those two optional-class rules, retaining missing-class checking for all other dependencies. Release then built successfully. No test exclusion, broad missing-class suppression or disabled shrinking was introduced. The final OCR bitmap uses the existing semantic paper token (the same fixed white as before); final Debug/Release rebuild, release lint, package inspection, design audit and the complete offline guest scan journey passed again after that source-style correction.

No customer PDF, live account mutation, backend deployment, signed store upload or physical-device test is implied. Native OCR quality can vary with broker layouts and scan quality; the supported synthetic/public fixtures passed. Full unrelated UI journeys were not rerun for this engine migration.

Before committing, the staged migration was exported into an isolated checkout without the unrelated share-history UI changes. That exact source tree passed 147 core JVM tests, 565 shared Android host tests, Android Debug assembly, all 85 reference tests, all 42 Python tests, package/module checks, UI inventory and design-color checks. Shared-file hunks and screenshots belonging to the concurrent UI work were excluded from the migration commit.
