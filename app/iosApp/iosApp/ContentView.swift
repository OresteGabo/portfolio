import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    @AppStorage("portfolio.screenCaptureAllowed") private var screenCaptureAllowed = false
    func makeUIViewController(context: Self.Context) -> UIViewController {
        CaptureShieldViewController(protectionEnabled: !screenCaptureAllowed)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {
        (uiViewController as? CaptureShieldViewController)?.setProtectionEnabled(!screenCaptureAllowed)
    }
}

struct ContentView: View {
    @AppStorage("portfolio.theme") private var theme = "system"

    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .preferredColorScheme(theme == "dark" ? .dark : theme == "light" ? .light : nil)
    }
}