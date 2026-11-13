# Native integrations

## App-icon shortcuts

Android launcher shortcuts and iPhone Home Screen quick actions expose Import PDF, Add purchase, and Open calendar. A shared whitelist accepts only these navigation requests. Requests wait for onboarding/authentication and an idle UI; they do not save purchases, bypass app locking, or upload documents. Logout clears pending requests. Android marks an intent as dispatched so activity recreation does not open the same action twice. Internal app links are excluded from PDF URI handling.

Android uses static launcher shortcut resources and a `structcapital://action/<action>` route. iPhone installs native quick actions and handles both cold scene connections and warm shortcut selection. The same URL route is available to platform integrations.

Verification: Android build/lint and shared whitelist test; installed shortcut/dispatch instrumentation; signed iPhone build and simulator launch/quick-action checks. No production account records are changed by shortcut testing.

## PDF sharing into the app

Android shared PDFs now open a preparation screen matching the iPhone layout: brand header, filename/size card, three import steps, privacy note, and a ready-for-review state. Light/dark styling and English/French copy follow app preferences. Preparing keeps the validated PDF in the existing in-memory inbox; only Open Struct Capital releases it to the normal authenticated/guest import path. Cancel or Android Back discards the waiting file. Verification: debug build, Android lint, iPhone shared-code compilation, four inbox/parser unit tests, and the installed cold/warm URI-sharing regression test passed. A synthetic PDF displayed the preparation and ready states in light/dark Android 16 emulator screenshots; Open Struct Capital resumed normal entry, and Android Back dismissed preparation. No investment was saved.

Android already receives PDF SEND/VIEW content URIs; its lifecycle/URI-grant test passes on a fresh stable Android 16 emulator. The test explicitly selects account entry so restored guest mode cannot consume its sign-in mailbox fixture. An older API 36.1 emulator crashed inside its EGL renderer during activity recreation; the stable image with host graphics passed.

iPhone has a native PDF document Action extension, labelled “Import into Struct Capital” (French: “Importer dans Struct Capital”) in the actions list below the app row, with an outlined monochrome Struct Capital logo icon (hollow S/C shapes and outer hexagon). The existing ShareImport bundle identifier/target is retained for installation compatibility. It accepts one PDF of up to 10 MB, checks its header and bounded file size, and ignores ancillary source URLs such as Safari's. Preparing an import stages the file locally in the existing App Group; it does not upload or save portfolio records. The user opens Struct Capital to continue through its normal authentication, parsing, review and save flow. The host app no longer advertises itself as a PDF viewer, avoiding a second entry in the app row. No unsupported attempt to launch the containing app from the Action extension is used.

The coordinated single-file disk inbox refuses to overwrite a waiting file. Files are protected until first unlock after restart, removed only for the matching delivery after intake, and expired after 24 hours when the inbox is next accessed. The widget extension reads only its separate `next-widget.json`; staged PDFs live under `pdf-import-inbox`. No PDF contents or private exception details are logged. Disabling GABO_WIDGETS_ENABLED also omits this App Group-dependent extension.

Action-list update verification: signed device and simulator builds passed. The simulator share service registered an enabled action cell titled “Import into Struct Capital” and loaded its initial template icon. The subsequent brand-icon build bundles transparent outlined monochrome logo PNGs at 2×/3× in CFBundleIconFiles; its signed device build passed. The final signed build was installed on the connected iPhone. Placement/favourites on the physical phone remain user-controlled.

Verification: signed device/simulator builds, Foundation checks for invalid/oversized input, safe filenames, overwrite refusal, matching cleanup and expiry. A synthetic PDF was shared from Safari through the actual extension, prepared successfully, and delivered to the disposable guest app's existing parser. The parser rejected the non-investment fixture as expected; no records were created.

## Siri, Shortcuts and Android Assistant

iPhone exposes three App Intents and automatic App Shortcuts: Import PDF, Add purchase, and Open calendar, with English/French titles and invocation phrases. Intents require device authentication, foreground the app, and queue the same navigation whitelist behind the app's own onboarding/lock state. They never create records in the background. Shortcuts can also be assigned by the user to system surfaces such as the Action Button where available.

Android declares OPEN_APP_FEATURE with English/French inline inventory mapped to stable shortcut IDs. The capability has a no-parameter fallback that simply opens the app. Google Assistant production voice discovery requires Google Play deployment/App Actions review and compatible Assistant configuration; a sideloaded debug APK does not activate that service. This integration does not promise another assistant vendor's support.

Verification: Android compile/lint and installed launcher/Assistant-extra dispatch instrumentation passed. Xcode extracted all three App Intents and localized shortcut metadata. Apple's actual Shortcuts app displayed all three actions; running Open calendar foregrounded the disposable simulator app at Calendar. Siri speech recognition and Google Play review are external activation steps, not asserted as end-to-end voice tests.

## Lock Screen widgets

iPhone's existing What’s next extension now supports accessoryInline, accessoryCircular and accessoryRectangular in addition to its Home Screen sizes. Compact layouts use system-rendered styling and the same Rwanda-date timeline/deep link. They show public issue facts or generic payment labels/dates, never personal holding names or amounts. Lock Screen placement is user-controlled.

