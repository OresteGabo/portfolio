import Foundation

@main enum Checks {
    static func main() {
        for zone in ["Africa/Kigali", "Europe/Paris", "America/Los_Angeles", "Pacific/Auckland"] {
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = TimeZone(identifier: zone)!
            for date in ["2028-02-29", "2027-03-28", "2027-10-31"] {
                guard let interval = CalendarEventDay.interval(date, timeZone: calendar.timeZone) else { fatalError("Missing valid day") }
                let day = Int(date.suffix(2))!
                precondition(calendar.component(.day, from: interval.start) == day)
                precondition(calendar.date(byAdding: .day, value: 1, to: interval.start) == interval.end)
                precondition(calendar.component(.hour, from: interval.start) == 0)
            }
        }
        for invalid in ["2027-02-29", "2028-02-30", "2028-13-01", "2028-2-29", "bad", "0000-01-01"] {
            precondition(CalendarEventDay.interval(invalid) == nil)
        }
        print("Calendar civil-date checks passed: leap dates, DST, phone time zones and malformed input.")
    }
}
