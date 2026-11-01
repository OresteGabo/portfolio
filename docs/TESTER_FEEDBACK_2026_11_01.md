# Tester feedback — startup, market data, calendar and original PDFs

Reviewed 1 November 2026 against the current KMP and companion Spring sources, plus the local legacy website implementation in `WebstormProjects/gabo`. The tester's older APK, account and production records were not inspected. These findings distinguish reproducible code paths from assumptions about that installation.

## 1. Startup and RSE data

The earlier biometric flow already validates the server session before exposing private content, then launches portfolio/document/notification synchronization without holding the global action lock. Previous production evidence attributed routine 17–21 second waits to Cloud Run scale-to-zero, and one warm instance was explicitly configured; see [cold-start evidence](UNLOCK_COLD_START.md). That is a dated verified configuration, not a fresh runtime measurement in this task.

Two remaining code gaps were fixed:

- Normal session restoration and password login still awaited `loadPrivateData`, including supplementary requests. They now use the same nonblocking `startPortfolioSync` path as biometric unlock after server authentication succeeds.
- Startup previously fetched equity quotes only after loading a portfolio with active shares. Markets fetched the slower multi-page bond board before requesting equity quotes. Startup now prefetches both independent resources concurrently for every authenticated investor, including users with no share holdings. The global action lock remains free. A mutex and the existing one-minute local cache prevent overlapping normal loads; explicit refresh keeps the existing server refresh query parameters. Failures retain existing data, and 401 handling remains authoritative.

The backend already obtains all equity closing quotes from one RSE page, rather than ten sequential company-price requests. It caches upstream responses for fifteen minutes, bounds HTTP connection/request timeouts, and has a short failure cache/stale fallback. No new scraper, provider, service, polling loop or database change was introduced. Per-company historical charts still load on demand rather than downloading every history at boot.

Regression: with a synthetic twenty-second bond-board delay and no equity holding, quotes are requested immediately, authenticated UI is unlocked, `busy` is false, and entering Markets does not duplicate the in-flight request. This proves scheduling behavior, not real-device or production latency.

## 2. Calendar month

The current screen already defaults to today's Kigali date, not the first coupon. However, `calendarFocus` from a notification/forecast survived a later bottom-bar visit. The bottom-bar Calendar action now clears that deep-link focus. Intentional notification/forecast navigation still opens the requested date. An untouched default/Today selection follows a date rollover while the screen remains alive; a deliberately browsed month stays selected.

The older APK symptom was not reproduced. A tester should verify a fresh launch, a dated notification → Home → Calendar, and an explicitly browsed month. The latter is deliberately retained while browsing, not automatically snapped back on every composition.

## 3. Reopening terms

The calendar header shows one compact annual coupon-rate line only when verified saved issuance terms identify that security. Explicit `reopensIssueId` references were added for the five existing published reopening rows, using the original auctions already named in their titles/source descriptions. `mergeBondIssuances` can carry the verified original ISIN forward only when maturity agrees. Coupon lookup requires that ISIN and maturity and refuses conflicting rates. A same-tenor/title/date resemblance alone is insufficient.

Auction price, yield and settlement dates are not inherited. Unknown rates remain absent. Regression tests cover explicit reopening provenance, wrong ISIN/maturity, conflicting rates and coupon-versus-yield distinction. No coupon amounts or investment values were changed.

## 4. Original PDFs and existing financial records

Verified legacy web path: `BondPdfUpload.readFile` extracts data and passes `result` and filename to `onImport`; `BondPortfolioPlanner.savePurchase` POSTs the extracted purchase/equity fields. That path does not upload the original bytes to `/documents`. Therefore a correctly saved investment can legitimately have no original file in the mobile library. This explains a possible source of the tester's symptom; it does not prove the contents of their account or the exact deployed website version.

Mobile upload already stores original PDFs separately, uses authenticated downloads, and checks byte digests; backend storage deduplicates by owner/SHA-256. This pass fixes the remaining evidence linkage:

- Existing bytes without completed purchase provenance remain reviewable and reuse the file; they no longer imply all financial information is saved.
- An equity purchase is complete only when its contract PDF ID is persisted on that purchase. A matching legacy equity without a link is submitted to the existing save endpoint after upload.
- Existing Spring POST endpoints accept evidence-only retries for an exactly matching saved investment. They validate the owned PDF/category, compare normalized financial/identity fields, retain the original investment ID/creation time, and attach only missing evidence. Repeating the operation is idempotent. Conflicting financial data or a different existing evidence link gets a review error, rather than another holding or an overwrite. Normal duplicate POSTs without evidence retain their existing conflict behavior.
- Old active bonds expose an unambiguous shared public issuance PDF matching ISIN, settlement and maturity, even without a stored issuance link. This read-time source link does not confirm personal allocation and does not change the investor's amount or coupon schedule.

Backend integration tests cover repeated bond/share reuploads, unchanged IDs/counts, conflicting details, another account's PDF and shared issuance evidence on an old holding. A model regression verifies that existing financial data without a file does not suppress upload.

## Rollout and manual acceptance

Deploy the companion backend changes **before** distributing the rebuilt app. The old backend rejects an evidence-bearing duplicate instead of linking it; mobile cannot compensate for that by safely duplicating holdings. No deployment, production data repair, infrastructure change or tester message was performed in this task. No schema migration is needed.

Then ask the tester to reupload from the app while signed into the same account/server used on the website. Confirm the PDF appears in Library and investment details, preview/download succeeds, and holding counts/amounts remain unchanged on repeat upload. If details differ, review those differences rather than forcing a match. Recovering an original PDF discarded by an earlier website requires the user to supply it again.

Local verification/results are recorded in [VERIFICATION.md](VERIFICATION.md). Device after-idle timings, deployed service readiness and the tester's actual account still need the rebuilt/deployed acceptance run. The shake-to-hide behavior was not modified.
