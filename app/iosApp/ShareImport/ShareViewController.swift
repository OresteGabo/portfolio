import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {
    private let titleLabel = UILabel()
    private let detailLabel = UILabel()
    private let action = UIButton(type: .system)
    private let cancel = UIButton(type: .system)
    private var pdf: Data?
    private var fileName = "Shared PDF.pdf"
    private var saved = false
    private var french: Bool { Locale.current.language.languageCode?.identifier == "fr" }
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        titleLabel.text = french ? "Importer dans Struct Capital" : "Import into Struct Capital"
        titleLabel.font = .preferredFont(forTextStyle: .title2);titleLabel.numberOfLines = 0
        detailLabel.font = .preferredFont(forTextStyle: .body);detailLabel.numberOfLines = 0
        detailLabel.text = french ? "Préparation du PDF…" : "Preparing PDF…"
        var configuration = UIButton.Configuration.filled()
        configuration.baseBackgroundColor = UIColor(red: 0.43,green: 0.37,blue: 0.06,alpha: 1)
        action.configuration = configuration
        action.setTitle(french ? "Préparer l’import" : "Prepare import", for: .normal)
        action.isEnabled = false
        action.addTarget(self,action: #selector(save),for: .touchUpInside)
        cancel.setTitle(french ? "Annuler" : "Cancel",for: .normal)
        cancel.addTarget(self,action: #selector(finish),for: .touchUpInside)
        let stack = UIStackView(arrangedSubviews: [titleLabel,detailLabel,action,cancel])
        stack.axis = .vertical;stack.spacing = 20;stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo:view.safeAreaLayoutGuide.leadingAnchor,constant:24),
            stack.trailingAnchor.constraint(equalTo:view.safeAreaLayoutGuide.trailingAnchor,constant:-24),
            stack.topAnchor.constraint(equalTo:view.safeAreaLayoutGuide.topAnchor,constant:24)])
        loadPDF()
    }
    private func loadPDF() {
        // Safari may attach the source URL alongside the PDF; accept one PDF and ignore ancillary items.
        let attachments = (extensionContext?.inputItems as? [NSExtensionItem] ?? []).flatMap { $0.attachments ?? [] }
            .filter { $0.hasItemConformingToTypeIdentifier(UTType.pdf.identifier) }
        guard attachments.count == 1, let provider = attachments.first else {
            detailLabel.text = SharedPdfError.notPDF.message(french:french);return
        }
        provider.loadFileRepresentation(forTypeIdentifier: UTType.pdf.identifier) { [weak self] url, _ in
            guard let self else { return }
            do {
                guard let url else { throw SharedPdfError.unavailable }
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                if let size = try url.resourceValues(forKeys:[.fileSizeKey]).fileSize, size > SharedPdfInbox.maximumBytes { throw SharedPdfError.tooLarge }
                let handle = try FileHandle(forReadingFrom:url);defer { try? handle.close() }
                let bytes = try handle.read(upToCount:SharedPdfInbox.maximumBytes + 1) ?? Data()
                try SharedPdfInbox.validate(bytes)
                DispatchQueue.main.async {
                    self.pdf = bytes;self.fileName = provider.suggestedName ?? url.lastPathComponent
                    self.detailLabel.text = self.fileName + "\n\n" + (self.french
                        ? "Préparer cet import n’envoie pas le PDF. Ouvrez ensuite Struct Capital pour vérifier les données avant de les enregistrer."
                        : "Preparing this import does not upload the PDF. Open Struct Capital next to review its details before saving any records.")
                    self.action.isEnabled = true
                }
            } catch {
                let failure = (error as? SharedPdfError) ?? .unavailable
                DispatchQueue.main.async { self.detailLabel.text = failure.message(french:self.french) }
            }
        }
    }
    @objc private func save() {
        if saved { finish();return }
        guard let pdf, let directory = SharedPdfInbox.directory else { detailLabel.text = SharedPdfError.unavailable.message(french:french);return }
        action.isEnabled = false;cancel.isEnabled = false
        let name = fileName
        DispatchQueue.global(qos:.userInitiated).async { [weak self] in
            do {
                try SharedPdfInbox.stage(pdf,fileName:name,directory:directory)
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.saved = true;self.pdf = nil;self.cancel.isHidden = true
                    self.detailLabel.text = self.french ? "PDF préparé. Ouvrez Struct Capital pour vérifier et importer les données." : "PDF prepared. Open Struct Capital to review and import it."
                    self.action.setTitle(self.french ? "Terminé" : "Done",for:.normal);self.action.isEnabled = true
                }
            } catch {
                let failure = (error as? SharedPdfError) ?? .unavailable
                DispatchQueue.main.async {
                    self?.detailLabel.text = failure.message(french:self?.french ?? false)
                    self?.action.isEnabled = true;self?.cancel.isEnabled = true
                }
            }
        }
    }
    @objc private func finish() { extensionContext?.completeRequest(returningItems:nil,completionHandler:nil) }
}
