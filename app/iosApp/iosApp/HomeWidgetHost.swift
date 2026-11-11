import Foundation
import UIKit
import Shared

@MainActor
enum HomeWidgetHost {
    static func install() {
        guard Bundle.main.object(forInfoDictionaryKey: "GaboWidgetsEnabled") as? String == "YES" else { return }
        HomeWidgetBridge.shared.install(save: { raw in
            _ = NextWidgetCache.save(raw)
        }, pin: { _ in
            showInstructions()
        })
    }
    private static func showInstructions() {
        let french = AppLanguage.shared.code == "fr"
        let message = french
            ? "Maintenez le doigt sur l’écran d’accueil, touchez Modifier puis Ajouter un widget. Recherchez Struct Capital et choisissez le format petit ou moyen."
            : "Touch and hold your Home Screen, tap Edit, then Add Widget. Search for Struct Capital and choose the small or medium size."
        let alert = UIAlertController(title: french ? "Ajouter le widget À venir" : "Add What’s next", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: french ? "Compris" : "Got it", style: .default))
        var presenter = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows).first { $0.isKeyWindow }?.rootViewController
        while let presented = presenter?.presentedViewController { presenter = presented }
        presenter?.present(alert, animated: true)
    }
}
