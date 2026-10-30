# Source inventory

Audit date: 30 October 2026. Paths refer to their repository root. This is a structural inventory, not a claim that every line received equal review. Critical financial, authentication, persistence, parser and reporting paths were reviewed in detail. All source families were scanned before choosing the implementation scope.

Binary branding, photographs, fonts, generated build products, local secrets, caches and vendored PDF/OCR internals were excluded from line-by-line review. Bundled runtimes, their limits, licences and the scripts that copy/generate them were inspected as dependency boundaries. Existing user changes were preserved.

## KMP repository

Baseline commit: `5952c80`. First-party/configuration source entries: **238**. Inventory reflects the working tree after the foundational extraction.

| Path | Lines |
|---|---:|
| `app/androidApp/build.gradle.kts` | 77 |
| `app/androidApp/src/androidTest/kotlin/dev/orestegabo/portfolio/investments/CaptureProtectionTest.kt` | 101 |
| `app/androidApp/src/androidTest/kotlin/dev/orestegabo/portfolio/investments/ComposeCaptureLayerTest.kt` | 318 |
| `app/androidApp/src/androidTest/kotlin/dev/orestegabo/portfolio/investments/DeviceApiTest.kt` | 41 |
| `app/androidApp/src/androidTest/kotlin/dev/orestegabo/portfolio/investments/DocumentsLibraryTest.kt` | 184 |
| `app/androidApp/src/androidTest/kotlin/dev/orestegabo/portfolio/investments/ImportConversationUiTest.kt` | 265 |
| `app/androidApp/src/androidTest/kotlin/dev/orestegabo/portfolio/investments/LocalEngineTest.kt` | 44 |
| `app/androidApp/src/androidTest/kotlin/dev/orestegabo/portfolio/investments/LoginFormTest.kt` | 187 |
| `app/androidApp/src/androidTest/kotlin/dev/orestegabo/portfolio/investments/OnboardingUiTest.kt` | 332 |
| `app/androidApp/src/androidTest/kotlin/dev/orestegabo/portfolio/investments/PdfDocumentRenderingTest.kt` | 54 |
| `app/androidApp/src/androidTest/kotlin/dev/orestegabo/portfolio/investments/PdfReaderTestFixtures.kt` | 47 |
| `app/androidApp/src/androidTest/kotlin/dev/orestegabo/portfolio/investments/PdfSharingTest.kt` | 114 |
| `app/androidApp/src/androidTest/kotlin/dev/orestegabo/portfolio/investments/SettingsTargetEditorTest.kt` | 180 |
| `app/androidApp/src/debug/AndroidManifest.xml` | 5 |
| `app/androidApp/src/debug/kotlin/dev/orestegabo/portfolio/ExperimentalCaptureLayer.kt` | 76 |
| `app/androidApp/src/debug/kotlin/dev/orestegabo/portfolio/investments/EngineHostActivity.kt` | 18 |
| `app/androidApp/src/main/AndroidManifest.xml` | 48 |
| `app/androidApp/src/main/kotlin/dev/orestegabo/portfolio/IncomingPdfIntent.kt` | 57 |
| `app/androidApp/src/main/kotlin/dev/orestegabo/portfolio/MainActivity.kt` | 62 |
| `app/androidApp/src/main/kotlin/dev/orestegabo/portfolio/PriceMessagingService.kt` | 36 |
| `app/androidApp/src/main/res/values/strings.xml` | 3 |
| `app/androidApp/src/main/res/xml/pdf_share_paths.xml` | 4 |
| `app/androidApp/src/release/kotlin/dev/orestegabo/portfolio/ExperimentalCaptureLayer.kt` | 4 |
| `app/iosApp/Configuration/Config.xcconfig` | 22 |
| `app/iosApp/iosApp/CaptureShieldViewController.swift` | 134 |
| `app/iosApp/iosApp/ContentView.swift` | 24 |
| `app/iosApp/iosApp/Info.plist` | 34 |
| `app/iosApp/iosApp/Portfolio.entitlements` | 10 |
| `app/iosApp/iosApp/Push.entitlements` | 12 |
| `app/iosApp/iosApp/iOSApp.swift` | 67 |
| `app/shared/build.gradle.kts` | 78 |
| `app/shared/src/androidHostTest/kotlin/dev/orestegabo/portfolio/investments/ReportPreviewTest.kt` | 27 |
| `app/shared/src/androidMain/assets/engine/index.html` | 4 |
| `app/shared/src/androidMain/kotlin/dev/orestegabo/portfolio/investments/AppLanguage.android.kt` | 7 |
| `app/shared/src/androidMain/kotlin/dev/orestegabo/portfolio/investments/AppVersion.android.kt` | 20 |
| `app/shared/src/androidMain/kotlin/dev/orestegabo/portfolio/investments/CaptureBackdrop.android.kt` | 108 |
| `app/shared/src/androidMain/kotlin/dev/orestegabo/portfolio/investments/CaptureProtection.android.kt` | 22 |
| `app/shared/src/androidMain/kotlin/dev/orestegabo/portfolio/investments/CredentialInput.android.kt` | 5 |
| `app/shared/src/androidMain/kotlin/dev/orestegabo/portfolio/investments/DevelopmentServer.android.kt` | 4 |
| `app/shared/src/androidMain/kotlin/dev/orestegabo/portfolio/investments/ImportDigest.android.kt` | 4 |
| `app/shared/src/androidMain/kotlin/dev/orestegabo/portfolio/investments/LocalInvestmentEngine.android.kt` | 91 |
| `app/shared/src/androidMain/kotlin/dev/orestegabo/portfolio/investments/PdfDocument.android.kt` | 69 |
| `app/shared/src/androidMain/kotlin/dev/orestegabo/portfolio/investments/PlatformExperience.android.kt` | 38 |
| `app/shared/src/androidMain/kotlin/dev/orestegabo/portfolio/investments/PlatformPorts.android.kt` | 192 |
| `app/shared/src/androidMain/kotlin/dev/orestegabo/portfolio/investments/SecureSignIn.android.kt` | 215 |
| `app/shared/src/androidMain/kotlin/dev/orestegabo/portfolio/investments/ShakeGesture.android.kt` | 32 |
| `app/shared/src/commonMain/composeResources/drawable/compose-multiplatform.xml` | 44 |
| `app/shared/src/commonMain/composeResources/drawable/struct_mark_dark.xml` | 8 |
| `app/shared/src/commonMain/composeResources/drawable/struct_mark_light.xml` | 8 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/App.kt` | 59 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/AboutScreen.kt` | 143 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/AnnualReport.kt` | 142 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/AppLanguage.kt` | 73 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/AppMotion.kt` | 62 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/AppVersion.kt` | 9 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/BondIssuance.kt` | 155 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/CalendarEvents.kt` | 111 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/CalendarPurchaseStatus.kt` | 62 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/CalendarScreen.kt` | 419 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/CaptureBackdrop.kt` | 79 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/CaptureProtection.kt` | 8 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/ConversationReview.kt` | 105 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/CredentialInput.kt` | 12 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/DevelopmentServer.kt` | 7 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/DocumentInvestmentLogo.kt` | 38 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/DocumentPreview.kt` | 47 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/DocumentsHub.kt` | 224 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/DocumentsScreen.kt` | 320 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/EmptyState.kt` | 147 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/EngineDocumentParser.kt` | 12 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/Formatting.kt` | 32 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/FrenchStrings.kt` | 1381 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/ImportCompletionReceipt.kt` | 86 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/ImportDuplicates.kt` | 57 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/ImportEmptyState.kt` | 38 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/ImportFlowDiagram.kt` | 134 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/ImportJournal.kt` | 113 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/ImportWallpaper.kt` | 81 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/IncomingPdf.kt` | 40 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentApp.kt` | 471 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentChart.kt` | 179 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentGoalSettings.kt` | 90 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentHistoryScreen.kt` | 56 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentLogo.kt` | 45 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/LegalContent.generated.kt` | 60 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/LegalScreen.kt` | 185 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/LibraryScreens.kt` | 219 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/LocalizedComponents.kt` | 164 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/NotificationPreferences.kt` | 49 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/Notifications.kt` | 188 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/NumberPrivacy.kt` | 85 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/OcrLayout.kt` | 22 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/OnboardingScreen.kt` | 180 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PdfImportProgressDialog.kt` | 33 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PdfPolicy.kt` | 14 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PdfViewerScreen.kt` | 195 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PendingConfirmationAction.kt` | 32 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PendingPurchases.kt` | 70 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PlanningScreens.kt` | 181 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PlatformExperience.kt` | 30 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PlatformPorts.kt` | 59 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PortfolioDashboard.kt` | 482 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PortfolioModel.kt` | 1129 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PortfolioPullRefresh.kt` | 65 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PortfolioPurchaseTiles.kt` | 153 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PortfolioReport.kt` | 167 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PurchaseForms.kt` | 141 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PurchaseImportScreens.kt` | 113 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PurchasePdfImport.kt` | 74 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PurchaseReviewState.kt` | 17 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/ReportPdf.kt` | 298 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/SecureSignIn.kt` | 19 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/SettingsScreen.kt` | 395 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/ShakeGesture.kt` | 61 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/ShareHoldingCard.kt` | 76 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/SharedAssets.kt` | 11 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/SignInScreen.kt` | 165 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/StatementScreens.kt` | 80 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/StockPriceHistory.kt` | 59 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/StructBrand.kt` | 24 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/UnlockScreen.kt` | 114 |
| `app/shared/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/UserFacingErrors.kt` | 143 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/AnnualReportTest.kt` | 80 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/AppLanguageTest.kt` | 73 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/BondIssuanceTest.kt` | 75 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/CalendarEventsTest.kt` | 122 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/CalendarPurchaseStatusTest.kt` | 71 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/DevelopmentServerTest.kt` | 16 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/DocumentInvestmentLogoTest.kt` | 29 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/DocumentParserAdapterTest.kt` | 64 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/DocumentPreviewTest.kt` | 135 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/DocumentUploadTest.kt` | 98 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/ImportConversationModelTest.kt` | 198 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/ImportDuplicatesTest.kt` | 260 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/ImportJournalTest.kt` | 65 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/IncomingPdfTest.kt` | 61 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/IssuanceConfirmationUploadTest.kt` | 162 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/LegalContentTest.kt` | 44 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/NotificationPreferenceSwitchTest.kt` | 196 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/NumberPrivacyTest.kt` | 28 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/OcrLayoutTest.kt` | 22 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/OnboardingTest.kt` | 15 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/PasswordSharingTest.kt` | 11 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/PdfImportProgressTest.kt` | 93 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/PdfRenderFixture.kt` | 23 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/PendingPurchasesTest.kt` | 118 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/PortfolioModelTest.kt` | 618 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/PortfolioReportTest.kt` | 68 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/PurchasePdfImportTest.kt` | 88 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/SecureSessionTest.kt` | 372 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/ShakeDetectorTest.kt` | 44 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/SharedRulesTest.kt` | 23 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/StatementPurchasesTest.kt` | 70 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/StockNotificationsTest.kt` | 86 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/StockPriceHistoryTest.kt` | 70 |
| `app/shared/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/UserFacingErrorsTest.kt` | 92 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/MainViewController.kt` | 10 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/AppLanguage.ios.kt` | 15 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/AppVersion.ios.kt` | 13 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/CaptureBackdrop.ios.kt` | 82 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/CaptureProtection.ios.kt` | 22 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/CredentialInput.ios.kt` | 15 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/DevelopmentServer.ios.kt` | 10 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/ImportDigest.ios.kt` | 14 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/IosPdfReceiver.kt` | 58 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/LocalInvestmentEngine.ios.kt` | 128 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/PasswordSave.ios.kt` | 60 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/PdfDocument.ios.kt` | 73 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/PlatformExperience.ios.kt` | 28 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/PlatformPorts.ios.kt` | 184 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/SecureSignIn.ios.kt` | 162 |
| `app/shared/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/ShakeGesture.ios.kt` | 26 |
| `app/shared/src/iosTest/kotlin/dev/orestegabo/portfolio/investments/CaptureBackdropLayerTest.kt` | 37 |
| `app/shared/src/iosTest/kotlin/dev/orestegabo/portfolio/investments/IosCaptureProtectionTest.kt` | 66 |
| `app/shared/src/iosTest/kotlin/dev/orestegabo/portfolio/investments/IosInvestmentEngineTest.kt` | 172 |
| `app/shared/src/iosTest/kotlin/dev/orestegabo/portfolio/investments/IosPdfReceiverTest.kt` | 42 |
| `app/shared/src/iosTest/kotlin/dev/orestegabo/portfolio/investments/PdfDocumentRenderingTest.kt` | 73 |
| `branding/struct-capital/calendar/calendar-assets/scripts/generate.py` | 205 |
| `branding/struct-capital/calendar/calendar-assets/scripts/outline-text.swift` | 38 |
| `branding/struct-capital/calendar/scripts/generate.py` | 205 |
| `branding/struct-capital/calendar/scripts/outline-text.swift` | 38 |
| `branding/struct-capital/data-processing/data-processing-assets/scripts/generate.py` | 279 |
| `branding/struct-capital/data-processing/data-processing-assets/scripts/verify.swift` | 26 |
| `branding/struct-capital/data-processing/scripts/generate.py` | 279 |
| `branding/struct-capital/data-processing/scripts/verify.swift` | 26 |
| `build.gradle.kts` | 11 |
| `core/build.gradle.kts` | 55 |
| `core/src/androidMain/kotlin/dev/orestegabo/portfolio/investments/HttpEngine.android.kt` | 10 |
| `core/src/commonMain/kotlin/dev/orestegabo/portfolio/GreetingUtil.kt` | 4 |
| `core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/AppDomains.kt` | 4 |
| `core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/BondMath.kt` | 77 |
| `core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentDates.kt` | 17 |
| `core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentDocumentParser.kt` | 12 |
| `core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/JsonRecord.kt` | 57 |
| `core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/MonthlyInvestmentGoal.kt` | 44 |
| `core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PortfolioApi.kt` | 127 |
| `core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PortfolioSnapshot.kt` | 63 |
| `core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/PortfolioValuation.kt` | 36 |
| `core/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/BondMathTest.kt` | 66 |
| `core/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/JsonRecordTest.kt` | 20 |
| `core/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/MonthlyInvestmentGoalTest.kt` | 37 |
| `core/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/PortfolioApiTest.kt` | 103 |
| `core/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/PortfolioSnapshotTest.kt` | 76 |
| `core/src/commonTest/kotlin/dev/orestegabo/portfolio/investments/PortfolioValuationTest.kt` | 75 |
| `core/src/iosMain/kotlin/dev/orestegabo/portfolio/investments/HttpEngine.ios.kt` | 15 |
| `core/src/jvmMain/kotlin/dev/orestegabo/portfolio/investments/HttpEngine.jvm.kt` | 5 |
| `core/src/jvmTest/kotlin/dev/orestegabo/portfolio/investments/LocalSpringApiCheck.kt` | 40 |
| `gradle.properties` | 12 |
| `gradle/gradle-daemon-jvm.properties` | 13 |
| `gradle/libs.versions.toml` | 61 |
| `gradle/wrapper/gradle-wrapper.properties` | 10 |
| `scripts/bundle-shared-logic.mjs` | 30 |
| `scripts/copy-education.mjs` | 29 |
| `scripts/extract-education.py` | 60 |
| `scripts/generate-app-icons.swift` | 29 |
| `scripts/generate_legal.py` | 31 |
| `scripts/sync-onboarding-art.py` | 49 |
| `scripts/sync-web-logic.mjs` | 35 |
| `server/build.gradle.kts` | 19 |
| `server/src/main/kotlin/dev/orestegabo/portfolio/Application.kt` | 20 |
| `server/src/main/resources/logback.xml` | 12 |
| `server/src/test/kotlin/dev/orestegabo/portfolio/ApplicationTest.kt` | 20 |
| `settings.gradle.kts` | 33 |
| `tests/bk-statement.test.mjs` | 22 |
| `tests/bond-issuance-import.test.mjs` | 56 |
| `tests/bond-pdf-import.test.mjs` | 93 |
| `tests/signed-pdf-import.test.mjs` | 55 |
| `tests/web-logic.test.mjs` | 58 |
| `web-logic/source/bk-statement-parser.ts` | 52 |
| `web-logic/source/calculations.ts` | 493 |
| `web-logic/source/equity-pdf-parser.ts` | 70 |
| `web-logic/source/equity-validation.ts` | 35 |
| `web-logic/source/pdf-file.ts` | 57 |
| `web-logic/source/pdf-import-diagnostics.ts` | 56 |
| `web-logic/source/pdf-import-errors.ts` | 56 |
| `web-logic/source/pdf-import-parser.ts` | 172 |
| `web-logic/source/pdf-import.ts` | 197 |
| `web-logic/source/types.ts` | 185 |

