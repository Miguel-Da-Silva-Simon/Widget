import Foundation

struct TimeTrackingTransitionError: Error {
    let message: String
}

enum TimeTrackingStateMachine {
    private struct Transition: Hashable {
        let from: TimeTrackingStatus
        let action: TimeTrackingAction
        let to: TimeTrackingStatus
    }

    private static let transitions: [Transition] = [
        Transition(from: .notClockedIn, action: .clockIn, to: .working),
        Transition(from: .offDuty, action: .clockIn, to: .working),
        Transition(from: .working, action: .startBreak, to: .onBreak),
        Transition(from: .working, action: .startCoffeeBreak, to: .onBreak),
        Transition(from: .working, action: .startFoodBreak, to: .onBreak),
        Transition(from: .onBreak, action: .endBreak, to: .working),
        Transition(from: .onBreak, action: .endCoffeeBreak, to: .working),
        Transition(from: .onBreak, action: .endFoodBreak, to: .working),
        Transition(from: .working, action: .clockOut, to: .offDuty),
        Transition(from: .onBreak, action: .clockOut, to: .offDuty)
    ]

    private static let lookup: [Key: TimeTrackingStatus] = {
        var map: [Key: TimeTrackingStatus] = [:]
        for t in transitions {
            map[Key(from: t.from, action: t.action)] = t.to
        }
        return map
    }()

    private struct Key: Hashable {
        let from: TimeTrackingStatus
        let action: TimeTrackingAction
    }

    private static let orderedActions: [TimeTrackingAction] = [
        .clockIn,
        .startCoffeeBreak,
        .startFoodBreak,
        .endCoffeeBreak,
        .endFoodBreak,
        .clockOut
    ]

    static func tryTransition(
        currentStatus: TimeTrackingStatus,
        activeBreakType: BreakType,
        action: TimeTrackingAction
    ) -> Result<TimeTrackingStatus, TimeTrackingTransitionError> {
        guard let next = lookup[Key(from: currentStatus, action: action)] else {
            return .failure(TimeTrackingTransitionError(message: invalidTransitionMessage(status: currentStatus, action: action)))
        }
        if let error = breakTypeCompatibilityError(activeBreakType: activeBreakType, action: action) {
            return .failure(TimeTrackingTransitionError(message: error))
        }
        return .success(next)
    }

    static func availableActions(status: TimeTrackingStatus, activeBreakType: BreakType) -> [TimeTrackingAction] {
        orderedActions.filter { action in
            lookup[Key(from: status, action: action)] != nil
                && breakTypeCompatibilityError(activeBreakType: activeBreakType, action: action) == nil
        }
    }

    private static func breakTypeCompatibilityError(activeBreakType: BreakType, action: TimeTrackingAction) -> String? {
        switch action {
        case .startBreak, .startCoffeeBreak, .startFoodBreak:
            if activeBreakType != .none {
                return "Ya hay un descanso activo de tipo \(activeBreakType)."
            }
            return nil
        case .endBreak:
            if activeBreakType == .none {
                return "No hay un descanso activo para finalizar."
            }
            return nil
        case .endCoffeeBreak:
            if activeBreakType == .coffee { return nil }
            if activeBreakType == .none {
                return "No hay un descanso de café activo para finalizar."
            }
            return "El descanso activo actual es \(activeBreakType), no café."
        case .endFoodBreak:
            if activeBreakType == .food { return nil }
            if activeBreakType == .none {
                return "No hay un descanso de comida activo para finalizar."
            }
            return "El descanso activo actual es \(activeBreakType), no comida."
        default:
            return nil
        }
    }

    private static func invalidTransitionMessage(status: TimeTrackingStatus, action: TimeTrackingAction) -> String {
        switch action {
        case .clockIn:
            return "No podés entrar cuando el estado actual es \(status)."
        case .startBreak:
            return "No podés iniciar descanso cuando el estado actual es \(status)."
        case .startCoffeeBreak:
            return "No podés iniciar un descanso de café cuando el estado actual es \(status)."
        case .startFoodBreak:
            return "No podés iniciar un descanso de comida cuando el estado actual es \(status)."
        case .endBreak:
            return "No podés finalizar descanso cuando el estado actual es \(status)."
        case .endCoffeeBreak:
            return "No podés finalizar un descanso de café cuando el estado actual es \(status)."
        case .endFoodBreak:
            return "No podés finalizar un descanso de comida cuando el estado actual es \(status)."
        case .clockOut:
            return "No podés salir cuando el estado actual es \(status)."
        default:
            return "La acción \(action) no es válida para el estado \(status)."
        }
    }
}

extension TimeTrackingSnapshot {
    static func initial(now: Date = Date()) -> TimeTrackingSnapshot {
        let day = Calendar.current.startOfDay(for: now)
        return TimeTrackingSnapshot(
            status: .notClockedIn,
            lastAction: .none,
            lastActionAtUtc: nil,
            currentShiftStartedAtUtc: nil,
            activeBreakType: .none,
            activeBreak: nil,
            history: [],
            workdayEvents: [],
            breakSessions: [],
            summary: .empty(dayStart: day),
            availableActions: TimeTrackingStateMachine.availableActions(status: .notClockedIn, activeBreakType: .none)
        )
    }
}
