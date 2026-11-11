import ActivityKit
import Foundation

struct BondSubscriptionAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var stage: String
        var endsAt: Double
    }
    var calendarId: String
    var title: String
    var month: String
    var language: String
}
