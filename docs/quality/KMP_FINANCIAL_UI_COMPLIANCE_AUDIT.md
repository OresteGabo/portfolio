# INZIRA financial presentation, privacy and regulatory scope review

> Historical scoped audit. Its original exclusions, failures and Git disposition below describe the earlier isolated attempt. The 15 November combined integration and current disposition are recorded in [KMP_PENDING_CHANGES_REVIEW.md](KMP_PENDING_CHANGES_REVIEW.md); that later review supersedes the status here without reclassifying a failed historical run as passed.

Date and official-source access date: **2026-11-15**. Baseline: `40aedf4`, branch `main`.

## Scope and confidence

KMP client only: `app/shared`, `app/androidApp`, iOS host/extensions, shared `core` calculations read for interpretation, and bundled legal content. No backend, website, financial formula, API contract, permission regime or regulated service was changed. Existing unrelated uncommitted UI/accessibility/responsibility work is preserved and excluded from the isolated commit/verification tree.

This is an engineering assessment with a bounded review of official sources, not a legal opinion, licensing determination, regulatory approval or compliance certification. Actual operator identity, establishment, user countries, registrations, service contracts, production processors/regions and store submissions were not independently verified. No production credentials, user financial PDFs, regulator applications, live account deletions or messages to third parties were used.

### What the application actually does

Client evidence supports **investment recordkeeping and informational/planning functionality**, not an execution platform:

- `PortfolioDashboard`, `InvestmentDetails`, `ShareDetailsScreen`, `CashLedgerScreen` show saved bond/share purchases, pending records, payments and recorded cash movements.
- `ShareOrderScreen` / `PortfolioShareOrders` save a pending record to the app API. Uploading a broker contract can replace that estimate with a recorded purchase. The inspected client has no broker execution SDK, custody account, money-transfer or payment checkout integration. Removing a pending record expressly leaves the broker order unchanged.
- `ApplicationBidScreen` records bid information; application forms are previewed/exported through native sharing. This is not evidence that Inzira submits orders to an exchange or guarantees allocations.
- `PlanningScreens` runs an assumption-based simulator. No individualized buy/sell recommendation engine was identified. This does not settle the legal classification of every tool, educational statement or future commercial offering.
- Personal guest data lives in a separate protected local workspace. Account saves send records/PDFs to the server. Guest-to-account migration is a separately reviewed and confirmed operation.
- Firebase/Apple notification adapters, native widgets/live status, biometric sign-in, document sharing and encrypted guest backup are implemented client capabilities. Their presence is relevant to notices and store disclosures, even without advertising analytics.

Bundled terms say the service is invitation-only and operated in Rwanda, and exclude order placement, custody, guarantees and personalized investment/tax/legal advice. These statements are consistent with the inspected client capabilities; the operator/jurisdiction statements still need factual confirmation. Absence of a client capability does not prove the operator conducts no related activity elsewhere.

## Financial presentation findings

Paths in this section are under `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/` unless qualified.

