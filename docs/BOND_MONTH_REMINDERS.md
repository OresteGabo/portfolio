# Bond month reminders

Choose a month **and year** in Calendar, then tap the **Set reminder** badge in the subscription header. The choice is saved on the account and applies to that month's published issuances, using the closing-book month. The active badge shows a check and **Reminder set**; tapping it again removes the reminder. A short toast confirms the result and explains when phone alerts still need setup. Guests do not register for remote delivery. Months without a future/current published window do not offer a new reminder.

| Milestone | In-app inbox | Phone for selected months | Live presentation |
| --- | --- | --- | --- |
| Actual form published and available in its issuance month | Yes | Yes | No |
| Published subscription opening date | Yes | Yes | Start/update |
| One day before closing, through the published closing day | Yes | Yes | Start/update |
| Day after the published closing date | Yes | Yes | End/remove |

Users without a selected month receive only the inbox entries. Existing category preferences still apply. Enabling a month explicitly enables the subscription category. No exact broker closing hour is inferred from date-only calendar data; all milestone dates use Africa/Kigali. A late worker does not send obsolete open/closing alerts. Stable event keys and the existing delivery outbox prevent routine duplicate notifications; migration V17 preserves existing subscription event IDs/read state.

## Phone presentation

- Android: a high-priority FCM data message builds a standard ongoing notification with the public issue title, stage, published closing date, and **Stop live status** action. On Android 16.1+ it requests Live Update promotion through the documented public extra; the OS/vendor may decline promotion. Older phones retain the standard ongoing card. It expires at the end of the published closing day, without a permanent background service. Dismissed live status is not reposted for the same issue; later milestones may still send ordinary alerts.
- iPhone: the embedded BondLiveActivity WidgetKit extension supports Lock Screen and Dynamic Island presentations. ActivityKit push-to-start tokens and activity update tokens are registered separately from ordinary APNs tokens. Opening/closing starts an activity or updates an existing active one. Each activity uses the system's maximum eight active hours, with up to four further hours of Lock Screen retention. Closing can start another activity when the earlier one has expired; the app does not continuously restart activities to bypass the limit. English and French text are supported. ActivityKit requires user/system permission and remote start support (the app's existing iOS minimum is 18.2).
- Removal/logout clears local live status. Server delivery rechecks account ownership, category permission and selected month; activity tokens belong to registered account devices. Content contains public bond details, never balances or holdings.

## Activation still required

Neither Firebase nor Apple push is configured in this checkout/account. A successful local build does **not** enable real remote delivery.

1. Deploy the companion `gabo-portfolio-api` changes, including Flyway V17, after its existing V16 changes. The sample Ktor `server` module is unrelated.
2. Android: register application ID `dev.orestegabo.portfolio` in Firebase, supply `app/androidApp/google-services.json`, and configure the server's `GABO_PUSH_FIREBASE_PROJECT` with an authorized server identity. Never bundle server credentials in the APK.
3. iPhone: enable Push Notifications for the app in a paid Apple Developer team; configure APNs signing/profile and the server's existing `GABO_PUSH_APPLE_TEAM`, `GABO_PUSH_APPLE_KEY_ID`, and `GABO_PUSH_APPLE_KEY` securely. Set `GABO_PUSH_ENABLED=YES` with matching sandbox/production values when building. The extension is signed and embedded only in push-enabled builds. Normal Android Studio/Xcode device runs keep it out of the app, so they do not require a separate extension provisioning profile.
4. Schedule authenticated `GET /api/bonds/notifications/bond-reminders/dispatch` every 15 minutes, including weekends, using the existing `CRON_SECRET` as its Bearer credential. Unlike an app refresh, this worker detects published forms while phones are closed. The existing market daily job also runs the milestone sync.
5. Grant notifications on each phone. Verify delivery while backgrounded, after normal app dismissal, and while locked. Android force-stop, iOS notification/Live Activity settings, Focus and vendor restrictions can affect presentation.

No live provider delivery, deployment, Apple account change, or cloud scheduler creation was performed as part of local implementation. No email delivery was added.

## Verification

Tests cover actual-form readiness, Rwanda date boundaries, open/closing/closed milestones, expired delivery suppression, stable inbox events, month/account push opt-in, outbox idempotency, month persistence, failed saves, logout cleanup, and Android ongoing-card transitions/expiry/opt-out. Backend integration uses only disposable loopback PostgreSQL databases. Provider-delivered APNs/FCM still requires the activation above.
