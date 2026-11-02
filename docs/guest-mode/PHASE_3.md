# Phase 3 — Durable personal guest portfolios

Implemented on `feature/guest-mode`, following [the architecture audit](ARCHITECTURE_PLAN.md) and [Phase 2](PHASE_2.md). Phase 2's statement that guest investment entry is unavailable is superseded here. No server, production database, remote API, signing or deployment changes.

## Storage decision

The current app has no Room/SQLDelight portfolio database. SharedPreferences/NSUserDefaults hold settings, and the biometric credential vault has a different purpose, size and lifecycle. Neither is reused for financial records.

Use a bounded, versioned snapshot behind `GuestSnapshotStorage` and `GuestPortfolioRepository`, with native atomic replacement and platform protection. This follows storage option A from the audit without adding a dependency, SQL schema or second financial engine. Kotlin serialization, coroutines, UUIDs and the existing expect/actual pattern supply the shared boundary; native platform APIs supply storage and cryptography. Both existing iOS targets and Android remain configured as before.

This is appropriate for a small device-local individual portfolio, not unlimited document storage or institutional ingestion. Maximum plaintext snapshot size is 4 MiB; each collection is capped at 10,000 rows, fields at 32,000 serialized characters. Capacity errors retain the previous commit. Revisit a transactional database if measured size/write frequency warrants it; never silently fall back to preferences/plaintext.

## Files and ownership

Paths below are relative to the mobile repository. Investment package suffix: `kotlin/dev/orestegabo/portfolio/investments/`.

