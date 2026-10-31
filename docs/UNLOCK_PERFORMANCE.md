# Biometric unlock investigation — 30 October 2026

**31 October production follow-up:** the remaining post-idle server-session delay is now attributed to Cloud Run scaling to zero and starting Spring on the first request. Read-only production request/startup logs reproduced 17.44 and 20.73 second session waits coinciding with new instances. With the user's explicit recurring-cost approval, service-level minimum instances was changed from 0 to 1; the revision, container configuration and authentication were preserved. See [production evidence, cost and verification limits](UNLOCK_COLD_START.md).

The original 30 October investigation verified a separate mobile navigation gate waiting for portfolio synchronization and optional enrichment. At that time, the physical-device request latency was not attributed: production Cloud Run logs/configuration were not inspected. The implementation below removed the mobile dependency and added diagnostics; the follow-up above supersedes that earlier attribution limit without turning its virtual-time tests into physical-device benchmarks.

## 31 October: initial portfolio error flash

The initial dashboard previously treated `!portfolioReady && !portfolioSyncing` as failure, even without a failed request. Blocking session restoration/password login publishes the authorized user before the snapshot finishes but does not own the background-sync flag. That gave the dashboard a false **Try again** state. Any intermediate unlock transition with the same flags also selected that presentation, although the exact brief screen on the physical iPhone was not captured in this investigation.

The dashboard now uses `portfolioLoading`: an initial snapshot remains loading until an explicit portfolio failure exists, including while asynchronous synchronization is running. Blocking initial-load failures persist in `portfolioSyncError`, independently of the five-second global banner/dismissal. Cancellation and rejected authorization do not become portfolio retry errors. Retry clears the old initial-action banner, starts loading immediately and preserves genuine failure/retry behavior. No artificial delay, navigation wait, biometric change or cached-data authorization bypass was added.

Two new regression tests failed against the prior presentation/error behavior and pass with the fix. A third covers repeated protected unlock, background relock and expired-session renewal with delayed investments. Malformed HTTP-200 HTML/non-JSON responses produce sanitized errors and recover on explicit retry; they do not expose response text. Existing error sanitization and the iOS engine do not render server JSON/HTML as part of unlock. The user's separate report of something resembling an HTTP response remains unconfirmed on-device; tests are not a claim to identify every visual flash. This client fix requires an updated mobile build; the warm-instance configuration alone cannot update its UI.

## Actual architecture and execution path

Android `MainActivity` creates shared Compose `App`; iOS SwiftUI hosts `MainViewController`/`ComposeUIViewController`. Shared `PortfolioModel` is a lifecycle ViewModel built directly by Compose, with direct settings/vault/API dependencies: there is no DI container or separate repository initialization to optimize here. Preferences use Android SharedPreferences / iOS NSUserDefaults. No local portfolio database or persisted portfolio cache is involved in unlock.

Android's vault uses AndroidX BiometricPrompt/device credentials plus Keystore-wrapped AES-GCM data keys. iOS uses LocalAuthentication and a passcode-bound, device-only Keychain item requiring user presence. The protected record contains saved credentials and an opaque server-cookie snapshot. Reading it is the biometric gate; returning from the OS prompt is not a server authorization result.

`PortfolioApi` uses Ktor 3.6: Android engine on Android, Darwin/NSURLSession on iOS, CIO for JVM checks. One client per model API instance, no application-level retry interceptor, JWT validation, refresh token or parallel refresh mechanism. Connect/request/socket timeouts remain 15/45/40 seconds. Cookie persistence is exclusively through the native vault. The configured origin remains `https://bonds.orestegabo.dev`; the website's local Next.js configuration rewrites `/api/bonds/*` to a configured Spring origin. Deployed proxy and instance settings were not verified.

The real backend is the sibling `gabo-portfolio-api`, not this repository's sample Ktor server. It uses Spring Boot 4.1.2, Spring Security, Spring JDBC/JdbcTemplate, PostgreSQL, Hikari and Flyway. JPA dependencies exist, but these financial services issue JDBC queries. A security filter validates hashed opaque session cookies against database expiry, credential version and account status. Sessions last twelve hours; invalid sessions renew once through the existing saved-password login flow, not an OAuth refresh endpoint.

### Before