## Companion Spring repository (inspected read-only)

Baseline commit: `348ed14`. First-party/configuration source entries: **42**. Inventory reflects the working tree after the foundational extraction.

| Path | Lines |
|---|---:|
| `.github/workflows/deploy.yml` | 125 |
| `build.gradle.kts` | 65 |
| `compose.yaml` | 18 |
| `gradle/wrapper/gradle-wrapper.properties` | 9 |
| `neon.ts` | 20 |
| `scripts/check-login.py` | 49 |
| `scripts/prepare-neon-env.py` | 64 |
| `settings.gradle.kts` | 1 |
| `src/main/kotlin/dev/orestegabo/portfolio/ApiSupport.kt` | 60 |
| `src/main/kotlin/dev/orestegabo/portfolio/AuthController.kt` | 72 |
| `src/main/kotlin/dev/orestegabo/portfolio/AuthService.kt` | 135 |
| `src/main/kotlin/dev/orestegabo/portfolio/BondIssuance.kt` | 33 |
| `src/main/kotlin/dev/orestegabo/portfolio/DocumentService.kt` | 54 |
| `src/main/kotlin/dev/orestegabo/portfolio/GaboPortfolioApiApplication.kt` | 11 |
| `src/main/kotlin/dev/orestegabo/portfolio/LifecycleNotifications.kt` | 143 |
| `src/main/kotlin/dev/orestegabo/portfolio/MarketController.kt` | 51 |
| `src/main/kotlin/dev/orestegabo/portfolio/MarketHistory.kt` | 61 |
| `src/main/kotlin/dev/orestegabo/portfolio/MarketService.kt` | 145 |
| `src/main/kotlin/dev/orestegabo/portfolio/PendingIssuanceService.kt` | 50 |
| `src/main/kotlin/dev/orestegabo/portfolio/PortfolioController.kt` | 55 |
| `src/main/kotlin/dev/orestegabo/portfolio/PortfolioService.kt` | 102 |
| `src/main/kotlin/dev/orestegabo/portfolio/PriceNotifications.kt` | 95 |
| `src/main/kotlin/dev/orestegabo/portfolio/PricePushSender.kt` | 87 |
| `src/main/kotlin/dev/orestegabo/portfolio/PurchaseValidation.kt` | 85 |
| `src/main/kotlin/dev/orestegabo/portfolio/SecurityConfig.kt` | 113 |
| `src/main/kotlin/dev/orestegabo/portfolio/StatementService.kt` | 46 |
| `src/main/kotlin/dev/orestegabo/portfolio/WebSeedImport.kt` | 77 |
| `src/main/resources/application-cloudrun.properties` | 19 |
| `src/main/resources/application-local.properties` | 2 |
| `src/main/resources/application.properties` | 39 |
| `src/main/resources/db/migration/V1__web_portfolio_schema.sql` | 254 |
| `src/main/resources/db/migration/V2__legacy_document_ids.sql` | 3 |
| `src/main/resources/db/migration/V3__bond_issuance_identity.sql` | 5 |
| `src/main/resources/db/migration/V4__shared_bond_issuances.sql` | 10 |
| `src/main/resources/db/migration/V5__price_notifications.sql` | 21 |
| `src/main/resources/db/migration/V6__general_notifications_and_coupon_receipts.sql` | 28 |
| `src/main/resources/db/migration/V7__pending_issuance_terms.sql` | 4 |
| `src/test/kotlin/dev/orestegabo/portfolio/GaboPortfolioApiApplicationTests.kt` | 408 |
| `src/test/kotlin/dev/orestegabo/portfolio/MarketParserTests.kt` | 30 |
| `src/test/kotlin/dev/orestegabo/portfolio/TestGaboPortfolioApiApplication.kt` | 9 |
| `src/test/kotlin/dev/orestegabo/portfolio/TestcontainersConfiguration.kt` | 18 |
| `src/test/kotlin/dev/orestegabo/portfolio/WebCompatibilityTests.kt` | 39 |

