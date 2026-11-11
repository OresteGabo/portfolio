# Native integrations

## App-icon shortcuts

Android launcher shortcuts and iPhone Home Screen quick actions expose Import PDF, Add purchase, and Open calendar. A shared whitelist accepts only these navigation requests. Requests wait for onboarding/authentication and an idle UI; they do not save purchases, bypass app locking, or upload documents. Logout clears pending requests. Android marks an intent as dispatched so activity recreation does not open the same action twice. Internal app links are excluded from PDF URI handling.

Android uses static launcher shortcut resources and a `structcapital://action/<action>` route. iPhone installs native quick actions and handles both cold scene connections and warm shortcut selection. The same URL route is available to platform integrations.

Verification: Android build/lint and shared whitelist test; installed shortcut/dispatch instrumentation; signed iPhone build and simulator launch/quick-action checks. No production account records are changed by shortcut testing.

## PDF sharing into the app

Android already receives PDF SEND/VIEW content URIs; its lifecycle/URI-grant test passes on a fresh stable Android 16 emulator. The test explicitly selects account entry so restored guest mode cannot consume its sign-in mailbox fixture. An older API 36.1 emulator crashed inside its EGL renderer during activity recreation; the stable image with host graphics passed.

iPhone has a native PDF Share extension. It accepts one PDF of up to 10 MB, checks its header and bounded file size, and ignores ancillary source URLs such as Safari's. Preparing an import stages the file locally in the existing App Group; it does not upload or save portfolio records. The user opens Struct Capital to continue through its normal authentication, parsing, review and save flow. No unsupported attempt to launch the containing app from the Share extension is used.

The coordinated single-file disk inbox refuses to overwrite a waiting file. Files are protected until first unlock after restart, removed only for the matching delivery after intake, and expired after 24 hours when the inbox is next accessed. The widget extension reads only its separate `next-widget.json`; staged PDFs live under `pdf-import-inbox`. No PDF contents or private exception details are logged. Disabling GABO_WIDGETS_ENABLED also omits this App Group-dependent extension.

Verification: signed device/simulator builds, Foundation checks for invalid/oversized input, safe filenames, overwrite refusal, matching cleanup and expiry. A synthetic PDF was shared from Safari through the actual extension, prepared successfully, and delivered to the disposable guest app's existing parser. The parser rejected the non-investment fixture as expected; no records were created.