| ID / severity | Evidence, impact and disposition |
|---|---|
| F01 High — absent projections shown as zero | `PlanningScreens.SimulatorScreen` used `optDouble(key, 0.0)` for summary/monthly monetary output and chart observations. Missing output could look like a genuine zero balance or loss. **Fixed:** `FinancialAmountPresentation` reports missing, null, malformed and non-finite results as **Unavailable**. Valid zero, negative and fractional values retain existing formatting. If a chart month has no usable value, withhold that chart and explain why, rather than plotting zero or silently joining across a missing month. This changes presentation of malformed/incomplete output, not the projection engine. |
| F02 Medium — return type visible only in explanation | `ShareReturnSummary` displayed gain/loss and percentage without a nearby classification; unrealized meaning was inside an information dialog. **Fixed:** a small **Unrealized gain / loss** label accompanies the amount/rate in company, lot and purchase-history summaries. **Estimated market value** replaces the unqualified valuation label. Existing gain signs, currencies and information dialog remain. |
| F03 Medium — missing detail amount was a dash | `InvestmentDetailComponents.DetailAmount` rendered nullable values as `—`. **Fixed:** explicit localized **Unavailable**; non-finite values also use that state. Currency remains separately visible. Financial masking still passes through `FinancialText`; no amount is added to an unmasked description. |
| F04 Medium — risk context absent in results mode | `SimulatorScreen` explained uncertainty in assumptions mode, but that explanation disappeared after switching to results. **Fixed:** one short results-side statement identifies an illustrative projection, with no guaranteed return, and directs the user to review assumptions. No warnings added to unrelated screens. |
| F05 High — successful stale quotes looked fresh in an order | `ShareOrderScreen` passed only `quoteRefreshFailed` to its retained-price message. A successful response with `source.stale=true` could still say **Latest closing price**. **Fixed:** respect both flags; show **Cached closing price**, the existing retained-price explanation and Retry. Recorded price, fixed 1.71% estimate, quantities, fees, ID/version and API payloads are unchanged. The instrumented order fixture explicitly exercises a successful stale response while asserting unchanged totals. |
| F06 Medium — account import storage ambiguous | `ConversationReview` said bond PDFs stayed private, without clearly saying server storage; equity/pending-contract and statement branches did not share that explanation. **Fixed:** before personal bond/equity/statement review, explain that saving stores the PDF and extracted details on the server for the account. Guest and public-issuance flows are excluded. This describes the inspected upload calls, not a new consent basis, exclusive-access guarantee or new privacy policy. |
| F07 Medium — dashboard is an investment estimate, not net wealth | `PortfolioHero` explanation already says bonds use principal, shares use available closes/cost fallback, pending records contribute, and cash/liabilities/dividends/selling fees are excluded. The main balance uses a currency marker and tappable valuation-basis explanation. **Retained.** It is not a liquidation value, deposit balance or guarantee. Prior product preference intentionally keeps the hero concise; further visible pending/estimate cues need a product review. |
| F08 Medium — quote dates/provenance have limits | `detailQuote` and `core/PortfolioValuation` reject invalid/future source dates, nonpositive closes and non-RWF repricing. Unpriced shares use cash cost, not zero. Details show quote date/cache status; hero basis includes source date. `ShareOrderScreen` still selects closing data differently and lacks a visible quote date. **Open:** reconcile these rules in a separate, characterized domain/data change; define market-calendar freshness before inventing an age cutoff. |
| F09 Medium — returns are neither annualized nor tax gains | `ValuedSharePosition`, `ShareValuesInfoButton` and `PortfolioReport` distinguish remaining-cost unrealized performance from recorded realized activity. Fees are included in acquisition cost; dividends, selling costs and annualization are excluded from the share return metric. **Retained.** Per-lot returns use their own cost and current quote, not a date-adjusted market-return benchmark. Do not describe this as time-weighted performance. |
| F10 Medium — coupons and receipt evidence | `InvestmentDetails` distinguishes gross payment, withholding/net amount, scheduled/recorded amounts and manual/automatic receipt states. `PortfolioHero` / `PortfolioInsights` expose pending estimates, interpolation assumptions and unavailable estimates excluded from totals. `≈` depends on estimated pending coupons. **Retained.** An automatic due-date marker is not proof of bank payment; recorded schedules are not a guarantee. |
| F11 Medium — numeric and currency presentation | `Formatting` retains recorded fractions and existing two-decimal display rounding, including RWF; `money` carries currency, dashboard totals stay currency-separated, and core avoids repricing non-RWF holdings using RSE RWF quotes. **No formula/rounding policy change.** Displayed components may not sum to a separately rounded total; do not reverse-engineer stored values from display strings. Product/legal owners must approve any currency-specific rounding policy separately. `formatNumber` scales into a `Long`; this task does not establish safe rendering for arbitrary unbounded finite API numbers. |
| F12 Medium — other default-zero surfaces remain | `InvestmentActivityTimeline` uses zero defaults for optional receipt/ledger amounts, and `PortfolioReport` has optional-record monetary defaults. **Open:** trace the actual contracts and malformed-record cases before changing those renderers or calculations. The simulator correction does not establish that every missing financial field throughout the app is now protected. |
| F13 Medium — fixed commission is an app estimate | `core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/ShareOrderEstimate.kt` fixes the pending budget at 1.71%; the confirmed-contract flow reviews actual fees. **Retained per existing business behavior.** This audit does not verify that rate against every current broker/exchange tariff or turn it into a legal fee rule. Operator should confirm the assumption and source; any rate/formula change requires a separate financial-domain review. |

### Financial surface inventory

