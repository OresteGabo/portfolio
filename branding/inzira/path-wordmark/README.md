# Inzira path wordmark — in-app preview

Prepared 2026-11-18 from the wordmark concept generated in this conversation, using the built-in image-generation tool.

The dashboard now displays the sculpted path lettering, with brighter champagne gold for dark mode and darker bronze for light mode. The launch signature uses matching flat lettering tinted with the existing brand olive/cream tokens. The emblem, its rotation and financial screens' text labels are unchanged.

## Saved app assets

- `app/shared/src/commonMain/composeResources/drawable/inzira_path_wordmark_3d_dark.png`
- `app/shared/src/commonMain/composeResources/drawable/inzira_path_wordmark_3d_light.png`
- `app/shared/src/commonMain/composeResources/drawable/inzira_path_wordmark_flat.png`

All three have real transparency and a 3:1 canvas. Generated 2172×724 outputs were resized with macOS `sips --resampleWidth 768` to 768×256 for the small UI placements, retaining alpha. They total roughly 385 KiB. Full-size generated images remain in the conversation's generated-image library and are not bundled in the app. This is baked metallic artwork, not another live 3D renderer or animation loop.

## Generation prompt set

1. **Dark metallic extraction.** Extract only the upper 3D Inzira lettering from the approved concept. Preserve the connected letter contours, proportions, route-like baseline and diamond i dot. Remove the ivory background and flat specimen. Use champagne-gold faces and bronze sidewalls with shallow extrusion, bevel lighting and transparent ambient shadow. Exactly one Inzira wordmark, tightly framed, without a backdrop, hexagon, labels or added icons. Optimize for dark forest-green surfaces.
2. **Light metallic counterpart.** Preserve the preceding wordmark's geometry, framing and transparency; change only its material/lighting to darker antique-gold and bronze-olive faces, controlled champagne highlights and dark bronze sidewalls. Keep it legible on ivory at small size, without pale overall faces, edge fringes, floating speckles or a background.
3. **Flat counterpart.** Preserve the same connected front-face letter shapes, open counters, path baseline and diamond dot. Remove depth, bevels, highlights and shadows. Use one uniform black silhouette on a transparent, tightly framed canvas. Runtime tint supplies the existing light/dark brand colors.

`InziraWordmark` retains its accessible name. The dashboard's loading and populated states share the same header component, so both receive the new lettering together. Old outlined vector resources remain available for comparison and the existing generated-color pipeline; they are not used by the new wordmark component.

## Verification — 2026-11-18

- `JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ./gradlew :app:shared:testAndroidHostTest :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest :app:androidApp:lintDebug :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 --console=plain` — passed; 587 shared tests, zero failures/errors. Lint retains six existing dependency/toolchain notices.
- Signed iPhone Debug `xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'id=00008101-000E3DD11E03001E' -destination-timeout 30 -derivedDataPath /tmp/inzira-share-timeline-ios -allowProvisioningUpdates build` — passed. Installed and launched on the connected iPhone with `devicectl`; iPhone rendering still requires the user's visual review.
- `adb -s emulator-5590 shell am instrument -w -e class dev.orestegabo.portfolio.investments.DashboardLoadingLayoutTest dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner` — passed unchanged assertions after recovering an emulator System UI ANR. The initial run failed awaiting the loading accessibility node; `dumpsys window` identified the foreground “Application Not Responding: com.android.systemui” dialog. Restarting that system process restored the launcher before the retry. No assertions were weakened. The passing test exercises both themes at 1.0 and 1.8 font scale, initial unknown balances, notification action and stable header bounds across snapshot arrival.
- Visually inspected all eight Android captures (loading/loaded × both themes × both font scales), saved under `/tmp/inzira-wordmark-visual-check`. The wordmark has transparent edges and fits its header. The existing notification count badge clips at large font scale; its code is outside this lettering change and remains a separate UI issue.
- `python3 scripts/check-kmp-packages.py`, `python3 scripts/check-kmp-ui.py`, `python3 scripts/sync-design-colors.py --check`, `git diff --check` — passed.
- Launch lettering compiled on both platforms but its animation was not visually inspected in this task. No screenshot references were replaced. No financial rules, backend code or user records were changed for the wordmark preview.

## Commit review — 2026-11-20

Re-ran the Gradle verification command above and all three repository quality scripts plus `git diff --check`; all passed. Gradle reused unchanged test/compile outputs. The Android visual runs and signed iPhone build/install evidence above are from the preceding preview, not represented as newly executed device checks. No production code changed during commit preparation.
