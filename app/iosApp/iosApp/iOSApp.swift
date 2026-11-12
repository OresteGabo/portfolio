import SwiftUI

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(PricePushDelegate.self) var pushDelegate
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    if url.scheme == "structcapital", url.host == "notifications" { NativePushBridge.shared.openInbox() }
                    else if url.scheme == "structcapital", url.host == "action", let action = url.pathComponents.last {
                        _ = NativeAppActions.shared.request(action: action)
                    }
                    else if url.scheme == "structcapital", url.host == "calendar" {
                        if let date = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "date" })?.value {
                            HomeWidgetBridge.shared.openCalendar(date: date)
                        }
                    }
                    else { IosPdfReceiver.shared.receive(url: url.absoluteString) }
                }
        }
    }
}

import UIKit
import UserNotifications
import Shared

final class PricePushDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    private var enabled: Bool { (Bundle.main.object(forInfoDictionaryKey: "GaboPushEnabled") as? String) == "YES" }
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        StructAppShortcuts.updateAppShortcutParameters()
        AppIconActions.install()
        HomeWidgetHost.install()
        CalendarExportHost.shared.install()
        SharedPdfImportHost.shared.start()
        let defaults = UserDefaults.standard
        let id = defaults.string(forKey: "portfolio.push.deviceId") ?? UUID().uuidString.lowercased()
        defaults.set(id, forKey: "portfolio.push.deviceId")
        NativePushBridge.shared.identify(deviceId: id, platform: "ios")
        guard enabled else { return true }
        BondLiveActivityMonitor.shared.start()
        NativePushBridge.shared.installAccountRefresh { [weak self] in self?.refreshPermission() }
        UNUserNotificationCenter.current().delegate = self
        refreshPermission()
        NotificationCenter.default.addObserver(self, selector: #selector(refreshPermission), name: UIApplication.didBecomeActiveNotification, object: nil)
        return true
    }
    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = AppActionSceneDelegate.self
        return configuration
    }
    @objc private func refreshPermission() {
        let entry = UserDefaults.standard
        guard entry.bool(forKey: "portfolio.onboardingCompleted"),
              !(entry.string(forKey: "portfolio.sessionChoice")?.hasPrefix("guest-") ?? false) else { return }
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            DispatchQueue.main.async {
                let allowed = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
                NativePushBridge.shared.install(available: true, allowed: allowed, requestPermission: { [weak self] in self?.requestPermission() })
                if allowed { UIApplication.shared.registerForRemoteNotifications() }
            }
        }
    }
    private func requestPermission() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            if settings.authorizationStatus != .notDetermined {
                DispatchQueue.main.async { if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) } }
            } else {
                UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in self.refreshPermission() }
            }
        }
    }
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken token: Data) {
        let defaults = UserDefaults.standard
        let id = defaults.string(forKey: "portfolio.push.deviceId") ?? UUID().uuidString.lowercased()
        defaults.set(id, forKey: "portfolio.push.deviceId")
        let environment = Bundle.main.object(forInfoDictionaryKey: "GaboPushEnvironment") as? String ?? "production"
        NativePushBridge.shared.register(deviceId: id, platform: "ios", token: token.map { String(format: "%02x", $0) }.joined(), environment: environment)
    }
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // Registration failure never interrupts sign-in or prints raw exceptions.
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        DispatchQueue.main.async { NativePushBridge.shared.priceUpdate() }
        completionHandler([])
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        DispatchQueue.main.async { NativePushBridge.shared.openInbox() }
        completionHandler()
    }
}
