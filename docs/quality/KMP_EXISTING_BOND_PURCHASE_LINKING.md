# Link an imported bond confirmation to a pending application

Updated 2026-11-18.

## Client behavior

Signed-in pending bond details now offer **Use imported purchase** beside the existing PDF import workflow. The dialog loads full purchase history and lists personal confirmation-backed purchases for the same issuer, currency, primary market, security, settlement and maturity. An allocation may be smaller than the amount applied, following the existing confirmation rule. It displays the applied amount and each confirmation's allocation, settlement and commission. Nothing is preselected.

`BondPurchaseReconciliation` owns matching, refreshed snapshots and the one-transaction request outside Compose. It rejects missing/stale records and changed financial/evidence fields. The actual purchase's PDF, allocation, price, commission, coupon settings and receipt history remain authoritative. Neither a new purchase nor another PDF is uploaded.

The server request is `POST /api/bonds/purchases/{pendingId}/link-imported`, with explicit confirmation, selected purchase ID and reviewed pending/purchase snapshots. An older server without this route shows a clear unavailable message; there is no fallback that deletes a record from the client. Guest mode and manually confirmed active purchases do not gain this pending-only action.

## Required backend change

The separate backend working tree contains `BondPurchaseLinks` and V20. A locked account transaction validates ownership, snapshots, issuance identity, allocation limits and owned personal evidence. Pending records with coupon receipts or cash-ledger activity are rejected for review. A successful operation sets link metadata on the original pending row and excludes it from normal portfolio reads. The confirmed row, its receipts and PDF are untouched. Repeat links to the same target return that purchase; attempts to redirect a completed link are rejected.

Pending issuance refresh and blank-form retention ignore archived links. Old detail URLs and application-bid retries resolve to the actual purchase through a read-only alias. Ordinary mutation paths cannot edit the archived pending row. Its original fields and provenance remain stored; deleting the actual purchase later follows the normal deletion flow and cascades its archived link.

The user approved live backend activation after verification; deployment evidence is recorded below. No real-account link was performed by the agent. Both repositories were left uncommitted during preview and activation.

## Verification

- Client: `./gradlew :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintDebug :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain` — passed; 587 shared tests, zero failures/errors/skips, both iOS targets and Android APKs compiled. Lint retains six existing dependency/toolchain warnings, zero errors.
- After supplying the UI fixture's initially missing synthetic native-actions provider, `./gradlew :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintDebug --console=plain` — passed. The first fixture run crashed before exercising the flow; no production fix or weakened assertion was used to conceal it.
- `adb -s emulator-5590 shell am instrument -w -e class dev.orestegabo.portfolio.investments.ExistingBondPurchaseLinkUiTest,dev.orestegabo.portfolio.investments.ExistingSharePurchaseLinkUiTest dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner` — two tests passed using synthetic records. The bond test proves only the imported allocation remains, its larger actual commission is retained once, and no purchase/PDF upload occurs. Both light/dark captures were visually inspected. The 1.8 density profile applies to the parent screen; system-font scaling inside the separate dialog window was not independently verified.
- Backend: `JAVA_TOOL_OPTIONS='-Dstruct.test.local-postgres=true -Dstruct.test.database-url=jdbc:postgresql://127.0.0.1:55491/struct_test_bond_linking' GABO_DATABASE_URL='jdbc:postgresql://127.0.0.1:55491/struct_test_bond_linking' GABO_DATABASE_USER=struct_test GABO_DATABASE_PASSWORD= ./gradlew test bootJar --console=plain` — 114 tests passed, zero failures/errors/skips, including real PostgreSQL transaction rollback, repeat linking, wrong-owner/security/date/amount/evidence/stale checks, preserved receipt/PDF data and a V19-shaped populated-table migration preservation check. Production credentials were not loaded.
- `python3 scripts/check-kmp-packages.py`, `python3 scripts/check-kmp-ui.py`, `python3 scripts/sync-design-colors.py --check`, and `git diff --check` in both repositories — passed.
- `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'id=00008101-000E3DD11E03001E' -destination-timeout 30 -derivedDataPath /tmp/inzira-share-timeline-ios -allowProvisioningUpdates build` — passed; installed/launched on the connected iPhone. Physical-iPhone dialog operation, screen readers and a real-account link were not automated. No screenshot baselines or performance claims were introduced.

## Approved live activation — 2026-11-18

The previously tested JAR was packaged unchanged (SHA-256 `f03a6bfabca65b0ba96114e270c5fa98e331229d781105711e43e30e23970763`) in the existing non-root Java 21 runtime, linux/amd64. Private image digest: `sha256:4c593e97787affdd411754eb92aa0454bc20889a8bd403a68ef8a5b41bf84e23`.

Cloud Run revision `gabo-portfolio-api-bondlink-f03a6bfabca6` was first deployed with no traffic. The runtime spec, environment/secret references, service identity and revision settings were compared with the previous revision and preserved. Flyway logs confirm one migration applied successfully, now version V20. Candidate health returned UP before promotion. Production was then switched to this revision at 100%; the temporary candidate tag was removed and existing tags retained. The app's direct backend health endpoint returned UP. An anonymous link request with a valid mobile origin returned 401; a request without an allowed origin returned 403.

No production purchase, receipt or PDF was created, deleted or linked as a deployment test. No commit or push occurred. The deployment source label explicitly identifies the uncommitted build. Configuration comparison used private temporary snapshots outside the repositories; those snapshots were removed afterward. No credentials were added to documentation.

## Commit review — 2026-11-20

The full client Gradle command above and repository quality scripts passed again with unchanged compile/test outputs reused. Previous device/UI and iPhone evidence remains dated above; no new device run is claimed for commit preparation. The matching backend is being recorded in its own feature commit. No live deployment or real-account linking is part of this Git operation.

The backend's complete `test bootJar --rerun-tasks` verification also passed again against disposable local PostgreSQL: 114 tests, zero failures/errors/skips. Its existing main-branch push workflow performs CI testing and deployment; no separate manual deployment is being invoked.