1. Model initialization loads packaged public calendar/education assets; a saved sign-in keeps the app locked and prevents private requests.
2. Foreground queues one biometric attempt; duplicate resume events do not open multiple prompts.
3. `PortfolioModel.unlock` owns global `busy` throughout: vault read/prompt → cookie restoration → `GET auth/session` → conditional password login.
4. Still locked, `loadPrivateData` awaits compact `mobile-overview` (or legacy purchases then equities), documents, optional old-overview history fallback, statement items and equity quotes.
5. `MarketService.quotes` on a cache miss synchronously reads RSE pages; the equity source has a five-second connection and eight-second request timeout. Its cache lasts fifteen minutes (failure cache one minute). Quote history also writes to PostgreSQL. This optional enrichment was on the navigation gate.
6. Vault save rewrites the unchanged protected record on every unlock.
7. Only then does the model publish `user`, clear `locked`, fetch notification preferences/inbox/coupon receipts and release `busy`. Once `user` is published, the UI separately synchronizes device registration; that effect already runs outside the unlock action. The notification call *inside* `loadPrivateData` was a no-op before `user` existed, so it was not two real inbox loads during this path.
8. The session endpoint separately called `auth.session` even though its security filter had already done so: duplicate session/account database reads on that one request.

There is no unconditional twenty-second sleep in this path. Pull-to-refresh presentation delays and the Android parser's twenty-second engine-ready timeout do not execute as part of dashboard unlock. Android creates a local parser WebView eagerly during composition; its constructor is now timed, but no evidence attributes the reported delay to it. iOS's JavaScript context initializes lazily when parsing/projection is requested.

### After

1. Native authentication and server session validation remain mandatory and unchanged in strength.
2. An empty/locally expired cookie snapshot goes directly to the existing password renewal rather than making a guaranteed unauthenticated session round trip. Valid snapshots still undergo server validation.
3. Only renewed sessions rewrite the native vault. Valid unchanged cookies do not need another protected-storage write.
4. After a server-validated identity, the model publishes the authenticated UI and releases the global action lock. A ViewModel-owned synchronization job loads investments, documents, statement evidence and notifications independently. Equity quotes follow the saved investment snapshot and never own the global action lock.
5. Legacy bond/equity reads run concurrently. Existing write/refresh synchronization reuses the same snapshot loader under a mutex. The initial snapshot merges independently loaded documents instead of overwriting them.
6. The portfolio shows a loading/retry state instead of an empty balance until investments load. The library has its own loading/error/retry state. A slow document or quote response cannot hold the dashboard locked. Existing quote fallback/stale presentation remains in use.
7. Lock/logout cancel synchronization, clear private state and invalidate response generations. Duplicate starts are coalesced. Explicit retry cancels the previous job with a separate sync revision so an old finalizer cannot clear new loading flags. Ordinary UI actions do not invalidate the session's sync revision.
8. A server 401 from synchronization or notification preferences/inbox invalidates the session and clears private data. No parallel background credential retries are introduced.
9. During slow session verification the unlock screen distinguishes “Device unlocked” from server verification and allows password fallback to cancel the pending verification safely.
10. The Spring session endpoint uses the principal established by the security filter. Validation still happens on every request; it is not cached across requests.

## Cold versus warm interpretation

“Close and reopen” is ambiguous. Within the existing **30-second** background grace period, the live ViewModel/session/snapshot can remain available; foreground may only refresh expired notification data. After the grace period, `lock` clears private records and the API client, requiring another biometric/server-validation cycle. A force-stop/process kill creates a new model, HTTP engine and vault context and starts with no private snapshot. Activity recreation can also detach/lock the Android vault; record lifecycle events rather than treating it as a guaranteed process kill. iOS suspension may defer coroutine timers, so the existing monotonic background-expiry check remains in place on resume.

Server cache warmth is separate: Spring instance startup, class loading, proxy/DNS/TLS, database connection establishment and market cache misses can differ between two launches. The checked-in `cloudrun` profile has Hikari maximum pool size 2, minimum idle 0, idle timeout 60 seconds and connection timeout 15 seconds. These settings permit connection establishment on demand; they **do not prove** a deployed 15-second wait, pool exhaustion, Cloud Run scale-to-zero, or Neon wake-up. No indexes, schemas, pool sizes, minimum-instance counts or infrastructure settings were changed without query/runtime evidence.

## Measurements and their limits

The controlled regression uses Ktor MockEngine plus the coroutine test scheduler. Its deliberately specified delays are session 200 ms, overview 200 ms, purchases/equities 300 ms each, documents 1,000 ms, statement records 2,000 ms and quotes 16,000 ms. The **same fixture** was first run against the original implementation with an assertion of 20,000 ms, then against the new flow with an assertion of 200 ms. These are executed virtual-time measurements of model gating, not phone/frame benchmarks or observations of RSE response times.

