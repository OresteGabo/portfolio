# Shared app motion

`AppMotion.kt` owns short content transitions for both platforms. All helpers read `LocalReducedMotion` and show the settled content immediately when the device requests reduced motion.

- `smoothContentSize()` eases changing layouts over 240 ms, anchored at the top. Portfolio's hero/status area and company chart cards use it.
- `AppLoadingLine()` fades and collapses a transient loading strip. Charts reserve a 4 dp loading slot so the graph never changes position when loading finishes.
- `AppFadeIn()` fades newly available content in over 240 ms without retaining outgoing financial text.
- `AppMovingItem()` eases investment-row placement and initial appearance. Removed rows are not retained for a fade-out.
- `AppAnimatedVisibility()` uses a short top-anchored expansion/fade for document, calendar and investment details.

Investment charts reveal observed points from left to right over 520 ms. Numeric amounts are never interpolated or replaced with intermediate fictional values. Chart redaction stays inside the existing capture-protection region; hidden-number state is not crossfaded with visible values.

Stock refresh retains the last fetched chart while the request runs. Successful responses update that company's cache; a failed refresh preserves the chart and offers Retry. Only the settled pager page is fetched, and cached company/period pairs remain reusable until a refresh revision invalidates them. Changing Month/Year/All still uses the same underlying observations and scales.

These transitions add no artificial network delay and do not change the existing pull-to-refresh minimum loading/success timings.
