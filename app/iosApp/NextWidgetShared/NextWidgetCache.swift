import Foundation
import WidgetKit

enum NextWidgetCache {
    static let kind = "StructCapitalWhatsNext"
    static var groupIdentifier: String {
        Bundle.main.object(forInfoDictionaryKey: "GaboWidgetAppGroup") as? String ?? "group.dev.orestegabo.portfolio.whatsnext"
    }
    private static var fileURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupIdentifier)?
            .appendingPathComponent("next-widget.json", isDirectory: false)
    }
    static func load() -> NextWidgetEnvelope? {
        guard let file = fileURL, let data = try? Data(contentsOf: file) else { return nil }
        return try? JSONDecoder().decode(NextWidgetEnvelope.self, from: data)
    }
    @discardableResult static func save(_ raw: String?) -> Bool {
        guard let file = fileURL else { return false }
        do {
            if let raw {
                let snapshot = try JSONDecoder().decode(NextWidgetSnapshot.self, from: Data(raw.utf8))
                if load()?.snapshot == snapshot { return true }
                let envelope = NextWidgetEnvelope(snapshot: snapshot, savedAt: Date())
                // Re-encode the typed whitelist, never an arbitrary API response or account session.
                try JSONEncoder().encode(envelope).write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            } else if FileManager.default.fileExists(atPath: file.path) {
                try FileManager.default.removeItem(at: file)
            }
            WidgetCenter.shared.reloadTimelines(ofKind: kind)
            return true
        } catch { return false }
    }
}
