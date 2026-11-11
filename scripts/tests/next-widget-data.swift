import Foundation

@main
struct NextWidgetDataChecks {
    static func main() throws {
        func issue(_ id: String, _ open: String, _ close: String, form: Bool = false) -> NextWidgetSnapshot.Issue {
            .init(id: id, title: "Public Treasury bond", open: open, close: close, form: form, reminder: false)
        }
        let snapshot = NextWidgetSnapshot(language: "en", issues: [
            issue("old", "2026-10-01", "2026-10-05"),
            issue("active", "2026-11-10", "2026-11-12"),
            issue("future", "2026-12-14", "2026-12-16", form: true),
            issue("invalid", "broken", "2026-11-20")
        ], payments: [.init(date: "2026-11-15", kind: "coupon")])
        let now = ISO8601DateFormatter().date(from: "2026-11-11T22:30:00Z")! // Already Nov 12 in Rwanda.
        let active = NextWidgetData.events(snapshot, at: now)
        precondition(active.first?.key == "issue:active")
        precondition(active.first?.status == "Closing today")
        precondition(active.last?.title == "Coupon payment")
        let after = NextWidgetData.events(snapshot, at: NextWidgetData.parse("2026-11-13")!)
        precondition(after.first?.title == "Coupon payment")
        precondition(after.last?.status == "Next subscription")
        let december = NextWidgetData.events(snapshot, at: NextWidgetData.parse("2026-12-01")!)
        precondition(december.first?.status == "Form available")
        precondition(NextWidgetData.parse("2026-02-30") == nil)
        precondition(NextWidgetData.parse("2026-2-01") == nil)
        precondition(NextWidgetData.url(for: NextWidgetData.parse("2027-01-11")!).absoluteString == "structcapital://calendar?date=2027-01-11")
        let french = NextWidgetSnapshot(language: "fr", issues: snapshot.issues, payments: snapshot.payments)
        precondition(NextWidgetData.events(french, at: now).first?.status == "Clôture aujourd’hui")
        // Persisting typed data must discard unknown/private fields from any input.
        let raw = #"{"language":"en","issues":[],"payments":[],"balance":99999,"session":"synthetic-secret"}"#
        let safe = try JSONDecoder().decode(NextWidgetSnapshot.self, from: Data(raw.utf8))
        let encoded = String(decoding: try JSONEncoder().encode(safe), as: UTF8.self)
        precondition(!encoded.contains("balance") && !encoded.contains("session") && !encoded.contains("synthetic-secret"))
        print("iPhone widget checks passed: Rwanda boundaries, expiry, priority, form month, malformed dates, French, deep links, privacy whitelist.")
    }
}
