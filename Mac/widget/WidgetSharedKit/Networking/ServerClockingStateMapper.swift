import Foundation

/// Convierte la respuesta del backend Spring en el documento JSON que lee el widget (misma zona que `app.timezone` en el servidor).
enum ServerClockingStateMapper {
    private static let madrid = TimeZone(identifier: "Europe/Madrid") ?? .current

    static func document(from dto: ClockingStateDTO, now: Date = Date()) -> TimeTrackingStateDocument {
        let apiState = dto.currentState.flatMap { ApiAttendanceState(rawValue: $0) }
        let (status, breakType) = mapAttendanceState(apiState, finished: dto.finished)

        let lastActionAt = parseHmToday(dto.lastActionTime, now: now)
        let lastAction = mapLastEventType(dto.lastEventType)

        let shiftStart: Date?
        if dto.finished || apiState == .some(.NOT_STARTED) {
            shiftStart = nil
        } else if dto.elapsedSeconds > 0 {
            shiftStart = now.addingTimeInterval(-TimeInterval(dto.elapsedSeconds))
        } else {
            shiftStart = nil
        }

        let lastCompleted: Int64 = dto.finished ? dto.elapsedSeconds : 0

        let breakSessions = buildBreakSessions(
            apiState: apiState,
            lastActionAt: lastActionAt
        )

        let apiActions = mapEnabledActions(dto.enabledActions)

        let (history, events) = buildHistoryAndEvents(
            lastEventType: dto.lastEventType,
            lastActionAt: lastActionAt,
            status: status,
            breakType: breakType
        )

        return TimeTrackingStateDocument(
            status: status,
            lastAction: lastAction,
            lastActionAtUtc: lastActionAt,
            currentShiftStartedAtUtc: shiftStart,
            activeBreakType: breakType,
            lastCompletedShiftWorkedSeconds: lastCompleted,
            workedThisMonthSeconds: 0,
            history: history,
            workdayEvents: events,
            breakSessions: breakSessions,
            apiAvailableActions: apiActions.isEmpty ? nil : apiActions
        )
    }

    private enum ApiAttendanceState: String {
        case NOT_STARTED, WORKING, BREAK_ACTIVE, MEAL_ACTIVE, FINISHED
    }

    private static func mapAttendanceState(_ state: ApiAttendanceState?, finished: Bool) -> (TimeTrackingStatus, BreakType) {
        if finished || state == .FINISHED {
            return (.offDuty, .none)
        }
        switch state {
        case .none, .NOT_STARTED:
            return (.notClockedIn, .none)
        case .WORKING:
            return (.working, .none)
        case .BREAK_ACTIVE:
            return (.onBreak, .coffee)
        case .MEAL_ACTIVE:
            return (.onBreak, .food)
        case .FINISHED:
            return (.offDuty, .none)
        }
    }

    private static func mapLastEventType(_ raw: String?) -> TimeTrackingAction {
        guard let raw else { return .none }
        switch raw {
        case "CLOCK_IN": return .clockIn
        case "CLOCK_OUT": return .clockOut
        case "BREAK_START": return .startCoffeeBreak
        case "BREAK_END": return .endCoffeeBreak
        case "MEAL_START": return .startFoodBreak
        case "MEAL_END": return .endFoodBreak
        default: return .none
        }
    }

    private static func mapWorkdayEventType(_ raw: String?) -> WorkdayEventType? {
        guard let raw else { return nil }
        switch raw {
        case "CLOCK_IN": return .clockIn
        case "CLOCK_OUT": return .clockOut
        case "BREAK_START": return .startCoffeeBreak
        case "BREAK_END": return .endCoffeeBreak
        case "MEAL_START": return .startFoodBreak
        case "MEAL_END": return .endFoodBreak
        default: return nil
        }
    }

    private static func mapEnabledActions(_ strings: [String]) -> [TimeTrackingAction] {
        var out: [TimeTrackingAction] = []
        for s in strings {
            guard let a = mapSingleEnabled(s) else { continue }
            if !out.contains(a) { out.append(a) }
        }
        return out
    }

    private static func mapSingleEnabled(_ raw: String) -> TimeTrackingAction? {
        switch raw {
        case "CLOCK_IN": return .clockIn
        case "CLOCK_OUT": return .clockOut
        case "BREAK_START": return .startCoffeeBreak
        case "BREAK_END": return .endCoffeeBreak
        case "MEAL_START": return .startFoodBreak
        case "MEAL_END": return .endFoodBreak
        default: return nil
        }
    }

    private static func buildBreakSessions(
        apiState: ApiAttendanceState?,
        lastActionAt: Date?
    ) -> [BreakSession] {
        guard let apiState else { return [] }
        let start = lastActionAt ?? Date()
        switch apiState {
        case .BREAK_ACTIVE:
            return [BreakSession(type: .coffee, startedAtUtc: start, endedAtUtc: nil)]
        case .MEAL_ACTIVE:
            return [BreakSession(type: .food, startedAtUtc: start, endedAtUtc: nil)]
        default:
            return []
        }
    }

    private static func buildHistoryAndEvents(
        lastEventType: String?,
        lastActionAt: Date?,
        status: TimeTrackingStatus,
        breakType: BreakType
    ) -> ([TimeTrackingHistoryEntry], [WorkdayEvent]) {
        guard let at = lastActionAt,
              let wt = mapWorkdayEventType(lastEventType)
        else {
            return ([], [])
        }
        let action = mapLastEventType(lastEventType)
        let entry = TimeTrackingHistoryEntry(
            occurredAtUtc: at,
            action: action,
            status: status,
            breakType: breakType
        )
        let ev = WorkdayEvent(
            occurredAtUtc: at,
            eventType: wt,
            statusAfterEvent: status,
            breakType: breakType
        )
        return ([entry], [ev])
    }

    /// `lastActionTime` del API es `HH:mm` en zona Europe/Madrid (mismo criterio que el backend).
    private static func parseHmToday(_ hm: String, now: Date) -> Date? {
        let t = hm.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty || t == "--:--" { return nil }
        let parts = t.split(separator: ":")
        guard parts.count >= 2,
              let h = Int(parts[0]),
              let m = Int(parts[1])
        else { return nil }

        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = madrid
        let dayComponents = cal.dateComponents([.year, .month, .day], from: now)
        var dc = DateComponents()
        dc.year = dayComponents.year
        dc.month = dayComponents.month
        dc.day = dayComponents.day
        dc.hour = h
        dc.minute = m
        dc.second = parts.count > 2 ? Int(parts[2]) : 0
        return cal.date(from: dc)
    }
}
