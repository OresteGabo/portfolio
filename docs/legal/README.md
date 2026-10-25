# Gabo Portfolio legal documents

English and French documents are available offline from Settings and before sign-in/unlock. Nothing in this change publishes a website or records acceptance of terms. These are implementation-matched drafts, not a legal opinion or certification of compliance.

## Editing

Edit `content.json`, then run `python3 scripts/generate_legal.py`. This generates the bilingual Kotlin content and individual Markdown documents from one source. Original third-party notices are bundled in shared Compose resources and shown from About & Legal Notice → Open-source notices, grouped by purpose. The reader removes source-comment wrappers only; copyright, conditions and disclaimers remain, and bundled originals are untouched.

The generated `*.en.md` and `*.fr.md` exports stay local and are ignored by Git. Commit `content.json`, this README, `scripts/generate_legal.py`, the generated `LegalContent.generated.kt`, the legal UI/tests and the bundled licence notices. The Kotlin output must remain versioned because Gradle compiles it directly without running the Python generator. Fresh checkouts can build the app without Python; run the generator when changing legal content or recreating the Markdown exports.

## Confirm before relying on these documents

- Identify the responsible natural person or registered entity behind the supplied service name **Gabo Portfolio**. Only the service name, Rwanda, contact email and invited-user scope have been confirmed.
- Check controller/processor registration with Rwanda's Data Protection and Privacy Office. Verify actual Vercel/Neon deployment and backup locations, processor agreements, subprocessors and any required authorization for storage or transfers outside Rwanda. No country-local storage is promised.
- Establish exact retention and erasure schedules for security logs, provider backups, abandoned uploads and legally required records. The policy explicitly discloses that these periods are not yet specified; this needs resolution rather than an invented duration.
- Review lawful bases for each purpose. In particular, assess the cross-account ticker/date/unit-price feed and whether separate consent or an effective opt-out is required. The existing feature has no in-app opt-out. Disclosure alone is not proof of valid consent or a substitute for any required lawful basis. Restrict secondary use until an appropriate basis and necessary controls are established.
- Confirm requests for access, correction, objection, restriction, portability and erasure can be handled within applicable legal deadlines. Owner and environment-managed accounts currently need manual handling. Check production storage deletion, including uploaded PDFs, before inviting more users.
- Verify institution logo/photo/market-data permissions and the complete transitive dependency licence inventory; bundled engine notices and credit text alone do not establish all rights.
- Have a qualified Rwandan adviser review terms, privacy notice, responsible-party identity and liability language. Set a policy-change notification process. No acceptance/version record was added; do not represent one as existing.
- If App Store or Google Play distribution is planned, review current distribution rules, supply required public privacy/deletion URLs and accurate store privacy disclosures. Invitation-only access does not by itself establish exemption. These offline pages do not create those URLs.

## Primary references reviewed (25 October 2026)

- [Rwanda DPP law and rights](https://dpo.gov.rw/dpp-law/rights-of-the-data-subject)
- [Controller and processor registration guide](https://dpo.gov.rw/fileadmin/DPO/ComplianceTools/registration-guide-for-data-controller-and-processor.pdf)
- [Apple review guidelines, privacy](https://developer.apple.com/app-store/review/guidelines/#privacy)
- [Apple account deletion](https://developer.apple.com/support/offering-account-deletion-in-your-app/)
- [Google Play User Data](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
- [Google account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en)
