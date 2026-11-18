# Mobile independence from the website

The Android/iPhone application owns its Kotlin UI, document rules, simulator and bundled Learn content. Deleting the separate website checkout does not remove any mobile source, build dependency or processing engine.

- Shared Kotlin `InvestmentDocumentRules` / `InvestmentProjection` implement processing. No JavaScript runtime is used by the mobile application.
- Android uses PDFBox Android and a bundled on-device ML Kit Latin OCR model; iPhone uses system PDFKit/Vision. Normal builds use standard Gradle/Android/Apple dependencies, without Node, React, Next.js or website source.
- Learn content is editable mobile `education.json`.
- Original JavaScript/TypeScript sources and libraries remain outside all mobile source sets in `tests/reference-engine` solely to retain independent regression references. Node tooling is optional for those developer tests.
- Signed-in online features still need the separate Spring backend/database, reached through the direct native API origin. They do not depend on website hosting or Vercel routing.
- Optional public-site links can stop opening if their destination is removed. Opt-in website password association also requires its domain; neither is required for native sign-in or investment processing.

No website, backend, database, production account or cloud configuration is deleted or changed by this migration. See [Kotlin migration](KOTLIN_ENGINE_MIGRATION.md) for detailed boundaries and verification.
