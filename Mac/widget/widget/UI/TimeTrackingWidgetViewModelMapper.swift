import AppKit
import Foundation

struct SignedOutWidgetPresentation: Equatable {
    let title: String
    let message: String
}

struct SignedInWidgetPresentation {
    let title: String
    let displayName: String
    let profileImage: NSImage?
    let statusHeadline: String
    let sessionCounter: String
    let timerBulletActive: Bool
    let lastAction: String
    let lastActionTime: String
    let lastCompletedShiftDuration: String
    let coffeeTodayDuration: String
    let foodTodayDuration: String
    let timelineText: String
    let timelineFormatted: String

    let showPrimaryInteractive: Bool
    let showPrimaryDisabled: Bool
    let primaryIsClockIn: Bool

    let showCoffeeActive: Bool
    let showCoffeeEndBreak: Bool
    let showCoffeeDisabled: Bool

    let showFoodActive: Bool
    let showFoodEndBreak: Bool
    let showFoodDisabled: Bool
}

enum TimeTrackingWidgetPresentation {
    case signedOut(SignedOutWidgetPresentation)
    case signedIn(SignedInWidgetPresentation)
}

enum TimeTrackingWidgetViewModelMapper {
    static func map(
        session: UserSession,
        snapshot: TimeTrackingSnapshot,
        profileImage: NSImage?,
        now: Date = Date()
    ) -> TimeTrackingWidgetPresentation {
        if !session.isAuthenticated {
            return .signedOut(
                SignedOutWidgetPresentation(
                    title: "Fichaje",
                    message: "Iniciá sesión en la app para fichar con tu cuenta."
                )
            )
        }

        let culture = Locale.current
        let displayName = session.user?.displayName
        let welcomeName: String
        if let d = displayName?.trimmingCharacters(in: .whitespacesAndNewlines), !d.isEmpty {
            welcomeName = d
        } else {
            welcomeName = "Usuario autenticado"
        }

        let activeBreakType = snapshot.activeBreakType
        let sessionCounter: String
        if activeBreakType == .coffee || activeBreakType == .food {
            let d = snapshot.activeBreak?.duration(nowUtc: now) ?? 0
            sessionCounter = formatDurationWithSeconds(d)
        } else {
            let primaryDuration = livePrimaryDurationForCounter(snapshot: snapshot, now: now)
            let normalized = normalizeCounterDuration(snapshot: snapshot, value: primaryDuration)
            sessionCounter = formatDurationWithSeconds(normalized)
        }

        let available = Set(snapshot.availableActions)

        let isActiveSession =
            activeBreakType == .none
                && !available.contains(.clockIn)
                && available.contains(.clockOut)

        let timelineRaw = buildTimelineText(snapshot: snapshot, locale: culture)

        let signedIn = SignedInWidgetPresentation(
            title: "Fichaje",
            displayName: "Bienvenido, \(welcomeName)",
            profileImage: profileImage,
            statusHeadline: buildStatusHeadline(snapshot: snapshot),
            sessionCounter: sessionCounter,
            timerBulletActive: isActiveSession,
            lastAction: snapshot.lastAction == .none
                ? "Sin acciones todavía"
                : translateAction(snapshot.lastAction),
            lastActionTime: formatLastActionTime(snapshot.lastActionAtUtc, locale: culture),
            lastCompletedShiftDuration: formatDuration(snapshot.summary.lastCompletedShiftWorkedDuration),
            coffeeTodayDuration: formatBreakDuration(snapshot.summary.coffeeBreakDurationToday),
            foodTodayDuration: formatBreakDuration(snapshot.summary.foodBreakDurationToday),
            timelineText: timelineRaw,
            timelineFormatted: formatTimelineForDisplay(timelineRaw),
            showPrimaryInteractive: available.contains(.clockIn)
                || (activeBreakType == .none && available.contains(.clockOut)),
            showPrimaryDisabled: activeBreakType != .none,
            primaryIsClockIn: available.contains(.clockIn),
            showCoffeeActive: activeBreakType == .none && (available.contains(.startCoffeeBreak) || available.contains(.startBreak)),
            showCoffeeEndBreak: activeBreakType == .coffee
                && (available.contains(.endCoffeeBreak) || available.contains(.endBreak)),
            showCoffeeDisabled: activeBreakType == .food
                || (activeBreakType == .none && !available.contains(.startCoffeeBreak) && !available.contains(.startBreak))
                || (activeBreakType == .coffee && !available.contains(.endCoffeeBreak) && !available.contains(.endBreak)),
            showFoodActive: activeBreakType == .none && available.contains(.startFoodBreak),
            showFoodEndBreak: activeBreakType == .food
                && (available.contains(.endFoodBreak) || available.contains(.endBreak)),
            showFoodDisabled: activeBreakType == .coffee
                || (activeBreakType == .none && !available.contains(.startFoodBreak))
                || (activeBreakType == .food && !available.contains(.endFoodBreak) && !available.contains(.endBreak))
        )

        return .signedIn(signedIn)
    }

