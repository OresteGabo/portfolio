import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    @AppStorage("portfolio.theme") private var theme = "system"

    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .preferredColorScheme(theme == "dark" ? .dark : theme == "light" ? .light : nil)
    }
}