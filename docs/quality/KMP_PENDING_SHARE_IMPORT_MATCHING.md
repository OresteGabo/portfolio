# Pending share contract suggestions

Updated 2026-11-18.

## Cause and change

Uploading from a pending share's **Upload contract** action supplied a `share-order:` target. General uploads had no target and used the new-equity save path, even when an equivalent pending order existed.

General share contract reviews now suggest pending records with the same normalized ticker, currency, positive finite quantity and price, and request month/year matching the actual trade month/year. Dates need not be the same day. All candidates remain available for explicit selection; none is automatically selected. The user can instead choose **This is a separate purchase**. When suggestions exist, saving requires a choice.

Selecting a pending record uses the existing versioned `/api/bonds/share-orders/{id}/confirm` endpoint and PDF upload path. The contract supplies the actual date, price and costs; pending estimates do not override those values. The candidate is revalidated before upload/save. The existing explicitly targeted flow still permits actual price/date differences while requiring a full matching security/currency/quantity. No backend contracts, formulas or database formats changed.

Existing already-completed duplicate imports retain the prior duplicate-file guard. This change does not automatically reconcile or delete records already saved before the fix, nor mutate a live account during testing. Suggestions deliberately exclude different prices or months; the pending record's own Upload contract action remains the explicit route for those legitimate differences.

## Verification

Passed:

- `./gradlew :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintDebug :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain` — 577 shared tests, no failures/errors/skips; Android app/test APKs, lint and both iOS compilations passed. Lint retains six existing dependency/toolchain warnings, zero errors.
- `adb -s emulator-5590 shell am instrument -w -e class dev.orestegabo.portfolio.investments.PendingShareImportUiTest dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner` — passed on an isolated API 36.1 emulator. A general upload cannot save before a decision; separate-purchase selection enables save; selecting the pending record changes the action to confirmation; saving leaves one confirmed holding, removes the pending order and preserves the actual trade date and PDF link. No new-equity POST occurs on confirmation.
- Domain tests cover exact candidate conditions, different days in the same month, year/month/security/quantity/price/currency/status mismatches, multiple candidates, malformed input and no mutation during matching. Model tests cover general-import confirmation, separate purchase and rejecting an unavailable selected order before any write/upload. Existing explicit-target lost-response and actual-price/date tests remain passing.
- The light-theme review screenshot from the synthetic UI journey was visually inspected. Dark theme, increased font scale, VoiceOver/TalkBack and physical-phone import execution were not manually verified for this change. No real user PDF was used.
- `python3 scripts/check-kmp-packages.py`, `python3 scripts/check-kmp-ui.py`, `python3 scripts/sync-design-colors.py --check`, `git diff --check` — passed.
- `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'id=00008101-000E3DD11E03001E' -destination-timeout 30 -derivedDataPath /tmp/inzira-share-timeline-ios -allowProvisioningUpdates build` — succeeded.

Initial new domain fixtures failed because review metadata was omitted and a nested JSON copy was modified without replacing it; those fixtures were corrected before the successful full run. No assertion was weakened or existing test disabled.
