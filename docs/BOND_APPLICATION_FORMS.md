# Shared bond application forms

Calendar shows a compact Open application form action for each published subscription range in the displayed month. It becomes available on the first day of the issuance month, using Rwanda's current date and the calendar's closing/issuance date, independently of the selected day or the bid window opening. Tapping opens the existing protected in-app PDF reader. Its toolbar Share PDF action presents the native share sheet, including save/export options, only when explicitly tapped after the PDF loads. Historical forms remain accessible.

Only the signed-in owner sees Upload application form. After choosing a PDF, the owner reviews its filename, the issuance and settlement date, and confirms it is a blank public form without personal details. Publishing a form never creates or confirms a purchase. Invited users and staff with shared-data publication permission cannot upload application forms.

## Backend and storage

The Spring API checkout is `/Users/muhirwagabooreste/AndroidStudioProjects/gabo-portfolio-api`.

- `GET /api/bonds/application-forms` lists shared form metadata for authenticated accounts.
- `POST /api/bonds/documents` uses the existing bounded 512 KiB chunk protocol with category `Bond application` and `metadata.calendarId`. Every chunk checks the authenticated account is the owner; request-body roles and account IDs are ignored.
- The server derives dates, title, tenor and filename from its bundled issuance calendar. Client-supplied auction terms cannot change the association.
- Flyway V12 creates `bond_application_forms`. Unique constraints on canonical auction identity (settlement date, tenor and maturity) and SHA-256 keep one permanent PDF per issuance across all accounts. Identical retries return the existing ID. A different PDF for an existing issuance, or assigning the same PDF to another issuance, returns 409 without replacing the source.
- Forms appear in the shared document catalog and use the existing authenticated `/api/bonds/documents/{id}` download route. There are no per-investor PDF copies. Form-backed bids can reference the shared ID without copying its bytes. Completed upload chunks are removed; unfinished uploads retain the existing bounded expiry policy.

The updated API and database migrations were deployed on 8 November 2026, and the supplied November form was published as one shared document. Users need the updated client for the Calendar preview action. The PDF is not bundled with the app. Replacing an already published form is not exposed in this version.

## Verification

Backend integration tests exercise actual-owner authorization on every chunk, unauthenticated rejection, cross-account listing/download, concurrent identical retries, one-row storage, conflict preservation, malformed PDFs and canonical server calendar metadata. Shared client tests cover month-start availability before bidding, reopening identity matching, owner versus staff permissions and the upload protocol. Production publishing is separate from these local checks.

On 8 November 2026, all 74 backend regression tests passed against a disposable loopback PostgreSQL cluster; the additional interrupted-upload test also passed. All 359 shared Android-host tests passed using a temporary source copy omitting the pre-existing uncompilable `StockPriceHistoryTest.kt`; the repository's stock tests were not edited. Android debug assembly and both simulator and physical-iPhone Kotlin compilation passed. At implementation verification time, no physical-device UI test or production rollout had been performed; the subsequent authorized production rollout is recorded below. Logs: `/private/tmp/struct-bond-form-api-full-tests.log`, `/private/tmp/struct-bond-form-chunk-tests.log` and `/private/tmp/struct-bond-form-final-checks.log`.

## Production rollout - 8 November 2026

The exact production-main source passed all 68 tests and `build`. Commit `52d2985c05f32802a680611a64385bc3323a3bc5` was published to main. GitHub Actions could not start because of an account billing lock, so the tested JAR was packaged with the existing Java 21/non-root runtime and deployed manually to Cloud Run. The runtime image was verified locally. Docker Desktop's proxy interrupted its large layer transfer; host-side OCI upload completed the exact image without changing its filesystem or runtime configuration.

Revision `gabo-portfolio-api-forms-52d2985` passed zero-traffic health verification before promotion. Flyway V11 and V12 are applied. V11 carries only the preceding additive guest-receipts table, preserving migration numbering; unrelated guest-mode API behavior was not deployed. Existing runtime identity, secrets, resource/scaling settings and API origins were preserved. Both Cloud Run service URLs returned healthy responses, and the public website proxy remained operational.

The owner-authorized operational publication used the existing deployment database connection, without creating sessions, changing credentials, adding users or modifying investments. The shared November form is bound to `2026-11-5y-new`, issuance 11 November, settlement 13 November, maturity 7 November 2031. Stored bytes match the attachment exactly; the canonical issue/hash query returns one copy.

Document ID: `b1388a58-91c6-4bc8-9eae-b0957e57d2fe`. PDF bytes: `314370`. SHA-256: `7ff7e4c4ddeed37b9f96377d7a313c79aa17b397c4f15f6047d9b04275f4bd32`.

Form listing and downloads remain authenticated. No customer session was used for production verification; authenticated cross-account download and owner-only upload enforcement were exercised in the isolated integration suite. A schema-only rollback record was saved privately; no production account rows or private PDFs were exported.

## iPhone build integration follow-up

The Android Studio iOS build sets `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES`, skipping the Xcode Gradle phase after the IDE prebuilds Kotlin. A clean IDE build reproduced `Unable to find module dependency: Shared` because its separate build-products folder lacked the framework symlink. `app/iosApp/Configuration/Config.xcconfig` now explicitly includes the SDK/configuration-specific shared framework directory in `FRAMEWORK_SEARCH_PATHS`, preserving inherited paths and the IDE's single-build behavior. The same IDE-mode device build then passed, as did the normal signed device build. Evidence: `/private/tmp/struct-android-studio-repro-before.log`, `/private/tmp/struct-android-studio-repro-after.log`, and `/private/tmp/struct-calendar-concise-iphone-build.log`.

