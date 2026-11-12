import Foundation

enum CalendarEventDay {
    /// Keep the civil date selected in the app, including when the phone is abroad or DST changes.
    static func interval(_ raw: String, timeZone: TimeZone = .current) -> DateInterval? {
        guard raw.range(of: "^[0-9]{4}-[0-9]{2}-[0-9]{2}$", options: .regularExpression) != nil else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let parts = raw.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3, parts[0] > 0,
              let day = calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2])),
              calendar.component(.year, from: day) == parts[0],
              calendar.component(.month, from: day) == parts[1],
              calendar.component(.day, from: day) == parts[2],
              let next = calendar.date(byAdding: .day, value: 1, to: day) else { return nil }
        return DateInterval(start: day, end: next)
    }
}
