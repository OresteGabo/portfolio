# Phase 4 — Private on-device guest PDF imports

Follows [the architecture audit](ARCHITECTURE_PLAN.md) and [durable storage](PHASE_3.md). The user's Phase 4 mission supersedes the audit's provisional phase numbering. All work remains on `feature/guest-mode`; no backend/API, infrastructure, dependency or signing changes.

## Existing implementation reused

Parsing was already **on-device**, not dependent on Spring Boot:

- Android `LocalInvestmentEngine.android.kt`: bundled PDF.js, text/OCR workers and shared extraction rules in a sandboxed WebView. Guest instances additionally set `blockNetworkLoads`; all allowed asset responses are supplied by the existing local interceptor. File/content access, DOM storage and network caching remain disabled. Authenticated engine instances retain their previous configuration.
- iOS `LocalInvestmentEngine.ios.kt`: PDFKit/Vision extraction and the same JavaScriptCore financial rules, with no network or AI bindings. No cloud OCR or replacement parser was introduced.
- `EngineDocumentParser`, `readPurchasePdf`, `PdfPolicy`, existing BNR/equity field extraction, `PurchaseEditor`, `EquityEditor`, receipt/calculation logic and the native PDF viewer are reused.

Guest support in this phase: **personal BNR allocation notices and share purchase contract notes**. The underlying parsers also recognize other formats, but guest statements and public issuance imports are explicitly rejected until their reconciliation/publication semantics have a local implementation. Guest imports never contribute private/public terms to Spring Boot.

Offline native testing exposed two existing Android OCR packaging defects: the vendored ESM library exposes a default object while the generated caller expected named exports, and AAPT expands `eng.traineddata.gz` into `eng.traineddata` in the APK. A small owned `native-tesseract.mjs` facade supplies the expected exports and requests the bundled uncompressed model. `scripts/native-pdf-module.mjs` and the existing sync script preserve the adaptation during regeneration. No vendor/minified library was edited or upgraded; the generated importer changes only its local OCR module path. This narrow loader fix also benefits existing authenticated Android scans without changing their financial rules, review or upload APIs. Temporary diagnostic code/network-switch probes were removed before final verification.

## Flow and trust boundary

```text
personal guest -> private native selection / existing Open In inbox
               -> bounded stream and PDF envelope checks
               -> local PDF engine + existing normalized review
               -> editable existing purchase form + explicit review acknowledgement
               -> validate instrument/financial data and choose any existing transaction
               -> protected original + atomic manifest/holding/evidence commit
               -> local library and native preview
```

`GuestPdfImport.kt` coordinates transient review/preview state. It has a local repository/parser dependency, **no HTTP client, server session, import-chat preference journal, analytics or publication dependency**. `PortfolioModel` routes guest saves through it only while a guest review is active. Sample mode cannot import. Account import code still uses its existing pipeline and endpoints.

The iOS private picker opens in place (`asCopy = false`), reads a bounded security-scoped stream and releases access. It never deletes or edits the provider's original. The authenticated picker keeps its existing copy behavior. Android already reads content-provider streams without retaining a picker copy. An OS/cloud file provider may itself fetch/synchronize the file under its own policy; the guest app does not upload the selected bytes, filename, extracted text, financial fields or digest.

Guest reviews/previews are cleared on mode/account transitions. Queued guest inbox bytes are discarded before entering account sign-in, preventing a waiting guest PDF from falling into account ingestion. This does not delete the external original or committed guest files. There is no automatic guest-to-account migration.

## Review, validation and duplicates

- Existing 10 MiB / 10-page / 5-OCR-page / 200,000-character policies remain in force. Existing PDF structure checks reject active content, unsupported annotations and encrypted/locked documents. Parser limits and deadlines are reused; native OS OCR calls remain cooperatively cancellable, not a hard preemptive sandbox deadline.
- Unsupported, unreadable, password-protected or malformed imports show a fixed actionable message. Dependency errors/document text are not interpolated into the guest error message or logs. Parsing alone never saves a document or holding.
- Fields are editable through the existing forms. Missing imported values remain blank for correction rather than taking a manual-entry default silently. A review acknowledgement is required again for each selected file. Source warnings/OCR/missing-field notices remain visible. Fees, investor tax and an estimated schedule are user choices if absent from the original, not facts attributed to the PDF.
- Imported records require an ISIN and existing local financial validation. Exact SHA-256 identity prevents duplicate files and duplicate financial writes for an already linked original; the first-added date/ID are retained. Names/dates alone are not byte identity.
- A retained original whose holding was deleted is parsed/reviewed again and can restore the purchase using the same document reference. A **missing** original can be explicitly restored after review without duplicating the linked investment. Corrupt originals/key failures are not silently overwritten; deletion/recovery remains explicit.
- Different bytes with potentially matching financial records require the user to choose the existing purchase. Candidates use security identity, dates, quantity and compatible account/broker context. Stored financial terms must agree before attachment; a conflict requires correcting the existing purchase. Evidence attachment preserves the selected record's notes, tax choices and financial fields rather than replacing them with parser defaults. A matching submitted bond becomes active when its personal allocation is explicitly reviewed/saved as active; a second confirmation action is not required. Partial/conflicting allocations require manual correction before linking, rather than guessing a match. Multiple candidates are never selected automatically. A match is never inferred solely from similar dates/amounts.

