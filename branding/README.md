# Inzira branding and artwork

The active app identity is **Inzira**. The active angled-I symbol geometry lives in `inzira/refined-initial/`; outlined lettering and historical concept presentations remain in `inzira/refined-10/`. The shared UI, launchers, widgets, PDF import action and exported reports derive their marks from these sources.

## Regeneration

From the repository root:

```sh
python3 scripts/generate-inzira-brand.py
python3 scripts/sync-design-colors.py
swift scripts/generate-app-icons.swift
# Verify embedded illustration logos without writing resources:
python3 scripts/generate-onboarding-motion-art.py --check
python3 scripts/sync-onboarding-art.py --check
```

`generate-inzira-brand.py` also regenerates the calendar, chip and document layers, so changing the master cannot leave old logos embedded in onboarding PNGs.

Artwork generation requires `rsvg-convert`; launcher PNG generation uses macOS AppKit. These tools are needed to change artwork, not to build the app from the checked-in runtime resources. Theme colors live in the shared design package; see [the color guide](../docs/design/COLORS.md).

The light identity uses olive on ivory. The dark identity uses cream on near-black. Android themed launcher colors follow the launcher configuration. Historical resource names, bundle IDs, backend addresses, preference keys and storage identifiers stay compatible with existing installations.

## Onboarding sources

`struct-capital/calendar/scripts/` and `struct-capital/data-processing/scripts/` retain the illustration generators and required text outlines. Their historical directory names are kept for existing regeneration commands. They read the current Inzira masters.

The app bundles only its selected `onboarding_*` resources. The focused scripts above regenerate those resources directly; duplicate source-pack exports, extracted folders, ZIP archives and obsolete logos are not retained. Optional full-pack exports are ignored by Git.

The opening draws the Inzira initial and its hexagonal frame around a travelling light, then hands the mark to the welcome screen. The processor face is an etched Inzira circuit surrounded by fine traces, contacts and logic cells; data packets follow its channel without rotating the logo. Calendar and report stories retain their native animations. Reduce Motion shows the completed artwork directly.

The **iosApp Brand Preview** Xcode scheme can replay the introduction without clearing account data. Normal launch schemes preserve first-run preferences.
