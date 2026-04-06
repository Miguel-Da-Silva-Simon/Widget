import Foundation

final class LocalJsonTimeTrackingService: @unchecked Sendable {
    private let store = JsonTimeTrackingStore()

    func getState() throws -> TimeTrackingSnapshot {
        let document = try store.load()
        return toSnapshot(document: document, nowUtc: Date())
    }

    func clockIn() throws -> TimeTrackingCommandResult {
        try execute(
            action: .clockIn,
            eventType: .clockIn,
            onSuccess: { doc, nowUtc in
                doc.currentShiftStartedAtUtc = nowUtc
                doc.activeBreakType = .none
            }
        )
    }

    func startBreak() throws -> TimeTrackingCommandResult {
        try startCoffeeBreak()
    }

    func endBreak() throws -> TimeTrackingCommandResult {
        var document = try store.load()
        switch document.activeBreakType {
        case .coffee:
            return try endCoffeeBreak()
        case .food:
            return try endFoodBreak()
        case .none:
            return try buildFailure(document, "No hay un descanso activo para finalizar.")
        }
    }

    func startCoffeeBreak() throws -> TimeTrackingCommandResult {
        try execute(
            action: .startCoffeeBreak,
            eventType: .startCoffeeBreak,
            onSuccess: { doc, nowUtc in
                doc.activeBreakType = .coffee
                doc.breakSessions.append(BreakSession(type: .coffee, startedAtUtc: nowUtc, endedAtUtc: nil))
            }
        )
    }

    func endCoffeeBreak() throws -> TimeTrackingCommandResult {
        try execute(
            action: .endCoffeeBreak,
            eventType: .endCoffeeBreak,
            onSuccess: { doc, nowUtc in
                closeActiveBreak(document: &doc, breakType: .coffee, nowUtc: nowUtc)
                doc.activeBreakType = .none
            }
        )
    }

    func startFoodBreak() throws -> TimeTrackingCommandResult {
        try execute(
            action: .startFoodBreak,
            eventType: .startFoodBreak,
            onSuccess: { doc, nowUtc in
                doc.activeBreakType = .food
                doc.breakSessions.append(BreakSession(type: .food, startedAtUtc: nowUtc, endedAtUtc: nil))
            }
        )
    }

    func endFoodBreak() throws -> TimeTrackingCommandResult {
        try execute(
            action: .endFoodBreak,
            eventType: .endFoodBreak,
            onSuccess: { doc, nowUtc in
                closeActiveBreak(document: &doc, breakType: .food, nowUtc: nowUtc)
                doc.activeBreakType = .none
            }
        )
    }

    func clockOut() throws -> TimeTrackingCommandResult {
        try execute(
            action: .clockOut,
            eventType: .clockOut,
            onSuccess: { doc, nowUtc in
                closeActiveBreakIfAny(document: &doc, nowUtc: nowUtc)
                if let start = doc.currentShiftStartedAtUtc {
                    let completed = calculateWorkedDurationForShift(document: doc, shiftStartUtc: start, nowUtc: nowUtc)
                    doc.lastCompletedShiftWorkedSeconds = Int64(completed)
                    doc.workedThisMonthSeconds += Int64(completed)
                }
                doc.currentShiftStartedAtUtc = nil
                doc.activeBreakType = .none
            }
        )
    }

    private func execute(
        action: TimeTrackingAction,
        eventType: WorkdayEventType,
        onSuccess: (inout TimeTrackingStateDocument, Date) -> Void
    ) throws -> TimeTrackingCommandResult {
        var document = try store.load()
        let nowUtc = Date()

        switch TimeTrackingStateMachine.tryTransition(
            currentStatus: document.status,
            activeBreakType: document.activeBreakType,
            action: action
        ) {
        case .failure(let err):
            return try buildFailure(document, err.message)
        case .success(let nextStatus):
            let breakBefore = document.activeBreakType
            onSuccess(&document, nowUtc)
            let breakForAction = resolveBreakTypeForAction(action: action, activeBreakTypeBeforeAction: breakBefore)
            document.status = nextStatus
            document.lastAction = action
            document.lastActionAtUtc = nowUtc
            document.history.append(
                TimeTrackingHistoryEntry(
                    occurredAtUtc: nowUtc,
                    action: action,
                    status: nextStatus,
                    breakType: breakForAction
                )
            )
            document.workdayEvents.append(
                WorkdayEvent(
                    occurredAtUtc: nowUtc,
                    eventType: eventType,
                    statusAfterEvent: nextStatus,
                    breakType: breakForAction
                )
            )
            trimHistory(&document)
            try store.save(document)
            return .success(toSnapshot(document: document, nowUtc: nowUtc))
        }
    }

    private func buildFailure(_ document: TimeTrackingStateDocument, _ message: String) throws -> TimeTrackingCommandResult {
        .failure(toSnapshot(document: document, nowUtc: Date()), message)
    }

