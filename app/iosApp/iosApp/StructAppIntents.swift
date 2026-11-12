import AppIntents
import Shared

struct ImportPdfIntent: AppIntent {
    static var title: LocalizedStringResource = "Import PDF"
    static var openAppWhenRun = true
    static var authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication
    @MainActor func perform() async throws -> some IntentResult {
        _ = NativeAppActions.shared.request(action: "import-pdf")
        return .result()
    }
}

struct AddPurchaseIntent: AppIntent {
    static var title: LocalizedStringResource = "Add purchase"
    static var openAppWhenRun = true
    static var authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication
    @MainActor func perform() async throws -> some IntentResult {
        _ = NativeAppActions.shared.request(action: "add-purchase")
        return .result()
    }
}

struct OpenCalendarIntent: AppIntent {
    static var title: LocalizedStringResource = "Open calendar"
    static var openAppWhenRun = true
    static var authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication
    @MainActor func perform() async throws -> some IntentResult {
        _ = NativeAppActions.shared.request(action: "calendar")
        return .result()
    }
}

struct StructAppShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: ImportPdfIntent(), phrases: ["Import a PDF in \(.applicationName)"],
                    shortTitle: "Import PDF", systemImageName: "doc.badge.plus")
        AppShortcut(intent: AddPurchaseIntent(), phrases: ["Add a purchase in \(.applicationName)"],
                    shortTitle: "Add purchase", systemImageName: "plus.circle")
        AppShortcut(intent: OpenCalendarIntent(), phrases: ["Open the calendar in \(.applicationName)"],
                    shortTitle: "Open calendar", systemImageName: "calendar")
    }
}
