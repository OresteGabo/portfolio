# Struct Capital domains

Official public origin: **https://structcapital.net**. App brand: **Struct Capital**. Creator attribution: **Built by GABO**. All three domains were purchased through Vercel. Their registrations and auto-renewal through 27 October 2027 were visible in the owner's Vercel dashboard on 27 October 2026; project assignment and serving the app have not been completed by these source changes.

| Host | Intended behavior |
| --- | --- |
| structcapital.net | Serve the Struct Capital app directly over HTTPS |
| www.structcapital.net | Permanent redirect to https://structcapital.net |
| structcapital.dev | Permanent redirect to https://structcapital.net |
| www.structcapital.dev | Permanent redirect to https://structcapital.net |
| structcapital.org | Permanent redirect to https://structcapital.net |
| www.structcapital.org | Permanent redirect to https://structcapital.net |

Use a 308 permanent redirect and retain path and query string. For example, `https://structcapital.org/join?ref=friend` should resolve to `https://structcapital.net/join?ref=friend`. Test invitation URLs with synthetic tokens only. URL fragments are not transmitted to the server; verify that browser redirects preserve the existing `#invite=` invitation flow. Do not serve separate copies of the app on the defensive domains.

## Changes in this repository

- `OFFICIAL_WEBSITE_ORIGIN` is the public `.net` origin and is used by the new Settings website link.
- The English/French offline legal credits list the new public website.
- `DEFAULT_ORIGIN` now targets the Spring backend service directly; see the 14 November direct-backend migration below. The legacy website gateway remains available for older binaries. The native HTTP client deliberately does not follow redirects.
- Branded contact addresses and their roles are defined in `docs/legal/content.json` and documented in `docs/EMAIL.md`. They replace the personal contact in app copy. Buying a domain does not provision the mailboxes; configure and verify delivery before publishing the contact update.

## Vercel setup

1. Open the Vercel project that actually serves the investment app, then **Settings → Domains**. The existing local web checkout links to `gabo-cv`; verify the intended project before assigning domains. The personal website and investment website share that project today.
2. **Fix host routing before assigning the primary domain.** The web checkout's `proxy.ts` currently recognizes only `bonds.orestegabo.dev` and `bonds.localhost` as investment hosts. Simply adding `structcapital.net` to that project would render the personal website at the root. Add the exact new primary hostname to the investment-host routing and preserve the existing investment rewrites, API exceptions, static files, security headers and `/.well-known/apple-app-site-association` exception. Do not treat every unrecognized hostname as an investment host. The suggested patch in `docs/domains/web-proxy.patch` is prepared against the local web checkout; it is not applied or deployed.
3. Deploy and verify host routing, metadata/canonical URLs, manifest/start URL, robots/sitemap, and the relevant authentication origin allowlist. Do not reuse or move another project's domains without confirming the intended project.
4. Add `structcapital.net` as a **Production** domain, serving directly with no redirect to `www`. Complete the Vercel-recommended DNS records shown for that project and wait for a valid TLS certificate.
5. Add the other five exact hosts in the table. Use **Edit → Redirect to → structcapital.net**, permanent **308**, for each. Ensure valid TLS on every source hostname: `.dev` requires HTTPS even to deliver a redirect.
6. Verify root, `/portfolio`, `/create-password`, `/join`, legal/public routes and representative `/api/bonds/` responses on the primary host. Verify each secondary host returns the expected `Location` with the path and query intact. Do not redirect API clients through the defensive domains.

Apex `.net` is an intentional branding choice. Vercel recommends `www` for routing flexibility but also supports apex domains. There should be one canonical public origin.

## Native API transport policy

Native apps use the direct Spring/Cloud Run origin defined in `core/.../BackendOrigin.kt`. Do not point `DEFAULT_ORIGIN` at the marketing website, the investment browser frontend, or a Vercel redirect/proxy. Website-domain setup is separate from the native transport.

When changing the backend service itself, verify its TLS, exact allowed Origin, authentication, private API routes, uploads and downloads first. Origin-bound cookies and saved passwords must not be silently copied to another host. Preserve custom development configurations and guest privacy. Keep legacy gateways working for binaries that still contain their old address until those apps update.

Optional Apple website-password association is independent of API transport. It must never be required for manual native sign-in or account features.

## Verification commands

These commands contain no credentials:

