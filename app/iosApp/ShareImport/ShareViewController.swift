import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {
    private static let olive = UIColor(red: 109/255, green: 94/255, blue: 15/255, alpha: 1)
    private static let cream = UIColor(red: 242/255, green: 230/255, blue: 167/255, alpha: 1)
    private let canvas = UIColor { $0.userInterfaceStyle == .dark
        ? UIColor(red: 15/255, green: 21/255, blue: 15/255, alpha: 1)
        : UIColor(red: 250/255, green: 248/255, blue: 242/255, alpha: 1) }
    private let accent = UIColor { $0.userInterfaceStyle == .dark ? ShareViewController.cream : ShareViewController.olive }
    private let cardColor = UIColor { $0.userInterfaceStyle == .dark
        ? UIColor(red: 29/255, green: 35/255, blue: 25/255, alpha: 1) : .white }
    private let heroTitle = UILabel()
    private let subtitle = UILabel()
    private let fileLabel = UILabel()
    private let fileDetail = UILabel()
    private let statusLabel = UILabel()
    private let statusIcon = UIImageView()
    private let statusCard = UIView()
    private let fileCard = UIView()
    private let action = UIButton(type: .system)
    private let cancel = UIButton(type: .system)
    private let steps = UIStackView()
    private var stepIcons: [UIImageView] = []
    private var pdf: Data?
    private var fileName = "Shared PDF.pdf"
    private var saved = false
    private var french: Bool { Locale.current.language.languageCode?.identifier == "fr" }
    private func text(_ en: String, _ fr: String) -> String { french ? fr : en }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = canvas
        let scroll = UIScrollView()
        scroll.alwaysBounceVertical = false
        scroll.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scroll)
        let content = UIStackView()
        content.axis = .vertical
        content.spacing = 24
        content.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(content)

        let logo = UIImageView(image: UIImage(named: "ImportBrand"))
        logo.contentMode = .scaleAspectFit
        logo.layer.cornerRadius = 12
        logo.clipsToBounds = true
        logo.isAccessibilityElement = false
        NSLayoutConstraint.activate([logo.widthAnchor.constraint(equalToConstant: 44), logo.heightAnchor.constraint(equalToConstant: 44)])
        let brand = label("Struct Capital", style: .headline)
        let overline = label(text("DOCUMENT IMPORT", "IMPORT DE DOCUMENT"), style: .caption2, color: accent)
        let brandText = column([brand, overline], spacing: 3)
        let header = UIStackView(arrangedSubviews: [logo, brandText])
        header.spacing = 12
        header.alignment = .center
        content.addArrangedSubview(header)

        configure(heroTitle, text: text("Your PDF.\nA clearer portfolio.", "Votre PDF.\nUn portefeuille plus clair."), style: .largeTitle)
        heroTitle.font = UIFontMetrics(forTextStyle: .largeTitle).scaledFont(for: .systemFont(ofSize: 32, weight: .bold))
        heroTitle.accessibilityTraits.insert(.header)
        configure(subtitle, text: text("Prepare your document, then review its details in Struct Capital.",
            "Préparez votre document, puis vérifiez ses données dans Struct Capital."), style: .body, color: .secondaryLabel)
        content.addArrangedSubview(column([heroTitle, subtitle], spacing: 10))

        fileCard.backgroundColor = cardColor
        fileCard.layer.cornerRadius = 22
        fileCard.layer.borderWidth = 1
        let pdfIcon = UIImageView(image: UIImage(systemName: "doc.richtext"))
        pdfIcon.preferredSymbolConfiguration = .init(pointSize: 27, weight: .regular)
        pdfIcon.tintColor = accent
        pdfIcon.contentMode = .scaleAspectFit
        pdfIcon.isAccessibilityElement = false
        let iconTile = UIView()
        iconTile.backgroundColor = accent.withAlphaComponent(0.09)
        iconTile.layer.cornerRadius = 15
        embed(pdfIcon, in: iconTile, inset: 13)
        NSLayoutConstraint.activate([iconTile.widthAnchor.constraint(equalToConstant: 54), iconTile.heightAnchor.constraint(equalToConstant: 62)])
        configure(fileLabel, text: text("Preparing your PDF…", "Préparation de votre PDF…"), style: .headline)
        fileLabel.numberOfLines = 3
        fileLabel.lineBreakMode = .byTruncatingMiddle
        configure(fileDetail, text: text("PDF document", "Document PDF"), style: .caption1, color: .secondaryLabel)
        let fileRow = UIStackView(arrangedSubviews: [iconTile, column([fileLabel, fileDetail], spacing: 6)])
        fileRow.spacing = 15
        fileRow.alignment = .center
        embed(fileRow, in: fileCard, inset: 18)
        content.addArrangedSubview(fileCard)

        steps.axis = .vertical
        steps.spacing = 17
        addStep("square.and.arrow.down", title: text("Prepare", "Préparer"),
            detail: text("Keep a local copy ready for import.", "Préparer une copie locale pour l’import."), current: true)
        addStep("text.magnifyingglass", title: text("Review in the app", "Vérifier dans l’app"),
            detail: text("Check the extracted investment details.", "Vérifier les données d’investissement extraites."))
        addStep("checkmark.seal", title: text("Save when ready", "Enregistrer après vérification"),
            detail: text("You decide what enters your portfolio.", "Vous décidez des données à ajouter au portefeuille."))
        content.addArrangedSubview(steps)

        statusCard.backgroundColor = accent.withAlphaComponent(0.07)
        statusCard.layer.cornerRadius = 16
        statusIcon.image = UIImage(systemName: "lock.shield")
        statusIcon.tintColor = accent
        statusIcon.contentMode = .scaleAspectFit
        statusIcon.isAccessibilityElement = false
        NSLayoutConstraint.activate([statusIcon.widthAnchor.constraint(equalToConstant: 20), statusIcon.heightAnchor.constraint(equalToConstant: 23)])
        configure(statusLabel, text: text("Preparing an import does not upload your PDF or save any investment records.",
            "La préparation n’envoie pas votre PDF et n’enregistre aucun investissement."), style: .footnote, color: .secondaryLabel)
        let statusRow = UIStackView(arrangedSubviews: [statusIcon, statusLabel])
        statusRow.spacing = 10
        statusRow.alignment = .top
        embed(statusRow, in: statusCard, inset: 15)
        content.addArrangedSubview(statusCard)

        var configuration = UIButton.Configuration.filled()
        configuration.cornerStyle = .capsule
        configuration.baseBackgroundColor = accent
        configuration.baseForegroundColor = UIColor { $0.userInterfaceStyle == .dark ? ShareViewController.olive : .white }
        configuration.image = UIImage(systemName: "square.and.arrow.down")
        configuration.imagePadding = 10
        configuration.contentInsets = .init(top: 16, leading: 20, bottom: 16, trailing: 20)
        configuration.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
            var result = incoming
            result.font = UIFont.preferredFont(forTextStyle: .headline)
            return result
        }
        action.configuration = configuration
        action.setTitle(text("Prepare import", "Préparer l’import"), for: .normal)
        action.isEnabled = false
        action.addTarget(self, action: #selector(save), for: .touchUpInside)
        cancel.setTitle(text("Cancel", "Annuler"), for: .normal)
        cancel.titleLabel?.font = .preferredFont(forTextStyle: .body)
        cancel.titleLabel?.adjustsFontForContentSizeCategory = true
        cancel.tintColor = .secondaryLabel
        cancel.addTarget(self, action: #selector(finish), for: .touchUpInside)
        let footer = column([action, cancel], spacing: 4)
        footer.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(footer)
        NSLayoutConstraint.activate([
            footer.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24),
            footer.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24),
            footer.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -8),
            cancel.heightAnchor.constraint(greaterThanOrEqualToConstant: 44),
            scroll.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: footer.topAnchor, constant: -14),
            content.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 24),
            content.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 24),
            content.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -24),
            content.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -20),
            content.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -48)
        ])
        updateBorder()
        registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (controller: ShareViewController, _: UITraitCollection) in
            controller.updateBorder()
        }
        loadPDF()
    }

    private func configure(_ label: UILabel, text: String, style: UIFont.TextStyle, color: UIColor = .label) {
        label.text = text
        label.font = .preferredFont(forTextStyle: style)
        label.adjustsFontForContentSizeCategory = true
        label.numberOfLines = 0
        label.textColor = color
    }
    private func label(_ text: String, style: UIFont.TextStyle, color: UIColor = .label) -> UILabel {
        let value = UILabel()
        configure(value, text: text, style: style, color: color)
        return value
    }
    private func column(_ views: [UIView], spacing: CGFloat) -> UIStackView {
        let stack = UIStackView(arrangedSubviews: views)
        stack.axis = .vertical
        stack.spacing = spacing
        return stack
    }
    private func embed(_ child: UIView, in parent: UIView, inset: CGFloat) {
        child.translatesAutoresizingMaskIntoConstraints = false
        parent.addSubview(child)
        NSLayoutConstraint.activate([
            child.topAnchor.constraint(equalTo: parent.topAnchor, constant: inset),
            child.bottomAnchor.constraint(equalTo: parent.bottomAnchor, constant: -inset),
            child.leadingAnchor.constraint(equalTo: parent.leadingAnchor, constant: inset),
            child.trailingAnchor.constraint(equalTo: parent.trailingAnchor, constant: -inset)
        ])
    }
    private func addStep(_ symbol: String, title: String, detail: String, current: Bool = false) {
        let icon = UIImageView(image: UIImage(systemName: symbol))
        icon.preferredSymbolConfiguration = .init(pointSize: 20, weight: .medium)
        icon.tintColor = current ? accent : .tertiaryLabel
        icon.contentMode = .scaleAspectFit
        icon.isAccessibilityElement = false
        NSLayoutConstraint.activate([icon.widthAnchor.constraint(equalToConstant: 28), icon.heightAnchor.constraint(equalToConstant: 28)])
        stepIcons.append(icon)
        let row = UIStackView(arrangedSubviews: [icon, column([
            label(title, style: .subheadline), label(detail, style: .footnote, color: .secondaryLabel)
        ], spacing: 3)])
        row.spacing = 13
        row.alignment = .top
        steps.addArrangedSubview(row)
    }
    private func updateBorder() { fileCard.layer.borderColor = accent.withAlphaComponent(0.13).resolvedColor(with: traitCollection).cgColor }
    private func showError(_ failure: SharedPdfError) {
        statusIcon.image = UIImage(systemName: "exclamationmark.circle")
        statusIcon.tintColor = .systemRed
        statusLabel.text = failure.message(french: french)
        statusLabel.textColor = .label
        UIAccessibility.post(notification: .announcement, argument: statusLabel.text)
    }
    private func loadPDF() {
        // Safari may attach the source URL alongside the PDF; accept one PDF and ignore ancillary items.
        let attachments = (extensionContext?.inputItems as? [NSExtensionItem] ?? []).flatMap { $0.attachments ?? [] }
            .filter { $0.hasItemConformingToTypeIdentifier(UTType.pdf.identifier) }
        guard attachments.count == 1, let provider = attachments.first else {
            fileLabel.text = text("No PDF selected", "Aucun PDF sélectionné")
            showError(.notPDF)
            return
        }
        provider.loadFileRepresentation(forTypeIdentifier: UTType.pdf.identifier) { [weak self] url, _ in
            guard let self else { return }
            do {
                guard let url else { throw SharedPdfError.unavailable }
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                if let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize, size > SharedPdfInbox.maximumBytes { throw SharedPdfError.tooLarge }
                let handle = try FileHandle(forReadingFrom: url)
                defer { try? handle.close() }
                let bytes = try handle.read(upToCount: SharedPdfInbox.maximumBytes + 1) ?? Data()
                try SharedPdfInbox.validate(bytes)
                let name = String(((provider.suggestedName ?? url.lastPathComponent) as NSString).lastPathComponent.prefix(180))
                DispatchQueue.main.async {
                    self.pdf = bytes
                    self.fileName = name
                    self.fileLabel.text = name
                    self.fileDetail.text = "PDF · " + ByteCountFormatter.string(fromByteCount: Int64(bytes.count), countStyle: .file)
                    self.action.isEnabled = true
                }
            } catch {
                let failure = (error as? SharedPdfError) ?? .unavailable
                DispatchQueue.main.async {
                    self.fileLabel.text = self.text("PDF unavailable", "PDF indisponible")
                    self.showError(failure)
                }
            }
        }
    }
    @objc private func save() {
        if saved { finish(); return }
        guard let pdf, let directory = SharedPdfInbox.directory else { showError(.unavailable); return }
        action.isEnabled = false
        cancel.isEnabled = false
        action.configuration?.showsActivityIndicator = true
        let name = fileName
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            do {
                try SharedPdfInbox.stage(pdf, fileName: name, directory: directory)
                DispatchQueue.main.async {
                    guard let self else { return }
                    self.saved = true
                    self.pdf = nil
                    self.cancel.isHidden = true
                    self.heroTitle.text = self.text("Ready for\nyour review.", "Prêt pour\nvotre vérification.")
                    self.subtitle.text = self.text("Open Struct Capital next to review and import this PDF.",
                        "Ouvrez ensuite Struct Capital pour vérifier et importer ce PDF.")
                    self.stepIcons[0].image = UIImage(systemName: "checkmark.circle.fill")
                    self.stepIcons[1].tintColor = self.accent
                    self.statusIcon.image = UIImage(systemName: "checkmark.shield")
                    self.statusIcon.tintColor = self.accent
                    self.statusLabel.textColor = .secondaryLabel
                    self.statusLabel.text = self.text("PDF prepared on this device. Nothing has been added to your portfolio yet.",
                        "PDF préparé sur cet appareil. Aucun investissement n’a encore été ajouté.")
                    self.action.configuration?.showsActivityIndicator = false
                    self.action.configuration?.image = UIImage(systemName: "checkmark")
                    self.action.setTitle(self.text("Done", "Terminé"), for: .normal)
                    self.action.isEnabled = true
                    UIAccessibility.post(notification: .layoutChanged, argument: self.heroTitle)
                }
            } catch {
                let failure = (error as? SharedPdfError) ?? .unavailable
                DispatchQueue.main.async {
                    self?.showError(failure)
                    self?.action.configuration?.showsActivityIndicator = false
                    self?.action.isEnabled = true
                    self?.cancel.isEnabled = true
                }
            }
        }
    }
    @objc private func finish() { extensionContext?.completeRequest(returningItems: nil, completionHandler: nil) }
}