## Protected originals and schema 3

`GuestDocumentStorage` extends the existing native protection mechanisms for separate PDF objects, each capped at 10 MiB. Originals use opaque UUID-based `pdf-…` filenames/Keystore aliases, not investor filenames. The existing 4 MiB portfolio manifest remains separate; it contains normalized records, private document metadata/hash and local references. Original text is not separately persisted. There is no new database or cryptographic algorithm.

- Android: existing Keystore AES-GCM/AtomicFile adapter, independent document keys, private `noBackupFilesDir`; ciphertext-only named persistent files.
- iOS: existing Application Support adapter, Complete Data Protection and directory backup exclusion. It now distinguishes explicit file-not-found errors from protected/unreadable failures; uncertain existence is never treated as a safe empty store or successful deletion.
- Schema 2 → 3 adds only `pendingFiles`, the local file-operation journal, through validated atomic replacement. Draft schema 1 migration is retained. Existing financial/document fields and IDs are preserved; unknown versions/corruption fail closed. The migration does not drop legacy reference metadata or normalize financial figures.

For a new original, commit a cleanup intent first, then atomically write the protected object, then commit document + holding references together and clear the intent. If interrupted before the final commit, the next **valid** load removes the tracked uncommitted file. A referenced committed object cannot be included in the cleanup journal. No garbage collection runs on an unreadable/corrupt manifest.

Deleting a local PDF requires confirmation, removes its references while retaining investments/receipt states, and journals physical file/key cleanup. Interrupted cleanup is idempotently retried on a valid load. Deleting an investment retains its source PDF for review/restoration. No external original is deleted. Failed writes, storage exhaustion and unreadable/key-loss cases never fall back to remote upload or plaintext storage.

## Preview and temporary data

Guest library originals are hash-verified before preview. Review previews use only the selected in-memory bytes. The existing native viewer, bounded rendering and capture/privacy policy apply; there is no browser/public-URL viewer or remote thumbnail service. Guest preview identity is stable across asynchronous state updates.

Android PdfRenderer still requires a seekable descriptor. Its temporary file is now **unlinked before any PDF bytes are written**; the renderer reads the anonymous descriptor and closes it on disposal/error. This also improves the existing account viewer without changing its interface. The cache filename never contains document content. iOS PDFKit uses in-memory data. Imported buffers and previews are cleared when discarded where mutable ownership allows; JVM/JS/native immutable copies/GC are not a guaranteed RAM wipe. Logical file/key deletion is not a forensic secure-erasure guarantee.

Guest data and originals remain excluded from backup; there is still no recovery/export guarantee, separate guest biometric gate, account synchronization or paid OCR. Physical iPhone passcode/lock/encryption behavior remains a release acceptance requirement from Phase 3.

## Changed source groups

All investment Kotlin filenames below use package `dev/orestegabo/portfolio/investments` under the stated source set.

- `commonMain`: `GuestPdfImport.kt`, `GuestPdfMatching.kt`, `GuestPortfolioRepository.kt`, `GuestScreens.kt`, `PortfolioModel.kt`, `IncomingPdf.kt`, `PlatformPorts.kt`, `InvestmentApp.kt`, `PurchaseForms.kt`, `PdfViewerScreen.kt`, `FrenchStrings.kt`; shared `App.kt` includes guest preview in the native privacy cover.
- `androidMain`: `GuestSnapshot.android.kt`, `LocalInvestmentEngine.android.kt`, `PdfDocument.android.kt`.
- `iosMain`: `GuestSnapshot.ios.kt`, `PlatformPorts.ios.kt`.
- Tests: `GuestPdfImportTest.kt`, schema-version assertions in `GuestPortfolioRepositoryTest.kt`, `GuestPdfNativeTest.kt`, guest shell instrumentation updates, native iOS engine integration cases, and sanitized `bnr-personal-synthetic.txt`. Debug-only `EngineHostActivity` can request the private engine configuration for instrumentation. Native asset generation/sync helper, OCR facade, generated import path and Node regression tests cover the loader fix. The existing renderer test now requires anonymous preview backing even while rendering, instead of expecting a named temporary file.

