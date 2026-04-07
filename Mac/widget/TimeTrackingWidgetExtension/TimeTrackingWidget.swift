//
//  TimeTrackingWidget.swift
//  TimeTrackingWidgetExtension
//

import AppIntents
import AppKit
import SwiftUI
import WidgetKit
import WidgetSharedKit

// MARK: - Configuration intent (no user-configurable parameters)

struct TimeTrackingWidgetConfigurationIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Fichaje"
    static var description = IntentDescription("Controla tu jornada desde el escritorio.")
}

// MARK: - Widget

struct TimeTrackingWidget: Widget {
    let kind: String = TimeTrackingWidgetKind.value

    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: kind, provider: Provider()) { entry in
            TimeTrackingWidgetEntryView(entry: entry)
                .containerBackground(BrandColors.surfaceTint, for: .widget)
        }
        .configurationDisplayName("Fichaje")
        .description("Controla tu jornada desde el escritorio.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

// MARK: - Timeline provider

struct Provider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> SimpleEntry {
        makeEntrySync(date: Date(), family: context.family)
    }

    func snapshot(for configuration: TimeTrackingWidgetConfigurationIntent, in context: Context) async -> SimpleEntry {
        makeEntrySync(date: Date(), family: context.family)
    }

    func timeline(for configuration: TimeTrackingWidgetConfigurationIntent, in context: Context) async -> Timeline<SimpleEntry> {
        let entry = await makeEntryWithNetworkFallback(date: Date(), family: context.family)
        let next = Calendar.current.date(byAdding: .second, value: 5, to: Date()) ?? Date().addingTimeInterval(5)
        return Timeline(entries: [entry], policy: .after(next))
    }

    private func makeEntrySync(date: Date, family: WidgetFamily) -> SimpleEntry {
        let loaded = WidgetTimelineSnapshotLoader.loadForWidgetEntry(now: date)
        return SimpleEntry(
            date: date,
            family: family,
            session: loaded.session,
            snapshot: loaded.snapshot,
            profileImage: loaded.profileImage
        )
    }

    private func makeEntryWithNetworkFallback(date: Date, family: WidgetFamily) async -> SimpleEntry {
        if let fresh = await WidgetTimelineSnapshotLoader.tryNetworkRestore(now: date) {
            return SimpleEntry(
                date: date,
                family: family,
                session: fresh.session,
                snapshot: fresh.snapshot,
                profileImage: fresh.profileImage
            )
        }

        let loaded = WidgetTimelineSnapshotLoader.loadForWidgetEntry(now: date)
        return SimpleEntry(
            date: date,
            family: family,
            session: loaded.session,
            snapshot: loaded.snapshot,
            profileImage: loaded.profileImage
        )
    }
}

// MARK: - Timeline entry

struct SimpleEntry: TimelineEntry {
    let date: Date
    let family: WidgetFamily
    let session: UserSession
    let snapshot: TimeTrackingSnapshot
    let profileImage: NSImage?
}

// MARK: - Entry view

struct TimeTrackingWidgetEntryView: View {
    @Environment(\.widgetFamily) var widgetFamily
    var entry: Provider.Entry

    var body: some View {
        TimeTrackingWidgetView(
            timelineSession: entry.session,
            snapshot: entry.snapshot,
            profileImage: entry.profileImage,
            family: widgetFamily,
            actionButtonBuilder: makeIntentButton
        )
    }
}