| Metric | Before | After | Evidence / limitation |
| --- | --- | --- | --- |
| Native Face ID/fingerprint prompt | Not measured | Instrumented; not measured on hardware | Tests use a synthetic vault, including delayed/cancelled prompt completion |
| Cookie/session restoration CPU time | Not separately measured | Instrumented | No fabricated native storage benchmark |
| Authentication network latency in controlled scenario | 200 ms | 200 ms | Same simulated server latency and mandatory validation |
| Model unlock/action lock release, controlled cold scenario | 20,000 ms | 200 ms | Same requests/delays; portfolio synchronization now asynchronous |
| Warm return within grace, settled scenario | No before benchmark | 0 ms additional scheduled waiting, no requests or vault read | Virtual-time regression; not a hardware frame measurement |
| Dashboard first visible frame / physical time to interactive | Not measured | First-frame hook added; requires device capture | Model unlock is not a measurement of display/rendering |
| Public unauthenticated session HTTP samples | No before benchmark | 304.938 ms then 198.068 ms | Two fresh curl processes from this workstation; no cookies; no proof of a cold instance |

For those public samples, first-byte times were 304.601 / 197.864 ms, DNS 63.586 / 4.373 ms and completed TLS 143.352 / 80.853 ms. They exercise the configured proxy/network path but **not** authenticated database lookup, portfolio loading or the affected person's network. They did not reproduce the twenty-second report. Local PostgreSQL request timings are development evidence only; test datasource connections are unpooled and cannot measure deployed Hikari contention.

## Diagnostic capture

All duration clocks are monotonic (`TimeSource.Monotonic` on KMP; `System.nanoTime` on Spring). Debug mobile builds enable `UnlockPerformance` at shared app startup; release defaults to silent. A controlled diagnostic release can explicitly enable it at the host before app construction, then disable it after capture. The diagnostic sink is guarded so a logging failure cannot break authentication.

Mobile stages cover ViewModel creation, public assets, Android vault/local-engine construction, native prompt, protected-storage read, Android key retrieval/decrypt or iOS Keychain read, cookie restore, renewed vault write, HTTP method/route/status, JSON encode/decode, portfolio snapshot and unlock-to-first-frame. `device_success_to_first_frame` starts when the authenticated vault read returns, after native decryption; it is not an exact OS callback timestamp. The shared first-frame hook runs on the next Compose frame after authenticated navigation; measure actual responsiveness separately with a device trace. No separate repository/DI stage exists in this architecture. HTTP totals include DNS/TLS/proxy/queuing but do not split them; use platform network traces or the curl timing breakdown when needed.

Enable Spring diagnostics temporarily with `GABO_PERFORMANCE_DIAGNOSTICS=true`. `RequestPerformanceFilter` records total handling, status, safe route label and `X-Request-ID`. A conditional datasource wrapper aggregates connection acquisition and statement execution durations without recording SQL/parameters. Authentication lookup/password verification/session creation/security queue, RSE HTTP and application JsonCodec operations have separate aggregate stages. These are nested timings, not additive totals. Servlet response conversion is included in total request time; `json_encode/decode` describes explicit JsonCodec operations, not every Spring MVC serializer invocation. Container boot/ingress delay before the filter and SQL result mapping outside execution require hosting/JVM traces.

Diagnostic mobile requests send random correlation IDs. The server accepts only 8–64 ASCII letters/digits/hyphens or generates a fresh UUID; arbitrary paths are reduced to `/api/bonds/other`. Neither side logs query strings, account IDs, emails, tokens, cookies, request bodies, biometric results, financial values or exception text. Keep diagnostic logs access-controlled and short-lived. Correlation/CORS header support is additive; no public response schema changed.

Capture a matrix on the affected Android APK and iPhone:

