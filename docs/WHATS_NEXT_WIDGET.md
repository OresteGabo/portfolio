# What’s next — home-screen widgets

Two Android providers are available in the launcher widget picker and in Settings → Home screen widgets:

- Small (2 × 2): one next event with its status, public bond title or generic payment label, date, and saved-data timestamp.
- Medium (4 × 2): the same primary event plus a second upcoming event. Each section opens the calendar at its event date.

An active subscription takes priority, followed by a form that is actually present and available in its issuance month. Otherwise the nearest upcoming subscription or expected coupon/principal payment is selected. Closed windows and past payment dates are excluded using Africa/Kigali dates. Expected payments are schedule estimates, not receipt confirmations. Receipt records marked received are omitted from the exported payment dates.

The app saves a minimal private widget projection after an account portfolio loads. It contains public issuance facts, reminder flags, generic payment dates and language. It never contains credentials, balances, payment amounts, holding names or purchase IDs. Guest/sample data is not exported. The safe snapshot survives background app locking; logout, guest entry, account reset and invalidated sessions clear it.

Widgets use the last saved app data. Opening/refreshing the app updates that projection when its contents change. A matching Firebase form-ready message can update a known cached issue without opening the app. The system’s 30-minute widget update request re-evaluates dates locally; it does not promise a 30-minute network refresh or exact delivery at midnight. Launchers may defer updates. The saved timestamp stays visible so old cache data is not presented as newly fetched. Widgets supplement push alerts.

Both widgets use lightweight framework RemoteViews; no additional widget SDK, background polling service, or account credential sharing is required. Pinning uses the launcher's confirmation; unsupported launchers show manual widget-picker instructions. French/English content follows the app language after the next saved projection. Empty or cleared widgets invite the user to open the app.

Verification: debug APK and instrument APK builds, Android lint, three emulator widget tests (date/priority/form boundaries, malformed dates, native layout inflation/provider discovery, cache clear), secure-session regression suite, and physical Honor launcher previews. After the owner unlocked the app, tapping the medium widget’s second event opened Calendar at January 2027, matching its 11 January event. Widget navigation honors the existing authentication gate.

## iPhone

The native `NextWidget` WidgetKit extension supports system small and medium families with the same event priority and privacy projection. SwiftUI handles iPhone widget sizing and the system widget background. Small uses a widget URL; the medium secondary event has its own Link. `structcapital://calendar?date=YYYY-MM-DD` opens the shared calendar after existing authentication completes.

The app and extension share only the typed projection in App Group `group.dev.orestegabo.portfolio.whatsnext`, using an atomic JSON file protected until the first unlock after restart. Unknown input fields are discarded when encoding the typed cache. The app requests timeline reloads after updates/clears. WidgetKit timelines include Rwanda-midnight transitions and request a later refresh; iOS controls actual reload timing. No widget API credentials or independent authenticated polling are used.

The existing Personal Team successfully provisioned both the app and extension with the App Group capability, independently of APNs. `GABO_WIDGETS_ENABLED=YES` is the default. Set it to `NO` to omit the extension and its App Group entitlement if building in an environment without that capability. Password-sharing/push entitlement combinations retain their existing settings. The optional Live Activity extension remains controlled by the separate push flag.

Add on iPhone: open/unlock Struct Capital once, then touch and hold the Home Screen → Edit → Add Widget → Struct Capital. Swipe between small and medium. Settings also shows instructions; iOS does not offer Android-style automatic pinning.

Verification: signed physical-device and simulator builds; app/extension App Group entitlements matched their profiles; the real iPhone produced a valid shared projection (only public issue metadata and generic payment dates); small/medium native gallery previews were visually checked on a disposable iPhone simulator with synthetic data. The owner confirmed adding the widgets on the physical iPhone. Foundation checks in `scripts/tests/next-widget-data.swift` cover Rwanda boundaries, expired windows, priority, form-month availability, malformed dates, French labels, URLs and the privacy whitelist.