| Surface | What was reviewed / remaining interpretation limits |
|---|---|
| Dashboard balance/allocation/income/target | Currency-separated values, principal vs market/cost basis, pending contributions and estimated coupons, gross/net labels, retained-data failure notices. Cash allocation is recorded investment cost, not portfolio liquidation value or total household cash. |
| Bond details, calendar/yearly coupons | Principal/maturity, coupon rate/frequency, withholding, confirmation confidence, generated dates, net receipts and automatic markers. Date presentation uses explicit day/month/year or ISO labels; no day-count, tax or holiday convention was changed. |
| Share overview and individual purchase/history | Quantity, acquisition cost including fees, remaining position, estimated close-based value, signed unrealized gain/loss and percentage. Realized activity is separate; no benchmark comparison inferred from purchase dates. |
| Pending shares/bonds and contract review | Estimated budget and pending status, broker evidence, actual-price review before recording execution. These records can contribute to totals before execution; they are not settled assets. |
| Markets and charts | Exchange source/date/cache markers, source notes for trade/close histories, sparse known dates, chart-hidden branch. Historical observations are not execution quotes or future forecasts. No chart algorithm or data provenance was changed. |
| Cash ledger and investment activity | Sale/dividend recording and review, receipt evidence, recorded cash exclusions. A record action is not bank settlement. Missing-amount defaults need further contract tracing (F12). |
| Simulator/results | Assumption inputs, configured coupon/tax/reinvestment rates, uncertainty, unavailable output and continuous-chart missing-data handling. Default rates are planning assumptions, not offered products or statutory rates. |
| PDF import, statements, report/export | Supported format explanation, OCR review, duplicate/conflict states, account storage notice, original evidence and structured report attachment, export confidentiality. Existing synthetic parsing tests do not verify every user document. |

No independently proven financial formula defect is corrected here. The differing quote-admission rules, default-zero surfaces and precise schedule/tax assumptions are separate domain-review items, not permission to change investment math in a UI task.

## Privacy, security and trust findings

| ID / severity | Client evidence / action or remaining risk |
|---|---|
| P01 High — operator identity incomplete | `LegalContent.generated.kt` / `docs/legal/content.json` identify “Inzira” and brand-domain mailboxes but not a verified natural/legal controller identity or establishment details. No registration number/address/license is invented. **Professional/operator review required** under the information duties identified below. Contact deliverability was not tested and no email was sent. |
| P02 High — processor and transfer notice cannot be verified from client | Notice names Vercel and Neon and says locations/permissions remain to be confirmed; Android includes Firebase messaging and iOS includes push/live/widget adapters. Notice contains no Firebase/Apple notification data-flow description. **Operator must validate processors, regions, purposes, retention and contracts** before publishing updated legal text. No backend infrastructure inspection/change or authorization claim was made. |
| P03 Medium — feature coverage in notice lags implementation | Legal version is `2026-11-03`; current client includes manual encrypted backup, guest transfer, reminders/live updates and widgets. Explain their data categories, local/server boundaries, public lock-screen exposure and export limits in reviewed notices. “No automatic cloud backup” remains consistent with manual export; it must not be read as “no backup capability.” |
| P04 Medium — guest boundaries are mostly explicit | `GuestPresentation`, guest repository/import, migration and backup UI identify personal vs fictional data, local storage, no automatic account transfer, confirmation before migration, and deletion boundaries. Guest workspace has no separate app biometric/PIN gate; notice says so. Local files chosen from cloud providers may still be downloaded by those providers. Do not promise device-wide or provider-wide deletion. |
| P05 Medium — account deletion confirmation has bounded evidence | `PortfolioModel.deleteAccount` checks the server's `deleted` response before clearing private state and showing success. Settings explain removal of account data and the effect on device sign-in; bundled policy distinguishes active data, backups/logs and external copies. **No server-wide erasure verified.** Owner/environment-managed accounts lack self-service deletion under `canDeleteAccount`; store-policy and rights-request coverage need review. Backend retention/cleanup proof requires its owner. |
| P06 Medium — permission is not universal legal consent | Native notification permission and month-reminder choices are functional preferences. Saved sign-in is optional; imports and migration require explicit actions. No new catch-all legal checkbox, age collection, tracking prompt or marketing opt-in was added. Operator must choose/document lawful grounds for each purpose rather than treating OS permission or terms links as authorization for everything. |
| P07 Medium — screenshot privacy is scoped | `NumberPrivacy` masks amounts in text and semantics; Android `FLAG_SECURE`/capture adapter and iOS secure capture/backdrop cover supported paths. Hide numbers and screenshot protection are separate settings; no changes made to either. Camera photos, deliberate exports and all vendor/OS capture paths cannot be guaranteed safe. Existing capture tests remain relevant; physical Honor/iPhone validation is separate. |
| P08 Medium — exports, clipboard and lock-screen exposure | `PortfolioReportDialog` explicitly warns reports contain readable amounts even with Hide numbers on. `IssueReportScreen` offers reviewed email/copy handoff and asks users to remove private content; report text is in memory, not automatically sent. Clipboard/email/export recipients are outside app deletion. Widgets deliberately publish a minimal projection, while notifications/live status may be visible on a locked phone. Owner should confirm desired metadata exposure; do not silently change global device settings. |
| P09 Medium — sign-in/session UX | Secure sign-in uses native protected credentials and device authentication, not biometric templates delivered to the app. Locked state/session verification/password fallback exist. Cancellation and failure do not imply a successful login. Account/session behavior is covered by existing tests; no real credential or biometric exercise was performed. |
| P10 Low — no advertising analytics identified in inspected client configuration | Android disables FCM auto-init/analytics collection and account code requests messaging registration explicitly; no analytics/ad SDK dependency was identified. iOS privacy manifest declares linked financial/contact/device/user-content categories and no tracking. **Not proof of no operational logs or a store Data Safety submission.** Manifest/SDK/provider behavior needs reconciliation with actual deployed services. |
| P11 High — push setup and sign-in are different boundaries | `MainActivity.installPush` may request a Firebase token after onboarding on a non-guest path before an authenticated model is ready. Account API synchronization is a separate step. OS notification permission, token retrieval and server device registration are distinct. **Review purpose/ground, timing and notice (P02/P06);** do not claim that all SDK traffic waits for sign-in, or impose a new consent gate without that analysis. |