| Source | Responsibility |
| --- | --- |
| `app/shared/src/commonMain/.../GuestPortfolioRepository.kt` | Storage/repository ports, validation, stable IDs, schema codec/migration, serialized CRUD and receipt transactions. |
| `app/shared/src/androidMain/.../GuestSnapshot.android.kt` | Android Keystore AES-GCM and AtomicFile adapter; app-scoped repository provider. |
| `app/shared/src/iosMain/.../GuestSnapshot.ios.kt` | Application Support file adapter, Complete Data Protection, atomic writing and backup exclusion; app-scoped provider. |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/App.kt` | Injects the native repository without opening guest files during account startup. |
| `app/shared/src/commonMain/.../PortfolioModel.kt` | Separate guest read state/jobs, narrow local CRUD routing, reload/error handling and isolation on mode/lifecycle changes. |
| `app/shared/src/commonMain/.../GuestScreens.kt`, `PurchaseForms.kt`, `FrenchStrings.kt` | Personal manual entry/edit/delete, latest due coupon receipt controls, honest cost/expected/recorded amounts, local-only copy, retry and translations. Existing account forms and routes remain in use. |
| `core/src/commonMain/.../ShareAcquisitionCost.kt` | Small public helper over the existing decimal/HALF_UP primitives for quantities × price + fees once. No new valuation engine. |
| Matching `commonTest`, `iosTest`, and Android app `androidTest` files | Financial equivalence, CRUD/migration/isolation/failure and native durability/protection regressions. |

The only workspace is `guest-personal`. Stable IDs are `guest:bond:<UUID>`, `guest:share:<UUID>`, `guest:receipt:<UUID>` and `guest:document:<UUID>`. Sample data never enters the store. Account IDs and server document references are rejected. `model.guestPortfolio` is separate from the cloud `model.portfolio`, account receipt list, documents and history flags. Explicit account entry clears guest view state, not durable files. Signing in never uploads or merges this data.

## Schema and safe updates

Schema 2 stores `workspace`, integer `revision`, creation/update metadata, full bond purchases, individual share lots, coupon receipt states and document-reference metadata. Existing normalized `JsonRecord` fields are retained for the shared calculations; dates are ISO dates and rates remain fractions. Snapshots returned to callers are detached from durable bytes, not mutable write-through cache entries.

Schema 1 is an explicitly supported **draft format**, not a claim that an earlier released app stored guest holdings: it has the same namespaced purchases, holdings and metadata, without receipts/documents and optionally without revision. Migration adds empty collections/revision and changes only the schema envelope. The entire result is validated before a single atomic replacement. Tests construct this format synthetically and prove failed migration writes preserve its bytes; a successful migration runs once. Phase 2 had no financial file to migrate. Future migrations must be explicit and tested the same way.

Each operation reads/validates the last commit under one repository mutex, changes a detached snapshot, increments revision and atomically persists before publishing success. Parsing, validation and serialization run off the UI thread; native IO/crypto use background dispatchers. The application-scoped repository is the single writer. Independent repository instances are used sequentially for reopen tests, **not** supported as concurrent writers; no background service or second process writes this store. Add an OS-level coordination strategy before introducing another writer.

Unknown schema, bad JSON, foreign namespace, invalid fields, orphan references, read failure, key loss and authentication-tag failure throw without resetting data. The UI shows a retryable local-storage error, not an empty success state. Failed writes do not publish an in-memory success. There is no automatic deletion, reset, key regeneration for an existing unreadable file, repair-by-dropping-records, backup rollback after logical corruption or plaintext recovery export.

Atomic replacement protects against interrupted writes; Android also syncs and verifies ciphertext after commit. It does not make storage hardware infallible. A process dying before commit can lose an unacknowledged edit; a process dying after commit but before the success frame may leave a saved edit the user has not yet seen. Reopen/reload before retrying an uncertain add. This phase does not claim a full financial correction journal or exactly-once UI delivery.

## Financial behavior

- `PortfolioSnapshot.summary`, `terms`, `couponSchedule`, `trackBond`, `netCouponPayment`, receipt presentation and existing decimal primitives are reused. No yield/pricing engine or market value is invented.
- Individual share lots retain quantities, unit price, individual charges and gross/fees/payable. Supplied totals must agree within the existing account validation tolerance of 0.02 and are retained, including legitimate document rounding differences. Missing totals use the existing decimal-safe primitives. Cost is not presented as current market valuation.
- Bond principal, rates, taxes, schedule confidence and explicit coupon dates are retained. There is no public-instrument fetch/canonical contribution in Guest Mode. Typing a bond name does not verify its terms.
- Receipt rows reference an existing local bond and a due scheduled date; future coupons cannot be marked received. A user action creates a manual receipt/not-received state. Passage of time never creates receipt evidence. Automatic states can be retained by the schema, but no automatic guest receipt scheduler is implemented.
- Existing recorded receipt amounts are preserved when terms are edited or a receipt is toggled, matching the existing account receipt behavior. The UI separates expected net payment from recorded received amount. Deleting a bond atomically deletes its receipt states.
- Document references contain a local ID, filename, PDF MIME type, SHA-256 and an opaque local file ID, never a path or remote URL. Financial edits retain existing references. These are **references only**: PDF bytes, import/reconciliation, document preview/download and evidence lifecycle are later-phase work. Do not claim a reference means an original PDF has been saved. Unlinked reference metadata is retained when an investment is deleted; no shared/private source PDF is destroyed implicitly.

## Protection, device lock and backup policy

Android: `noBackupFilesDir/guest-portfolios/personal.scg`, separate `struct.guest.snapshot.personal.v1` Keystore AES-256 key, system-generated randomized GCM IVs and authenticated version header. Original, temporary and rollback files contain ciphertext only. Existing manifest backup/transfer exclusion remains enabled. A missing key for an existing snapshot fails closed and is never silently replaced. Hardware backing depends on device capabilities; no claim of StrongBox availability or hardware attestation is made.

Guest keys are independent of the account biometric vault. **This phase does not add an app-specific guest biometric lock or per-use key authentication.** Access assumes the app sandbox and legitimate user of the unlocked device. Android device file encryption/Keystore protection are not a promise that a compromised unlocked app cannot read its data. Set a device screen lock; do not describe Guest Mode as equivalent to the account's biometric session gate. Ordinary backgrounding clears the guest read snapshot when no write is active; a pending atomic operation may finish before its next foreground reload. Existing privacy covers/masking remain in place.

On iOS, `Library/Application Support/GuestPortfolios/personal.json` uses `NSDataWritingAtomic | NSDataWritingFileProtectionComplete`; the directory also requests `NSFileProtectionComplete` and checks backup exclusion through `NSURLIsExcludedFromBackupKey`. File encryption and key management belong to iOS; there is no home-grown cryptography or guest financial payload in Keychain/preferences. This requires physical passcode/lock testing before release. Unlocked-process access can see plaintext; a simulator file is not proof of hardware encryption.

**No cloud backup, device transfer or recovery guarantee.** Uninstall, device loss, key invalidation or damaged storage may permanently lose local data. The personal workspace banner states that data stays on-device and is neither synced nor backed up. An opt-in protected export/recovery flow is deferred; no silent account upload is used as backup. App updates retain the local file and keys under the same app identity, subject to safe schema migration; downgrade to unsupported schema must fail closed.

Platform references: [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [Apple Complete file protection](https://developer.apple.com/documentation/foundation/nsdata/writingoptions/completefileprotection), [Apple Data Protection classes](https://support.apple.com/en-au/guide/security/secb010e978a/web), [Apple backup guidance](https://developer.apple.com/documentation/foundation/optimizing-your-app-s-data-for-icloud-backup). These describe platform mechanisms, not a claim of legal compliance or verified physical-device protection in this task.

## Verification and remaining checks

Final execution results are recorded below. All test records/accounts are synthetic. Native tests use distinct `test-guest-phase3-*` files and keys and never open/delete a user's personal workspace. The staged Android reboot test is opt-in via an instrumentation argument, so ordinary suites do not depend on test ordering.

Remaining manual acceptance: real Android/iPhone passcode/lock behavior, iPhone process death/reboot, hardware key characteristics, OS backup/restore/device transfer exclusion, signed app update/downgrade, accessibility/font scaling, realistic maximum portfolio size, abrupt physical power loss and real storage exhaustion. Fake-storage failures and native reopen tests are useful evidence, not substitutes for these checks.

Still deferred: broader repository routing across investor screens, full local calendar/report/history adapters, guest PDF import/original files, market enrichment, account migration, encrypted recovery export, explicit durable reset and full correction/provenance journal. No account financial calculations/API contracts were replaced and no Spring Boot changes are required.

### Final execution record — 2 November 2026

| Check | Result |
| --- | --- |
| Core JVM | 61 tests; zero failures/errors/skips |
| Shared Android host | 311 tests; zero failures/errors/skips |
| Core iOS simulator | 61 tests; zero failures/errors/skips |
| Shared iOS simulator | 330 tests; zero failures/errors/skips |
| Android debug app and instrumentation APK | BUILD SUCCESSFUL |
| Shared physical iOS arm64 compilation | BUILD SUCCESSFUL; no signing or device deployment |
| Android native Keystore/file tests | Passed CRUD/reopen, encrypted bytes, GCM tampering and missing-key failure without overwrite |
| Android staged durability test | Passed write → force-stop → new instrumentation process read → emulator reboot → read/cleanup |
| Android real App guest journey | Passed onboarding/sample/personal/native-choice restart/sign-in-return flow with zero account API requests |
| iOS native file test | Passed write/reopen/delete, corrupt-byte preservation and backup-exclusion attribute checks |
| Whitespace verification | `git diff --check` passed |

The shared suites include 14 new repository/model regressions; the core includes 3 new acquisition-cost tests. Core tasks reused up-to-date results on the last rerun after their earlier successful execution; they were not rerun redundantly. The last iOS shared run contains 330 passing tests.

The first iOS native run failed its unconditional protection-class assertion because the simulator returned **no** `NSFileProtectionKey` attribute. The test now explicitly records that limitation only when `SIMULATOR_UDID` is present; physical runs still require Complete protection. No production protection option was removed. Backup exclusion was actually read back successfully. Encryption/lock enforcement on a real iPhone is **not** verified by these results.

Android native/UI tests ran before the final supplied-total parity adjustment; the native adapters themselves were unchanged afterward. The final Android/iOS shared regressions and both platform compilations cover that adjustment. Rendered Android personal/sample entry screens were inspected; full visual/accessibility coverage and signed release builds were not performed.

Logs: `/private/tmp/struct-guest-phase3-final-build.log`, `/private/tmp/struct-guest-phase3/android-native.log`, individual staged native/UI logs in that directory and `/private/tmp/struct-guest-phase3/ios-tests.log`. Temporary simulators were isolated from personal devices. No production service or database was accessed; no real credential or user PDF was used. Both repositories remain on `feature/guest-mode`; the backend has no changes.
