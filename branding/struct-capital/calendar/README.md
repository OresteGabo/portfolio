# Calendar illustration assets

Editable vector recreation of the supplied AI-generated calendar reference, adapted to Struct Capital's olive and cream palette. Only artwork is included; no onboarding screens or application code were changed.

## Files

- `svg/`: 22 standalone SVGs. All illustration lettering and numbers are paths, with no font dependencies, embedded raster images, external resources, scripts, filters, or SVG text elements.
- `png/1x/`, `png/2x/`, `png/3x/`: 66 RGBA PNG exports, with identical composition and increasing pixel resolution.
- `preview.png` / `preview.svg`: comparison sheet of the 20 transparent variants, displayed on appropriate theme surfaces.
- `preview-fr.png` / `preview-fr.svg`: French artwork in all four color variants.
- `manifest.json`: names, dimensions, transparency, and paths for every export.
- `calendar-assets.zip`: complete downloadable package.
- `scripts/`: reproducible source generator and cached vector lettering.

## Compositions

| Name | Use | Native size |
| --- | --- | --- |
| `calendar-notifications` | Full calendar and phone, with abstract text lines for localized onboarding | 760 × 560 |
| `calendar-detail` | Full reference-style artwork, with outlined English subscription and payment labels | 760 × 560 |
| `calendar-detail-fr` | Full reference-style artwork, with outlined French subscription and payment labels | 760 × 560 |
| `calendar` | Standalone calendar with payment markers | 590 × 530 |
| `notifications` | Standalone phone and notification bubbles | 278 × 318 |

The text-free versions retain small outlined notification counter digits. The detailed version preserves the illustrative dates from the reference; it is decorative artwork, not a functional calendar.

## French and English

Choose `calendar-detail-fr-{theme}.svg` for French, and the existing `calendar-detail-{theme}.svg` for English. PNG files use the same naming in each density folder. The manifest includes `locale: "fr"`, `"en"`, or `null` (language-neutral).

French labels include “Souscription obligataire / Ouverture”, “Souscription / Clôture”, “Dividende / versé / Notification”, “Dividende versé / Notification”, and “Coupon versé”. Dates use “27 mars” and “19 mars”. Accents are preserved in the outlined paths, so fonts are not required.

Language-neutral compositions are shared by both locales. Selecting assets when the app language changes remains a future integration step; this delivery only supplies image files.

## Colors and surfaces

Each composition has four variants:

| Suffix | Intended surface | Artwork |
| --- | --- | --- |
| `dark` | Dark olive | Warm cream strokes, gold accents, light payment cards |
| `light` | Off-white | Olive strokes, pale olive accents, white payment cards |
| `mono-olive` | Light | Single olive ink with off-white occlusion fills |
| `mono-cream` | Dark | Single cream ink with dark olive occlusion fills |

All 20 have transparent outer canvases. Phone and card interiors have intentional opaque fills to hide overlapping artwork; monochrome means a single ink color with surface-colored knockout fills. Match these to the host surface (`#272B1B` dark, `#FAF8F2` light).

Two extra full compositions, `calendar-notifications-dark-background` and `calendar-notifications-light-background`, include solid theme backgrounds for uses where a self-contained image is useful. These are the only opaque-canvas exports.

For future onboarding, start with `calendar-notifications-dark.svg` / `calendar-notifications-light.svg`. Keep explanatory copy in the app for translation and accessibility. Use the PNG equivalents when the target renderer cannot display SVG paths. The existing Compose app can consume PNGs; this package deliberately does not add them to generated resources or wire up a screen.

## Resolution

For the full illustration, exports are 760 × 560, 1520 × 1120, and 2280 × 1680. Other compositions scale by the same factors. Preserve the aspect ratio and fit the entire image without cropping. SVGs scale independently of density.

## Rebuild

From the repository root on a machine with Python 3 and `rsvg-convert` (librsvg):

```sh
python3 branding/struct-capital/calendar/scripts/generate.py
```

The generator uses checked-in `text-outlines.json`, so regenerating artwork does not require fonts. If label wording changes, update `outline-text.swift`, regenerate its JSON on macOS, then rebuild:

```sh
swift -module-cache-path /tmp/calendar-swift-cache branding/struct-capital/calendar/scripts/outline-text.swift > branding/struct-capital/calendar/scripts/text-outlines.json
python3 branding/struct-capital/calendar/scripts/generate.py
```

The reference was visually redrawn as vector geometry; this is a recreation rather than an exact pixel trace. The source image is not embedded or redistributed in this package.
