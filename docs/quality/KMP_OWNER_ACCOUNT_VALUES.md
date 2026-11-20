# Owner account portfolio values

Implemented 2026-11-20.

Settings → Administration → Accounts now shows an estimated net-worth total per account. Each independently expandable row shows bond principal, share value, pricing date/fallback information and the existing account details/publication controls. Multiple currencies stay separate. All financial figures use the existing privacy-aware components; expanding a row never changes Hide numbers. An older server without the new summary displays Unavailable rather than inventing zero. A supported empty portfolio shows zero RWF.

The backend's existing owner-only `/api/bonds/auth/accounts` endpoint adds `portfolio.valuation` (currency totals, price date, stale flag and pending-inclusion metadata). It exposes aggregate values rather than new raw holdings or documents. The existing currentOwner guard is retained. Account inventory/orchestration moved from PortfolioService to OwnerAccountPortfolios, preventing that repository from acquiring a market-service dependency. Market quotes are fetched once per inventory request.

The estimate follows the mobile dashboard basis: active/pending bond principal plus remaining active shares and pending share orders, using RWF closing prices with a non-future publication date. Missing/unusable prices and non-RWF positions use recorded remaining cash cost; the UI identifies that fallback. Closed/future confirmed purchases are excluded; linked pending bonds already excluded by normal portfolio reads stay excluded. Currencies are never combined or converted. This is tracked investment value, not a new cash/liability calculation. Backend aggregation uses BigDecimal without introducing a rounding policy; presentation reuses existing money formatting.

## Verification

- `JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintDebug :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain` — passed; 587 shared tests, zero failures/errors/skips. Existing compiler/lint notices remain.
- Backend `test bootJar --console=plain` with the existing explicitly fenced disposable local PostgreSQL settings — passed; 119 tests, zero failures/errors/skips. New tests cover exact fractional arithmetic, remaining shares after sales, pending inclusion, closed/future exclusions, missing/future/invalid prices, foreign currencies, empty portfolios, account separation and anonymous/non-owner rejection.
- `adb -s emulator-5590 shell am instrument -w -e class dev.orestegabo.portfolio.investments.OwnerAccountValuationTest dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner` — passed (one test, 14.551 seconds). Synthetic account fixtures only. Checks real owner Settings/Accounts navigation, total/breakdown, expand/collapse, legacy unavailable state and Hide numbers. The first run was blocked by a foreground `Application Not Responding: com.android.systemui` window; after restarting that system process and verifying launcher focus, the unchanged test passed. Failure evidence remains at `/tmp/inzira-owner-account-ui.log` and passing output at `/tmp/inzira-owner-account-ui-recovered.log`.
- Visually inspected the synthetic light-theme screenshot `/tmp/inzira-owner-account-values.png`. No dark/large-font or physical iPhone interaction verification is claimed.
- Signed iPhone Debug xcodebuild using the connected iPhone destination and `/tmp/inzira-share-timeline-ios` — passed. Device installation is recorded separately below.
- Package, UI inventory, design-color scripts and `git diff --check` — passed.

The unrelated pre-existing SettingsScreen annotation removal was preserved. No navigation-bar design changes, account-role grants, database migration or actual investment-record mutations are part of this feature. Live backend activation requires separate approval; local test success does not imply deployment.

The signed Debug build was installed and successfully launched on the connected iPhone with devicectl. Its real Accounts values require the approved backend activation; no real-account financial values were inspected during verification. The agent-owned emulator and disposable PostgreSQL cluster were stopped afterward.

## Approved live activation — 2026-11-20

The user explicitly approved activation. The tested JAR was packaged unchanged (SHA-256 `de43f991676252932bf86d3af85cc89ee7c80d7c8ee59363b1adec665c96c348`) in the existing non-root Java 21 runtime, linux/amd64, user 10001. Container JAR hash was verified before rollout. Image digest: `sha256:9f5a6baf5a05d5af0bfdd514f4e6af2e02395dca88946df718be5880508d4645`.

A no-traffic candidate was deployed, preserving runtime settings, environment/secret references, service identity and existing traffic. Candidate health returned UP; an anonymous Accounts request with the allowed native origin returned 401. Production then switched to `gabo-portfolio-api-owner-values-de43f9916762` at 100%. The public app-backend health endpoint returned UP; the temporary candidate tag was removed and pre-existing tags retained. The previous production revision was `gabo-portfolio-api-gh-44635372334-1`, based on the same clean source base `dc2e1f6` plus this preview's changes. Deployment labels identify the uncommitted build.

No real account portfolio was retrieved for deployment verification, and no investment records or account permissions were changed by the deployment test. Actual owner values are covered by synthetic integration/UI tests and remain available for the user's manual review. No migration, commit or push was performed. Private temporary service configuration snapshots were removed after comparison.

## Commit verification — 2026-11-20

Re-ran the full client Gradle verification command above and all three repository quality scripts plus `git diff --check`: passed. Gradle reused unchanged shared test/compilation outputs. The previous owner Accounts device test and iPhone build evidence above remain valid; no fresh device execution is claimed for commit preparation. The unrelated SettingsScreen annotation removal is deliberately excluded from this feature commit.
