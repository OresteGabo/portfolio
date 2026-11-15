# Import and report illustration source

This generator supplies Inzira’s processor package, financial document stack and diagram components. Current logo geometry comes from `branding/inzira/`. The processor’s live etched paths and signals are drawn by shared `ProcessingChip.kt`; the source generator supplies its metal surface, circuitry and pins.

Regenerate the selected app resources from the repository root:

```sh
python3 scripts/generate-onboarding-motion-art.py
python3 scripts/sync-onboarding-art.py
```

`python3 branding/struct-capital/data-processing/scripts/generate.py` optionally exports the full SVG/PNG collection, previews and a manifest locally. These outputs are ignored by Git. No ZIP or extracted duplicate is created. `scripts/verify.swift` can validate those optional PNG exports against their generated manifest. `rsvg-convert` is required for raster exports.

The historical directory and internal layer names remain stable for existing regeneration commands. Runtime resources are checked in, so normal app builds do not need the export tools.