## Verification and limits

Final execution results follow below. Fixtures are synthetic/anonymized. No supplied customer PDF, production account, database, service or real credential is used. Tests deliberately isolate their manifest/files and remove only their own originals/keys.

Remaining manual checks: real OS picker/provider cancellation and temporary-copy behavior on physical devices; iPhone passcode/locked-data behavior; screen capture/recents; accessibility/font scale and review layout on small screens; device storage exhaustion and physical power loss. Tests use fake picker actions feeding real native PDF bytes/engines; this is not a claim that the OS picker UI itself was automated. The full guest review UI was not visually certified in this phase. Existing backup/retention limitations still apply.

### Executed checks (2026-11-02)

Commands run from the KMP repository, without deployment or signing:

```sh
./gradlew :core:jvmTest :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:shared:compileKotlinIosArm64
./gradlew :core:iosSimulatorArm64Test :app:shared:iosSimulatorArm64Test -PiosTestDevice=<isolated-iPhone-16-UUID>
node --test tests/native-pdf-module.test.mjs tests/document-parser-regression.test.mjs tests/bond-pdf-import.test.mjs tests/signed-pdf-import.test.mjs
git diff --check
```

Android shared tests: **324 passed**. Core JVM: **61 passed** (up-to-date on final build). Android debug/test APK assembly and iOS device-arm64 Kotlin compilation passed. Node: **54 passed**, none skipped. Compilation still reports existing deprecated API/interop warnings; no new dependency was introduced. An arm64 compilation is not a signed iOS release build.

Native Android tests used isolated emulator userdata and airplane mode with Wi-Fi/mobile data disabled. Each test class was invoked separately using:

```sh
adb -s <isolated-emulator> shell am instrument -w -r \
  -e class dev.orestegabo.portfolio.investments.<TestClass> \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

- Android 17 / API 37, 16 KiB Pixel 2 emulator: `GuestPdfNativeTest` (1), `GuestEntryTest` (1), `LocalEngineTest` (2), `PdfDocumentRenderingTest` (3), `ImportConversationUiTest` (1) passed. The guest native test exercises the actual network-blocked engine, vector BNR/equity fixtures, scanned equity OCR, explicit review, protected originals, duplicate detection and malformed rejection. Existing authenticated native parser/projection and import-chat coverage passed on this emulator.
- Android 16 / API 36 Pixel 8 Pro emulator: guest native import (1), guest entry (1), existing engine (2), renderer (3), and PDF sharing (1) passed with networking disabled.
- **Unresolved test failures, not counted as passes:** `PdfSharingTest` crashed twice in native EGL/RenderThread surface disposal on the Android 17 emulator. It also crashed with an independently built immutable archive of Phase 3 commit `cc7dbe5` on that same emulator; it passed on Android 16. This predates the phase, but physical-device sharing still requires QA. `ImportConversationUiTest` on Android 16 encountered `NetworkOnMainThreadException` during Ktor HTTP timeout cancellation; its Android 17 run passed. The Android 16 baseline comparison result is recorded below. No renderer-driver or networking refactor was attempted as part of private PDF import.

The failed Android OCR runs initially reproduced real loader defects (missing named ESM export and compressed asset path). Both were fixed as described above, then the scanned-PDF test passed with `blockNetworkLoads = true`. Temporary diagnostics were removed and the Node regression checks reject their reintroduction.

This phase does not certify a public release. No Spring Boot changes, schema deployment, production requests, guest migration, public market API or phases 5–8 work are included.

Final iPhone 16 / iOS 26.2 simulator run: **344 shared tests passed**, zero failures/errors/skips, including actual PDFKit personal bond/share parsing, retained originals/duplicate handling, encrypted and malformed rejection and existing Vision scanned-document tests. Core iOS: **61 passed**, up-to-date in the final run. The temporary simulator was shut down and deleted by the test script. Final result XML was checked rather than inferring success from compilation.

Android 16 authenticated chat comparison: the Phase 3 APK passed once (71.765 s); the current APK failed again, including after clearing only the isolated test app's data, with the same Ktor/`HttpURLConnection` cancellation exception. A final clean-data Phase 3 run exceeded the 180-second harness limit and was stopped. Therefore the Android 16 result is **inconclusive as to regression versus existing test/network instability**, and is not dismissed as an emulator-only or pre-existing problem. This is a release-validation blocker requiring a focused authenticated thumbnail/cancellation investigation before release. Guest native offline tests and Android 17 authenticated chat passed, but they do not erase this failed check. No production performance claim follows from these tests.
