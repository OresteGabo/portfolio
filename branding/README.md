# Gabo brand assets

`gabo-icon.svg` and `gabo-logo-full.svg` are copied unchanged from the web project's `public/icon.svg` and `public/logo-full.svg`.

The shared Compose header uses `app/shared/src/commonMain/composeResources/drawable/gabo_mark.xml`, a portable vector conversion of the mark. It preserves the source geometry, cream background and olive strokes without applying icon tint.

Run `swift scripts/generate-app-icons.swift` from the repository root on macOS to regenerate the iOS 1024px app icon and legacy Android launcher PNGs from `gabo-icon.svg`. Android adaptive launchers use the same mark in `ic_launcher_foreground.xml`, centered in the safe area on the cream background. Both platforms apply their system launcher shape.

Navigation and action icons use the shared Compose Material icons library. Filled icons identify the selected tab; outlined icons identify the other destinations. Labels and accessibility descriptions accompany the icons.
