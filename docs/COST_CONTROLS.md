# Struct Capital cost controls — 31 October 2026

The user selected a **€35 combined monthly target across providers**, not €35 per provider. There is no shared real-time billing cap spanning Google, Neon, Vercel, GitHub and domain registration. The controls below reduce exposure but **do not guarantee a €35 total invoice**.

## Verified controls and actions

| Component | Verified state / action | What is actually enforced |
| --- | --- | --- |
| Google Cloud Run | Existing service-specific monthly cap reduced from €35 to **€25**; API read-back reports `CONFIGURED`, EUR, calendar month, gross estimated costs excluding credits. Current-spend alerts remain at 50%, 80%, 100% (€12.50, €20, €25). | Spend-cap enforcement can pause new Cloud Run usage. Delayed enforcement, in-flight usage and continuing fixed costs can exceed the threshold. |
| Cloud Run scaling | Added service-level max=1; min=1 remains enabled. Ready revision and full container template compared unchanged. Existing revision max=1 also remains. | Restricts ordinary scaling; not an absolute spending guarantee or immunity to temporary instance overlap/restarts. No mobile reinstallation required. |
| Whole Google project | Created and read back **€35/month alerts-only** budget for all services in `gabo-portfolio-api`, gross costs, 50%/80%/100% thresholds (€17.50, €28, €35), project recipients enabled. | Notification only. This is deliberately not called an enforcement cap. No supported universal cap for all these Google services was configured. |
| Neon database | Linked organization reports `plan=free`. Running backend datasource host was compared with the audited project's endpoint and matches. | Existing Free-plan allowances, not a new paid €35 budget. No upgrade, database mutation or compute resizing. Paid-plan spending notifications are not needed to keep this Free deployment at $0; changing plans requires a new cost review. |
| Vercel | The team serving both `gabo-cv` (mobile API proxy) and `struct-capital` reports active `hobby`; billing `invoiceItems=null`. | Existing Hobby hosting quotas, not a new paid spend cap. No upgrade or team-wide pause affecting unrelated sites. |
| GitHub Actions | Both mobile/backend repositories verified public. Existing workflows use standard Ubuntu/macOS hosted runners, not paid larger runners; mobile artifact retention is seven days. | Standard public-repository runner time is free. Account-level paid artifact/cache/package budgets were not changed or independently verified; free runner time is not proof all GitHub storage is free. |

The €25 Cloud Run cap leaves **€10 planning headroom**, not an enforced allocation, for other charges under the €35 combined target. No other provider received a separate €35 allowance. Neon and Vercel limits were already present; they were inspected and retained rather than represented as new configuration changes. Existing CLI login refresh restored provider metadata access; no new API keys, broader permissions, accounts or paid services were created.

## Costs not covered by the enforcement cap

- Google image storage, logs, network, Secret Manager and other non-Cloud Run charges remain outside the service cap and inside the Google project alert. Artifact Registry inspection found one Docker repository, approximately 1,134.727 MB; no image or rollback revision was deleted. Enabled API inventory is not a usage/cost report; no claim all enabled services incur charges.
- Purchased domain registration/renewal fees are separate from Vercel hosting quotas. `structcapital.net` reports renewal enabled. No renewal setting was disabled: protecting the domains remains the user's expressed intent. Annual renewal months can exceed an otherwise small monthly target; actual renewal prices/taxes must be budgeted separately.
- Other subscriptions, mail services, app-store memberships, taxes and unrelated projects are not silently included or changed. Vercel Hobby does not by itself prove every registration/Marketplace purchase is free. No current Marketplace charge was inferred from a deployment file.
- Neon compute/storage/transfer allowance exhaustion and Vercel free hosting limits can interrupt availability rather than create paid overages. Don't upgrade plans automatically to bypass those limits. Free quota protection is different from operational reliability.

## Operations

Review actual invoices/usage across providers regularly, with an early review after the first tester cohort. This pass did not total an invoice or forecast actual spend. Keep automated emails enabled and verify receipt; no manual test email was sent. Google/Neon/Vercel clocks and monthly cycles need not align, and USD-priced fees vary in EUR.

Before changing plans, adding paid compute/storage, running larger CI runners or enabling add-ons, recalculate the combined allowance and adjust individual controls. Do not claim a new provider's free tier or an alerts-only setting enforces the shared target. If an exact no-overage total is mandatory, the current multi-provider pay-as-you-go arrangement cannot promise it; review genuinely fixed-price/prepaid alternatives and annual renewals before migrating anything.

Sources checked 31 October 2026: [Google spend caps](https://docs.cloud.google.com/billing/docs/how-to/budgets-spend-caps), [Google alerts-only budgets](https://docs.cloud.google.com/billing/docs/how-to/budgets), [maximum instances](https://docs.cloud.google.com/run/docs/configuring/max-instances), [Neon plans](https://neon.com/docs/introduction/plans), [Vercel plans](https://vercel.com/docs/plans), [Vercel spend-management scope](https://vercel.com/docs/spend-management), [GitHub Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions).

No customer investment records, SQL data, PDFs, passwords or provider tokens are included in this document. Application code, authentication, database schema and image deployment were unchanged. Both repositories' documentation whitespace checks pass; financial/runtime test suites were not rerun for billing settings/documentation changes.