    private func toSnapshot(document: TimeTrackingStateDocument, nowUtc: Date) -> TimeTrackingSnapshot {
        let calendar = Calendar.current
        let dayStart = calendar.startOfDay(for: nowUtc)
        let activeBreak = document.breakSessions.last { $0.isActive }
        let breakSessionsForSummary = getBreakSessionsForSummary(document: document, nowUtc: nowUtc)
        let todayEvents = document.workdayEvents
            .filter { calendar.isDate($0.occurredAtUtc, inSameDayAs: nowUtc) }
            .sorted { $0.occurredAtUtc < $1.occurredAtUtc }

        let currentShiftWorked: TimeInterval
        if let start = document.currentShiftStartedAtUtc {
            currentShiftWorked = calculateWorkedDurationForShift(document: document, shiftStartUtc: start, nowUtc: nowUtc)
        } else {
            currentShiftWorked = 0
        }

        let summary = DailyWorkSummary(
            dayStart: dayStart,
            currentShiftWorkedDuration: currentShiftWorked,
            lastCompletedShiftWorkedDuration: TimeInterval(document.lastCompletedShiftWorkedSeconds),
            workedThisMonthDuration: TimeInterval(document.workedThisMonthSeconds) + currentShiftWorked,
            coffeeBreakDurationToday: sumBreakDuration(sessions: breakSessionsForSummary, type: .coffee, nowUtc: nowUtc),
            foodBreakDurationToday: sumBreakDuration(sessions: breakSessionsForSummary, type: .food, nowUtc: nowUtc)
        )

        return TimeTrackingSnapshot(
            status: document.status,
            lastAction: document.lastAction,
            lastActionAtUtc: document.lastActionAtUtc,
            currentShiftStartedAtUtc: document.currentShiftStartedAtUtc,
            activeBreakType: document.activeBreakType,
            activeBreak: activeBreak,
            history: document.history.sorted { $0.occurredAtUtc > $1.occurredAtUtc },
            workdayEvents: todayEvents,
            breakSessions: breakSessionsForSummary,
            summary: summary,
            availableActions: TimeTrackingStateMachine.availableActions(
                status: document.status,
                activeBreakType: document.activeBreakType
            )
        )
    }

    private func getBreakSessionsForSummary(document: TimeTrackingStateDocument, nowUtc: Date) -> [BreakSession] {
        let calendar = Calendar.current
        if let shiftStart = document.currentShiftStartedAtUtc {
            return document.breakSessions
                .filter { $0.startedAtUtc >= shiftStart }
                .sorted { $0.startedAtUtc < $1.startedAtUtc }
        }

        let clockOutsToday = document.workdayEvents
            .filter { $0.eventType == .clockOut && calendar.isDate($0.occurredAtUtc, inSameDayAs: nowUtc) }
            .sorted { $0.occurredAtUtc < $1.occurredAtUtc }

        if let lastClockOut = clockOutsToday.last {
            let clockInsToday = document.workdayEvents
                .filter {
                    $0.eventType == .clockIn
                        && calendar.isDate($0.occurredAtUtc, inSameDayAs: nowUtc)
                        && $0.occurredAtUtc <= lastClockOut.occurredAtUtc
                }
                .sorted { $0.occurredAtUtc < $1.occurredAtUtc }

            if let lastClockIn = clockInsToday.last {
                let shiftStartUtc = lastClockIn.occurredAtUtc
                let shiftEndUtc = lastClockOut.occurredAtUtc
                return document.breakSessions
                    .filter { $0.startedAtUtc >= shiftStartUtc && $0.startedAtUtc <= shiftEndUtc }
                    .sorted { $0.startedAtUtc < $1.startedAtUtc }
            }
        }

        return document.breakSessions
            .filter { calendar.isDate($0.startedAtUtc, inSameDayAs: nowUtc) }
            .sorted { $0.startedAtUtc < $1.startedAtUtc }
    }

    private func sumBreakDuration(sessions: [BreakSession], type: BreakType, nowUtc: Date) -> TimeInterval {
        sessions.filter { $0.type == type }.reduce(0) { $0 + $1.duration(nowUtc: nowUtc) }
    }

    private func closeActiveBreak(document: inout TimeTrackingStateDocument, breakType: BreakType, nowUtc: Date) {
        guard let index = document.breakSessions.lastIndex(where: { $0.isActive && $0.type == breakType }) else { return }
        var session = document.breakSessions[index]
        session.endedAtUtc = nowUtc
        document.breakSessions[index] = session
    }

    private func closeActiveBreakIfAny(document: inout TimeTrackingStateDocument, nowUtc: Date) {
        guard let index = document.breakSessions.lastIndex(where: { $0.isActive }) else { return }
        var session = document.breakSessions[index]
        session.endedAtUtc = nowUtc
        document.breakSessions[index] = session
    }

    private func calculateWorkedDurationForShift(
        document: TimeTrackingStateDocument,
        shiftStartUtc: Date,
        nowUtc: Date
    ) -> TimeInterval {
        TimeTrackingLiveDuration.netWorkedSeconds(
            shiftStartUtc: shiftStartUtc,
            breakSessions: document.breakSessions,
            nowUtc: nowUtc
        )
    }

    private func trimHistory(_ document: inout TimeTrackingStateDocument) {
        if document.history.count > LocalStatePaths.maxHistoryEntries {
            document.history = document.history
                .sorted { $0.occurredAtUtc > $1.occurredAtUtc }
                .prefix(LocalStatePaths.maxHistoryEntries)
                .sorted { $0.occurredAtUtc < $1.occurredAtUtc }
        }

        let cutoff = Calendar.current.date(byAdding: .day, value: -30, to: Date()) ?? Date.distantPast
        document.workdayEvents = document.workdayEvents
            .filter { $0.occurredAtUtc >= cutoff }
            .sorted { $0.occurredAtUtc < $1.occurredAtUtc }
        document.breakSessions = document.breakSessions
            .filter { $0.startedAtUtc >= cutoff }
            .sorted { $0.startedAtUtc < $1.startedAtUtc }
    }

    private func resolveBreakTypeForAction(action: TimeTrackingAction, activeBreakTypeBeforeAction: BreakType) -> BreakType {
        switch action {
        case .startCoffeeBreak, .endCoffeeBreak:
            return .coffee
        case .startFoodBreak, .endFoodBreak:
            return .food
        case .startBreak, .endBreak:
            return activeBreakTypeBeforeAction
        default:
            return .none
        }
    }
}
