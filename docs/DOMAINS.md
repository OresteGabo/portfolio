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
- `DEFAULT_ORIGIN` remains the working `https://bonds.orestegabo.dev` transport origin until cutover is verified. Existing saved origins, sessions, invitations and password setup continue to use their matching backend. The native HTTP client deliberately does not follow redirects.
- Branded contact addresses and their roles are defined in `docs/legal/content.json` and documented in `docs/EMAIL.md`. They replace the personal contact in app copy. Buying a domain does not provision the mailboxes; configure and verify delivery before publishing the contact update.

## Vercel setup

1. Open the Vercel project that actually serves the investment app, then **Settings → Domains**. The existing local web checkout links to `gabo-cv`; verify the intended project before assigning domains. The personal website and investment website share that project today.
2. **Fix host routing before assigning the primary domain.** The web checkout's `proxy.ts` currently recognizes only `bonds.orestegabo.dev` and `bonds.localhost` as investment hosts. Simply adding `structcapital.net` to that project would render the personal website at the root. Add the exact new primary hostname to the investment-host routing and preserve the existing investment rewrites, API exceptions, static files, security headers and `/.well-known/apple-app-site-association` exception. Do not treat every unrecognized hostname as an investment host. The suggested patch in `docs/domains/web-proxy.patch` is prepared against the local web checkout; it is not applied or deployed.
3. Deploy and verify host routing, metadata/canonical URLs, manifest/start URL, robots/sitemap, and the relevant authentication origin allowlist. Do not reuse or move another project's domains without confirming the intended project.
4. Add `structcapital.net` as a **Production** domain, serving directly with no redirect to `www`. Complete the Vercel-recommended DNS records shown for that project and wait for a valid TLS certificate.
5. Add the other five exact hosts in the table. Use **Edit → Redirect to → structcapital.net**, permanent **308**, for each. Ensure valid TLS on every source hostname: `.dev` requires HTTPS even to deliver a redirect.
6. Verify root, `/portfolio`, `/create-password`, `/join`, legal/public routes and representative `/api/bonds/` responses on the primary host. Verify each secondary host returns the expected `Location` with the path and query intact. Do not redirect API clients through the defensive domains.

Apex `.net` is an intentional branding choice. Vercel recommends `www` for routing flexibility but also supports apex domains. There should be one canonical public origin.

## Native API cutover after the host is ready

1. Serve the existing API directly on `https://structcapital.net` using the same production data; configure the backend's exact allowed Origin and trusted-host rules. Confirm JSON responses, authentication, uploads and document downloads before changing native defaults.
2. Change `DEFAULT_ORIGIN` to `OFFICIAL_WEBSITE_ORIGIN`. Decide how to migrate saved legacy origins explicitly; do not silently copy cookies, sessions or credentials between hosts. The app's secure vault is origin-bound, so a new manual sign-in may be needed. Keep the old backend available for already installed app versions.
3. Update the exact `passwordSharingDomain` allowlist and `PasswordSharingTest`, plus the `webcredentials:` entries in both iOS entitlement files. Publish the matching Apple association JSON directly on the new host before enabling password sharing. A redirect to the old association URL is insufficient.
4. Change invitation generation and password-setup links to the new origin as part of the verified API migration. Validate the complete flow without real invitation secrets in logs or terminal history.
5. Build Android, compile the shared iOS target, and run core/shared tests. Verify a clean install and an existing-user upgrade on a device. Only then publish the native update that switches transport defaults.

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