## Additional inspected families

- README, architecture, deployment, domain, report, privacy/store-readiness and verification documentation in both repositories.
- Android and iOS host configuration, source-set boundaries, settings/vault/push/file adapters and native capture protection.
- TypeScript financial and broker parsers under `web-logic/source`; generated ESM/portable runtime entry points and both platform loading paths.
- Flyway V1–V7, owner-scoped uniqueness, document digests, session/receipt/notification tables and deployment workflow configuration.
- Existing synthetic fixtures, common/JVM/Native tests and Android instrumentation journeys. See ASSESSMENT.md for tests actually executed during this audit.

No production database, user PDFs, credentials or live deployment state were inspected. The sibling web project was not comprehensively audited; its copied logic and synchronization boundary are documented here.

## Follow-up parser hardening

[DOCUMENT_PARSING.md](../DOCUMENT_PARSING.md) records the subsequent parser inspection and changes. Added `web-logic/source/document-parser-support.ts`, its generated common ESM asset, `tests/document-parser-regression.test.mjs`, `tests/fixtures/bnr-investor-results-synthetic.txt` and fixture provenance notes. The companion DocumentService and existing integration-test class now include the statement-category compatibility fix and regression; earlier statements that backend source was unchanged describe the preceding audit, not this follow-up.

## Follow-up financial and reconciliation work

[Financial calculations](../FINANCIAL_CALCULATIONS.md) and [reconciliation](../RECONCILIATION.md) own the subsequent source changes and evidence. Added the native decimal primitive/actuals, reference and presentation tests, statement matching/model tests, movement fixtures, V8 source/decision tables and an opt-in fenced local PostgreSQL test configuration. Existing domain, parser, service, endpoint and screen names remain the main implementation boundaries. Full isolated PostgreSQL and native/host test execution is recorded in VERIFICATION.md; this source snapshot is not a deployment.
