# Native integrations

## App-icon shortcuts

Android launcher shortcuts and iPhone Home Screen quick actions expose Import PDF, Add purchase, and Open calendar. A shared whitelist accepts only these navigation requests. Requests wait for onboarding/authentication and an idle UI; they do not save purchases, bypass app locking, or upload documents. Logout clears pending requests. Android marks an intent as dispatched so activity recreation does not open the same action twice. Internal app links are excluded from PDF URI handling.

Android uses static launcher shortcut resources and a `structcapital://action/<action>` route. iPhone installs native quick actions and handles both cold scene connections and warm shortcut selection. The same URL route is available to platform integrations.

Verification: Android build/lint and shared whitelist test; installed shortcut/dispatch instrumentation; signed iPhone build and simulator launch/quick-action checks. No production account records are changed by shortcut testing.