    private static func buildStatusHeadline(snapshot: TimeTrackingSnapshot) -> String {
        switch snapshot.status {
        case .notClockedIn:
            return "Sin fichar"
        case .working:
            return "Estás trabajando"
        case .onBreak:
            if snapshot.activeBreakType == .coffee {
                return "En descanso"
            }
            if snapshot.activeBreakType == .food {
                return "En descanso de comida"
            }
            return "En descanso"
        case .offDuty:
            return "Jornada finalizada"
        }
    }

    private static func resolvePrimaryDuration(snapshot: TimeTrackingSnapshot) -> TimeInterval {
        switch snapshot.status {
        case .working, .onBreak:
            return snapshot.summary.currentShiftWorkedDuration
        default:
            return snapshot.summary.lastCompletedShiftWorkedDuration
        }
    }

    /// Crono del chip: con `TimelineView` el `now` avanza, pero el `summary` solo se recalcula al persistir; aquí recalculamos jornada neta en vivo.
    private static func livePrimaryDurationForCounter(snapshot: TimeTrackingSnapshot, now: Date) -> TimeInterval {
        guard snapshot.status == .working,
              let start = snapshot.currentShiftStartedAtUtc
        else {
            return resolvePrimaryDuration(snapshot: snapshot)
        }
        return TimeTrackingLiveDuration.netWorkedSeconds(
            shiftStartUtc: start,
            breakSessions: snapshot.breakSessions,
            nowUtc: now
        )
    }

    private static func normalizeCounterDuration(snapshot: TimeTrackingSnapshot, value: TimeInterval) -> TimeInterval {
        switch snapshot.status {
        case .working, .onBreak:
            return value
        default:
            return value < 60 ? 0 : value
        }
    }

    private static func translateAction(_ action: TimeTrackingAction) -> String {
        switch action {
        case .clockIn: return "Entrada"
        case .startBreak: return "Iniciar café"
        case .endBreak: return "Finalizar descanso"
        case .startCoffeeBreak: return "Iniciar café"
        case .endCoffeeBreak: return "Finalizar café"
        case .startFoodBreak: return "Iniciar comida"
        case .endFoodBreak: return "Finalizar comida"
        case .clockOut: return "Salida"
        default: return "Sin acciones"
        }
    }

    private static func buildTimelineText(snapshot: TimeTrackingSnapshot, locale: Locale) -> String {
        let events = resolveTimelineEvents(snapshot: snapshot)
        if events.isEmpty {
            return "Todavía no hay hitos registrados hoy."
        }
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.dateFormat = "HH:mm"
        formatter.timeZone = TimeZone.current
        return events.map { "\(formatter.string(from: $0.occurredAtUtc)) \(translateEvent($0))" }
            .joined(separator: " · ")
    }

    private static func resolveTimelineEvents(snapshot: TimeTrackingSnapshot) -> [WorkdayEvent] {
        let allowed: Set<WorkdayEventType> = [
            .clockIn, .clockOut, .startCoffeeBreak, .endCoffeeBreak, .startFoodBreak, .endFoodBreak
        ]
        let ordered = snapshot.workdayEvents
            .filter { allowed.contains($0.eventType) }
            .sorted { $0.occurredAtUtc < $1.occurredAtUtc }
        if ordered.isEmpty { return [] }
        let lastClockInIndex = ordered.lastIndex { $0.eventType == .clockIn }
        let scoped: ArraySlice<WorkdayEvent>
        if let idx = lastClockInIndex {
            scoped = ordered[idx...]
        } else {
            scoped = ordered[...]
        }
        return Array(scoped.suffix(10))
    }

    private static func translateEvent(_ item: WorkdayEvent) -> String {
        switch item.eventType {
        case .clockIn: return "Entrada"
        case .startCoffeeBreak: return "Inicio café"
        case .endCoffeeBreak: return "Fin café"
        case .startFoodBreak: return "Inicio comida"
        case .endFoodBreak: return "Fin comida"
        case .clockOut: return "Salida"
        }
    }

    private static func formatDuration(_ value: TimeInterval) -> String {
        let totalSeconds = Int(value.rounded())
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        return String(format: "%02dh %02dm", hours, minutes)
    }

    private static func formatBreakDuration(_ value: TimeInterval) -> String {
        formatDuration(value)
    }

    private static func formatDurationWithSeconds(_ value: TimeInterval) -> String {
        let totalSeconds = Int(value.rounded())
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let seconds = totalSeconds % 60
        return String(format: "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private static func formatLastActionTime(_ date: Date?, locale: Locale) -> String {
        guard let date else { return "—" }
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        formatter.timeZone = TimeZone.current
        return formatter.string(from: date)
    }

    private static func formatTimelineForDisplay(_ value: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "" }
        let items: [String]
        if trimmed.contains(" · ") {
            items = trimmed.components(separatedBy: " · ")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
        } else if trimmed.contains(" Â· ") {
            items = trimmed.components(separatedBy: " Â· ")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
        } else {
            items = [trimmed]
        }
        if items.count == 1 {
            return items[0]
        }
        return items.map { "• \($0)" }.joined(separator: "\n")
    }
}
