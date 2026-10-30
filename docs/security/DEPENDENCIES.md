# Dependency security snapshot — 30 October 2026

This was a live public OSV batch review of exact selected module versions, not a claim that all code or the deployed service is vulnerability-free. Only public package names/versions were sent; no source, credentials, real documents or financial data. Temporary Gradle init tasks read `resolutionResult.allComponents` from Android `debugRuntimeClasspath` and Spring `runtimeClasspath`; no permanent scanner plugin or paid service was installed. Backend locked npm tooling and identified vendored PDF/OCR modules were included. Project components themselves were excluded from public package queries.

## Findings and remediation

Initial inventory: **488 unique coordinates**, **four matched coordinates**, **nine advisory IDs**. Re-resolved/requeried after changes: **493 coordinates**, **zero returned advisory matches**, zero recorded query errors. The changed graph contains additional stable OpenTelemetry modules; it is not the same coordinate count by assertion. All version upgrades are in the companion backend build, not a new mobile dependency.

| Component | Before → resolved after | Advisory / assessed reachability |
|---|---|---|
| Guava | 33.6.0-jre → 33.7.2-jre, explicit transitive constraint | [Vendor GHSA-xxph-c9ww-hj94](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94): malicious native Java collection deserialization can allocate excessive memory. No application `ObjectInputStream` entry point was found; a component match is not a demonstrated endpoint exploit |
| OpenTelemetry stable family | 1.62.0 → 1.67.0 through Spring's existing `opentelemetry.version` property | [Vendor GHSA-ccrj-gw8r-854m](https://github.com/open-telemetry/opentelemetry-java/security/advisories/GHSA-ccrj-gw8r-854m): crafted W3C tracestate extraction can consume CPU. Actual incoming trace extraction/agent/proxy behaviour was not observed. API/context/SDK family alignment avoids an isolated API-only override; Google's separate alpha incubator dependency remains transitive |
| jsoup | 1.21.2 → 1.23.2 | [Vendor Cleaner advisory](https://github.com/jhy/jsoup/security/advisories/GHSA-pmhh-3w7g-xqp8), [vendor namespace-parser fix](https://github.com/jhy/jsoup/pull/2556) (OSV GHSA-65r4-943x-97jj). Source uses HTML parsing of bounded fixed-host RSE pages, not custom raw-text Safelists or explicit XML parsing. Market parser regressions pass; no XSS/XML attack against production was attempted |
| Bouncy Castle bcprov-jdk18on | 1.83 → 1.85.2 | OSV GHSA-574f-3g2m-x479, GHSA-9pwp-9qqc-pr26, GHSA-c3fc-8qff-9hwx, GHSA-p93r-85wp-75v3, GHSA-qp49-qgx5-5m26. They concern GOST counter reuse, name constraints, LDAP, FrodoKEM timing and lazy ASN.1 depth. The direct application use is scrypt password verification, not those algorithms. See [vendor release](https://github.com/bcgit/bc-java/releases/tag/r1rv85v2), [LDAP advisory](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%900636), [ASN.1 advisory](https://github.com/bcgit/bc-java/wiki/CVE-2026-13506). Existing hash/authentication tests pass; no forced password/key rotation was invented from an unreachable-algorithm advisory |

Published versions were checked against Maven Central metadata and vendor advisories, then the actual resolved graph was inspected. Full Spring tests and `bootJar` passed after upgrading. No real Firebase/APNs delivery, external cloud credential flow or all optional provider branches were exercised; verify configured providers before production rollout.

## PDF/OCR and platform boundaries

The vendored Android PDF.js identifies itself as **6.4.299** and byte-matches the source under the installed dependency used by the copy workflow. Tesseract.js/core are **7.0.0**. These coordinates returned no OSV matches in this scan. For example, the older [Mozilla PDF.js font-matrix advisory](https://www.mozilla.org/en-US/security/advisories/mfsa2024-22/) must not be reported as an active vulnerability merely because the app uses PDF.js; version and execution path matter.

This review does not scan Android/iOS OS-native PDF libraries, all native/WASM binaries, firmware, images, build hosts or proprietary packages. It does not validate signatures of existing PDFs, fuzz parser engines or replace device security updates. Dependency feeds may be incomplete, delayed or wrong; reevaluate new advisories and actual runtime reachability.

## Repeatable low-cost process

1. Record exact resolved runtime versions (Gradle dependency graph, not only declared catalog versions) and lockfile/vendored versions. Keep generated assets aligned with their source and document copy provenance.
2. Query a public advisory service such as [OSV's query API](https://google.github.io/osv.dev/api/) using public coordinates only; retain the timestamp, counts, matches/errors and affected paths. Treat an unavailable query as incomplete, not clean.
3. Verify matches against maintainer advisories/fixed versions and actual source use. Prefer targeted compatible updates, aligned BOM families and the existing isolated tests. Do not downgrade a working platform or add broad security infrastructure to satisfy a scanner.
4. Re-resolve and rescan after upgrades, test authentication/parsing and configured providers, then deploy deliberately. Consider dependency locking/verification and a modest CI review cadence next; neither was falsely marked installed by this one-off review.

Public scan summaries and resolved-version evidence are recorded in DEPENDENCY_REVIEW.json. Full one-off raw query outputs remain temporary local artifacts, not customer data or a permanent security monitoring service.
