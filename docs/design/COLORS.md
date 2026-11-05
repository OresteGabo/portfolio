# App color system

The shared package is `dev.orestegabo.portfolio.design`. It is a package inside the existing shared
module, so Android and iOS use the same decisions without adding another dependency or a runtime
configuration loader.

## Edit points

- `StructColors.kt`: the **74 unique authored UI ARGB values**, grouped by role. Brand olive, cream,
  ink and canvas are defined once. Other uses reference these values or explicit aliases.
  `ThemeColor` and `ThemeGradient` hold light/dark pairs; components select the theme they already
  use for their surfaces.
- `StructTheme.kt`: the single mapping to Material color roles. Screens continue to use
  `MaterialTheme.colorScheme` for ordinary surfaces, text, actions and errors. Uncustomized Material
  roles keep their existing library defaults; this refactor does not introduce a new theme or
  dynamic-color policy.
- `ReportColors.kt`: fixed print colors and locale-independent PDF color formatting. Report accent
  and vector logo colors derive from the brand olive, preserving the existing PDF precision.
  Print-specific shades remain separate from screen colors.
- `NativeColors.ios.kt`: the adapter from shared colors to UIKit. Native PDF/OCR rendering and the
  unlock cover consume the shared roles. Android PDF paper uses the same role converted to an
  Android color integer.

For a brand edit:

```sh
# Edit StructColors.kt, then synchronize derived platform resources.
python3 scripts/sync-design-colors.py
python3 scripts/sync-design-colors.py --check
```

The sync generates Android named color resources and the color attributes of the Compose light/dark
monogram vectors, preserving their geometry and transparent paths. Native Android launcher and
notification vectors reference the generated named resources. Generated literals are output copies,
not independent editable definitions; CI detects drift.

Changing a brand color updates its aliases, theme assignments, report accent and generated
mark/native resources. Distinct palette shades and light/dark contrasts still need design review; a
single primary color cannot safely substitute for every tone in a theme.

## Role boundaries

Brand colors, financial states, calendar categories, fixed document paper, language-switch states,
national flags, dashboard gradients and appearance previews have separate roles. Equal colors share
a definition. Similar greens/golds with different existing visual roles were not silently merged
just because they look related. This preserves the existing presentation while making future
consolidation deliberate and reviewable.

PDF paper and original company/flag identity do not recolor when the Struct Capital brand changes.
Inherited `Color.Unspecified` and operational transparency remain at their call sites. Contextual
opacity, layout, disabled-state treatment and gradient placement also stay with the relevant
component.

The one intentional normalization is the native iOS unlock cover: its former approximate RGB cream
now uses the exact shared brand canvas, matching the Compose privacy cover.

## Scan coverage and exclusions

The scan covers first-party shared Compose code, native Android/iOS Kotlin adapters, Android app
code/resources, Swift wrappers, and the shared PDF renderer. The hidden HTML engine host has no
presentation stylesheet or authored colors. No authored presentation-color constructors remain in
these production Kotlin files outside the design package.

Original company logos, photographs, onboarding PNG illustrations, launcher PNGs, SVG brand masters
and third-party artwork are image assets. Their embedded pixels/source artwork are not runtime color
definitions and were not recolored. A visual rebrand still needs those assets regenerated/reviewed
separately. The unused Compose Multiplatform template vector, platform system colors and vendored
PDF/OCR code remain independent. Test-only red/blue/magenta fixtures remain test sentinels and do
not follow brand colors.

## Checks

`python3 scripts/sync-design-colors.py --check` is read-only. It rejects scattered production color
constructors, duplicate authored ARGB values, native Android inline hex colors, and stale generated
resources. The mobile release workflow runs it before builds.

The Python generator regressions check that one brand edit changes native resources and logo
fills/strokes while preserving transparency; stale-resource checks do not write files; and the audit
catches new hardcoded values/duplicates. Shared Kotlin regressions check important foreground/action
contrast pairs in both themes and portable PDF precision. They supplement the existing
financial/document/UI regressions, not a complete accessibility certification.

References: [Material color roles](https://m3.material.io/m3/pages/color-roles), [Design Tokens color module](https://www.designtokens.org/tr/drafts/color/).

Verified 3 November 2026: 359 shared Android-host tests and all 9 Python checks passed; Android
debug/test APK assembly and shared iOS simulator compilation passed. The synthetic bond/share layout
test passed in both themes at 411dp. Each of the four captured bond/share PNGs was pixel-identical
to its pre-refactor baseline (zero differing channel bytes). Native iOS colors were compiled but not
visually inspected on iOS. Raster artwork, default Material roles and other screens were not claimed
as pixel-comparison coverage.
