# Post-idle unlock: production cold-start evidence

Investigated **31 October 2026 (Europe/Paris)** following the screenshot showing **Device unlocked / Checking your server session** after roughly twenty minutes away. This is a follow-up to [the earlier mobile unlock fix](UNLOCK_PERFORMANCE.md), not a second authentication implementation.

## Finding

The routine 15–25 second delay is explained by the deployed **Cloud Run scale-to-zero setting and Spring/JVM cold initialization**. Face ID has already completed. The mobile app correctly waits for server authorization; the first session request causes a backend instance to start. This is not evidence of KMP rendering or biometric verification taking twenty seconds.

Verified active revision: `gabo-portfolio-api-gh-41138071565-1`, Git SHA `3bc3dd568dfa0defc9279c6d50cbedd1b883c315`, all production traffic. This includes the earlier session/performance/security changes. The incident is not explained by that backend patch being absent.

Before the change, service and revision minimum instances were both 0 (default), maximum revision instances 1, request-based billing/CPU throttling enabled, startup CPU boost already enabled, 1 vCPU/1 GiB, concurrency 8. Adding CPU boost again is not a fix; it was already active.

## Correlated production evidence

Only allowlisted session timings/status, revision/instance correlation and startup messages were inspected. No account identities, IPs, cookies, authorization values, financial records or PDFs were printed. No password, real user session or database contents were accessed. Times below are UTC, with the screenshot's Paris time shown separately.

| Screenshot-time event | UTC timestamp | Meaning |
| --- | --- | --- |
| Session request begins | 30 Oct 23:20:03.211599 | **31 Oct 00:20:03 Paris**, matching the screenshot's displayed minute |
| Cloud Run starts a new instance | 23:20:03.229949 | Request arrival and instance creation separated by about 18 ms |
| Spring application starting | 23:20:07.482776 | JVM/container initialization underway |
| Hikari start / start completed | 23:20:16.749349 / 16.921990 | Pool initialization; not an isolated authenticated-query benchmark |
| Spring reports ready | 23:20:20.277603 | Startup message: 13.869 s application startup, process running 16.685 s |
| Session request latency/status | **17.438266484 s / HTTP 200** | Approximately 17.066 s of the request interval elapsed before Spring reported ready |
| Subsequent private requests | Starting 23:20:21 | Documents 249 ms, statement items 386 ms, overview 322 ms; these proceed after authorization |

The timestamp match and subsequent authorized requests strongly correlate this occurrence with the reported experience; no phone-to-server trace ID was supplied, so it is not claimed as a unique device-identity match. Another independently checked occurrence at 21:46:22.893435 UTC took **20.732527007 s**; Cloud Run started an instance at 21:46:22.924146 and Spring reported ready at 21:46:43.375683 (16.164 s application startup, process running 19.625 s).

For the explicitly bounded UTC window **30 Oct 00:00 through 31 Oct 00:00**, the session-request logs returned 25 requests, all HTTP 200:

- 8 requests exceeded 3 s: minimum **15.3027 s**, maximum **20.9966 s**.
- The other 17 requests had median **79.2 ms**, range **6.35–1,131.50 ms**.
- The fast/slow groups are latency classifications, not a claim that every request's client identity, authentication state or startup attribution was individually verified. Two slow instances were directly correlated as above.

