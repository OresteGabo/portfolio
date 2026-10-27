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

`StructBrand.kt` uses portable Compose XML vectors (`struct_mark_light.xml` / `struct_mark_dark.xml`) generated from the supplied symbol paths. It selects contrast from the active app theme; fixed light surfaces request the olive version explicitly. Sign-in, unlock, navigation, dashboard and privacy cover use the same mark.

Android adaptive launchers center the mark in the safe area on olive. Android 13+ also includes a monochrome themed launcher; price notifications use the white symbol as their small icon. iOS includes light and dark 1024px launcher assets.

Run `swift -module-cache-path /tmp/struct-swift-module-cache scripts/generate-app-icons.swift` from the repository root on macOS to reproduce the iOS and legacy Android launcher PNGs from the checked-in 1024px masters. Platform launchers apply their own corner masks.

Exported PDFs draw the same source paths as native PDF vectors (quadratic SVG curves converted to cubic curves). Reports use the Struct Capital title and filename. Legal source copy lives in `docs/legal/content.json`; run `python3 scripts/generate_legal.py` after changing it.

`gabo-icon.svg` and `gabo-logo-full.svg` are retained only as historical source assets.

## Onboarding illustrations

The introduction uses the language-neutral `data-pipeline`, `calendar-notifications` and `document-stack` compositions from the supplied data-processing/calendar asset packs. Six unchanged 2× PNGs (light/dark) are copied into shared Compose drawables as `onboarding_*`; together they add under 1 MB. The welcome screen uses the existing vector monogram. Copy remains native text for translation and accessibility; illustration labels, sample financial amounts, fabricated return figures and live-market promises are not embedded in the flow. Source packs and their generators remain intact in `struct-capital/`.
