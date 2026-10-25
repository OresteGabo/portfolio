import UIKit
import Shared

/// Best-effort UIKit workaround, isolated here because the secure canvas is undocumented.
/// Keep the owning text field alive; never move the app's UIWindow or its layers.
final class CaptureShieldViewController: UIViewController {
    private let captureBackdrop = UIImageView()
    private var backdropObserver: NSObjectProtocol?
    private var protectionEnabled: Bool
    private var secureField: UITextField?
    private var container: UIView?
    private var childConstraints: [NSLayoutConstraint] = []
    private var shieldAvailable = false
    private let content = MainViewControllerKt.MainViewController()

    init(protectionEnabled: Bool) {
        self.protectionEnabled = protectionEnabled
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("Use init(protectionEnabled:)") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        captureBackdrop.isUserInteractionEnabled = false
        captureBackdrop.isAccessibilityElement = false
        captureBackdrop.contentMode = .scaleToFill
        captureBackdrop.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(captureBackdrop)
        NSLayoutConstraint.activate([
            captureBackdrop.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            captureBackdrop.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            captureBackdrop.topAnchor.constraint(equalTo: view.topAnchor),
            captureBackdrop.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        backdropObserver = NotificationCenter.default.addObserver(
            forName: NSNotification.Name("portfolio.captureBackdrop"), object: nil, queue: .main
        ) { [weak self] notification in
            guard let self, self.protectionEnabled, let image = notification.userInfo?["image"] as? UIImage else { return }
            // Only a redacted image can enter this unprotected layer. Never store a live screenshot.
            self.captureBackdrop.image = image
            self.reportProtectionState()
        }
        addChild(content)
        installContainer()
        content.didMove(toParent: self)
        registerForTraitChanges([UITraitSceneCaptureState.self]) {
            (controller: CaptureShieldViewController, _: UITraitCollection) in
            controller.reportProtectionState()
        }
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        reportProtectionState()
    }

    func setProtectionEnabled(_ enabled: Bool) {
        guard enabled != protectionEnabled else { return }
        protectionEnabled = enabled
        captureBackdrop.image = nil
        if isViewLoaded { installContainer() }
    }

    deinit {
        if let backdropObserver { NotificationCenter.default.removeObserver(backdropObserver) }
    }

    private func reportProtectionState() {
        CaptureProtection_iosKt.updateIosCaptureShield(
            available: shieldAvailable && captureBackdrop.image != nil,
            captured: traitCollection.sceneCaptureState == .active
        )
    }

    private func installContainer() {
        // Mask shared financial values while the native container is being replaced.
        shieldAvailable = false
        reportProtectionState()

        var field: UITextField?
        var protectedCanvas: UIView?
        if protectionEnabled {
            let candidate = UITextField(frame: view.bounds)
            candidate.isSecureTextEntry = true
            candidate.isAccessibilityElement = false
            candidate.text = " "
            candidate.layoutIfNeeded()
            // Use only the known rendering container. If UIKit changes its hierarchy,
            // leave the shared UI masked rather than silently claiming protection.
            protectedCanvas = candidate.subviews.first {
                NSStringFromClass(type(of: $0)).hasSuffix("TextLayoutCanvasView")
            }
            if protectedCanvas != nil { field = candidate }
        }

        let next = protectedCanvas ?? UIView()
        next.removeFromSuperview()
        next.isUserInteractionEnabled = true
        next.isAccessibilityElement = false
        next.translatesAutoresizingMaskIntoConstraints = false
        next.backgroundColor = .clear
        #if DEBUG
        // Test-only launch flag exposes exactly the safe image used beneath the secure live view.
        if protectionEnabled && ProcessInfo.processInfo.arguments.contains("-captureBackdropPreview") { next.layer.mask = CALayer() }
        #endif
        view.addSubview(next)
        NSLayoutConstraint.activate([
            next.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            next.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            next.topAnchor.constraint(equalTo: view.topAnchor),
            next.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        NSLayoutConstraint.deactivate(childConstraints)
        content.view.removeFromSuperview()
        content.view.translatesAutoresizingMaskIntoConstraints = false
        next.addSubview(content.view)
        childConstraints = [
            content.view.leadingAnchor.constraint(equalTo: next.leadingAnchor),
            content.view.trailingAnchor.constraint(equalTo: next.trailingAnchor),
            content.view.topAnchor.constraint(equalTo: next.topAnchor),
            content.view.bottomAnchor.constraint(equalTo: next.bottomAnchor)
        ]
        NSLayoutConstraint.activate(childConstraints)
        container?.removeFromSuperview()
        container = next
        secureField = field
        view.layoutIfNeeded()
        shieldAvailable = protectedCanvas != nil
        reportProtectionState()
    }
}
