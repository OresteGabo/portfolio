# Linking an imported share purchase to a pending order

Updated 2026-11-18. Extends the general-upload suggestions in `KMP_PENDING_SHARE_IMPORT_MATCHING.md`.

## Behavior

A saved pending share screen now offers **Use imported purchase** beneath **Upload contract**. The dialog loads complete purchase history and lets the user select an existing contract-backed holding for the same security, currency and full requested quantity. Actual contract dates and prices can differ from the pending estimate. It shows the original trade date, quantity, price and total before explicit confirmation. No candidate is preselected.

`SharePurchaseReconciliation` keeps candidate rules and account data access outside the composable. Before writing, it fetches the current pending order and holdings, checks the order version, verifies the selected purchase's identity/evidence/financial fields have not changed, and rejects ambiguous duplicate contract records. Dashboard aggregate rows and purchases without contract evidence are excluded.

The existing `/api/bonds/share-orders/{id}/confirm` transaction is reused. Inspection of the separate backend's `ShareOrders.confirm` and `PortfolioService.saveShare` confirmed that contract-backed financial identities are reused and the pending order is closed atomically. No backend changes or deployment are needed. No independent equity POST, PDF upload, deletion or cancellation is performed. The original purchase and its PDF link remain; refreshed statistics exclude the closed pending estimate.

Failures remain visible and the dialog can be cancelled or history retried. Account-level action ownership, authentication, number hiding and capture protection remain with existing owners. No calculation, serialization or persistence format changes.

## Private duplicate check

At the user's request, an account-scoped production check was performed using a read-only transaction with full TLS verification and a statement timeout. It compared saved purchase identities, contract references and private PDF hashes. No account data was changed, no document contents were downloaded and no credentials or financial records were copied into the repository. The specific findings were reported privately in chat. This was an inspection of stored data, not physical-iPhone UI automation.

## Verification

- Shared tests cover original-field preservation, price/date changes, evidence requirements, aggregate exclusion, security/currency/quantity mismatches, stale/deleted/changed records, ambiguous duplicate contracts, failure propagation and cancellation. The model test asserts zero PDF uploads, zero independent equity writes, one original holding and removal of the pending order after refresh.
- `ExistingSharePurchaseLinkUiTest` exercises the actual pending-share screen/dialog and verifies one original holding and PDF with no upload or new-equity write. `PendingShareImportUiTest` remains the regression for general-upload suggestions. Both use synthetic records in a disposable emulator.
- Exact commands and final outcomes are recorded below after execution. No screenshot baselines are replaced, real account merges performed, or legal/accessibility/performance certification claimed.

Passed commands:

- `./gradlew :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintDebug :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain` — final run succeeded; 583 shared tests, zero failures/errors/skips. Android app/test APKs and both iOS compilations passed. Lint retains six existing dependency/toolchain warnings, zero errors.
- `adb -s emulator-5590 shell am instrument -w -e class dev.orestegabo.portfolio.investments.ExistingSharePurchaseLinkUiTest,dev.orestegabo.portfolio.investments.PendingShareImportUiTest dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner` — two tests passed. The linking journey exercised light and dark themes with a 1.8 Compose density/font-scale profile on the parent screen. The platform dialog creates its own window; increased system font size inside that dialog was not independently verified. Both captured dialog themes were visually inspected.
- `python3 scripts/check-kmp-packages.py`, `python3 scripts/check-kmp-ui.py`, `python3 scripts/sync-design-colors.py --check`, `git diff --check` — passed.

Initial new tests exposed equality comparisons of absent numeric fields (`NaN != NaN`) and an incomplete synthetic settlement date. Presence-aware comparisons and the fixture were corrected; no existing assertions were weakened. An unnecessary helper extraction was removed and the full Gradle checks rerun against the final source. No manual screen-reader check, screenshot baseline comparison or performance measurement was performed.

- `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'id=00008101-000E3DD11E03001E' -destination-timeout 30 -derivedDataPath /tmp/inzira-share-timeline-ios -allowProvisioningUpdates build` — succeeded. Physical-iPhone dialog operation and a real-account merge were not automated.
