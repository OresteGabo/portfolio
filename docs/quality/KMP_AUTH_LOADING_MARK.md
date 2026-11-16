# Authentication loading mark

The approved combined loader is a shared Canvas component: a highlight follows the hexagonal frame while light travels through the stationary initial. Both contours come from `InziraBrandGeometry`; no raster animation or new dependency is introduced.

`UnlockScreen` uses it in the existing logo position while `model.busy` is true. `SignInScreen` uses it in the submitting button. Authentication, biometric prompts, disabled actions, navigation and error handling are unchanged; the animation never delays completion.

A single 2.6-second phase synchronizes both highlights. Frame state is read during drawing, rather than recomposing the form each frame. Work is scoped to the composable and the screen's RESUMED lifecycle. Idle and reduced-motion states show the complete stationary logo, while active loading retains indeterminate-progress semantics. The reusable component defaults to the app's reduced-motion preference; isolated hosts can supply that preference explicitly.

## Verification

- Shared host regression suite: 558 tests passed.
- Android debug build/lint and both iOS target compilations passed. Existing toolchain notices remain visible.
- `InziraLoadingMarkTest.loaderMovesInBothThemesAndStopsForIdleReducedMotionAndBackground` passed on the disposable API-36.1 emulator. Real PixelCopy frames differ while loading, remain identical when idle/reduced/backgrounded, and change again after resuming. The active mark exposes progress semantics in both themes. No golden reference was recorded or altered.
- `AuthenticationUiQualityTest.signInShowsLoadingRetainsInputAfterFailureAndGuestChoiceCanBeCancelled` passed on the same emulator: disabled loading action, one blocked request, retained inputs, recoverable failure/retry and cancelled guest selection.
- Python suite: 35 tests passed, including onboarding artwork freshness. Package, color and UI evidence inventory checks passed; the full inventoried Android UI suite was not run for this change.

Focused device command (synthetic data only):

```sh
JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home ANDROID_SERIAL=emulator-5554 ./gradlew :app:androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.orestegabo.portfolio.investments.InziraLoadingMarkTest,dev.orestegabo.portfolio.investments.AuthenticationUiQualityTest -Pandroid.testInstrumentationRunnerArguments.uiAuditSnapshots=true --console=plain
```

The component's explicit reduced-motion input lets the isolated test host exercise accessibility behavior without exposing the app's internal composition local. No visibility checks were suppressed. iOS UI automation and manual screen-reader testing were not run; no runtime performance improvement is claimed.
