# INZIRA KMP accessibility audit

> Historical scoped audit. Its original exclusions, failures and Git disposition below describe the earlier isolated attempt. The 15 November combined integration and current disposition are recorded in [KMP_PENDING_CHANGES_REVIEW.md](KMP_PENDING_CHANGES_REVIEW.md); that later review supersedes the status here without reclassifying a failed historical run as passed.

Date: 2026-11-15. Baseline: `f7ef53f` on `main`.

## Scope, reference and confidence

Mobile UI only: shared Compose screens, dialogs, navigation, charts, forms, platform adapters and native widgets/share/notification presentation. No backend, website, API, investment formula or persistence contract changes. The source review includes existing pending UI/responsibility extractions; verification uses an isolated export of this accessibility patch against HEAD, excluding unrelated pending work. Existing pending file extractions are preserved: the reviewed working-tree `CredentialField.kt` and `CalendarComponents.kt` correspond to baseline implementations in `SignInScreen.kt` and `CalendarScreen.kt`. The patch changes those baseline owners without committing the unrelated moves. The chart's unrelated commented-code removal is also excluded.

WCAG 2.2 Level AA is the engineering reference, including applicable Level A criteria. Native dp/points and web CSS pixels are not interchangeable certification units. WCAG [2.5.8](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html) uses a 24 CSS-pixel minimum with exceptions; this review additionally aims for Android's [48dp touch targets](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) and platform-appropriate iOS controls. A 32dp control is not automatically a WCAG target-size failure.

