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

## Siri, Shortcuts and Android Assistant

iPhone exposes three App Intents and automatic App Shortcuts: Import PDF, Add purchase, and Open calendar, with English/French titles and invocation phrases. Intents require device authentication, foreground the app, and queue the same navigation whitelist behind the app's own onboarding/lock state. They never create records in the background. Shortcuts can also be assigned by the user to system surfaces such as the Action Button where available.

Android declares OPEN_APP_FEATURE with English/French inline inventory mapped to stable shortcut IDs. The capability has a no-parameter fallback that simply opens the app. Google Assistant production voice discovery requires Google Play deployment/App Actions review and compatible Assistant configuration; a sideloaded debug APK does not activate that service. This integration does not promise another assistant vendor's support.

Verification: Android compile/lint and installed launcher/Assistant-extra dispatch instrumentation passed. Xcode extracted all three App Intents and localized shortcut metadata. Apple's actual Shortcuts app displayed all three actions; running Open calendar foregrounded the disposable simulator app at Calendar. Siri speech recognition and Google Play review are external activation steps, not asserted as end-to-end voice tests.

## Lock Screen widgets

iPhone's existing What’s next extension now supports accessoryInline, accessoryCircular and accessoryRectangular in addition to its Home Screen sizes. Compact layouts use system-rendered styling and the same Rwanda-date timeline/deep link. They show public issue facts or generic payment labels/dates, never personal holding names or amounts. Lock Screen placement is user-controlled.

Both Android providers advertise home_screen and keyguard with an initial keyguard layout. Android's newer Lock Screen widget host is OS/OEM-dependent; this metadata cannot make the Honor Android 15 expose an unsupported widget surface. Home Screen widgets continue to work. No lock bypass or background service is added.

Verification: signed iPhone and simulator builds passed; the native Lock Screen picker listed Struct Capital's circular and rectangular families and accepted both widgets. The simulator compositor did not display the wallpaper-edit overlays in captured screenshots, so visual typography on a physical Lock Screen still deserves user review. Native Swift date/expiry/French/privacy checks and all three Android widget projection/render/provider instrumentation checks passed. Inline registration is compiled; its above-clock placement remains a user-device check.
