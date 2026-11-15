# Inzira — concept 10 vector refinement

Hand-reconstructed from the large symbol in the selected Inzira concept 10 exploration. The small generated icon in that sheet differed from the large mark; the large mark is the reference for this refinement.

The symbol consists of two filled Bézier paths. Flat olive (#6D5E0F) and cream (#F2E6A7) variants use identical geometry. The winding opening, tapered fold and rounded upper corner are preserved, with cleaned curves and aligned lower extents.

## Deliverables

- `inzira-symbol-light.svg` / `inzira-symbol-dark.svg`: transparent symbol masters.
- `inzira-logo-light.svg` / `inzira-logo-dark.svg`: transparent horizontal lockups.
- `inzira-logo-stacked-light.svg` / `inzira-logo-stacked-dark.svg`: transparent stacked lockups.
- `inzira-wordmark-light.svg` / `inzira-wordmark-dark.svg`: standalone outlined lettering.
- `inzira-light-dark-review.png`: light and dark presentation with small-size examples.
- `inzira-preview-light.png` / `inzira-preview-dark.png`: separate presentations.
- `geometry.json`: canonical symbol paths.
- `wordmark-outlines.json`: canonical outlined wordmark, prepared from Avenir Next Demi Bold using CoreText. No font file is distributed or required by the SVGs.

All master SVGs contain vector paths only, without embedded images, live text, filters, or external dependencies. Preview backgrounds are ivory (#FAF8F2) and near-black (#11150F). Master files are transparent.

The app uses these masters through `scripts/generate-inzira-brand.py`. Native UI marks, launcher icons, widgets, sharing and report artwork are derived from this geometry. Bundle identifiers, backend URLs, storage formats and account data remain compatible.
