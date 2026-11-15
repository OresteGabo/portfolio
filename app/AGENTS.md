# INZIRA mobile UI engineering

Applies to the Android/iOS hosts and shared Compose UI under `app/`. Respect any parent instructions and the user's authorized scope.

- Inspect the actual source sets, theme, navigation and pending work before editing. Keep unrelated changes out of commits; do not modify the backend or website for a UI task.
- Preserve Inzira's visual identity. Reuse the existing theme, semantic colors, typography, spacing, shapes and dimensions where available. Drawing coordinates are not general layout tokens.
- Follow the existing navigation ownership. Test toolbar/system Back, restoration and external entry points when touched; do not replace the navigation framework for a small fix.
- Composables render state and forward intentions. Keep financial rules, parsing, persistence and networking with their existing owners. Add boundaries only when they solve a concrete problem.
- Preserve currencies, source dates, gross/net and realized/unrealized distinctions. Unknown values are not zero. Do not change formulas, fee/tax assumptions or rounding as incidental UI cleanup.
- Use meaningful accessible labels, roles and state descriptions. Keep decorative artwork silent. Financial descriptions must respect number hiding and capture protection.
- Scope native renderers to their Activity and surface generation. Retire ownership before invoking release callbacks; keep capture protections active during backgrounding and teardown.
- Support font scaling, safe areas, scrolling and keyboard dismissal. Avoid fixed-height text containers and overlapping targets; test large text and short windows.
- Distinguish loading, refreshing, empty, partial, success and recoverable error states. Preserve drafts after failures; guard duplicate saves and intentional destructive actions.
- Profile before claiming a performance gain. Recomposition counts and compiler stability reports alone do not establish jank. Prefer lifecycle-owned state and measured, correctly invalidated work over speculative caches.
- Use deterministic synthetic data for UI tests. Never use production credentials, real financial documents or real-account deletion for a UI fixture. Target a disposable emulator explicitly.
- Keep each lifecycle/security test intact when using Android Test Orchestrator. A failure must remain a failure; do not exclude tests, weaken privacy, relax assertions or keep rerunning solely to obtain green output.
- Review screenshot differences before accepting baselines. Never automatically record missing references during verification. Keep platform/font/density assumptions explicit and preserve failing actual images.
- Update `docs/quality/ui-coverage.json` for changed critical-flow tests. Run the relevant UI/unit/native tests, compilation and configured checks. Record skipped/unavailable checks; never claim an unexecuted check passed.
- Avoid unnecessary dependencies and incompatible tool upgrades. Use `-PcomposeReports=true` for compiler diagnostics when useful. Do not add arbitrary file-length rules or abstractions just to satisfy a metric.
- Treat legal text, provider statements and licensing as reviewed facts/proposals. Do not claim legal compliance, invent operator details or publish unreviewed legal requirements.
