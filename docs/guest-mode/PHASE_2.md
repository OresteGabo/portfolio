# Phase 2 — Guest entry and session state

Implemented 2 November 2026 on `feature/guest-mode`, following [the Phase 1 audit](ARCHITECTURE_PLAN.md). This is an entry/session increment, not the durable offline portfolio implementation. No backend, schema, dependency or production configuration changes.

## State and startup

`app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/SessionState.kt` defines `SignedOut`, `Guest(path)` and `Authenticated(accountId, origin)`. The latter derives from the existing verified `PortfolioModel.user`; Guest never fabricates an account. Device lock and server-session verification retain their existing separate state.

`PortfolioModel.kt` restores non-sensitive operating-mode metadata before checking saved sign-in or creating an HTTP client. Persisted Guest skips account initialization, secure-vault reads and portfolio synchronization. Its guarded lazy API accessor and action boundary prevent remote financial actions. `App.kt` defers session checking during first-run onboarding; finishing onboarding for account use explicitly starts the existing account-session path. Bundled assets can load independently; they are not a guest-entry dependency.

Native `PlatformPorts.android.kt` stores `sessionChoice` in the existing `connection` SharedPreferences; Android commits the choice before entry succeeds. `PlatformPorts.ios.kt` uses `portfolio.sessionChoice` in NSUserDefaults. Values are versioned `guest-personal-v1` and `guest-sample-v1`. No financial fields, documents, credentials or account identifiers are stored in these preferences.

An unreadable or unknown choice fails closed to manual account entry. After a guest explicitly authenticates without saving new device sign-in, `account-manual-v1` suppresses an older saved vault entry on restart. Successfully saving the new sign-in clears that marker. This avoids silently restoring a different previously saved account. Existing installations without mode metadata retain the existing account startup policy.

## Entry and switching

`OnboardingScreen.kt` adds Continue as Guest to the existing introduction. `GuestScreens.kt` offers two explicit choices:

- **Explore fictional sample:** isolated `sample:*` records, fictional instrument names, visible sample labels, acquisition amounts only. No live valuation, invented coupon or received income. These records never enter `model.portfolio`, persistence or account APIs.
- **Start personal portfolio:** the real application shell with an honest empty workspace. Only the mode selection is persistent in this phase; financial entry is unavailable.

`InvestmentApp.kt` reuses the existing Scaffold, navigation destinations, theme, masking and native capture policy. Guest content uses the normal Home/Documents/Calendar/Markets/Settings shell. Unavailable destinations explain the limitation instead of pretending imports, prices or payment schedules work. The native PDF engine is not created for Guest.

Guest settings allow changing paths and explicit sign-in. The existing sign-in, device-unlock and account Settings screens also expose Guest entry. Explicit sign-in keeps the guest choice until authentication succeeds; failed or abandoned sign-in can return to Guest. Signing in does not upload, merge or delete sample/personal data.

Entering Guest cancels account work, invalidates generation tokens, closes the active HTTP client, clears private in-memory snapshots, previews and import context, and locks the credential vault. Late session/login/unlock responses are rejected. Saved protected sign-in is retained, not deleted. **Switching to Guest is not server-side logout**; the existing Sign out action remains distinct.

## Privacy and boundaries

- No anonymous backend account or guest API endpoint is added. Signed-in authorization and server ownership checks are unchanged.
- Android/iOS push setup skips guest registration/permission work; shared private-device synchronization remains gated on a real user. Android foreground messaging drops deliveries while Guest is selected.
- Existing native device identity metadata may still be created locally. Firebase/OS SDK startup and previously registered OS background notifications are not fully disabled or revoked by this change. This phase proves absence of app account API requests for tested guest journeys, **not a guarantee of zero traffic from every SDK or OS service**. Review that distinction before making an offline/privacy marketing claim.
- Guest has no sensitive persisted financial content yet. At-rest encryption, local biometric policy, crash-safe writes, backup/recovery, durable reset and local documents remain later-phase decisions from the audit.
- Incoming PDF intents do not become guest uploads; the existing account-required handling remains. No guest market client, calendar/report adapter, document parser/save pipeline or financial repository is claimed functional.

## Verification

- Shared Android host suite: **297 tests, zero failures/errors** after correcting an incomplete synthetic overview fixture in the new account-to-guest isolation test. Includes 11 new guest state tests: restart reconstruction, deferred onboarding, late server response, sample separation, explicit/failed sign-in, API guards, failed preference write, unknown metadata, no saved-credential reads and account-cache isolation.
- Core JVM suite remains green from its existing 58-test report; the final command reused that task's up-to-date result.
- Android debug app and instrumentation APK builds pass.
- Real Android `MainActivity` instrumentation passes on an isolated emulator: onboarding → fictional sample → unavailable Documents/Markets → personal workspace → Activity/model close and reopen → sign-in → Return to Guest. Native preferences retain the choice and the unavailable local fixture server receives **zero account requests**. This tests Activity/model recreation, not physical-device force-stop, OS backup or reinstall recovery.
- Shared iOS simulator suite: **315 tests, 0 failures, 0 errors, 0 skipped**, including the same 11 guest state regressions. Full Xcode Debug build for an unsigned iOS simulator host also passes; no developer credentials or release signing used.
- English rendered Android sample/personal screens were inspected at emulator dimensions. French strings were added. Physical iOS/Android, full accessibility/font-scale/theme coverage, release signing and store acceptance are not claimed.

Temporary local evidence lives under `/private/tmp/struct-guest-phase2/`, `/private/tmp/struct-guest-phase2-tests.log` and `/private/tmp/struct-guest-phase2-checks.log`; these are not repository deliverables or financial fixtures. Tests use only synthetic accounts and financial records. No real credentials, production endpoints, user PDFs or databases were used.

## Next phase interfaces

Keep `SessionState` and `GuestPath` as the operating-mode boundary. Add an actual local workspace/store only after validating encryption, key lifecycle, atomicity and native recovery. Route existing financial operations to that store before enabling entry, imports, payments or reports; do not remove the Guest guards merely to expose account screens. Retain the `sample:*` namespace outside all real holding repositories. Account migration remains an explicit future consented operation, never a side effect of login.
