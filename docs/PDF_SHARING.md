# PDF handoff and sharing

## Receive a PDF

- **Android:** choose Struct Capital from a PDF's Share or Open with menu. The single-task activity receives both a new launch and a new intent in an existing app instance. The content URI is read immediately using the sender's grant; the app does not ask for broad storage access or download a remote URL.
- **iPhone:** choose Open in / Copy to Struct Capital where the sending app offers document opening. The app declares the PDF document type and receives its URL through SwiftUI. Local/security-scoped file reading keeps the same 10 MB limit as the native picker. Only the system's immediate-child copy in this app's Documents/Inbox is removed; originals outside that Inbox are preserved, including paths containing parent-directory segments.

Incoming bytes wait in memory through onboarding, sign-in and device unlock. An existing review or unsaved bond/purchase editor is not replaced. One waiting file is retained; another file cannot silently overwrite it. Android delivery IDs suppress repeated delivery after activity recreation. The pending file can be dismissed, and signing out clears it. It is not a durable upload queue across process termination.

After authentication, the file enters the existing local parser without opening a second picker. Bond confirmations, share contracts, issuance reports and BK Capital statements follow their existing review/import paths and matching rules. Statements retain their existing missing-document reminder reconciliation; a public issuance report is not treated as a personal allocation. Unsupported/invalid files show an error instead of creating a purchase.

### iPhone signing limitation

This implementation adds document Open In support without new signing capabilities, suitable for the current free Personal Team. It does not add a dedicated iOS Share extension to the row of share targets. The proposed extension-to-host file queue needs a provisioned App Group and an extension target; that capability is unavailable under the current signing setup. See Apple's [supported iOS capabilities](https://developer.apple.com/help/account/reference/supported-capabilities-ios) and [App Group configuration](https://developer.apple.com/documentation/xcode/configuring-app-groups). Do not add an unsupported entitlement to the existing free-team build.

## Share a download or generated report

The document action and annual/portfolio report exports call the native PDF share sheet. iPhone's sheet includes Save to Files and installed PDF-capable applications; Android offers installed compatible recipients. The exported file retains a sanitized PDF filename.

Android uses a non-exported FileProvider scoped only to `cache/pdf-share/`, content URIs and temporary read permission. It does not grant write permission or expose the entire cache. Old export directories are removed after 24 hours on a later export. iPhone writes a unique temporary file, anchors the iPad popover, and removes the file when its activity completes or is cancelled. The existing explicit save-picker implementation remains available behind the native-actions interface.

Messages say the PDF is ready to share, not that a recipient delivered it. Android's chooser does not provide a reliable sent/saved confirmation; iPhone cancellation returns without success feedback. Both share sheets count as system interaction for the existing background privacy handling.

## Verification

- 158 shared Android host tests and 167 iPhone simulator tests pass, including exact-byte parsing of the four supported review kinds without a second picker, mailbox bounds/deduplication, native iPhone local URL reads, original-file preservation, traversal-safe cleanup, oversized rejection and refusal to fetch remote URLs.
- Two Android instrumentation tests pass: cold/warm PDF intents, activity recreation, target registration, provider bytes and read-only grants, native share sheet presentation/cancellation, and Documents category/expansion/share controls. ActivityScenario's launch intent is restored in the fixture after testing onNewIntent so lifecycle tracking can close the activity reliably.
- The full unsigned iPhone simulator app builds, including the Swift URL handler. An OS file-URL opening on a disposable iPhone SE simulator launched Struct Capital's PDF handler, cleaned its Inbox copy and displayed the pending-import sign-in notice against a signed-out localhost fixture.
- WhatsApp itself and physical-device recipient delivery were not exercised. No real account, production upload, message recipient or original user PDF was used.
