# Rwanda data-protection review questions

Reviewed against official public sources on **30 October 2026**. This is an engineering checklist for legal verification, not a legal opinion, registration certificate or compliance claim. The app handles personal financial documents; counsel must confirm the applicable legal classification, establishment, data subjects and processing activities.

## Official requirements relevant to the design

Rwanda's Law No 058/2021 addresses controller/processor registration. The supervisory authority's FAQ says registration is not avoided merely by an organization's small size. Identify the actual controller/legal person, contact details and any processor roles; “Struct Capital” and “built by GABO” branding do not settle legal identity. Verify registration status and whether an overseas operator needs a Rwanda representative. [DPO FAQ](https://dpo.gov.rw/faqs), [controller/processor obligations](https://dpo.gov.rw/dpp-law/obligations-of-the-data-controller-and-the-data-processor).

Arts 48–50 address overseas transfers, safeguards/contracts and storage. Art 50 requires a certificate authorizing overseas storage; do not assume selecting an overseas cloud region or obtaining a generic consent checkbox settles the requirements. Have counsel verify the appropriate transfer route, authorization/certificate scope and written processor/recipient terms for the actual deployment. [Official sharing, transfer and storage provisions](https://dpo.gov.rw/dpp-law/sharing-transferstorage-and-retention-of-personal-data).

Arts 43–44 distinguish a **48-hour notification** obligation after awareness from the **72-hour breach report**. The processor-to-controller obligation also needs to be reflected in agreements. A generic “notify within 72 hours” incident plan is insufficient. Confirm the applicable awareness trigger, reporting route and data-subject communications with counsel/NCSA. [Official controller/processor obligations](https://dpo.gov.rw/dpp-law/obligations-of-the-data-controller-and-the-data-processor).

Art 52 covers purpose-based retention and exceptions. Specify actual record, PDF, staging, local metadata, security log and backup periods rather than borrowing an unverified banking retention period. [Official storage/retention FAQ](https://dpo.gov.rw/faqs). Technical deletion from the active database does not establish deletion from provider backups or files deliberately exported to others.

## Questions to resolve before broader use

- Who is the controller: an individual, registered business or another entity? What are its establishment, address and rights-request contact? Does operating from abroad trigger a representative requirement? What registrations/certificates exist, with what countries/activities and expiry dates?
- What lawful purposes/bases apply to storing user-provided statements/contracts, extracting investment facts, account security and notifications? Do any identifiers or document contents fall into statutory sensitive categories? OS biometric authentication does not mean the app collects biometric templates; confirm disclosures match the actual API use.
- Where do the web proxy, Spring service, PostgreSQL, backups, provider logs, Firebase/APNs and support systems actually process/store data? Vercel, Cloud Run and Neon references in configuration are not verified region or contract evidence. Identify indirect transfers, subprocessors and actual safeguards.
- Are privacy notices complete and accurate for identity, purposes, recipients, rights, retention, exports, automated processing and transfers? The new notice reflects account-scoped contract charts and owner-published public reports; publish it only with the matching backend. Do not claim anonymity, bank verification or legal compliance from these changes.
- How will access, correction, restriction/objection and applicable erasure requests be verified and handled? Define response handling, deletion-on-restore, permitted exceptions and the distinct path for environment-managed owner/friend accounts. Shared public reports must not be a channel for retaining misclassified personal evidence.
- Do the source findings affect any previously deployed version? Assess real exposure before deciding whether breach notifications/reporting are required. Local synthetic reproduction is not evidence that production was accessed. Preserve relevant evidence and get advice promptly if facts indicate an incident.
- What retention, processor contracts, processing records, DPO/contact role, security documentation and data-protection impact assessment obligations apply to this actual service? Diagnostic timings and technical financial audit rows are not automatically statutory processing records.

## Current limits and deferred programs

No registration, provider agreement, transfer/storage authorization, retention approval, live restore evidence or legal sign-off was reviewed. No regulator filing was made. See OPERATIONS.md for immediate low-cost operational verification.

Institutional compliance, enterprise audit evidence, advanced tenant security, broker-specific controls, external penetration-testing programs and certifications remain documented future work. They require a defined business/service scope and legal assessment; their deferral does not excuse current account isolation, secure handling or lawful processing.
