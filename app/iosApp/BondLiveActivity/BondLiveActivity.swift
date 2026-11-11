import ActivityKit
import SwiftUI
import WidgetKit

@main
struct BondLiveActivityBundle: WidgetBundle {
    var body: some Widget { BondLiveActivity() }
}

struct BondLiveActivity: Widget {
    private let olive = Color(red: 0.29, green: 0.35, blue: 0.19)
    private let islandAccent = Color(red: 0.86, green: 0.80, blue: 0.43)
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: BondSubscriptionAttributes.self) { context in
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Label("Struct Capital", systemImage: "building.columns.fill")
                        .font(.caption.weight(.semibold))
                    Spacer()
                    Text(status(context)).font(.caption.weight(.bold))
                }
                Text(context.attributes.title).font(.headline).lineLimit(2)
                HStack {
                    Text(context.attributes.language == "fr" ? "Date de clôture publiée" : "Published closing date")
                    Spacer()
                    Text(Date(timeIntervalSince1970: context.state.endsAt - 1), style: .date)
                        .environment(\.timeZone, TimeZone(identifier: "Africa/Kigali")!)
                }.font(.caption)
                Text(context.attributes.language == "fr" ? "Confirmez l’heure limite avec votre courtier." : "Confirm the closing time with your broker.")
                    .font(.caption2).foregroundStyle(.secondary)
            }
            .padding(16)
            .activityBackgroundTint(Color(red: 0.96, green: 0.95, blue: 0.90))
            .activitySystemActionForegroundColor(olive)
            .foregroundStyle(olive)
            .widgetURL(URL(string: "structcapital://notifications"))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: "building.columns.fill").foregroundStyle(islandAccent)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(status(context)).font(.caption.bold())
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(context.attributes.title).font(.headline).lineLimit(2)
                        Text(context.attributes.language == "fr" ? "Ouvrez Struct Capital pour le formulaire et les dates." : "Open Struct Capital for the form and published dates.").font(.caption)
                    }
                }
            } compactLeading: {
                Image(systemName: "building.columns.fill").foregroundStyle(islandAccent)
            } compactTrailing: {
                Text(context.isStale ? "…" : context.state.stage == "closing" ? (context.attributes.language == "fr" ? "Clôture" : "Closing") : (context.attributes.language == "fr" ? "Ouvert" : "Open")).font(.caption2)
            } minimal: {
                Image(systemName: "building.columns.fill").foregroundStyle(islandAccent)
            }
            .widgetURL(URL(string: "structcapital://notifications"))
        }
    }
    private func status(_ context: ActivityViewContext<BondSubscriptionAttributes>) -> String {
        let french = context.attributes.language == "fr"
        if context.state.stage == "closed" { return french ? "Souscriptions closes" : "Subscriptions closed" }
        if context.isStale || context.state.stage == "ended" { return french ? "Voir le statut actuel" : "View latest status" }
        return context.state.stage == "closing" ? (french ? "Clôture prochaine" : "Closing soon") : (french ? "Souscriptions ouvertes" : "Subscriptions open")
    }
}
