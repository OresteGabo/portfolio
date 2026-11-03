# Reporting a problem

Users can open **Report a problem** from Settings → Help & support, from a screen's top-right More options menu, or from sign-in. The report opens above the current screen; closing it returns to that screen. It works for guest users and does not require an authenticated backend request.

The first version prepares an email to **support@structcapital.net**. It does not create a server ticket, upload a report, confirm delivery, or provide a report status inbox.

The form collects an issue category and a required description (10–2,000 characters after trimming), with optional reproduction steps and expected result/source (up to 1,000 characters each). Categories cover broken features, incorrect/missing data, slow/unresponsive behavior and other concerns. Users review the exact email body before selecting **Open email draft**. They still send the email in their mail app. If no handler opens, the form keeps the report visible and offers **Copy report**. Copy is also available regardless of mail setup. Screenshots can be added manually in the mail app after hiding private information; the app does not capture or attach any.

Optional app details contain only installed app version/build, operating system version, language and a fixed screen name. PDF filenames, investment names, account identifiers, email addresses, balances, original documents, API URLs, credentials, logs and raw errors are not read or attached automatically. Screen names use an allowlist; PDF previews become “PDF viewer”. The user can remove all app details before review.

Free-form drafts stay in Compose memory. They are not persisted in preferences, saved-instance state, logs or the backend. Closing a nonempty draft asks for confirmation. Drafts can be lost when the process/activity is recreated; locking or changing account/session clears the report overlay. The existing app privacy/capture protections remain authoritative.

English and French labels, review text, status and email body headings use the existing app-language system. User-written text is never translated or modified beyond field limits and trimming at composition.

Implementation: `IssueReport.kt` holds bounded report formatting, screen-name filtering and encoded mailto construction; `IssueReportScreen.kt` implements edit/review/copy and explicit mail handoff. `NativeActions.composeEmail` uses Android ACTION_SENDTO or iOS openURL. Its Boolean reports handler opening only, never email delivery. Native URI recipients are restricted to the app's support/security contacts; this flow always uses support.

Validation: `IssueReportTest` checks bounds, optional metadata, private-title filtering, French formatting and URI encoding/header-recipient protection. `IssueReportingUiTest` uses a debug-only empty activity and a fake mail action to check category selection, review, metadata exclusion/inclusion, unavailable-mail fallback, copying, opened-draft wording, and discard confirmation. It never launches a real mail app or sends an email. Real mail-client composition/delivery and iOS visual checks require device review.

Verified 3 November 2026: **355 shared Android-host tests passed**, including five report-format/privacy/URI cases. The report UI workflow passed in English/light at 411dp and French/dark at 320dp/160% text (one instrumented test per profile); final form/review images were inspected. Android debug application/test APKs and shared iOS simulator compilation passed. Early harness attempts needed waits for the category popup and semantic clicks after scrolling; the final runs passed. No real email was opened or sent, and iOS visual/mail-client verification was not performed.
