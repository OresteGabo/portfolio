# Struct Capital email contacts

Use `@structcapital.net`, matching the official website. The owner has not registered `structcapital.com`. Creator attribution: Built by GABO; app brand: Struct Capital.

| Address | Use in this project |
| --- | --- |
| hello@structcapital.net | General contact in the legal hub and credits |
| support@structcapital.net | App support, Terms of Service, Settings contact action and legal-page contact actions |
| privacy@structcapital.net | Privacy policy, data rights, access, corrections, exports and account-deletion assistance |
| security@structcapital.net | Suspected unauthorized access, phishing and vulnerability reports |
| info@structcapital.net | Business enquiries in the legal hub and credits |
| oreste@structcapital.net | Reserved professional correspondence; not a public app-support or privacy contact |

## Source and generation

`docs/legal/content.json` owns all six addresses under `contacts`. Legal source text refers to them with `{{support}}`, `{{privacy}}` and the other role keys. Run `python3 scripts/generate_legal.py` after editing it. This generates the shared `BrandContacts` constants, English/French offline legal content and local Markdown exports. Do not edit `LegalContent.generated.kt` manually.

The policy version is 27 October 2026 for the branding and contact update. Existing retention, legal-rights and service descriptions remain as previously documented; this change does not establish mailbox availability or legal-entity registration.

Settings and legal contact rows open an email draft in the platform mail app. If no email app can handle it, the app shares the address so the user can copy it. No message is sent automatically. General browser links retain their HTTP/HTTPS-only policy.

## Delivery status and publication

**Source configured; mailbox provisioning and delivery are not verified.** No email provider, mailbox or forwarding service was created by these repository changes. Vercel domain ownership alone does not provide email hosting. Do not publish these addresses as working support/privacy channels until receiving and replying have been tested.

1. Choose a mail provider for `structcapital.net`. These role addresses can be aliases delivered to one monitored mailbox if the provider supports that arrangement.
2. Add the exact MX and sender-authentication records supplied by the chosen provider in Vercel DNS. Preserve website DNS records and existing unrelated records. Configure SPF, DKIM and DMARC for the actual sending service; do not invent values or add conflicting SPF records.
3. Verify receiving at each advertised alias and replying from the appropriate branded address. Test both English/French contact rows on Android and iOS with a mail app installed, and the fallback when one is absent. Test emails are a separate action requiring authorization; none were sent here.
4. Monitor support, privacy and security requests consistently before releasing the revised contact copy. Forwarding alone may not permit replies using the branded sender.
5. Keep personal mailbox destinations out of public documentation. Do not ask users to email passwords, invitation tokens or complete financial documents.

Website redirects for `.dev` and `.org` do not redirect email. No contact address on those domains is advertised in this project. Automated sending/notification providers and their sender identities are a separate configuration; this update does not change credentials or claim the app sends from these aliases.
