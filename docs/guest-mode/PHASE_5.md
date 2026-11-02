# Phase 5 — Public quotes and offline valuation

2 November 2026. Builds on [the architecture audit](ARCHITECTURE_PLAN.md) and [Phase 4](PHASE_4.md). Scope: optional public equity quotations for the personal guest workspace, independent local caching and honest valuation labels. No new backend endpoint, financial schema migration, dependency, account integration, production change, or phases 6–8 work.

## Distribution decision — retrieval disabled

The founder confirmed **no written permission yet; keep retrieval disabled**. `GUEST_RSE_DISTRIBUTION_APPROVED` in `GuestQuotes.kt` is therefore `false`. The production adapter neither reads the public cache nor constructs a quote client in this state. The application displays an unavailable explanation and continues to show local acquisition costs. This is a tested implementation behind a distribution gate, **not a claim that online guest quotations are currently enabled**.

Sources inspected on 2 November 2026:

- [RSE website terms, effective 2 November 2025](https://rse.rw/uploads/configs/1762105919_0d5bd1ab287713733e26.pdf), section 4: personal non-commercial viewing/downloading is distinguished from redistribution/public display requiring written consent. Section 5 warns that prices can be delayed or periodically updated.
- [RSE data-vending page](https://rse.rw/data-vending): describes real-time/end-of-day and historical services; it does not grant Struct Capital redistribution/caching rights.
- [Existing upstream equities page](https://rse.rw/equities-market). Public accessibility and robots directives are not a redistribution licence; robots.txt could not be retrieved with the research tool. No permission is inferred from that failure.

Before enabling: retain evidence of permission covering app display, redistribution through the existing backend, local retention, attribution, users/platforms and any fees/limitations; verify the actual serving backend/provider contract and expiry/revocation process. A different permitted provider requires a deliberate source adapter and matching source validation, not relabelled RSE data. Written permission was not fabricated or requested from RSE on the founder's behalf. The pre-existing authenticated RSE usage is unchanged and needs the same rights review before wider release. This is a technical gate, not legal-compliance certification.

## Existing pipeline inspected and reused

Companion Spring sources under `src/main/kotlin/dev/orestegabo/portfolio/`:

- `MarketController.kt`: existing anonymous `GET /api/bonds/rse/quotes?kind=equities`. It returns the complete public board, not a portfolio. Anonymous `refresh` cannot bypass the cache; cron/daily/snapshot have separate secret authorization. Optional `history.record` failures are swallowed so history storage cannot make available quotations fail.
- `MarketService.kt`: reads `https://rse.rw/equities-market` through bounded `RsePages`, parses positive closing prices and the actual “Last updated” publication time (Kigali GMT+2), caches upstream data for 15 minutes, shortens failed fetch caching, and can flag retained data stale. No private document source is used.
- `SecurityConfig.kt`: permits `/rse/**`, while private `/api/bonds/**` remains authenticated. `AuthService.session` returns before a database lookup when no session cookie exists. Existing tests cover anonymous refresh restrictions and denial of missing administrative secrets. Those integration tests were inspected, not rerun: their setup truncates configured database tables.

No anonymous financial endpoint or auth exception was added. The existing cache and controller were sufficient; guest code uses neither bond-board analytics/implied prices nor account-scoped history. No server-side quote rate limiter was added to an unchanged endpoint. Upstream caching already limits source fetches; per-client throttling below bounds normal app traffic. Existing general public-endpoint abuse/rate-limit review remains an operational dependency before broad release.

Core `PortfolioValuation.kt` remains the calculation engine. Its fallback `estimatedValue` can equal cost when unpriced; the new UI explicitly checks `closingPrice != null` before showing a quote-based value or unrealized gain. Missing symbols/currencies remain unpriced. Bond face value is labelled principal, not market valuation. No charts, fake quotes, yield estimates or alternative financial engine were added.

## New boundary and data flow

```text
personal guest startup / foreground / explicit refresh
   ├─ local portfolio load → usable local UI (independent job)
   └─ distribution gate → separate protected public cache → UI
                        → fixed full-board GET → validate/whitelist → atomic cache → UI
sample / account transition → cancel guest quote job; ignore late results
```

- Core `PublicEquityQuotes.kt`: cookie-free Ktor client using the existing native HTTP engines. One fixed GET path/query, HTTPS origins only, redirects disabled, no retry loop, 4 s connect / 6 s socket / 8 s total timeout, 128 KiB streamed response bound. There is no arbitrary-path/body/ticker/account/document parameter or credential-vault dependency. Received Set-Cookie is not retained/returned. The Android public factory fails closed if a process-global CookieHandler exists, rather than modifying shared policy; the existing Darwin factory explicitly disables native cookie storage/handling and URL caching. No portfolio-derived ticker request, installation ID, tracing identifier, Authorization, Cookie or Origin header is added. Network peers necessarily see connection metadata such as IP address.
- Normalization whitelists `source` (fixed official URL, status, publication/check timestamps, stale flag) and up to 200 distinct ticker/name/positive finite closing-price rows. Unknown fields are discarded. Missing/invalid/future/inconsistent source dates, duplicate tickers, empty/malformed datasets and errors never overwrite a good cache. A retrieval timestamp is never invented as a market publication date.
- Shared `GuestQuotes.kt`: schema-1 reconstructible cache, provider identity, source dates and received-at timestamp. Uses the existing `GuestSnapshotStorage` port in a separate `public-quotes` store; no guest holding IDs, quantities, cash amounts, document hashes/filenames/content or auth origins. The native adapters reuse Android Keystore/AtomicFile + noBackup storage and iOS atomic Complete Data Protection + backup exclusion. No financial manifest schema or migration changed.
- Successful cache survives object/process recreation. Corrupt, oversized, future-dated or unknown-version public cache is unavailable; it is never interpreted as empty financial storage or used to reset holdings. A later verified quotation may replace this reconstructible cache. Network/write/validation failure preserves the previous committed cache.
- Refresh uses a separate mutex and 15-minute successful-cache / monotonic attempt throttle (including failures); repeated/simultaneous requests do not fan out. Refresh has no dependency on private portfolio load or PDF work, and never toggles the financial/global loading gate. Model generation/mode checks reject late responses after sign-in or sample switching.

## Display semantics

Home keeps acquisition cost separate, then shows grouped share positions through the existing `valuedShares` function. Quote-based value and unrealized gain include the existing fee-aware cost basis; dividends and realized returns are not invented. Unpriced securities show market value unavailable, even though the reused engine has an internal cost fallback. Markets shows the public board only for personal guests. Fictional sample records never consume real prices.

Source, publication timestamp and saved-on-device time are visible. All prices are called **published closing quotes**, never live. Stale means upstream stale, publication older than 72 hours, source check older than 24 hours, or a local clock behind the saved timestamp. These are conservative display thresholds, not an exchange holiday/trading calendar. A stale quote remains an explicitly dated historical close, not a current executable price. Failed refresh retains and labels saved data. A quote-free/offline portfolio remains usable. No currency conversion, bond market pricing, portfolio synchronization or signup blocker was introduced.

English/French strings use the existing theme/components/translation map. This is limited Phase 5 integration, not the full shared-screen work of Phase 6.

## Verification

Final commands/results are recorded below. Data fixtures are clearly synthetic test values, never production app quotations. Real production quotation retrieval is deliberately not exercised while distribution is unapproved. No account credential, customer PDF, production database or infrastructure setting was accessed.

Executed checks (synthetic fixtures; exact Gradle task names):

```sh
./gradlew :core:jvmTest :core:testAndroidHostTest :app:shared:testAndroidHostTest :app:shared:compileKotlinIosArm64 :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest
./gradlew :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest
./gradlew :core:iosSimulatorArm64Test :app:shared:iosSimulatorArm64Test -PiosTestDevice=<temporary-iPhone-16-UUID>
# Companion backend; only this pure unit-test class, no database fixtures:
./gradlew test --tests 'dev.orestegabo.portfolio.MarketParserTests'
git diff --check
```

- Core JVM: **65 passed**, zero failures/errors/skips. Four new common tests audit fixed URL/method/query/body/header behavior, ignored Set-Cookie, redirects/errors/response bounds, unsafe origins and normalization/ambiguity rejection.
- Core Android host: **66 passed**, zero failures/errors/skips. Includes the native Android factory regression: an installed process-global cookie handler blocks public transport without modifying that handler or starting a network request.
- Shared Android host: **331 passed**, zero failures/errors/skips. Seven new tests cover online/cache recreation/staleness (including upstream stale), offline/malformed preservation, disabled zero-I/O policy, corrupt/version/size handling, concurrent/throttled requests, existing valuation parity/missing prices, slow-network local interactivity and late-result/session/sample isolation.
- Android debug and instrumentation APKs and shared iOS device-arm64 compilation passed. Existing deprecation/interop warnings remain; no signed store build or physical device deployment was attempted.
- Backend market-parser unit tests: **3 passed**, zero failures/errors/skips. Includes actual publication-date parsing/Kigali offset/New Year handling. No Spring API contract or authorization policy was changed; existing database-backed authorization integration tests were not executed.

Native Android runner:

```sh
adb -s <isolated-Android-16-emulator> shell am instrument -w -r \
  -e class dev.orestegabo.portfolio.investments.GuestEntryTest \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

The emulator uses new temporary userdata, airplane mode and disabled Wi-Fi/mobile data. The first harness lost its ADB connection during boot; the next attempt was blocked by an OS “System UI isn't responding” dialog. After dismissing that dialog, the updated guest sample/personal/restart/sign-in-return journey passed. This is not hidden as an application pass on the failed attempts. A screenshot of the personal guest empty state was inspected at the emulator's normal mobile dimensions; full dark-mode/font-scale/physical-device quote UI is not certified. The final run additionally asserts the disabled personal Markets state; its result follows below.

## Remaining limits and release gate

- Retrieval remains **disabled by explicit founder decision** until distribution/cache permission is established. Online behavior is tested with injected synthetic responses, not an unauthorized production-data fetch. No production throughput, freshness SLA, source uptime or financial accuracy guarantee is claimed.
- No automatic signed-in data export/migration, quote-derived personalized advice, bond pricing, subscription, or new server exposure was introduced. Future permitted-source changes must retain the fixed-board privacy boundary and source validation.
- Physical-device secure file protection/backup checks and the Phase 4 Android 16 authenticated thumbnail-cancellation validation blocker remain unresolved. This phase does not certify those authenticated native flows or declare the application release-ready.
- Review public endpoint abuse controls, provider licensing/expiry, the serving domain and operational cost before enabling or distributing to more users. Existing backend anonymous cache cannot be replaced by unrestricted force-refresh requests.
- Full guest detail/calendar/history/doc/settings experience remains Phase 6. No production configuration, deployment, merge, or push was performed.

Final results: iPhone 16 / iOS 26.2 simulator **351 shared + 65 core tests passed**, zero failures/errors/skips. The final Android 16 guest journey **passed (1 test)**, including the disabled personal Markets message, sample separation, restart and sign-in-return; the local account-request counter remained zero. Temporary iOS simulators were shut down/deleted by their test script. The Android emulator was no longer present when cleanup checked it; the additional disabled-screen screenshot could not be retrieved. The passing instrumentation result is retained. No physical device or signed iOS app was used. The existing authenticated thumbnail-cancellation blocker was not silently reclassified as passed.

Final result XML counts were checked. Local logs: `/private/tmp/struct-guest-phase5/final-build.log`, `final-ui-build.log`, `final-tests.log`, `ios-tests.log`, `android-native.log`, `GuestEntryTest.log`; backend log `/private/tmp/struct-guest-phase5-backend-tests.log`. These are local execution records, not required runtime assets.

## Intended source changes

- Core `PublicEquityQuotes.kt`: fixed-board public client and normalized quote whitelist. `HttpEngine.android.kt` / `.ios.kt` / `.jvm.kt`: separate public factory using the established platform transport, with Android global-cookie refusal.
- Shared `GuestQuotes.kt`: permission gate, separate versioned cache and conservative freshness policy. Native `GuestSnapshot.android.kt` / `.ios.kt`: inject the existing protected storage into that cache.
- `PortfolioModel.kt` / shared `App.kt`: optional independent quote job, cache-first presentation, cancellation/generation isolation and composition wiring; existing account market state and API remain separate.
- `GuestScreens.kt` / `FrenchStrings.kt`: limited delayed/stale/unavailable public quote and fee-aware position labels, public board display and sample separation using established components.
- `PublicEquityQuotesTest.kt`, Android-host `PublicQuoteTransportTest.kt`, `GuestQuotesTest.kt`, and the existing Android `GuestEntryTest.kt`: transport/privacy, cache/valuation/lifecycle regressions and disabled-mode device journey.
- This document: verified source and licence findings, data flow, runtime gate, actual checks and remaining dependencies.

Both repositories remain on `feature/guest-mode`. Backend worktree is unchanged; only the KMP repository requires a phase commit. No push/merge/deployment is authorized or performed.
