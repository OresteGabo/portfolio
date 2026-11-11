import Foundation

struct NextWidgetSnapshot: Codable, Equatable {
    struct Issue: Codable, Equatable {
        let id: String
        let title: String
        let open: String
        let close: String
        let form: Bool
        let reminder: Bool
    }
    struct Payment: Codable, Equatable {
        let date: String
        let kind: String
    }
    let language: String
    let issues: [Issue]
    let payments: [Payment]
}

struct NextWidgetEnvelope: Codable {
    let snapshot: NextWidgetSnapshot
    let savedAt: Date
}

struct NextWidgetEvent {
    let key: String
    let title: String
    let status: String
    let date: Date
    let detail: String
    let priority: Int
}

enum NextWidgetData {
    static var marketCalendar: Calendar {
        var value = Calendar(identifier: .gregorian)
        value.timeZone = TimeZone(identifier: "Africa/Kigali")!
        return value
    }
    static func parse(_ raw: String) -> Date? {
        let formatter = DateFormatter()
        formatter.calendar = marketCalendar
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = marketCalendar.timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.isLenient = false
        guard let date = formatter.date(from: raw), formatter.string(from: date) == raw else { return nil }
        return date
    }
    static func formatted(_ date: Date, pattern: String, french: Bool) -> String {
        let formatter = DateFormatter()
        formatter.calendar = marketCalendar
        formatter.timeZone = marketCalendar.timeZone
        formatter.locale = Locale(identifier: french ? "fr_FR" : "en_GB")
        formatter.dateFormat = pattern
        return formatter.string(from: date)
    }
    static func url(for date: Date) -> URL {
        URL(string: "structcapital://calendar?date=\(formatted(date, pattern: "yyyy-MM-dd", french: false))")!
    }
    static func events(_ snapshot: NextWidgetSnapshot, at now: Date) -> [NextWidgetEvent] {
        let french = snapshot.language == "fr"
        func text(_ en: String, _ fr: String) -> String { french ? fr : en }
        let calendar = marketCalendar
        let today = calendar.startOfDay(for: now)
        var candidates: [NextWidgetEvent] = []
        for issue in snapshot.issues {
            guard let open = parse(issue.open), let close = parse(issue.close), close >= open, close >= today else { continue }
            let active = today >= open
            let monthStart = calendar.date(from: calendar.dateComponents([.year, .month], from: close))!
            let form = issue.form && today >= monthStart
            let status = active ? (today == close ? text("Closing today", "Clôture aujourd’hui") : text("Subscriptions open", "Souscription ouverte"))
                : (form ? text("Form available", "Formulaire disponible") : text("Next subscription", "Prochaine souscription"))
            let due = active ? close : open
            let detail = text(active ? "Closes" : "Opens", active ? "Clôture" : "Ouverture")
                + " · \(calendar.component(.year, from: due))"
                + (issue.reminder ? text(" · Reminder set", " · Rappel activé") : "")
            candidates.append(NextWidgetEvent(key: "issue:\(issue.id)", title: issue.title, status: status,
                date: due, detail: detail, priority: active ? 0 : form ? 1 : 2))
        }
        for payment in snapshot.payments {
            guard let due = parse(payment.date), due >= today else { continue }
            let maturity = payment.kind == "maturity"
            candidates.append(NextWidgetEvent(key: "payment:\(payment.kind):\(payment.date)",
                title: maturity ? text("Principal repayment", "Remboursement du capital") : text("Coupon payment", "Paiement du coupon"),
                status: text("Expected payment", "Paiement prévu"), date: due,
                detail: text("Estimated", "Estimation") + " · \(calendar.component(.year, from: due))", priority: 2))
        }
        let ordered = candidates.sorted {
            if $0.priority != $1.priority { return $0.priority < $1.priority }
            if $0.date != $1.date { return $0.date < $1.date }
            return $0.key < $1.key
        }
        guard let first = ordered.first else { return [] }
        let second = ordered.filter { $0.key != first.key }.sorted {
            $0.date == $1.date ? $0.key < $1.key : $0.date < $1.date
        }.first
        return [first] + (second.map { [$0] } ?? [])
    }
}
