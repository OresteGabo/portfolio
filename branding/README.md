# Struct Capital brand assets

The current brand is **Struct Capital**. The app's package names, bundle identifiers, backend origins, preferences and credential keys retain their existing identifiers for installation and data compatibility. “Portfolio” remains the name of the investment tab and domain model.

`struct-capital/` contains the supplied V2 SVG masters and PNG exports. The SC ribbon monogram, outlined hexagon, olive `#6D5E0F`, cream `#F2E6A7` and off-white `#FAF8F2` are preserved. The concept and preview images are reference material; production assets use the final vector geometry.

## Theme variants

- `struct-capital-symbol.svg`: transparent olive monogram for light surfaces.
- `struct-capital-symbol-reversed.svg`: generated transparent cream monogram for dark surfaces.
- `struct-capital-logo-transparent.svg` and `struct-capital-logo-transparent-reversed.svg`: transparent full logos, including the generated reversed variant, with typography outlined.
- `struct-capital-logo.svg` and `struct-capital-logo-reversed.svg`: supplied full logos with light and olive backgrounds.
- `struct-capital-icon-light-*`: off-white launcher with olive artwork.
- `struct-capital-icon-*`: olive launcher with cream artwork.

The missing transparent reversed variants were derived directly from the SVG masters by changing only the artwork color. No raster regeneration or geometry changes were needed.

## App integration

Authored runtime colors now live in the shared `dev.orestegabo.portfolio.design` package. Run
`python3 scripts/sync-design-colors.py` after changing brand colors to update derived Android
resources and Compose mark colors. See [the color-system guide](../docs/design/COLORS.md). Source
artwork and raster exports remain separate assets and require their own regeneration for a visual
rebrand.

`StructBrand.kt` uses portable Compose XML vectors (`struct_mark_light.xml` / `struct_mark_dark.xml`) generated from the supplied symbol paths. It selects contrast from the active app theme; fixed light surfaces request the olive version explicitly. Sign-in, unlock, navigation, dashboard and privacy cover use the same mark.

Android adaptive launchers center the same mark in the safe area. Default launcher resources use olive artwork on off-white, matching the iPhone light icon; night resources use cream artwork on olive, matching its dark icon. Legacy Android PNGs include the same light/night variants. Icon appearance follows the system/launcher configuration, independently of the app’s own theme setting. Android 13+ also includes a monochrome themed launcher, whose colors are controlled by the user’s launcher/wallpaper; price notifications use the white symbol as their small icon. iOS includes light and dark 1024px launcher assets.

Run `swift -module-cache-path /tmp/struct-swift-module-cache scripts/generate-app-icons.swift` from the repository root on macOS to reproduce the iOS and legacy Android launcher PNGs from the checked-in 1024px masters. Platform launchers apply their own corner masks.

Exported PDFs draw the same source paths as native PDF vectors (quadratic SVG curves converted to cubic curves). Reports use the Struct Capital title and filename. Legal source copy lives in `docs/legal/content.json`; run `python3 scripts/generate_legal.py` after changing it.

`gabo-icon.svg` and `gabo-logo-full.svg` are retained only as historical source assets.

## Onboarding illustrations

The introduction uses the supplied `calendar-notifications` and `document-stack` compositions, plus individual `processor-core`, `allocation-chart`, `value-chart` and `event-calendar` components. Theme-specific 2× PNGs are copied unchanged into shared Compose drawables as `onboarding_*`. The import page assembles the components with native English/French labels and bundled processor traces connected to item ports: broker PDF → extract/review → portfolio mix, estimated value and coupon dates. Bond records, share records and purchase costs are labelled branches. All screen widths retain the connected processor design. Illustration captions adapt to their nodes while the explanatory copy keeps system text scaling; short viewports show the copy first and scroll the artwork below. The old unlabelled `data-pipeline` composite remains in the source pack but is no longer bundled for onboarding. The welcome screen uses the existing vector monogram. Explanatory labels and onboarding copy remain native text for translation, text scaling and accessibility. The flow includes no sample financial amounts, fabricated return figures or live-market promises. Source packs and their generators remain intact in `struct-capital/`.

The onboarding processor PNGs include the official symbol engraved into the die in the same isometric plane. Document headers, event-calendar headers, and the illustrated calendar phone also carry small printed marks. All source SVG/PNG themes, previews, ZIPs, and existing extracted copies are kept in sync by the pack generators. After regenerating either source pack, run `python3 scripts/sync-onboarding-art.py` to refresh the two document-stack PNGs from the canonical 2× exports; the remaining active layers are produced by `generate-onboarding-motion-art.py`. The sync preserves image dimensions and filenames.

### Opening signature

The first-run opening reveals the source SC ribbons against faint construction lines, traces the hexagon, and briefly shows the wordmark. During the final part of the 2.6-second sequence, the same vector travels into the measured welcome artwork while onboarding appears. The overlay uses the `StructMark` 512-unit viewport so the handoff preserves the logo geometry. Reduce Motion opens onboarding directly. The completed signature has its own versioned device preference; replaying the introduction from Settings includes the signature.

For repeatable iPhone design review, run the shared **iosApp Brand Preview** Xcode scheme. It overrides only the onboarding/signature completion preferences through launch arguments, without clearing saved app data. Normal launch schemes retain first-run behavior.

### Onboarding stories

The import story scans the PDF and carries binary packets along the real connector paths. Incoming data powers a layered die glow, orbiting traces, and a two-turn rotation of the official symbol within the processor’s isometric plane. Extracted records lead into the review checkpoint, followed by natively drawn allocation bars, a traced value graph, and a calendar with a drawn check. The calendar story highlights a date range, delivers event signals, and reveals springing reminder cards with delivery ripples and restrained phone motion. Report sheets gather independently, body lines write under a scan, and a completion check draws at the end. Each story plays once after its page settles, replays on a new visit, and pauses in the background. Reduce Motion shows the completed illustrations immediately; tour controls never wait for these stories.

Run `python3 scripts/generate-onboarding-motion-art.py` to recreate the 18 theme-specific motion layers directly from the existing calendar and document SVG generators. The processor’s blank die and the report’s blank body provide surfaces for native animation. Original source packs and static images are preserved.
