# Date presentation and device alerts

Read-only app text formats ISO date-only values as `8 Nov 2026`, year/month values as `November 2026`, and timestamps with weekday, full date and local time. English and French follow the current app language. Statements, import receipts, calendar form details, date ranges and security activity use the same presentation helpers. Inputs, API requests, sorting, source PDFs and persisted records retain their original values. A date-only value never passes through time-zone conversion.

Security alerts and sign-in activity support these optional fields:

- `deviceModel`: manufacturer/model on Android; an Apple hardware name when the identifier is listed in the checked-in Xcode profile mapping, otherwise family and hardware identifier.
- `osVersion`: OS name/version (and API level on Android).
- `appVersion`: installed version and build.
- `signedInAt`: server timestamp captured when a new-device alert is created.
- `firstSeen` and `lastSeen`: server timestamps for first registration and most recent session-device report. Last seen is activity, not proof of a currently active session or a fresh password entry.

The API's `V13__sign_in_device_details.sql` adds optional device details and last-seen storage. Existing devices start with empty details and their original registration time; later app reports enrich them. Older clients and previously saved alerts remain supported. Alert metadata snapshots the original event even when the device's app version later changes.

Only bounded model/OS/app strings are accepted; client-supplied account IDs and timestamps are ignored. These strings are informational, not authentication evidence. No personal device name, location, serial number or IP address is added by this change. Sign-in activity remains scoped to the authenticated account.

Deploy the API migration and updated API before distributing the updated clients. The older API tolerates the clients' extra fields but cannot retain or return them. This change has not itself deployed the API or installed a new app on a physical phone.