1. Start a diagnostic build with a still-valid saved session; note OS version, device, build, selected server and network condition without credentials.
2. Measure fresh process start, immediate background/return (<30 s), background/return (>30 s), and actual process kill separately. Android Back/Home, swipe-away and `adb shell am force-stop` are not interchangeable. Force-stop only the developer's test installation after consent, not another person's app/data. On iOS distinguish suspension from process termination.
3. Repeat with an expired session, rejected saved password, airplane mode, unavailable server and slow portfolio APIs. Use synthetic accounts/fixtures for failure injection.
4. Collect `[UnlockPerformance]` client events and matching Spring request IDs. Compare the HTTP total to Spring total, then connection/execute, password and RSE stages. Inspect instance boot timestamps/cold-start labels, pool metrics and database query evidence before altering hosting or indexes.
5. Capture screen/frame timings with Android system tracing or Xcode Instruments. In debug Android, `adb logcat -v monotonic | rg UnlockPerformance` is a useful filtered log view. Server logs must be captured from the same deployed build with diagnostics enabled. Verify the proxy preserves the correlation header.
6. Turn diagnostics off. Never paste raw HTTP headers/cookies or a user's financial documents into reports.

## Security and remaining limits

The cold authorized UI still waits for the server session check (and login if necessary). If **that** request takes twenty seconds, this change cannot promise a subsecond authenticated UI. The screen now identifies that stage and remains cancellable. Treating a biometric read or unverified saved cookie as current server authorization would violate the existing policy. The target of under one second is realistic for an already live warm session or a promptly validated session, not a guarantee across an unavailable server.

No insecure persisted portfolio cache, new offline authorization policy, JWT shortcut or unbounded credential retry was introduced. Within-grace in-memory reuse and clearing on lock are preserved. Protected records are never displayed before both native authentication and the required server check. Backend checks, cookie flags, credential-version invalidation and account isolation remain intact. Financial calculations, imports, database schemas and deployment settings were not changed.

Exact production attribution still requires authenticated cold/warm traces, hosting logs and real-device UI measurements. Large JSON decoding and eager Android WebView setup now have timing hooks but were not proven slow enough to justify unrelated threading/lazy-engine refactors. Slow authenticated session validation, cold container/database wake-up, proxy delays and physical render cost remain measured follow-ups, not established causes.

## Changed files

Paths below are relative to each checkout.

| Checkout / files | Purpose |
| --- | --- |
| KMP `core/.../UnlockPerformance.kt`, `PortfolioApi.kt` | Opt-in monotonic diagnostics, safe request correlation, JSON/HTTP timing |
| KMP shared `PortfolioModel.kt` | Separate authorized navigation from synchronization; concurrent legacy reads; cancellation/retry/session handling |
| KMP shared `PortfolioDashboard.kt`, `DocumentsHub.kt`, `UnlockScreen.kt`, `FrenchStrings.kt` | Independent loading/retry states, explicit server verification and French translations |
| KMP shared `InvestmentApp.kt`, `App.kt` | Next-frame navigation timing, debug diagnostic setup, ViewModel initialization timing |
| Android shared `SecureSignIn.android.kt`, `PlatformPorts.android.kt` | Prompt/storage/vault/engine timing with unchanged native policies |
| iOS shared `SecureSignIn.ios.kt` | Prompt and Keychain-read timing with unchanged ACL |
| KMP tests `SecureSessionTest.kt`, `UnlockPerformanceTest.kt` | Delays, failures, expiry, concurrency, lifecycle and log privacy regressions |
| Spring `AuthController.kt`, `SecurityConfig.kt` | Reuse per-request validated principal; correlate requests; time security validation |
| Spring `AuthService.kt`, `MarketService.kt`, `ApiSupport.kt` | Authentication, upstream and explicit JSON timing |
| Spring `RequestPerformance.kt`, `application.properties` | Opt-in request/JDBC instrumentation; release default off |
| Spring `RequestPerformanceTests.kt` | Sanitization, cleanup and transparent datasource wrapping regressions |
| Documentation `UNLOCK_PERFORMANCE.md`, architecture link, verification entry; Spring `docs/PERFORMANCE.md` | Evidence, limitations, test results and capture procedure |

## Verification

- Controlled before-fix regression passed with an asserted 20,000 ms virtual unlock wait; after-fix regression passed with 200 ms.
- 215 shared Android host tests, 41 core JVM tests and 42 Spring tests passed. Spring tests used fresh disposable PostgreSQL 18 with migrations V1–V8 and diagnostics enabled; no production data or schema was used.
- Android debug APK assembly, shared iOS simulator compilation and Spring bootJar passed.
- 230 shared iOS simulator tests passed. Synthetic vault tests do not exercise real Face ID hardware.
- Test coverage includes valid/expired sessions, rejected renewal, slow/unavailable/offline responses, simultaneous unlock, concurrent 401s without refresh storms, cancellation, prompt ordering, new model/cold start, within-grace warm return and background expiry.
- No deployment, installation on a person's phone, Git commit or production configuration change was performed for this task.
