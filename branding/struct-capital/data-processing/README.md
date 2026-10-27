# Data-processing illustration assets

Language-neutral vector artwork inspired by the supplied AI-generated reference. The processor, flowing mesh ribbon, branching circuits, documents, charts, and small calendar are independently reusable. No phone mockup, logo, onboarding copy, buttons, or app screens are included.

## Contents

44 SVGs and 132 transparent PNGs: 11 components × 4 color variants, with PNGs at 1×, 2×, and 3×. SVGs contain vector paths and shapes, without fonts, text, embedded raster images, scripts, filters, or external resources. Document content is represented by abstract horizontal rules. These same assets work for English, French, and other app languages.

| Component | Contents | 1× PNG dimensions |
| --- | --- | --- |
| `processor-flow` | Complete isometric processor with branching traces and flowing mesh ribbon | 640 × 460 |
| `processor-core` | Processor body, engraved substrate, inset die, and exposed pins | 480 × 360 |
| `circuit-traces` | Branching circuit paths, terminal nodes, and top arrow, without chip or ribbon | 624 × 462 |
| `data-ribbon` | Flowing wire mesh, without chip or traces | 492 × 420 |
| `document-stack` | Four folded financial documents | 320 × 380 |
| `document` | Single folded document | 312 × 397 |
| `allocation-chart` | Five-bar chart with diagonal engraving | 400 × 352 |
| `value-chart` | Rising line graph with grid and arrow | 400 × 352 |
| `event-calendar` | Small calendar with binding rings, folded corner, and check mark | 342 × 360 |
| `flow-connectors` | Four curved routing arrows from the assembled diagram | 510 × 435 |
| `data-pipeline` | Assembled document → processor → charts/calendar illustration | 920 × 700 |

## Themes

| Filename suffix | Intended surface | Colors |
| --- | --- | --- |
| `dark` | Dark olive `#272B1B` | Cream outlines, gold accents, subtle olive chip surfaces |
| `light` | Off-white `#FAF8F2` | Olive outlines, pale substrate, warm gold accents |
| `mono-olive` | Light | Single olive ink `#6D5E0F`, with off-white interior fills |
| `mono-cream` | Dark | Single cream ink `#F2E6A7`, with dark olive interior fills |

All production canvases are transparent. Chip, document, chart bar, and calendar interiors use intentional opaque fills to hide overlapping geometry. The monochrome variants use one ink color plus the intended host surface color for those fills.

Example filenames:

- `svg/processor-flow-dark.svg`
- `svg/processor-flow-light.svg`
- `png/3x/processor-flow-dark.png` (1920 × 1380)
- `svg/document-stack-mono-olive.svg`

The SVGs preserve fine technical engraving when enlarged. For small layouts, use `processor-core` and simpler individual assets; the full processor's mesh detail is best at roughly 280 logical pixels wide or larger. Keep the original aspect ratio and avoid cropping.

## Preview and package

- `preview.png` / `preview.svg`: full component and theme gallery, with names below each illustration.
- `processor-preview.png` / `processor-preview.svg`: focused processor comparison.
- `pipeline-preview-dark.png` / `.svg` and `pipeline-preview-light.png` / `.svg`: assembled illustration with a solid theme background for viewing. These are previews; production `svg/` and `png/` assets remain transparent.
- `manifest.json`: every production asset's name, theme, SVG viewBox, dimensions, and PNG export paths. `locale` is null for all assets.
- `data-processing-assets.zip`: complete package with artwork, previews, manifest, documentation, and reproducible source.

Only illustration files were generated. App resource registration, theme selection, and onboarding implementation are intentionally left for the later app work.

## Reuse and editing

Individual SVGs retain descriptive groups such as `isometric-processor-core`, `branching-circuit-traces`, and `flowing-data-ribbon`. The complete processor draws ribbon, traces, then chip so overlaps appear naturally.

Chip, ribbon, and trace geometry use the same source coordinates. To recombine their separately cropped SVGs, preserve their `viewBox` offsets as recorded in the manifest, or copy their groups into the full processor's `0 0 640 460` canvas. Do not center their differing cropped canvases on top of one another.

PNG exports can be used in the existing Compose app. SVGs can be edited in a vector editor or consumed by a compatible SVG renderer. Reuse the individual illustrations freely within this app; the original reference image is not embedded in the package.

## Rebuild and verify

Requires Python 3 and `rsvg-convert` (librsvg). Run from the repository root:

```sh
python3 branding/struct-capital/data-processing/scripts/generate.py
```

On macOS, verify PNG dimensions, visible content, transparency, and clear edges:

```sh
swift -module-cache-path /tmp/data-processing-swift-cache branding/struct-capital/data-processing/scripts/verify.swift branding/struct-capital/data-processing
```

Artwork was redrawn as vector geometry inspired by the reference, rather than extracted from the raster image. No image-generation service or installed fonts are needed to rebuild it.
