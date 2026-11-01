# Security policy — Struct Capital

## Reporting a vulnerability
Please report suspected security vulnerabilities privately to the project maintainers through a designated private security-reporting channel (for example, GitHub private vulnerability reporting, if enabled). **Do not open a public issue containing exploit details, credentials, or customer data.**

Maintainers: configure and publish an actual monitored security contact/channel before public release. Do not invent a contact email.

Include affected component/version, safe reproduction steps with synthetic data, impact, and suggested mitigation. Do not access other users' data or conduct disruptive testing.

## Supported versions
Maintainers should list currently supported application and backend versions before release.

## Internal process
Acknowledge and triage reports, limit access to evidence, assign severity, implement and verify remediation, coordinate disclosure, and document incident response if exploitation is suspected. Do not promise response timelines until operational capacity exists.

## Assessment and release status

The [31 October 2026 assessment](SECURITY_AUDIT_REPORT.md),
[threat model](THREAT_MODEL.md), [test plan](SECURITY_TEST_PLAN.md) and
[dependency review](DEPENDENCY_ADVISORIES.md) record evidence and outstanding gates.
They are not a certification or authorization to probe a live service. Current
build-tool advisories, deployment/backup verification and reporting-channel setup
remain tracked work. No supported-version or response-time promise is implied.

## Incident handling

The maintainer should privately record detection time, affected environment/version,
containment decisions and minimal redacted evidence; restrict access, preserve
relevant logs safely, and assign an incident lead. Any credential rotation,
production change, customer/regulator notification or restore requires an
approved operator procedure. Practice on a synthetic restored database before
claiming recovery readiness. Do not put financial documents or live credentials
in a public issue or automated report.

Before public release, verify applicable Rwanda reporting and cross-border hosting
requirements with qualified advice. The [DPP authority's breach provisions](https://dpo.gov.rw/dpp-law/obligations-of-the-data-controller-and-the-data-processor)
describe 48-hour notification and 72-hour reporting duties; applicability and the
actual escalation contact must be established operationally, not inferred from
these technical checks.