Both Android providers advertise home_screen and keyguard with an initial keyguard layout. Android's newer Lock Screen widget host is OS/OEM-dependent; this metadata cannot make the Honor Android 15 expose an unsupported widget surface. Home Screen widgets continue to work. No lock bypass or background service is added.

Verification: signed iPhone and simulator builds passed; the native Lock Screen picker listed Struct Capital's circular and rectangular families and accepted both widgets. The simulator compositor did not display the wallpaper-edit overlays in captured screenshots, so visual typography on a physical Lock Screen still deserves user review. Native Swift date/expiry/French/privacy checks and all three Android widget projection/render/provider instrumentation checks passed. Inline registration is compiled; its above-clock placement remains a user-device check.

## Add to phone calendar

Expanded calendar-event details now offer Add to phone calendar on iPhone and Android. Each action creates an editable all-day draft for that event's civil date. Public issuances include the public title/milestone; coupon and maturity exports use generic labels with no holding name, broker, amount or internal record ID. Notes explain that dates are indicative and include a link back to the app's calendar. The native editor lets the user choose alerts/calendar and explicitly save or cancel. Repeated exports may create duplicates; exports are copies and do not automatically track later date changes, as stated beside the action.

Android uses ACTION_INSERT with both Events.CONTENT_URI and the event item MIME type (required by the Honor editor), UTC all-day start and exclusive next-day end. It requests no calendar permissions and handles missing calendar apps. iPhone uses EventKitUI's iOS 17+ remote editor without requesting calendar read/write access or calling programmatic save. Local Gregorian civil dates preserve the selected day abroad and through DST; the all-day end is the last second of that day.

Verification: Android build/lint, two shared privacy-projection tests, installed intent/date validation instrumentation, native Swift leap/DST/time-zone checks, signed iPhone and simulator builds passed. Tapping the app's export action for a fictional coupon opened Apple's editor with matching start/end day and generic title; cancellation returned to the app. The Honor native editor accepted the equivalent standard intent with the correct leap-day start/end and all-day state. The final installed Honor app also opened the editor from the public Announcement event, preserving its title and December 1 date. Test drafts were discarded without saving. The final builds were installed on both connected phones.


## Pending share orders

Signed-in accounts can save a pending RSE share order from Home or Add shares. It uses a separate `/api/bonds/share-orders` API/table, outside PortfolioSnapshot holdings and all investment/valuation calculations. Guest mode continues to record completed purchases only. The form has only a company dropdown populated from the market feed and a share-count input. The market price is automatic and read-only, with a compact 1.71% commission/total summary. Dates, broker details, notes, availability checkboxes and explanatory panels are omitted. Request dates are supplied internally for API compatibility; older metadata is preserved when editing. Dates never automatically create a trade.

Order estimates use decimal HALF_UP money arithmetic and the configured 1.71% combined market commission on gross consideration. For 1,000 shares at RWF 720: gross 720,000, estimated commission 12,312, estimated payable 732,312. The server recomputes these figures and ignores supplied totals/rates. Existing contract-note fee breakdowns remain actual purchase data; they are not inferred from an estimate.

Editing uses a version check to reject stale changes from another phone. Cancelling removes only the app's pending record, with an explicit notice to contact the broker about any real order. Record executed purchase requires a separate confirmation and the normal purchase review, including actual dates, price and contract-note charges. Conversion validates the full requested security/quantity, creates the purchase and closes the request in one account-locked transaction, and returns the existing purchase on retry. Partial fills must be reviewed by adjusting the pending record first. The app never submits, cancels or executes an actual market order.

Backend activation requires the additive V18 migration and new API. An older/unreachable server leaves the existing portfolio available and disables pending-order saves. Pending orders are cleared from client memory on account reset, isolated by authenticated owner on the server, and included in account-deletion cleanup.


Verification (13 November 2026): two decimal-estimate tests, 24 existing account-model tests, Android build/lint, iPhone shared compilation and signed device build passed. The installed Android synthetic screen test verified the RWF 720 default quote, 1,000-share amount, 732,312 estimated total and pending-only save (zero completed-equity writes). The iPhone build is installed; no Honor was connected. All 100 backend tests passed against the disposable local PostgreSQL cluster, including V18, per-owner isolation, stale edits/cancellation, partial-fill rejection and idempotent full conversion.

With explicit user approval, production revision `gabo-portfolio-api-orders-e6d013ae0eeb` was activated at 100% traffic. Runtime configuration was compared with the previous revision and preserved. Staged/live health returned UP; Flyway applied V18, and an unauthenticated orders request returned 401. The image `europe-west1-docker.pkg.dev/gabo-portfolio-api/portfolio/api:orders-e6d013ae0eeb` has index digest `sha256:bce704977462b40377d6bf1cd39702323796efa6914bf9bac817829d91b17df0`. It packages the locally tested JVM boot JAR using the existing non-root Java 21 runtime settings. No code commit was made, and no production order/holding was created during deployment verification.

The simplified form revision was rebuilt for Android/iPhone and installed on the connected iPhone. The Android screen check opened the company dropdown, verified both synthetic companies, selected BOK, confirmed the automatic RWF 720 price and RWF 732,312 total, checked the removed fields were absent, and saved only a pending order. No backend redeployment was needed for this UI simplification.