Text thresholds: [1.4.3](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html), 4.5:1 normal / 3:1 qualifying large text. Meaningful UI/graphic indicators: [1.4.11](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html), 3:1 where applicable. [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics) and [Apple VoiceOver guidance](https://developer.apple.com/design/human-interface-guidelines/voiceover) supplement the criteria. Material supplies many roles, states and minimum targets; custom pointer input and containers need explicit review.

**This is not a legal assessment, WCAG certification, or claim of complete conformance.** Source inspection, contrast arithmetic, accessibility-tree tests and a bound accessibility service do not replace a human TalkBack/VoiceOver review. Runtime cases and unavailable checks are identified below.

## Implemented improvements and findings

Paths are relative to `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/` unless qualified. “Changed” describes the patch, not an application-wide compliance claim.

| ID / severity | Criterion / evidence | Change and verification boundary |
|---|---|---|
| A01 High — pointer-only chart observations | [2.1.1 Keyboard](https://www.w3.org/WAI/WCAG22/Understanding/keyboard.html), [4.1.2 Name/Role/Value](https://www.w3.org/WAI/WCAG22/Understanding/name-role-value.html). `InvestmentChart` selected the nearest observation only through `detectTapGestures`. Its canvas description contained the current point/count, without an equivalent action to visit other points. | **Changed:** focusable chart, Previous/Next/Latest custom accessibility actions, Left/Right hardware keys, image role and polite current-observation state. Boundary actions are omitted when inapplicable; Tab remains with the focus system. A visible 2dp brand-primary outline marks keyboard focus. Price filtering, date positioning and financial calculations are unchanged. All three styles share these controls. |
| A02 High — insufficient chart alternative | 1.1.1 Non-text Content, 1.3.1 Info and Relationships. A single selected value did not convey the overall financial trend or extrema. | **Changed:** `FinancialChartAccessibility.kt` describes first/latest observations, observed low/high, count and rising/falling/unchanged direction. Selected state includes position/date/value and original source note. English/French templates preserve supplied values. No inferred prices, returns convention or new financial formula. |
| A03 High — financial privacy must include accessibility | 4.1.2; privacy regression concern. Adding a rich description could expose values while `LocalNumbersHidden` masks visuals. | **Preserved and tested:** hidden chart branch renders only the existing privacy placeholder, without summary/actions/amounts. Tests traverse text, content and state descriptions after hiding, including after keyboard/custom-action selection. Capture protection and `FinancialText` remain intact. This is not a security review of all platform capture paths. |
| A04 Medium — chart axis truncation at large text | [1.4.4 Resize Text](https://www.w3.org/WAI/WCAG22/Understanding/resize-text.html). Numeric axis labels had a fixed 54dp column, single-line limit and 184dp plot height. | **Changed:** width/plot height follow font scale from 1x upward; numeric labels can wrap; timeline indentation follows axis width. No global font-scale clamp. Test matrix includes 200% text. Full arbitrary-value visual clipping and very large font scales still need device checks. |
| A05 Medium — low-contrast dashboard labels | 1.4.3. `PortfolioInsights.AllocationLegend` used foreground at .65 opacity. Palette/alpha arithmetic gives a minimum about **4.34:1** across the authored light gradient endpoints. Other income labels used .68 opacity, leaving little margin. | **Changed:** use the existing opaque insight foreground for all three supporting label locations. Font size/weight retains hierarchy. Added ≥4.5 contrast assertions in both themes against gradient endpoints and a conservative 30% decorative-tint envelope. This arithmetic is not a pixel audit of every composited gradient. Brand colors are unchanged. |
| A06 Medium — faint credential boundaries | 1.4.11, 2.4.7 Focus Visible. `CredentialField` used `onSurface` at .18 opacity for unfocused borders. Its fill is separately tinted; the faint border offers weak field identification. | **Changed:** use the existing Material `outline` semantic role; keep the primary focused border and existing labels/password/autofill/IME logic. Tests assert ≥3 boundary and focus contrast against the authored fill in both themes. Current working owner is `CredentialField.kt`; staged baseline owner is `SignInScreen.kt`. |
| A07 Medium — duplicate consent targets | 4.1.2, 1.3.1. `BondIssuance.PublicIssuanceConsent` combined a clickable row with an independently interactive checkbox. The row itself lacked checkable state. | **Changed:** one 48dp-minimum toggleable row with Checkbox role/state and the existing full consent text; the visual checkbox has no separate action/semantics. Same change callback and confirmation policy. No public-report or import business rule change. |
| A08 Medium — selection not exposed at PDF review rows | 4.1.2, 1.3.1. `ConversationReview` match rows and `StatementScreens.StatementBondDialog` rows used `clickable` with a passive radio glyph. The actionable parent did not expose selected/radio state. | **Changed:** parent `selectable` with RadioButton role, selected/enabled state, minimum 48dp height; decorative child radio semantics removed. Existing financial text, selection callbacks and statement reset rules remain. No new purchase matching policy. |
| A09 Medium — onboarding targets/readout | 2.5.8 supplemented by Android targets; 1.1.1. Four 32dp step controls had only 2dp horizontal gaps; automatic touch expansion could overlap. Animated artwork also contains synthetic labels/amounts unrelated to a real account. | **Changed:** 48dp allocated step controls, retaining names/Tab role/selected state. Purely decorative artwork subtrees are excluded while explanatory copy/headings and the import diagram’s existing meaningful description remain. On short windows, the footer gets its own bounded scroll area so enlarged actions do not consume the entire story pane. No new automatic progression or animation timing changes. |
| A10 Medium — calendar contrast and state | 1.4.3, 4.1.2. Adjacent-month dates remained actionable but used `onSurfaceVariant` at .45 opacity. Expanded event ListItems had a clickable action and an expand-icon description but no explicit expanded state on the parent. | **Changed:** full semantic supporting-text color for actionable dates; event row Button role and Expanded/Collapsed state. The chevron is decorative to avoid repeating state. Names, dates, receipt controls and reminder behavior remain. Current working owner `CalendarComponents.kt`; baseline owner `CalendarScreen.kt`. |
| A11 Medium — next-coupon action role | 4.1.2. `PortfolioInsights.CouponIncomeCard` had a named click action but no semantic control role. | **Changed:** Button role on the existing clickable row. Its financial content continues through privacy-aware text. |
| A12 High — protected custom overlays need focus audit | 2.4.3 Focus Order, [2.4.11 Focus Not Obscured](https://www.w3.org/WAI/WCAG22/Understanding/focus-not-obscured-minimum.html), 2.1.2 No Keyboard Trap. `InvestmentTargetDialog` and `IssueReportScreen` use protected main-scene overlays, not ordinary platform dialogs. Target overlay has pane title/back/cancel, but source alone cannot prove background screen-reader isolation or restored focus. | **Open:** verify traversal, background exclusion and restoration with both screen readers and hardware keyboard. Do not move financial editing into an iOS sibling dialog scene merely to obtain modal semantics; source explicitly documents the capture-protection reason. No security boundary is weakened here. |
| A13 Medium — field-specific error associations incomplete | 3.3.1 Error Identification, 3.3.2 Labels or Instructions, 3.3.3 Error Suggestion, 4.1.3 Status Messages. `Field`/credential inputs have persistent labels and IME handling. Several purchase/import forms surface validation through shared `Notice` or plain text instead of individual `isError`/error semantics. | **Open:** map field-specific validation to existing controls without exposing raw parser/server exceptions or changing admission rules. Verify retries retain input, errors announce once and focus moves only when useful. A generic global error framework is not introduced. |
| A14 Medium — remaining dense layouts | 1.4.4, 1.4.10 Reflow, 2.4.11. `CalendarScreen` header/legend, the annual chart’s rotated month labels in a 42dp canvas, legacy `Fact` two-column rows, dashboard fixed-height/scaled insight cards and root five-destination navigation require large-font/short-window coverage. `DetailValueRow`/`DetailColumns` already stack at width/font thresholds. | **Open:** add actual-screen 200% text, long French names, keyboard-open and iPad split-view cases. No claim that every static fixed dimension is a reproduced defect. Seven-column calendar cells need real hit-bound/overlap testing on narrow phones; a 48dp height alone does not establish a 48dp-wide target. Do not remove existing adaptive detail behavior. |
| A15 Medium — native extension coverage | 1.4.4, 4.1.2, 1.4.11. iOS share labels use preferred text styles/`adjustsFontForContentSizeCategory` and decorative icons are excluded. SwiftUI widgets have useful explicit event labels but some fixed font sizes/line limits; live activity has separate palette/layout. Android RemoteViews have platform size constraints. | **Open:** actual VoiceOver rotor/Dynamic Type, TalkBack widget/card actions and compact/expanded text checks. Readout, date pronunciation and sufficient contrast for each native surface are not established by compiling shared Compose. |
| A16 High — full Android verification blocker | Test infrastructure/runtime limitation. Earlier UI audit and current baseline have a native `PdfSharingTest` RenderThread surface-destruction crash. | Must not hide/disable that test or claim complete verification. Full-suite result and Git disposition are recorded below. |

## Existing accessibility retained

- `AppText` keeps localized visible content; `FinancialText`, hidden charts and capture masks protect readouts. Do not add raw-value content descriptions around masked descendants.
- Top-level icon buttons generally supply localized action names through `AppIcon`; icons beside a visible label are mostly decorative. Avoid duplicating visible text in `contentDescription`. Logo artwork with adjacent issuer/company text is not a separate action.
- Material buttons, tabs, switches, text fields and bottom navigation supply many roles/states by default. Settings row switches already use a single toggleable parent and clear passive switch semantics. Reminder badges expose on/off state. Document disclosures expose expanded state.
- Shared detail, legal, onboarding and yearly-coupon titles use heading semantics. `PageTitle` marks its title as a heading. This does not prove complete heading coverage/reading order for every nested pane.
- Gains/losses include signed values and labels, pending investments have text badges, and import errors use explanatory text. These are not communicated solely by green/red. Allocation has named legends, amounts and a textual percentage summary; chart-segment contrast and distinguishability still need visual review.
- `Notice`, import completion/progress and refresh UI use polite live regions. New chart changes expose the selected observation, not animation frames. [4.1.3](https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html) remains a manual announcement-frequency check for every asynchronous flow.
- Android `rememberReducedMotion` observes animator duration scale; iOS observes `UIAccessibilityIsReduceMotionEnabled` and its change notification. `AppMotion`, chart reveal and onboarding honor `LocalReducedMotion`; offscreen onboarding stories pause through lifecycle ownership. Critical values remain textual. No flashing-frequency certification or exhaustive timed-content audit is claimed.
- Credential fields keep password visibility names, input type/autofill and Next/Go/Done handling. Shared numeric fields clear focus on Done. Existing native authentication and biometric prompts retain platform accessibility.

## Screen, dialog and component coverage

This is a **source review inventory**, not a list of manually passed screens. Inline confirmation/error dialogs are included with their owning feature. The runtime matrix below is deliberately narrower.

| Feature / destinations | Owners reviewed | Focus of review / remaining device check |
|---|---|---|
| Welcome, brand intro, four onboarding pages | `OnboardingScreen`, `BrandIntroScreen`, `ProcessingChip`, import/chart/calendar/report artwork | Decorative readout, headings, step names/targets, reduced motion, short-window action reachability. |
| Authentication, unlock, sessions | `SignInScreen`, `CredentialField`, `UnlockScreen`, `SecureSignIn`, `SignInActivityScreen` | Persistent labels, password reveal, traversal, focus outline; native biometric/VoiceOver review still required. |
| Home, valuation, filters, investment menu | `PortfolioDashboard`, `PortfolioHero`, `PortfolioInsights`, purchase/holding cards, `EmptyState`, skeleton/pull refresh | Masking, trend/allocation summaries, label contrast, icon names, carousel controls; large values/long titles/manual focus review. |
| Bonds, pending bonds, editing, receipt ledger | `InvestmentDetails`, `PurchaseForms`, `PurchaseImportScreens`, pending cards, detail helpers | Disclosure state, labelled amounts, safe financial text, form/error association, receipt controls. |
| Shares, lots, pending orders | `ShareDetailsScreen`, `ShareHoldingCard`, `ShareOrderScreen`, quantity/total components | Read-only quantities and named steppers, purchase history, labelled return disclosures, full-size financial values. |
| Calendar, subscription/reminder/coupons | `CalendarScreen`, `CalendarComponents`, `BondMonthReminders`, `YearlyCouponsScreen`, `YearlyCouponComponents` | Complete date descriptions, selected/state names, category text, reminder state, event disclosures, large-font rows. |
| Markets and trends | `StocksScreen`, `CompanyPriceChart`, `InvestmentChart` (baseline market owner `LibraryScreens`) | Chart equivalent controls/summary, selected period, keyboard escape/traversal, privacy and axis scaling. |
| Document import/library/detail/reader | `DocumentsHub`, `DocumentsScreen`, `ConversationReview`, `ImportCompletionReceipt`, `PdfImportPreparationScreen`, `PdfImportProgressDialog`, `DocumentDetails`, `DocumentThumbnail`, `PdfViewerScreen` | Review choices and labels, safe progress, status announcements, reader page actions. Image-only/scanned PDF content still needs document-accessibility investigation; no PDF tagging/OCR accessibility guarantee. |
| Statements and missing evidence | `StatementScreens` (`MissingDocumentsScreen`, reconciliation/bond dialogs) | Selection roles/states and explicit evidence warnings; error/focus recovery still needs screen-reader review. |
| Planning and activity | `PlanningScreens` (simulator/education), `InvestmentToolsScreen`, `InvestmentHistoryScreen`, `InvestmentActivityTimeline`, `CashLedgerScreen` | Input labels, chronological order, explanations, large-text and embedded education-content reading order. |
| Settings, notifications, goals, backup | `SettingsScreen`, `SettingsComponents`, `SettingsRecordsSheet`, `NotificationPreferences`, `Notifications`, `InvestmentGoalSettings`, `EncryptedBackupScreen` | Parent toggle ownership, named controls and error text; protected overlay focus and restore-confirmation keyboard review. |
| Guest/import/migration | `GuestScreens`, `GuestPresentation`, `GuestMigrationScreen` | Consent/choice state, privacy notices, accessible errors and completion; no guest persistence policy changes. |
| About, legal, support/report | `AboutScreen`, `LegalScreen`, `IssueReportScreen` | Headings/links, labelled report fields, confirmation/error readout; overlay isolation and character-count verbosity remain manual checks. |
| Native surfaces | Android `NextWidget`, `BondReminderNotifications` and RemoteViews XML; iOS share extension, widgets/live activity, capture host | Platform event labels, decorative exclusions and Dynamic Type declarations inspected. Physical screen-reader operation and extension contrast not fully verified. |

## Verification and Git disposition

Verification export: `/tmp/inzira-a11y-index.rkxD52`. Its 14 staged production/test inputs were compared byte-for-byte with the export and matched. No tests were disabled and no assertion tolerances were relaxed. The existing pending synchronous notification-test permission setup correction is included so that regression test grants permission before checking notification eligibility; production notification/permission logic is unchanged.

### Compilation, host tests and static checks

```sh
export JAVA_HOME=/Users/muhirwagabooreste/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home
export ANDROID_HOME=/Users/muhirwagabooreste/Library/Android/sdk
# a11y_export identifies the isolated accessibility tree; use the repository Gradle wrapper.
./gradlew --project-dir "$a11y_export" :core:jvmTest :core:testAndroidHostTest \
  :app:shared:testAndroidHostTest :core:allMetadataJar :app:shared:allMetadataJar \
  :app:androidApp:assembleDebug :app:androidApp:assembleDebugAndroidTest \
  :app:androidApp:bundleRelease :app:androidApp:lintRelease \
  :app:shared:compileKotlinIosArm64 :app:shared:compileKotlinIosSimulatorArm64 \
  :core:compileTestKotlinIosArm64 :app:shared:compileTestKotlinIosArm64 --console=plain
```

Final sources: **BUILD SUCCESSFUL**, 3m 46s. Core JVM **145**, core Android-host **145**, shared Android-host **530** test executions; zero failures/errors/skips. Includes two spoken-chart description/translation tests and two accessibility contrast tests. Android release lint: **94 warnings**, zero errors; not warning-free. Existing Kotlin deprecation/redundant-nullability warnings remain. An initial local compilation error in the onboarding constraint receiver was fixed before this verification; it is not an unresolved baseline failure.

```sh
xcrun simctl boot 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4
xcrun simctl bootstatus 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 -b
./gradlew --project-dir "$a11y_export" :core:iosSimulatorArm64Test \
  --device 1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 \
  :app:shared:iosSimulatorArm64Test \
  -PiosTestDevice=1726E7D8-877B-4A6D-ABCD-7292D35D5BD4 --console=plain
```

**BUILD SUCCESSFUL**, 1m 56s. Core **144** + shared **556** native simulator test executions; zero failures/errors/skips. Xcode 26.3; iPhone 16 Pro simulator on iOS 18.6. These are shared native tests, not VoiceOver speech/gesture validation or native extension screenshot tests. Optional private-document hooks do not exercise a user file without explicit input.

```sh
python3 "$a11y_export/scripts/check-kmp-packages.py"
python3 "$a11y_export/scripts/sync-design-colors.py" --check
python3 -m unittest discover -s "$a11y_export/scripts/tests" -p 'test_*.py'
git diff --cached --check
```

Passed: 409 source/test files match existing package/source-set/module rules; authored/generated colors consistent; **19** script tests passed; no patch whitespace errors. These are structural/palette checks, not a full accessibility scanner. No configured ATF/Espresso accessibility scanner, Compose UI-test rule/semantics-assertion library, screenshot-golden task, or detekt/ktlint accessibility task exists. The new tests exercise the actual Compose-exported Android accessibility tree through the existing instrumentation stack rather than adding a new framework.

Working-tree integration (including previous pending UI/extractions):

```sh
./gradlew :app:shared:testAndroidHostTest --tests '*FinancialChartAccessibilityTest' \
  --tests '*AccessibilityContrastTest' :app:androidApp:assembleDebug --console=plain
```

**BUILD SUCCESSFUL**, 59s, four focused tests passed with the working-tree theme/components. This narrower integration check does not claim all unrelated pending changes passed a new full suite.

### Chart accessibility, keyboard and text-size matrix

API 36.1 ARM64 emulator `Sequo_References_API_36_1`, started with the Android CLI. Installed final isolated debug/test APKs with `adb install -r`. Here `adb` denotes `$ANDROID_HOME/platform-tools/adb`.

```sh
adb -s emulator-5554 shell am instrument -w \
  -e class dev.orestegabo.portfolio.investments.ChartAccessibilityTest \
  -e a11ySnapshots true \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

Each invocation checks **12 variants**: three styles × two themes/languages (light English, dark French) × 100%/200% text. It verifies filtered observation count/values, previous/next/latest actions, changed state, keyboard Left/Right, Tab exit and Enter activation of the next button, then absence of chart values/summary in the current accessibility tree after hiding. It does not assert that external accessibility services erase previously received information.

| Window overrides | Approximate dp before insets | Result |
|---|---|---|
| Default 1080×2400, 420 dpi | 411×914 | **OK (1 test)**, 76.770s |
| `wm size 960x1800`, `wm density 480` | 320×600 | **OK (1 test)**, 44.712s |
| `wm size 1800x960`, `wm density 480` | 600×320 | **OK (1 test)**, 21.048s |

36 variant executions across those windows. PixelCopy synthetic captures at `/tmp/inzira-a11y-screens/{phone,small,landscape}/{false,true}.png` show area-chart keyboard focus at 200% text (false=light English; true=dark French). Representative captures were visually inspected: labels/values remain readable and focus is visible; short windows scroll to reveal content/actions. A short-window capture is only the current viewport, not proof that the complete graph fits at once. This is not a golden comparison or an arbitrary-large-value/annual-month-label audit.

TalkBack **16.0.0.777931756** is installed on the emulator. Enabled its existing service, confirmed `Bound services: TalkBack` (spoken/haptic/audible feedback capabilities), then ran:

```sh
adb -s emulator-5554 shell settings put secure enabled_accessibility_services \
  com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
adb -s emulator-5554 shell settings put secure accessibility_enabled 1
adb -s emulator-5554 shell am instrument -w \
  -e class dev.orestegabo.portfolio.investments.ChartAccessibilityTest \
  -e withTalkBack true \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

**OK (1 test)**, 55.310s, all 12 variants. `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` kept the service running; an additional assertion obtained chart accessibility focus, followed by the same action/key/privacy checks. This is a service-backed interaction test, **not a human listening test or proof of ideal TalkBack utterances**. The emulator's Google TTS process had an independent native SIGILL at boot; audio quality/pronunciation was not verified. Restored the original service settings (no enabled services, accessibility disabled) before ordinary gesture tests. No physical device settings were changed.

### Full Android regression suite

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew --project-dir "$a11y_export" \
  :app:shared:connectedAndroidDeviceTest \
  -Pandroid.injected.device.serial=emulator-5554 --console=plain
ANDROID_SERIAL=emulator-5554 ./gradlew --project-dir "$a11y_export" \
  :app:androidApp:connectedDebugAndroidTest \
  -Pandroid.injected.device.serial=emulator-5554 --console=plain
```

Shared Android device suite: **BUILD SUCCESSFUL**, 37s, **524 tests**, zero failures/errors/skips.

Initial complete app run: **BUILD FAILED**, 5m 40s, with `ComposeCaptureLayerTest` navigation-pixel mismatch and the known native `PdfSharingTest` crash. Investigation found the new hardware-key fixture left the display outside touch mode. Added `finally` cleanup restoring its original mode; no production capture code, assertion tolerance or wait threshold changed. Rebuilt the Android test APK (**BUILD SUCCESSFUL**, 3s), then ran the new chart test immediately followed by the unchanged capture-layer test: **OK (2 tests)**, 39.561s.

Reran the **complete, unfiltered** app task after that cleanup: **BUILD FAILED**, 5m 8s. `ChartAccessibilityTest`, `ComposeCaptureLayerTest` and `OnboardingUiTest` passed. The remaining failure was `PdfSharingTest.pdfTargetsReceiveColdAndWarmIntentsAndExportsGrantOnlyReadAccess`, with an instrumentation process crash. 43 cases were scheduled; the report records one failure and four opt-in skips, and the crashed run is not a complete pass. The earlier baseline already had this RenderThread/EGL-surface failure; it is not attributed to a particular Kotlin line without evidence. No private screenshot/capture protection was disabled to make the test pass.

**Overall verification gate: FAILED. No commit or push.** The index is returned to its initially empty state, preserving the accessibility work and all previous pending changes in the working tree. Compilation and targeted successes do not override the failing full suite. The patch must be verified again after the native failure is resolved. Existing real-Firebase/private-document/process-reboot/benchmark fixture hooks are opt-in; they are not counted as exercised successes.

### Explicit limits

Additional device-setting check:

```sh
adb -s emulator-5554 shell settings put global animator_duration_scale 0
adb -s emulator-5554 shell am instrument -w \
  -e class dev.orestegabo.portfolio.investments.OnboardingUiTest \
  -e startupOnly true \
  dev.orestegabo.portfolio.test/androidx.test.runner.AndroidJUnitRunner
```

**OK (1 test)**, 15.382s. The existing synthetic startup test traversed/completed the tour with both unauthenticated and authenticated background sessions while Android animations were disabled; a session response did not unexpectedly dismiss the tour. Restored the previous animator scale **1.0**. This checks functional reduced-motion operation, not flashing frequency or every animated frame.

No physical-phone VoiceOver review, iPad split view, switch/voice control user journey, human speech/rotor review, exhaustive keyboard/overlay focus restoration, complete 200% screen/dialog matrix, native extension Dynamic Type audit, pixel contrast scan, or flashing measurement was completed. Source-level fixes and passing selected tests do not establish full WCAG 2.2 AA compliance.

## Manual acceptance checklist

Before claiming broader accessibility readiness:

1. On a physical Android phone with TalkBack and an iPhone with VoiceOver, visit every destination above; explore by touch, swipe/rotor, activate primary/secondary/destructive actions, and verify names, roles, state and reading order in both supported languages.
2. Verify chart custom actions reach every valid observation; compare values/source notes with the visual chart, then hide numbers and inspect both readout and focus history. Repeat with screenshot protection enabled independently.
3. With hardware keyboard/Full Keyboard Access, traverse forms, menus, sheets and protected overlays; confirm visible focus, no background trap, Back/Escape behavior and focus restoration after dismissal. Check focus is not hidden by IME/system bars.
4. At 200% text and largest platform accessibility sizes, check narrow/landscape screens, iPad split view, all inline error/confirmation dialogs and native extensions. Essential financial values must remain available without relying on truncation.
5. Verify reduced motion before launch and while running; test onboarding, chart refresh, carousels and asynchronous imports. Check no essential status/value depends on an animation, and assess flashing/auto-updating content manually.
6. Inspect actual rendered contrast (including gradients, logos/watermarks, disabled/selected controls, native cards and focus outlines) and color-vision alternatives. Automated palette checks cover only declared cases.
7. Submit invalid/incomplete forms, retry network/import failures, cancel safely, and complete a synthetic import. Errors should be actionable and associated with relevant controls; completion should announce without disclosing masked data.

## Source inventory appendix

The current working tree contains 92 commonMain composable source files, including pre-existing uncommitted component extractions and the previous UI patch. The isolated test export does not include those unrelated changes. Generated icon implementations, financial report/PDF generation, business/data implementations and test fixtures are not counted as screens. Listed files were searched/read for the audit; the appendix is not proof of runtime conformance.

- `App.kt`
- `investments/AboutScreen.kt`
- `investments/AppLanguage.kt`
- `investments/AppMotion.kt`
- `investments/AppVersion.kt`
- `investments/ApplicationAmountSelector.kt`
- `investments/ApplicationBidScreen.kt`
- `investments/BondApplicationForms.kt`
- `investments/BondIssuance.kt`
- `investments/BondMonthReminders.kt`
- `investments/BrandIntroScreen.kt`
- `investments/CalendarComponents.kt`
- `investments/CalendarScreen.kt`
- `investments/CaptureBackdrop.kt`
- `investments/CashLedgerScreen.kt`
- `investments/CompanyPriceChart.kt`
- `investments/ConversationReview.kt`
- `investments/CredentialField.kt`
- `investments/DocumentDetails.kt`
- `investments/DocumentPreview.kt`
- `investments/DocumentThumbnail.kt`
- `investments/DocumentsHub.kt`
- `investments/DocumentsScreen.kt`
- `investments/EmptyState.kt`
- `investments/EncryptedBackupScreen.kt`
- `investments/FormContent.kt`
- `investments/GuestMigrationScreen.kt`
- `investments/GuestPortfolioRepository.kt`
- `investments/GuestPresentation.kt`
- `investments/GuestQuotes.kt`
- `investments/GuestScreens.kt`
- `investments/ImportCompletionReceipt.kt`
- `investments/ImportEmptyState.kt`
- `investments/ImportFlowDiagram.kt`
- `investments/ImportWallpaper.kt`
- `investments/InvestmentActivityTimeline.kt`
- `investments/InvestmentApp.kt`
- `investments/InvestmentChart.kt`
- `investments/InvestmentDetailComponents.kt`
- `investments/InvestmentDetails.kt`
- `investments/InvestmentDetailsBackdrop.kt`
- `investments/InvestmentGoalSettings.kt`
- `investments/InvestmentHistoryScreen.kt`
- `investments/InvestmentLogo.kt`
- `investments/InvestmentPlatformEffects.kt`
- `investments/InvestmentToolsScreen.kt`
- `investments/IssueReportScreen.kt`
- `investments/LegalScreen.kt`
- `investments/LocalizedComponents.kt`
- `investments/NotificationPreferences.kt`
- `investments/Notifications.kt`
- `investments/NumberPrivacy.kt`
- `investments/OnboardingCharts.kt`
- `investments/OnboardingMotion.kt`
- `investments/OnboardingScreen.kt`
- `investments/OnboardingStories.kt`
- `investments/PdfImportPreparationScreen.kt`
- `investments/PdfImportProgressDialog.kt`
- `investments/PdfViewerScreen.kt`
- `investments/PendingConfirmationAction.kt`
- `investments/PendingPurchaseCards.kt`
- `investments/PlanningScreens.kt`
- `investments/PlatformExperience.kt`
- `investments/PlatformPorts.kt`
- `investments/PortfolioDashboard.kt`
- `investments/PortfolioHero.kt`
- `investments/PortfolioInsights.kt`
- `investments/PortfolioLoadingSkeleton.kt`
- `investments/PortfolioPullRefresh.kt`
- `investments/PortfolioPurchaseTiles.kt`
- `investments/PortfolioReportDialog.kt`
- `investments/PresentationComponents.kt`
- `investments/ProcessingChip.kt`
- `investments/ProgressContent.kt`
- `investments/PurchaseForms.kt`
- `investments/PurchaseImportScreens.kt`
- `investments/SecureSignIn.kt`
- `investments/SettingsComponents.kt`
- `investments/SettingsRecordsSheet.kt`
- `investments/SettingsScreen.kt`
- `investments/ShakeGesture.kt`
- `investments/ShareDetailsScreen.kt`
- `investments/ShareHoldingCard.kt`
- `investments/ShareOrderScreen.kt`
- `investments/SignInActivityScreen.kt`
- `investments/SignInScreen.kt`
- `investments/StatementScreens.kt`
- `investments/StocksScreen.kt`
- `investments/StructBrand.kt`
- `investments/UnlockScreen.kt`
- `investments/YearlyCouponComponents.kt`
- `investments/YearlyCouponsScreen.kt`