```sh
curl --head https://structcapital.net/
curl --head 'https://structcapital.dev/portfolio?domain_check=1'
curl --head 'https://structcapital.org/portfolio?domain_check=1'
curl --head 'https://www.structcapital.net/portfolio?domain_check=1'
curl --head 'https://www.structcapital.dev/portfolio?domain_check=1'
curl --head 'https://www.structcapital.org/portfolio?domain_check=1'
curl -i https://structcapital.net/.well-known/apple-app-site-association
```

Defensive registrations reduce impersonation opportunities for these exact names; they do not prevent lookalike domains elsewhere. Keep registration auto-renewal and Vercel account MFA enabled. If a domain will not send email, separately configure a non-sending SPF policy and rejecting DMARC after checking that no existing email service relies on it.

Sources: [Vercel domain setup](https://vercel.com/docs/domains/set-up-custom-domain), [Vercel domain redirects](https://vercel.com/docs/domains/working-with-domains/deploying-and-redirecting), [Google Registry .dev HTTPS requirement](https://www.registry.google/domains/dev/).


## Mobile API outage and recovery — 14 November 2026

A separate website migration moved investment browser pages to `https://invest.orestegabo.dev` and redirected the old `bonds.orestegabo.dev` host, including `/api/bonds/*`. The native client correctly refuses redirects, so the resulting HTTP 308 prevented installed apps from reaching the healthy Spring service. This was a gateway routing failure, not lost account or investment data.

Recovery retains the native origin and origin-bound sessions: the investment deployment excludes the exact `/api/bonds` namespace from legacy-host redirects and forwards it directly to the existing Spring backend. The legacy hostname is now attached to the separate Vercel **gabo-portfolio** project (`prj_OPxGdH1uAr6pzVIGT3v7WyMeaXwy`), alongside `invest.orestegabo.dev`, rather than the personal **gabo-cv** project. Personal-site deployments no longer own the mobile API hostname. Existing browser paths still redirect with their queries intact, and Apple association JSON stays direct.

Verified production deployment: `dpl_3vjUbwejJn8JzMTa2TVVQ1CK19PY` (`gabo-portfolio-e8k03qi5n-orestegabos-projects.vercel.app`). Plain public HTTPS checks with redirects disabled confirmed legacy session HTTP 200 JSON, private overview GET and document POST HTTP 401 JSON, browser `/stocks?period=all` HTTP 308 to the matching invest URL, and Apple association HTTP 200 JSON. The new investment session endpoint and personal homepage remain HTTP 200. Vercel confirms the legacy hostname is attached, verified, and configured correctly; no DNS records or backend settings were changed.

**Compatibility requirement:** keep `/api/bonds` and its subpaths directly accessible at the installed clients’ origin. Never replace this route with a domain-level redirect. Future API-origin migrations require explicit native-client and session-migration testing; browser redirect checks alone are insufficient. `structcapital.net` currently serves a separate marketing website and is not a verified native API origin.


## Direct native backend connection — 14 November 2026

The new native build removes Vercel and website hosting from API transport entirely:

`Android/iPhone → https://gabo-portfolio-api-639858675564.europe-west1.run.app → Spring/PostgreSQL`

`BackendOrigin.kt` defines the direct default. Only exact known legacy production gateway settings (`https://bonds.orestegabo.dev` and `https://invest.orestegabo.dev`) migrate automatically; custom servers and explicit development build overrides stay explicit. Legacy-default accounts without a saved server setting are detected using vault presence metadata only. The migration selects manual account sign-in and does not read, copy, or delete old-host credentials or cookies. After a fresh sign-in, optional secure sign-in saves a new record for the backend origin. Guest mode remains offline and never opens the account vault during migration.

The backend uses the existing service, database, authentication and allowed-origin settings. Public direct HTTPS verification confirmed session GET 200 JSON, unauthenticated document POST 401, and a login CORS preflight accepting the exact direct origin. No DNS, backend settings or account data are changed by this native update.

The repaired legacy host is compatibility infrastructure for old installed binaries only; keep it available until those clients update. The public marketing website, browser investment frontend and optional OS website-password association are separate features, not native API transport dependencies. The app can sign in, load investments, import PDFs and manage reminders with no website deployed. Do not switch the native transport back to a frontend hostname during future branding/domain changes.
