import ActivityKit
import Foundation
import Shared

@MainActor
final class BondLiveActivityMonitor {
    static let shared = BondLiveActivityMonitor()
    private var started = false
    private var observed = Set<String>()
    func start() {
        guard !started else { return }
        started = true
        NativePushBridge.shared.installReminderCleanup { months in
            let selected = Set(months.split(separator: ",").map(String.init))
            Task { @MainActor in
                for activity in Activity<BondSubscriptionAttributes>.activities where !selected.contains(activity.attributes.month) {
                    await activity.end(nil, dismissalPolicy: .immediate)
                }
            }
        }
        Task {
            for await token in Activity<BondSubscriptionAttributes>.pushToStartTokenUpdates {
                NativePushBridge.shared.liveStartTokenChanged(token: hex(token))
            }
        }
        for activity in Activity<BondSubscriptionAttributes>.activities { observe(activity) }
        Task {
            for await activity in Activity<BondSubscriptionAttributes>.activityUpdates { observe(activity) }
        }
    }
    private func observe(_ activity: Activity<BondSubscriptionAttributes>) {
        guard observed.insert(activity.id).inserted else { return }
        Task {
            for await token in activity.pushTokenUpdates {
                NativePushBridge.shared.liveActivityTokenChanged(activityId: activity.id,
                    calendarId: activity.attributes.calendarId, month: activity.attributes.month, token: hex(token))
            }
        }
    }
    private func hex(_ data: Data) -> String { data.map { String(format: "%02x", $0) }.joined() }
}