## Form-backed bids (local implementation)

Calendar and the application PDF viewer offer **Record bid** for a verified form from the first day of the issuance month through its closing date, inclusive. The real Rwanda date controls eligibility; selecting a past day/month does not reopen a closed window. Before the published opening day, brokers may accept applications, so early-month recording is supported. Closing-day clock times are not published in the bundled schedule; confirm the deadline with the broker.

Only the amount is chosen, in RWF 100,000 increments. The backend extracts the bond number, tenor and issuance/settlement/redemption dates from the shared PDF and checks those against the server calendar. It extracts once for a new form, or lazily enriches an existing stored form once, without uploading or duplicating it per investor. Unreadable, mismatched or unverified forms cannot create a bid. November's supplied PDF was verified with the new parser as FXD 6/2026/5YRS, issuance 11 November 2026, settlement 13 November, maturity 7 November 2031.

`POST /api/bonds/application-forms/{id}/bids` accepts only `amount` and a retry UUID `bidId`; the authenticated account, server dates and verified PDF determine all other fields. Flyway V16 adds references and the original bid amount to the existing purchase row. A retry returns the same purchase even after closing, while a new bid after closing is rejected. No form bytes are copied. Client-supplied status, dates or owner fields are rejected.

Saving returns to Home and shows the bond as **Awaiting BNR confirmation**. It stays `submitted`, excluded from active worth/coupon income. Its original application form remains linked. This is a personal record, not a submission to BNR or a broker. No commission or final coupon is inferred from a blank form. Published results can enrich the pending terms through the existing issuance-matching flow, but allocation still needs explicit confirmation or personal confirmation evidence. Actual fees supplied by that evidence are not replaced with the bid's unknown-fee placeholder.

Verification includes date boundaries, early-month recording, malformed/spoofed requests, original-amount retention and retry deduplication, legacy form enrichment, actual PDF extraction, pending Home visibility and exclusion from active totals. V16 has not been deployed; the production API rollout above covers forms/downloads only. The updated app has been built, installed and launched on the connected iPhone.

The bid follow-up also aligns Home's shared financial date and the API compact overview with Rwanda time. Previously they used UTC while Calendar used Rwanda time; a bid recorded during Rwanda's first two hours of a day could be filtered out as a future purchase until UTC caught up. Verified public confirmation uses the issuance date, not the earlier bid-recording date, while retaining the original bid amount for retry/history purposes.

Validation: 88 API tests and 382 shared Android-host tests passed. Android debug app/test builds and the signed iPhone build passed. Both native Android calendar journeys pass, covering parsed form terms and older server responses, formatted amount steps, visible drafts, privacy after saving, PDF preview and explicit sharing. Test data and screen captures are synthetic; production records were not changed.

Calendar now always exposes **Add temporary purchase** for an eligible calendar bond, even when the published form is absent or the deployed API has not added parsed `bidTerms`. The calendar passes that issue directly to amount entry; the bond selector is hidden. Parsed forms still use the verified form-bid endpoint. Older form responses use the existing submitted-purchase endpoint with calendar dates and the existing provisional commission; this remains an unconfirmed personal record. Both paths return to Home and use the real Rwanda date to stop new entries after closing. Browsing other months never reopens closed subscriptions.


Numeric-field screenshot masks use passive layout/text instead of a disabled text field, which could intercept taps over the live input. Visibility changes preserve input composition. The final temporary-purchase flow uses the noneditable selector described below.

Amount display refinement: temporary purchases now use a noneditable amount card instead of a text field. It starts at RWF 100,000 and changes only with the two formatted 100,000-step buttons. Grouping follows the app language (100,000 in English), and long amounts shrink to fit on one line. Draft screens ignore manual number hiding; screenshot redaction and the native shield-unavailable safety fallback remain active. Saving returns to the original privacy setting, so saved purchases can be hidden as usual. No local eye button or keyboard is offered.


The pending purchase page uses matching rounded cards: a bond header with pending status and labeled settlement/maturity dates, an icon notice for “No file needed”, the amount selector, and a cost or application-terms summary. English and French copy are included.

The iPhone startup crash was traced to failure cleanup accessing uninitialized state during immediate constructor execution. Startup now runs after all state delegates and caches initialize, with a regression test for immediate public-asset failure. Android Studio’s Xcode build mode also runs `syncComposeResourcesForIos` when it skips framework compilation; a test build with its resources removed verified they were repackaged. The signed app launched successfully and remained running on the connected iPhone.


Bond identities: November 2026 uses `FXD 6/2026/5YRS`, verified from the supplied shared application form (issuance 11 November, settlement 13 November, maturity 7 November 2031). The bundled calendar carries this published identity while retaining its descriptive auction title. New temporary purchases save the official code. Home, history and details resolve existing descriptive pending names using a unique settlement/maturity/tenor match without rewriting saved records; confirmed or already-coded names are retained. Verified shared-form terms and public issuance/security data can supply codes for other auctions. Explicit calendar reopenings inherit only the matching original security identity, never its auction price or allocation.