This explains why an immediate reopen feels fast while a return after twenty minutes can be slow. Cloud Run can remove idle instances; a warm app process/Face ID does not keep the remote service running. Spring initialization is paid again when a new instance is needed. [Google scaling/billing behavior](https://docs.cloud.google.com/run/docs/configuring/billing-settings), [minimum-instance behavior](https://docs.cloud.google.com/run/docs/configuring/min-instances).

## Attribution limits

- The Cloud Run request alone accounts for the reported order of delay; the proxy, mobile connection and UI can add smaller delays. The logs do not establish those as zero.
- Database initialization can occur within Spring startup. Hikari's 173 ms initialization log is not proof all database work took 173 ms. Per-query diagnostics were disabled in this deployed configuration; SQL/database wake-up and later pool contention are not individually benchmarked here.
- Current `PortfolioModel.unlock` validates the session (or renews saved sign-in when needed), publishes authorized UI and launches portfolio synchronization asynchronously. The image's screen is the remaining authorization gate. Showing protected cached data before valid server authorization would change existing security policy and is not the fix applied.
- Force-stopping the phone adds local initialization, but Cloud Run cold start is independent of whether the mobile process survived. No new physical-device trace/cold-start benchmark was recorded in this investigation.

## Approved deployment change

The user explicitly approved **Keep one instance warm** after being informed of recurring cost. Applied to this project/service only:

```sh
gcloud run services update gabo-portfolio-api --project=gabo-portfolio-api \
  --region=europe-west1 --min=1 --quiet
```

Verified service annotation `run.googleapis.com/minScale=1`. The ready revision and complete revision template were compared before/after and were unchanged: no image/code deployment, secret/configuration/resource change, database migration, auth relaxation or change to production traffic. Existing revision maximum 1 and request-based billing remain. Service-level minimum is preferred to adding minimums to every tagged candidate revision. Existing image-only deployment workflow preserves the service setting; future infrastructure-as-code must preserve it explicitly.

This removes the normal scale-from-zero condition while the one warm instance is healthy. It is not a guarantee against restarts, outages, deployments, saturation, network latency or database wake-up. Minimum instances are a best-effort target and can be restarted. No repeating keep-alive job, full-CPU idle billing, database keep-alive or paid OCR service was added. [Google minimum-instance guidance](https://docs.cloud.google.com/run/docs/configuring/min-instances).

Cost reviewed before approval: Belgium is Tier 1; request-based idle minimum CPU is US$0.0000025/vCPU-second and idle memory US$0.0000025/GiB-second. At 1 vCPU/1 GiB for thirty fully idle days, `2,592,000 × (0.0000025 + 0.0000025) = US$12.96`. This is an **idle baseline before shared free-tier discounts**, not an invoice or total bill: active CPU, requests, database, storage, other services, currency and taxes differ. No billing-account usage or credits were read. [Official Cloud Run pricing](https://cloud.google.com/run/pricing).

Rollback, if later chosen for cost reasons: the same command with `--min=0` restores scale-to-zero and its routine cold-start tradeoff. Do not silently revert the latency fix during another deployment.

### Monthly spend control — 31 October 2026

**Later combined-budget decision:** the user subsequently chose €35 **total across services**. The Cloud Run cap is now **€25**, with a separate €35 alerts-only Google-project budget and service max=1. Neon Free and Vercel Hobby plans were verified and retained. [Current cross-provider controls](COST_CONTROLS.md) supersedes the initial €35 Cloud Run allocation described below; no exact all-provider invoice ceiling is claimed.

After discovering that the linked billing account uses EUR, the user explicitly chose **€35 per month**, rather than the euro equivalent of US$35. Created and read back `Struct Capital - Cloud Run monthly spend cap`; API state is `CONFIGURED`. Scope is only project `gabo-portfolio-api` / Cloud Run (`services/152E-C115-5142`), calendar month, gross estimated cost excluding credits. Alerts use current spend at 50%, 80% and 100% (€17.50, €28, €35), with project-level recipients enabled. Required Cloud Billing/Budget management APIs were enabled in this project. No other project budget or service setting was changed.

This is spend-cap enforcement, not an alerts-only budget. Enforcement pauses new scoped usage when triggered and automatically resets next month; it can interrupt the backend. Reporting/enforcement delays, in-flight work and continuing fixed costs can cause overages. Database, Vercel, Artifact Registry and other services are outside this cap. It is not a guaranteed €35 total invoice ceiling. No alert email was manually sent and delivery was not independently tested. [Official spend-cap behavior](https://docs.cloud.google.com/billing/docs/how-to/budgets-spend-caps).

## Verification and next observation

The production configuration update succeeded and post-update metadata comparison passed. Six fresh anonymous curl requests (separate curl processes, no retries/cookies) returned HTTP 200:

| Post-change sample | Mobile API origin through Vercel, total | Direct Cloud Run origin, total |
| --- | ---: | ---: |
| 1 | 495.29 ms | 242.06 ms |
| 2 | 255.90 ms | 162.50 ms |
| 3 | 324.38 ms | 165.87 ms |

These include workstation DNS/TLS/network/proxy time and test routing/readiness, not authenticated SQL or the user's 5G connection. They are warm-configuration checks immediately after the change; they are not a controlled before/after phone benchmark or a completed twenty-minute idle retest. No application behavior was changed, so the prior full financial/security regression suite was not rerun for this configuration/documentation investigation. Documentation links, arithmetic and diff whitespace were checked.

For the final user-device check: return after at least twenty minutes normally, complete Face ID and observe the server-session screen duration, then compare Cloud Run request/startup logs. Check force-stop separately. Do not ask for user passwords/cookies. Investigate any remaining long request with existing opt-in correlation/JDBC timings rather than assuming min=1 solves every layer. The new configuration does not require reinstalling the mobile app.