## Regulatory scope — official sources only

All links in this section were accessed **2026-11-15**. Applicability is based on the inspected client and the service's stated Rwanda context, not on an assumed broker/adviser license. Every rule below identifies jurisdiction and provision. Store policies are distribution conditions, not statutes. Draft laws and third-party legal summaries were not treated as current binding law.

### A — Clearly applicable to the stated Rwanda processing context

| Finding / provision / jurisdiction | Bounded consequence |
|---|---|
| A1 — Rwanda DPP Law 058/2021, **Arts. 2–3**; [official DPO text](https://dpo.gov.rw/dpp-law/general-provisions) | Identifiable account/financial records are personal data. Rwanda establishment or processing people located in Rwanda brings the described scope into consideration. Financial information is privacy-sensitive in engineering; it is not automatically every statutory special category. Verify actual operator/user geography. |
| A2 — Rwanda DPP Law, **Arts. 37, 42, 46–47**; [official duties](https://dpo.gov.rw/dpp-law/obligations-of-the-data-controller-and-the-data-processor) | Transparency, purpose/retention, lawful grounds and security apply in that scope. The collection notice needs controller identity, recipients and retention information. P01/P02 remain factual gaps. A code audit does not establish operational compliance. |
| A3 — Rwanda DPP Law, **Arts. 18–24**; [official rights](https://dpo.gov.rw/dpp-law/rights-of-the-data-subject) | Access/correction/portability/objection/erasure mechanisms require appropriate handling under their conditions. A report export or delete button alone is not a complete rights process. Establish support ownership and coverage of all account types. |
| A4 — Rwanda DPP Law, **Arts. 6–8**; [official consent provisions](https://dpo.gov.rw/dpp-law/processing-and-quality-of-personal-data) | If relying on consent, it must be informed and withdrawable. This does not make consent the required basis for every purpose; other grounds are addressed in Art. 46. No universal consent checkbox added. |

“A” assumes the notice's stated Rwanda service context is correct; legal/operator confirmation of the underlying facts remains necessary. These are duties to assess and implement, not declarations that current registration, notices or controls satisfy them.

### B — Potentially applicable, depending on scope or distribution

| Finding / provision / jurisdiction | Trigger and action |
|---|---|
| B1 — Rwanda DPP Law, **Art. 29**; [official registration chapter](https://dpo.gov.rw/dpp-law/registration-of-a-data-controller-and-a-data-processor) | Controller/processor registration must be checked against the actual operator role and processing scope. No certificate was supplied or verified. |
| B2 — Rwanda DPP Law, **Arts. 48–50, 52**; [official transfer/storage chapter](https://dpo.gov.rw/dpp-law/sharing-transferstorage-and-retention-of-personal-data) | Overseas transfer/storage requires assessment of lawful grounds, safeguards/contracts and storage authorization; retention also needs defined purposes. Art. 50 addresses certificate authorization for overseas storage. Do not substitute a checkbox for confirming infrastructure regions and authorizations. |
| B3 — EU GDPR, **Art. 3** and, if engaged, **Arts. 5–7, 12–13, 17, 25, 32, 44–49**; [official EUR-Lex text](https://eur-lex.europa.eu/eli/reg/2016/679/oj/eng) | Check EU establishment, offering services to people in the EU, or relevant monitoring. French UI, a developer's current location or incidental access alone does not settle scope. If engaged, map grounds, notices, rights, security and transfers. No assertion that GDPR applies to every user or that GDPR is satisfied. |
| B4 — Rwanda **Law 011/2026**, **Arts. 3, 22, 26, 74, 76**; [official Gazette, MINICOM](https://www.minicom.gov.rw/fileadmin/user_upload/Minicom/Publications/Laws/Consumer_Protection/OG_n___Special_of_04.03.2026__Ihiganwa_mu_bucuruzi_no_kurengera_umuguzi.pdf) | Assess economic/business activity affecting Rwanda consumers. Misleading service representations are relevant to financial presentation. This March 2026 law repeals Law 36/2012; do not use the old law as the current baseline. Determine whether commercial/distance-contract provisions apply to the actual operating model. |
| B5 — Rwanda financial consumer protection **Law 017/2021**, **Art. 2(4), (12), (13), Arts. 3–4**; [official BNR Gazette](https://www.bnr.rw/documents/Law_relating_to_Financial_Service_Consumer_Protection__2021.pdf) | The definitions include related financial information, while the provider definition involves regulatory authorization. Do not automatically classify a personal tracker as a regulated provider, or exempt it solely because it lacks custody. Counsel/CMA/BNR must assess the actual offering. No new financial service, KYC regime or broker obligation implemented. |
| B6 — Apple distribution, **Guidelines 3.2.1(viii), 5.1.1(i), (v), (ix), 5.1.2**; [official guidelines](https://developer.apple.com/app-store/review/guidelines/) | Privacy disclosures, account deletion and publisher identity need review before submission. Apple's financial-app language is broad; request an appropriate classification for this recordkeeping product rather than declaring a financial-institution requirement either automatically applicable or irrelevant. Store listing/team details not inspected. |
| B7 — Google Play distribution, **Financial Services policy / declaration**; [official policy](https://support.google.com/googleplay/android-developer/answer/9876821?hl=en) | Complete the financial-features declaration accurately if distributing on Play. This does not itself establish regulated brokerage. |
| B8 — Google Play distribution, **User Data / privacy policy / account deletion**; [official policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en) | Reconcile financial/device/document data, purposes and SDK processing with the privacy notice and Data Safety answers. Verify both in-app and external deletion-request routes before distribution. No Play Console submission verified. |

### C — Not applicable to the known implemented capability, unless the offering changes

No in-client lending, crypto/binary-options trading, bank deposits, custody, discretionary asset management, exchange order routing or personalized merits-based advice was identified. Those feature-specific regimes are not imposed as UI requirements here. This is not a statutory exemption or evidence of an operator license. The boundary is evaluated against Rwanda capital-market **Law 01/2011 Arts. 3–6 and Annex II**, read with **Law 45/2018 Arts. 1–2** and CMA's currently published licensing material: [official original law](https://www.cma.rw/fileadmin/user_upload/law_regulating_capital_market_business_in_rwanda.pdf), [official amendment](https://www.cma.rw/fileadmin/user_upload/Law_modifying_Law_governing_CMA.pdf), [CMA licensing](https://www.cma.rw/licensing/application-for-license). If Inzira begins arranging/executing trades, managing assets or giving advice, reassess before shipping; a disclaimer cannot override actual regulated activity.

Advertising tracking was not identified, so a new advertising-tracking consent flow is not justified from current evidence. Keep reassessing if SDKs or processing purposes change; this is not a finding that all device/vendor identifiers are anonymous.

### D — Requires legal or operator confirmation

- Confirm the controller's actual legal/natural identity, establishment, territories and reachable contacts (A1/A2), registrations (B1), current processors/regions and overseas arrangements (B2), lawful grounds (A2/A4), and rights/retention operations (A3/B2).
- Confirm whether the information/planning product or any business activity outside the client falls within CMA/BNR or store financial classifications (B5/B6/C). No license register search would establish exemption from a function-based regime.
- Confirm EU targeting/establishment/monitoring before deciding GDPR scope (B3), and age/audience handling before child-related processing. Rwanda DPP **Art. 9** addresses known under-16 data: [official child/consent provisions](https://dpo.gov.rw/dpp-law/processing-and-quality-of-personal-data). No date-of-birth collection or unsupported blanket age gate was introduced.
- **Electronic communications research limitation:** RURA's [official regulatory index](https://www.rura.rw/sectors/ict/regulatory-instruments/regulations-and-guidelines?cHash=97567d632be6e9e5e9a4d2a61ed0d186&tx_filelist_filelist%5Bcontroller%5D=File&tx_filelist_filelist%5BcurrentPage%5D=3&tx_filelist_filelist%5Bpath%5D=) lists Regulation 001/R/STDCMR/RURA/018 of 06/07/2018 on unsolicited commercial communications. Attempts to fetch the full RURA/Minijust Gazette PDFs timed out. No unverified article, push-specific exclusion or marketing-consent obligation is asserted from that index. Obtain current full text and determine whether any messages are commercial marketing or requested operational reminders before setting messaging requirements. General consumer/financial transparency rules above do not settle channel-specific regulation.

## Legal content proposals — professional review only, not published

The source of bundled legal documents is `docs/legal/content.json`; `LegalContent.generated.kt` explicitly says not to edit it manually. That source may also serve other surfaces, so neither source nor generated legal content was changed in this client-only task.

Review the existing Terms, Privacy Policy, disclaimer, account-deletion guide and credits together. The following are **draft content structures with missing facts**, not final notices or product requirements:

1. **Controller identification:** “The controller is [verified person/entity], established at [verified jurisdiction/contact details]. Privacy requests: [verified channel].” Do not populate from a username, brand, domain ownership assumption or invented company registration.
2. **Purpose/recipient matrix:** For account records/PDFs, authentication, notifications/live status, support handoff, widgets, guest data, transfer and manual backups, record [actual fields], [purpose/ground], [actual recipients/regions], [retention] and [user controls]. Distinguish local extraction from server saving, and device notification permission from the legal ground for processing. Confirm Firebase/Apple and hosting/database roles before naming them as current processors.
3. **Deletion and retention:** Describe active-account deletion separately from provider backups/security logs, local guest data, exports/clipboard and broker-held investments. Insert only confirmed retention periods and deletion assurances supported by server/provider evidence.
4. **Financial basis:** Reuse reviewed factual explanations for close-based/cost fallback valuation, pending records, non-annualized unrealized returns, coupon estimates and scenario assumptions. Do not add promises of suitability, guaranteed payment, regulator endorsement or protection against every capture method.

Operator/counsel approval and translation review are prerequisites for publishing these legal proposals. The implemented UI corrections above express observed functionality and uncertainty; none selects an unresolved legal basis or claims registration/compliance.

## Backend/operational follow-up and remaining risks

No backend/API redesign is included. Its owners must separately establish: account-scoped PDF access and authorization evidence; complete active-data deletion plus backup/log retention; processor inventory/contracts/regions; notification token/reminder lifecycle and lock-screen payload minimization; source quote dates/staleness/provenance; and support procedures for all account types. Client success checks cannot prove these operational properties.

Prioritize P01/P02 and jurisdiction/classification review before wider public distribution. Follow with F08/F12 domain/data characterization, a notice revision based on verified facts, and physical-device checks. Confirm that public institution logos/source labels are attribution rather than endorsements; existing legal text already states no implied affiliation. Verify contact and policy destinations operationally without assuming the old brand domain has stopped working.

## Verification

Technical results, exact commands and Git disposition are recorded below after execution. They cannot establish legal compliance. Existing pending accessibility work is excluded from the isolated patch; privacy semantics and capture behavior are checked through the repository's existing tests. No dedicated legal-compliance scanner or certification was invented.

### Final KMP compilation, financial/privacy tests and static checks

Final isolated tree: `/tmp/inzira-financial-final.dMhTcX`. The earlier exploratory tree `/tmp/inzira-financial-index.q55q0a` predates F05 and the large-value fixture correction; its results are not substituted for final verification. Only this task's staged changes are included in the final tree.

```sh
export JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home
export ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk
fin_export=/tmp/inzira-financial-final.dMhTcX
xcrun simctl boot 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4
xcrun simctl bootstatus 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 -b
./gradlew --project-dir "$fin_export" :core:jvmTest :core:testAndroidHostTest \
  :app:shared:testAndroidHostTest :core:allMetadataJar :app:shared:allMetadataJar \
  :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest \
  :app:androidApp:bundleRelease :app:androidApp:lintRelease \
  :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 \
  :core:compileTestKotlinIosArm64 :app:shared:compileTestKotlinIosArm64 \
  :core:iosSimulatorArm64Test --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
  :app:shared:iosSimulatorArm64Test \
  -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

**BUILD SUCCESSFUL**, final reviewed sources, 2m 22s (`/tmp/inzira-financial-reviewed-verification.log`). XML counts: core JVM **145**, core Android-host **145**, shared Android-host **535**, core iOS simulator **144**, shared iOS simulator **561**; zero failures/errors/skips. Includes existing valuation, decimal money, coupons, financial presentation, legal content and number-privacy tests plus two new amount/chart presentation tests. No financial-domain source or formula was edited. Android release lint: **94 warnings, zero errors**, not warning-free.

New shared tests distinguish null/missing/malformed/non-finite data from legitimate zero, preserve negative/fractional amounts, existing rounding and English/French currencies, and reject an incomplete projection chart without manufacturing a zero or joining over a missing month. Diff review subsequently caught one remaining default-zero path in the dynamically keyed summary rows. That loop was corrected, and the UI test now requires exactly one displayed zero from its one explicit zero result. The full compilation/host/native command above was rerun successfully on that reviewed source; the strengthened UI result is recorded below.

Native environment: iPhone 16 Pro simulator, iOS 18.6, Xcode 26.3. These are native shared-code tests, not iPhone UI, physical biometric or store-submission checks.

```sh
python3 "$fin_export/scripts/check-kmp-packages.py"
python3 "$fin_export/scripts/sync-design-colors.py" --check
python3 -m unittest discover -s "$fin_export/scripts/tests" -p 'test_*.py'
git diff --cached --check
```

Passed: **417** source/test files satisfy the configured package/source-set import/module checks; generated/authored colors consistent; **19** script tests. Final patch whitespace review is part of the Git gate. There is no configured dedicated accessibility scanner, legal-compliance scanner, detekt/ktlint task or screenshot-golden task to claim. Existing privacy semantics, capture tests and accessible-node UI assertions are the available relevant checks; no TalkBack/VoiceOver certification is claimed.

Working-tree integration (including unrelated pending extractions/UI):

```sh
./gradlew :app:shared:testAndroidHostTest --tests '*FinancialAmountPresentationTest' \
  --tests '*FinancialPresentationTest' --tests '*NumberPrivacyTest' --tests '*LegalContentTest' \
  :app:androidApp:assembleDebug --console=plain
```

**BUILD SUCCESSFUL**, final reviewed working tree, 13s, **12** focused test executions (`/tmp/inzira-financial-reviewed-working.log`). This is not a new full verification of every unrelated pending change.

### Android UI and visual verification

API 36.1 ARM64 emulator `Sequo_References_API_36_1`, started with the Android CLI. All fixtures use synthetic localhost/mock accounts and invented records, not production credentials or user PDFs. `adb` is `$ANDROID_HOME/platform-tools/adb`.

```sh
adb -s emulator-5554 install -r "$fin_export/app/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
adb -s emulator-5554 install -r "$fin_export/app/androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk"
adb -s emulator-5554 shell am instrument -w -r \
  -e class dev.orestegabo.portfolio.investments.FinancialTransparencyUiTest,dev.orestegabo.portfolio.investments.InvestmentDetailLayoutTest,dev.orestegabo.portfolio.investments.ShareOrderScreenTest \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -r \
  -e class dev.orestegabo.portfolio.investments.InvestmentDetailLayoutTest \
  -e language fr -e quoteMode missing -e profile large \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -r \
  -e class dev.orestegabo.portfolio.investments.InvestmentDetailLayoutTest \
  -e language fr -e quoteMode stale \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

Final results:

- **OK (3 tests)**, 33.944s (`/tmp/inzira-financial-final-focused.log`): incomplete projection shows **Unavailable**, retains an actual `RWF 0`, explains missing chart data and identifies illustrative results; detail screens render in both themes with estimated/unrealized labels; successful-but-stale order response shows cached feedback while existing quantity/price/commission/total assertions still pass.
- **OK (1 test)**, 10.25s (`/tmp/inzira-financial-final-french-missing.log`): both themes, French, large financial amounts and missing quotes use purchase cost. An initial optional-matrix failure revealed the old fixture incorrectly expected market value in this combination. The corrected expected value is actual recorded cash cost; no assertion was removed or tolerance relaxed.
- **OK (1 test)**, 9.099s (`/tmp/inzira-financial-final-french-stale.log`): French cached-price presentation in both themes.

Representative synthetic share light/dark captures were extracted and visually inspected at `/tmp/inzira-financial-visual/share-light.png` and `share-dark.png`. The added labels and currency are readable without overlapping values; the gain remains signed and separate from cost. These are not golden baselines, an arbitrary-screen-size audit or a 200% text-scaling test. Physical Honor/iPhone, VoiceOver/TalkBack, every import branch, true offline/push delivery and external file/email/clipboard behavior were not manually re-tested in this phase.

The unfiltered device regression command is:

```sh
./gradlew --project-dir "$fin_export" :app:shared:connectedAndroidDeviceTest \
  :app:androidApp:connectedDebugAndroidTest --console=plain
```

The preliminary tree's full run failed in 8m 40s (`/tmp/inzira-financial-connected.log`): `PdfSharingTest.pdfTargetsReceiveColdAndWarmIntentsAndExportsGrantOnlyReadAccess` crashed on the emulator RenderThread with SIGSEGV in `RefBase::incStrongRequireStrong` / EGL surface teardown / `EglManager::destroySurface`. This matches the earlier intermittent full-process baseline recorded in the UX audit. No capture protection, renderer, security flag, test exclusion or tolerance was changed to bypass it. The later unfiltered attempt and resulting Git decision are recorded below.


### Final results, blocker and Git disposition

The latest unfiltered device attempt (`/tmp/inzira-financial-final-connected.log`, **BUILD FAILED**, 5m 56s) reproduced the native PDF-sharing crash. XML reports show **529 shared Android device tests passed**, zero failures/errors/skips. The interrupted app report contains **42 test entries, one native-process failure, four opt-in skips**; it is not a successful 45-test app run and does not validate cases after the crash. The failed case is `PdfSharingTest.pdfTargetsReceiveColdAndWarmIntentsAndExportsGrantOnlyReadAccess`. Crash evidence is in `/tmp/inzira-financial-penultimate-crash.txt`. The failure signature matches the baseline; that observation does not prove its root cause or justify a production renderer workaround.

Four existing opt-in methods were skipped: real Firebase delivery, supplied-user-PDF regression, process/reboot guest persistence and the startup measurement fixture. No real delivery, real-document compatibility, reboot behavior or performance result is claimed from those skips.

That full-device attempt preceded the final summary-loop review correction. After that correction, the full KMP compilation, host/native tests, release build and lint were rerun successfully as recorded above. The strengthened `FinancialTransparencyUiTest` was run with the same `adb am instrument` command restricted to its class: **OK (1 test)**, 5.141s (`/tmp/inzira-financial-reviewed-ui.log`), confirming absent summary fields are not fabricated zeros. The unchanged share/order/theme corrections had passed the three-test batch and French variants. **No new successful unfiltered app run is claimed after the last edit.** The repeated unresolved native crash closes the requested commit gate; repeated runs were not used to conceal it.

**No commit or push:** technical verification is incomplete because of the interrupted full app suite. The requested message `fix(kmp-ui): improve financial transparency and privacy ux` was not used. The reviewed patch and this audit remain in the working tree; unrelated pending work is preserved. Legal proposals remain unpublished, and operator/regulatory unknowns were not silently turned into product requirements. Resolve the full-process native lifecycle failure (including comparison with the baseline emulator/driver and physical-device results), then rerun the complete gate before committing.


Final diff review confirmed **11 code/test files** match the reviewed isolated tree byte-for-byte; this report is the twelfth patch file. The patch contains no backend, website, core formula, API, serialized/persisted model, legal source/generated content, dependency or privacy-default change. Existing unrelated edits are excluded. Final package/color/script checks and patch whitespace checks passed. The temporary review index is cleared before handoff; the scoped patch is retained at `/tmp/inzira-financial-reviewed.patch` for inspection, with no commit or push.
