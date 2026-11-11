import Foundation

enum SharedPdfError: Error {
    case notPDF, tooLarge, waiting, unavailable
    func message(french: Bool) -> String {
        switch self {
        case .notPDF: return french ? "Choisissez un fichier PDF." : "Choose a PDF file."
        case .tooLarge: return french ? "Choisissez un PDF de 10 Mo maximum." : "Choose a PDF of up to 10 MB."
        case .waiting: return french ? "Ouvrez Struct Capital pour traiter le PDF en attente." : "Open Struct Capital to finish the waiting PDF first."
        case .unavailable: return french ? "Impossible de préparer ce PDF. Réessayez." : "Could not prepare this PDF. Try again."
        }
    }
}

struct SharedPdfMetadata: Codable {
    let delivery: String
    let fileName: String
    let createdAt: Date
}

enum SharedPdfInbox {
    static let maximumBytes = 10 * 1024 * 1024
    static var directory: URL? {
        let group = Bundle.main.object(forInfoDictionaryKey: "GaboWidgetAppGroup") as? String ?? "group.dev.orestegabo.portfolio.whatsnext"
        return FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)?.appendingPathComponent("pdf-import-inbox", isDirectory: true)
    }
    static func validate(_ data: Data) throws {
        guard data.count <= maximumBytes else { throw SharedPdfError.tooLarge }
        guard data.starts(with: Data("%PDF-".utf8)) else { throw SharedPdfError.notPDF }
    }
    private static func coordinate<T>(_ directory: URL, _ body: (URL) throws -> T) throws -> T {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var error: NSError?
        var result: Result<T,Error>?
        NSFileCoordinator().coordinate(writingItemAt: directory, options: .forMerging, error: &error) { folder in
            result = Result { try body(folder) }
        }
        if let error { throw error }
        guard let result else { throw SharedPdfError.unavailable }
        return try result.get()
    }
    private static func metadata(_ folder: URL, now: Date) -> SharedPdfMetadata? {
        let metadataURL = folder.appendingPathComponent("pending.json")
        guard let data = try? Data(contentsOf: metadataURL),
              let value = try? JSONDecoder().decode(SharedPdfMetadata.self, from: data),
              now.timeIntervalSince(value.createdAt) < 86400 else {
            try? FileManager.default.removeItem(at: metadataURL)
            try? FileManager.default.removeItem(at: folder.appendingPathComponent("pending.pdf"))
            return nil
        }
        return value
    }
    static func stage(_ data: Data, fileName: String, directory: URL, now: Date = Date()) throws {
        try validate(data)
        try coordinate(directory) { folder in
            guard metadata(folder, now: now) == nil else { throw SharedPdfError.waiting }
            let value = SharedPdfMetadata(delivery: UUID().uuidString,
                fileName: String((fileName as NSString).lastPathComponent.prefix(180)), createdAt: now)
            try data.write(to: folder.appendingPathComponent("pending.pdf"), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            try JSONEncoder().encode(value).write(to: folder.appendingPathComponent("pending.json"), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        }
    }
    static func pending(directory: URL, now: Date = Date()) throws -> SharedPdfMetadata? {
        try coordinate(directory) { metadata($0, now: now) }
    }
    static func remove(delivery: String, directory: URL) throws {
        try coordinate(directory) { folder in
            guard metadata(folder, now: Date())?.delivery == delivery else { return }
            try? FileManager.default.removeItem(at: folder.appendingPathComponent("pending.pdf"))
            try? FileManager.default.removeItem(at: folder.appendingPathComponent("pending.json"))
        }
    }
}
