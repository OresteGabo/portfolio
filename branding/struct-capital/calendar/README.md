# Calendar illustration source

This generator supplies the calendar, phone and reminder-card layers used by Inzira onboarding. The source symbol comes from `branding/inzira/inzira-artwork-symbol.svg`; `scripts/text-outlines.json` contains the illustration label outlines.

Regenerate the runtime layers from the repository root:

```sh
python3 scripts/generate-onboarding-motion-art.py
```

`python3 branding/struct-capital/calendar/scripts/generate.py` optionally exports a full SVG/PNG collection and previews locally. These generated outputs are ignored by Git. No ZIP or extracted duplicate is created. `rsvg-convert` is required for raster exports.

The historical directory and internal layer names remain stable for the motion-layer extractor. The app uses the current Inzira logo.
