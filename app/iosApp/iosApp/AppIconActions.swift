import UIKit
import Shared

@MainActor
enum AppIconActions {
    static func install() {
        let fr = AppLanguage.shared.code == "fr"
        UIApplication.shared.shortcutItems = [
            item("import-pdf",fr ? "Importer un PDF" : "Import PDF","doc.badge.plus"),
            item("add-purchase",fr ? "Ajouter un achat" : "Add purchase","plus.circle"),
            item("calendar",fr ? "Ouvrir le calendrier" : "Open calendar","calendar")
        ]
    }
    private static func item(_ action: String, _ title: String, _ symbol: String) -> UIApplicationShortcutItem {
        UIApplicationShortcutItem(type: "structcapital.action.\(action)", localizedTitle: title,
            localizedSubtitle: nil, icon: UIApplicationShortcutIcon(systemImageName: symbol), userInfo: nil)
    }
    static func handle(_ item: UIApplicationShortcutItem) -> Bool {
        guard item.type.hasPrefix("structcapital.action.") else { return false }
        return NativeAppActions.shared.request(action: String(item.type.dropFirst("structcapital.action.".count)))
    }
}

final class AppActionSceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let shortcut = connectionOptions.shortcutItem { _ = AppIconActions.handle(shortcut) }
    }
    func windowScene(_ windowScene: UIWindowScene, performActionFor shortcutItem: UIApplicationShortcutItem, completionHandler: @escaping (Bool) -> Void) {
        completionHandler(AppIconActions.handle(shortcutItem))
    }
    func sceneDidBecomeActive(_ scene: UIScene) { AppIconActions.install() }
}
