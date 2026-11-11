import UIKit
import Shared

@MainActor
final class SharedPdfImportHost: NSObject {
    static let shared = SharedPdfImportHost()
    private var draining = false
    func start() {
        guard Bundle.main.object(forInfoDictionaryKey:"GaboWidgetsEnabled") as? String == "YES" else { return }
        NotificationCenter.default.addObserver(self,selector:#selector(drain),name:UIApplication.didBecomeActiveNotification,object:nil)
        drain()
    }
    @objc private func drain() {
        guard !draining, NativePdfInbox.shared.pending == nil, let directory = SharedPdfInbox.directory,
              let item = try? SharedPdfInbox.pending(directory:directory) else { return }
        draining = true
        IosPdfReceiver.shared.receiveQueued(url:directory.appendingPathComponent("pending.pdf").absoluteString,
            deliveryId:item.delivery,fileName:item.fileName) { remove in
            DispatchQueue.main.async {
                if remove.boolValue { try? SharedPdfInbox.remove(delivery:item.delivery,directory:directory) }
                self.draining = false
            }
        }
    }
}
