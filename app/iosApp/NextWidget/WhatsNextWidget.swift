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
    private var saved: String {
        guard let savedAt = entry.envelope?.savedAt else { return text("No saved data", "Aucune donnée") }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: french ? "fr_FR" : "en_GB")
        formatter.dateFormat = "d MMM · HH:mm"
        return text("Saved ", "Enregistré ") + formatter.string(from: savedAt)
    }
    private func date(_ event: NextWidgetEvent?) -> String {
        event.map { NextWidgetData.formatted($0.date, pattern: "d MMM", french: french) } ?? text("Calendar", "Calendrier")
    }
    private func title(_ event: NextWidgetEvent?) -> String {
        event?.title ?? (entry.envelope == nil ? text("Open Struct Capital", "Ouvrir Struct Capital") : text("No upcoming events", "Aucun événement à venir"))
    }
    private var header: some View {
        HStack(spacing: 6) {
            Image("WidgetLogo").resizable().scaledToFit().frame(width: 19, height: 19)
                .clipShape(RoundedRectangle(cornerRadius: 4)).accessibilityHidden(true)
            Text(text("What’s next", "À venir")).font(.system(size: 12, weight: .semibold)).foregroundStyle(cream).lineLimit(1)
            if family == .systemMedium {
                Spacer(minLength: 4)
                Text(saved).font(.system(size: 8)).foregroundStyle(muted).lineLimit(1).minimumScaleFactor(0.8)
            }
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
    private var homeScreen: some View {
        let first = events.first
        return VStack(alignment: .leading, spacing: family == .systemSmall ? 3 : 7) {
            header
            if family == .systemMedium {
                HStack(alignment: .top, spacing: 16) {
                    primary(first, small: false).frame(maxWidth: .infinity, alignment: .leading)
                    let second = events.dropFirst().first
                    Link(destination: NextWidgetData.url(for: second?.date ?? entry.date)) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(text("ALSO COMING UP", "ÉGALEMENT À VENIR"))
                                .font(.system(size: 9)).foregroundStyle(muted).lineLimit(1).minimumScaleFactor(0.7)
                            Spacer(minLength: 0)
                            Text(second?.title ?? text("All upcoming events", "Tous les événements"))
                                .font(.system(size: 12, weight: .medium)).foregroundStyle(white).lineLimit(3).minimumScaleFactor(0.85)
                            Spacer(minLength: 0)
                            Text(date(second)).font(.system(size: 22, weight: .bold)).foregroundStyle(cream).lineLimit(1).minimumScaleFactor(0.8)
                            Text(second?.detail ?? text("Tap to explore", "Voir le calendrier"))
                                .font(.system(size: 9)).foregroundStyle(muted).lineLimit(1).minimumScaleFactor(0.8)
                        }.frame(maxWidth: 115, maxHeight: .infinity, alignment: .leading).contentShape(Rectangle())
                    }.buttonStyle(.plain)
                }
            } else {
                primary(first, small: true)
                Text(saved).font(.system(size: 8)).foregroundStyle(muted).lineLimit(1).minimumScaleFactor(0.8)
            }
        }
        .padding(14)

    }
    private func primary(_ event: NextWidgetEvent?, small: Bool) -> some View {
        VStack(alignment: .leading, spacing: small ? 2 : 4) {
            Text(event?.status ?? text("Your investment calendar", "Votre calendrier"))
                .font(.system(size: 10)).foregroundStyle(muted).lineLimit(1).minimumScaleFactor(0.8)
            Spacer(minLength: 0)
            Text(title(event)).font(.system(size: small ? 14 : 15, weight: .semibold))
                .foregroundStyle(white).lineLimit(small ? 2 : 3).minimumScaleFactor(0.8)
            Spacer(minLength: 0)
            Text(date(event)).font(.system(size: small ? 25 : 27, weight: .bold))
                .foregroundStyle(cream).lineLimit(1).minimumScaleFactor(0.8)
            Text(event?.detail ?? text("Open the app to update", "Ouvrir l’app pour actualiser"))
                .font(.system(size: 9)).foregroundStyle(muted).lineLimit(1).minimumScaleFactor(0.8)
        }
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
