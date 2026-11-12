import UIKit
import EventKit
import EventKitUI
import Shared

@MainActor
final class CalendarExportHost: NSObject, EKEventEditViewDelegate {
    static let shared = CalendarExportHost()
    private var editor: EKEventEditViewController?

    func install() {
        NativeCalendarBridge.shared.install { [weak self] date, title, notes in
            self?.present(date: date, title: title, notes: notes)
        }
    }
    private func present(date: String, title: String, notes: String) {
        guard editor == nil, let interval = CalendarEventDay.interval(date) else { return }
        var presenter = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .filter { $0.activationState == .foregroundActive }
            .flatMap(\.windows).first { $0.isKeyWindow }?.rootViewController
        while let presented = presenter?.presentedViewController { presenter = presented }
        guard let presenter, !presenter.isBeingDismissed else { return }
        // iOS 17+: EventKitUI performs the user-approved save in its own process.
        // No read/write calendar permission or programmatic save is requested.
        let store = EKEventStore()
        let event = EKEvent(eventStore: store)
        event.title = String(title.prefix(180))
        event.notes = String(notes.prefix(1000))
        event.isAllDay = true
        event.startDate = interval.start
        event.endDate = interval.end.addingTimeInterval(-1) // Last instant of the selected all-day event.
        event.url = URL(string: "structcapital://calendar?date=\(date)")
        let controller = EKEventEditViewController()
        controller.eventStore = store
        controller.event = event
        controller.editViewDelegate = self
        editor = controller
        presenter.present(controller, animated: true)
    }
    func eventEditViewController(_ controller: EKEventEditViewController, didCompleteWith action: EKEventEditViewAction) {
        controller.dismiss(animated: true)
        editor = nil
    }
}
