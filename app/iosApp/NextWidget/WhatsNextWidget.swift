import SwiftUI
import WidgetKit

struct NextWidgetEntry: TimelineEntry {
    let date: Date
    let envelope: NextWidgetEnvelope?
}

struct NextWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> NextWidgetEntry { NextWidgetEntry(date: Date(), envelope: nil) }
    func getSnapshot(in context: Context, completion: @escaping (NextWidgetEntry) -> Void) {
        completion(NextWidgetEntry(date: Date(), envelope: NextWidgetCache.load()))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<NextWidgetEntry>) -> Void) {
        let now = Date()
        let envelope = NextWidgetCache.load()
        var dates = [now]
        // Date-driven transitions happen at Rwanda midnight without continuous app execution.
        let calendar = NextWidgetData.marketCalendar
        for offset in 1...3 {
            if let day = calendar.date(byAdding: .day, value: offset, to: calendar.startOfDay(for: now)) { dates.append(day) }
        }
        completion(Timeline(entries: dates.map { NextWidgetEntry(date: $0, envelope: envelope) },
            policy: .after(now.addingTimeInterval(1800))))
    }
}

struct WhatsNextWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: NextWidgetEntry
    private let cream = Color(red: 0.95, green: 0.90, blue: 0.65)
    private let white = Color(red: 0.97, green: 0.96, blue: 0.91)
    private let muted = Color(red: 0.73, green: 0.77, blue: 0.68)
    private var french: Bool { entry.envelope?.snapshot.language == "fr" }
    private func text(_ en: String, _ fr: String) -> String { french ? fr : en }
    private var events: [NextWidgetEvent] {
        entry.envelope.map { NextWidgetData.events($0.snapshot, at: entry.date) } ?? []
    }
    private func date(_ event: NextWidgetEvent?) -> String {
        event.map {
            let calendar = NextWidgetData.marketCalendar
            let pattern = calendar.component(.year, from: $0.date) == calendar.component(.year, from: entry.date) ? "d MMM" : "d MMM yy"
            return NextWidgetData.formatted($0.date, pattern: pattern, french: french)
        } ?? text("Calendar", "Calendrier")
    }
    private func title(_ event: NextWidgetEvent?) -> String {
        guard let event else { return entry.envelope == nil ? text("Open Inzira", "Ouvrir Inzira") : text("Nothing upcoming", "Rien à venir") }
        if event.key.hasPrefix("payment:maturity:") { return text("Maturity", "Échéance") }
        if event.key.hasPrefix("payment:") { return text("Coupon", "Coupon") }
        return event.title
    }
    private var header: some View {
        HStack(spacing: 6) {
            Image("WidgetLogo").resizable().scaledToFit().frame(width: 19, height: 19)
                .clipShape(RoundedRectangle(cornerRadius: 4)).accessibilityHidden(true)
            Text(text("What’s next", "À venir")).font(.system(size: 12, weight: .semibold)).foregroundStyle(cream).lineLimit(1)
        }
    }
    var body: some View {
        Group {
            switch family {
            case .accessoryInline:
                Label("\(date(events.first)) · \(title(events.first))", systemImage: "calendar")
            case .accessoryCircular:
                ZStack {
                    AccessoryWidgetBackground()
                    VStack(spacing: 0) {
                        Image(systemName: "calendar").font(.caption2)
                        if let event = events.first {
                            Text(NextWidgetData.formatted(event.date, pattern: "d", french: french))
                                .font(.system(.title3, design: .rounded, weight: .bold))
                            Text(NextWidgetData.formatted(event.date, pattern: "MMM", french: french))
                                .font(.system(size: 9, weight: .medium))
                        } else {
                            Text(text("Open", "Ouvrir")).font(.caption2)
                        }
                    }
                }.accessibilityLabel("\(title(events.first)), \(date(events.first))")
            case .accessoryRectangular:
                HStack(spacing: 7) {
                    RoundedRectangle(cornerRadius: 2).frame(width: 3)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(events.first?.status ?? text("What’s next", "À venir"))
                            .font(.caption2).lineLimit(1)
                        Text(title(events.first)).font(.headline).lineLimit(1)
                        Label(date(events.first), systemImage: "calendar").font(.caption).lineLimit(1)
                    }
                }
            default:
                homeScreen
            }
        }
        .widgetURL(NextWidgetData.url(for: events.first?.date ?? entry.date))
        .containerBackground(for: .widget) {
            if family == .systemSmall || family == .systemMedium {
                Color(red: 0.078, green: 0.125, blue: 0.078)
            }
        }
    }
    private func dateLabel(_ event: NextWidgetEvent) -> String {
        if event.key.hasPrefix("payment:") { return text("Expected", "Prévu") }
        if event.priority == 0 {
            return NextWidgetData.marketCalendar.isDate(event.date, inSameDayAs: entry.date)
                ? text("Closes today", "Clôture aujourd’hui") : text("Closes", "Clôture")
        }
        return text("Opens", "Ouverture")
    }
    private var homeScreen: some View {
        VStack(alignment: .leading, spacing: 12) {
            header
            if family == .systemMedium, let first = events.first {
                HStack(spacing: 16) {
                    eventCard(first, small: false).frame(maxWidth: .infinity, alignment: .leading)
                    if let second = events.dropFirst().first {
                        Rectangle().fill(muted.opacity(0.22)).frame(width: 1)
                        Link(destination: NextWidgetData.url(for: second.date)) {
                            eventCard(second, small: false).frame(maxWidth: .infinity, alignment: .leading)
                        }.buttonStyle(.plain)
                    }
                }
            } else if let first = events.first {
                eventCard(first, small: family == .systemSmall)
            } else {
                Spacer(minLength: 0)
                Text(title(nil)).font(.system(size: 16, weight: .medium)).foregroundStyle(white)
                    .lineLimit(2)
                Spacer(minLength: 0)
            }
        }.padding(16)
    }
    private func eventCard(_ event: NextWidgetEvent, small: Bool) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(dateLabel(event)).font(.system(size: 10, weight: .medium))
                .foregroundStyle(muted).lineLimit(1).minimumScaleFactor(0.85)
            Text(date(event)).font(.system(size: small ? 29 : 28, weight: .semibold, design: .rounded))
                .foregroundStyle(cream).lineLimit(1).minimumScaleFactor(0.8)
            Spacer(minLength: 4)
            Text(title(event)).font(.system(size: 13, weight: .medium))
                .foregroundStyle(white).lineLimit(2).minimumScaleFactor(0.85)
        }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(event.status), \(event.title), \(NextWidgetData.formatted(event.date, pattern: "d MMM yyyy", french: french)), \(event.detail)")
    }

}

@main
struct WhatsNextWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: NextWidgetCache.kind, provider: NextWidgetProvider()) { entry in
            WhatsNextWidgetView(entry: entry)
        }
        .configurationDisplayName("What’s next")
        .description("Upcoming bond subscriptions and expected payments. No personal amounts.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryInline, .accessoryCircular, .accessoryRectangular])
        .contentMarginsDisabled()
    }
}
