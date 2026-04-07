import Foundation

public struct CurrentUser: Equatable, Sendable {
    public let userId: String
    public let displayName: String
    public let email: String?

    public init(userId: String, displayName: String, email: String?) {
        self.userId = userId
        self.displayName = displayName
        self.email = email
    }
}

public struct UserSession: Equatable, Sendable {
    public let state: AuthenticationState
    public let user: CurrentUser?
    public let signedInAtUtc: Date?

    public var isAuthenticated: Bool {
        state == .signedIn && user != nil
    }

    public init(state: AuthenticationState, user: CurrentUser?, signedInAtUtc: Date?) {
        self.state = state
        self.user = user
        self.signedInAtUtc = signedInAtUtc
    }

    public static func signedOut() -> UserSession {
        UserSession(state: .signedOut, user: nil, signedInAtUtc: nil)
    }
}

public struct BreakSession: Equatable, Sendable, Codable {
    public var type: BreakType
    public var startedAtUtc: Date
    public var endedAtUtc: Date?

    public enum CodingKeys: String, CodingKey {
        case type = "Type"
        case startedAtUtc = "StartedAtUtc"
        case endedAtUtc = "EndedAtUtc"
    }

    public var isActive: Bool { endedAtUtc == nil }

    public init(type: BreakType, startedAtUtc: Date, endedAtUtc: Date?) {
        self.type = type
        self.startedAtUtc = startedAtUtc
        self.endedAtUtc = endedAtUtc
    }

    public func duration(nowUtc: Date) -> TimeInterval {
        let end = endedAtUtc ?? nowUtc
        guard end > startedAtUtc else { return 0 }
        return end.timeIntervalSince(startedAtUtc)
    }
}

public struct WorkdayEvent: Equatable, Sendable, Codable {
    public var occurredAtUtc: Date
    public var eventType: WorkdayEventType
    public var statusAfterEvent: TimeTrackingStatus
    public var breakType: BreakType

    public enum CodingKeys: String, CodingKey {
        case occurredAtUtc = "OccurredAtUtc"
        case eventType = "EventType"
        case statusAfterEvent = "StatusAfterEvent"
        case breakType = "BreakType"
    }

    public init(occurredAtUtc: Date, eventType: WorkdayEventType, statusAfterEvent: TimeTrackingStatus, breakType: BreakType) {
        self.occurredAtUtc = occurredAtUtc
        self.eventType = eventType
        self.statusAfterEvent = statusAfterEvent
        self.breakType = breakType
    }
}

public struct TimeTrackingHistoryEntry: Equatable, Sendable, Codable {
    public var occurredAtUtc: Date
    public var action: TimeTrackingAction
    public var status: TimeTrackingStatus
    public var breakType: BreakType

    public enum CodingKeys: String, CodingKey {
        case occurredAtUtc = "OccurredAtUtc"
        case action = "Action"
        case status = "Status"
        case breakType = "BreakType"
    }

    public init(occurredAtUtc: Date, action: TimeTrackingAction, status: TimeTrackingStatus, breakType: BreakType) {
        self.occurredAtUtc = occurredAtUtc
        self.action = action
        self.status = status
        self.breakType = breakType
    }
}

public struct DailyWorkSummary: Equatable, Sendable {
    public let dayStart: Date
    public let currentShiftWorkedDuration: TimeInterval
    public let lastCompletedShiftWorkedDuration: TimeInterval
    public let workedThisMonthDuration: TimeInterval
    public let coffeeBreakDurationToday: TimeInterval
    public let foodBreakDurationToday: TimeInterval

    public init(
        dayStart: Date,
        currentShiftWorkedDuration: TimeInterval,
        lastCompletedShiftWorkedDuration: TimeInterval,
        workedThisMonthDuration: TimeInterval,
        coffeeBreakDurationToday: TimeInterval,
        foodBreakDurationToday: TimeInterval
    ) {
        self.dayStart = dayStart
        self.currentShiftWorkedDuration = currentShiftWorkedDuration
        self.lastCompletedShiftWorkedDuration = lastCompletedShiftWorkedDuration
        self.workedThisMonthDuration = workedThisMonthDuration
        self.coffeeBreakDurationToday = coffeeBreakDurationToday
        self.foodBreakDurationToday = foodBreakDurationToday
    }

    public static func empty(dayStart: Date) -> DailyWorkSummary {
        DailyWorkSummary(
            dayStart: dayStart,
            currentShiftWorkedDuration: 0,
            lastCompletedShiftWorkedDuration: 0,
            workedThisMonthDuration: 0,
            coffeeBreakDurationToday: 0,
            foodBreakDurationToday: 0
        )
    }
}

public struct TimeTrackingSnapshot: Equatable, Sendable {
    public let status: TimeTrackingStatus
    public let lastAction: TimeTrackingAction
    public let lastActionAtUtc: Date?
    /// Inicio del turno actual; necesario para el crono en vivo (el resumen se congela hasta la próxima acción).
    public let currentShiftStartedAtUtc: Date?
    public let activeBreakType: BreakType
    public let activeBreak: BreakSession?
    public let history: [TimeTrackingHistoryEntry]
    public let workdayEvents: [WorkdayEvent]
    public let breakSessions: [BreakSession]
    public let summary: DailyWorkSummary
    public let availableActions: [TimeTrackingAction]

    public init(
        status: TimeTrackingStatus,
        lastAction: TimeTrackingAction,
        lastActionAtUtc: Date?,
        currentShiftStartedAtUtc: Date?,
        activeBreakType: BreakType,
        activeBreak: BreakSession?,
        history: [TimeTrackingHistoryEntry],
        workdayEvents: [WorkdayEvent],
        breakSessions: [BreakSession],
        summary: DailyWorkSummary,
        availableActions: [TimeTrackingAction]
    ) {
        self.status = status
        self.lastAction = lastAction
        self.lastActionAtUtc = lastActionAtUtc
        self.currentShiftStartedAtUtc = currentShiftStartedAtUtc
        self.activeBreakType = activeBreakType
        self.activeBreak = activeBreak
        self.history = history
        self.workdayEvents = workdayEvents
        self.breakSessions = breakSessions
        self.summary = summary
        self.availableActions = availableActions
    }
}

struct TimeTrackingStateDocument: Equatable, Sendable, Codable {
    var status: TimeTrackingStatus
    var lastAction: TimeTrackingAction
    var lastActionAtUtc: Date?
    var currentShiftStartedAtUtc: Date?
    var activeBreakType: BreakType
    var lastCompletedShiftWorkedSeconds: Int64
    var workedThisMonthSeconds: Int64
    var history: [TimeTrackingHistoryEntry]
    var workdayEvents: [WorkdayEvent]
    var breakSessions: [BreakSession]
    /// Si viene del backend, el widget usa esta lista en lugar de inferir acciones con la máquina de estados local.
    var apiAvailableActions: [TimeTrackingAction]?

    enum CodingKeys: String, CodingKey {
        case status = "Status"
        case lastAction = "LastAction"
        case lastActionAtUtc = "LastActionAtUtc"
        case currentShiftStartedAtUtc = "CurrentShiftStartedAtUtc"
        case activeBreakType = "ActiveBreakType"
        case lastCompletedShiftWorkedSeconds = "LastCompletedShiftWorkedSeconds"
        case workedThisMonthSeconds = "WorkedThisMonthSeconds"
        case history = "History"
        case workdayEvents = "WorkdayEvents"
        case breakSessions = "BreakSessions"
        case apiAvailableActions = "ApiAvailableActions"
    }

    static func createDefault() -> TimeTrackingStateDocument {
        TimeTrackingStateDocument(
            status: .notClockedIn,
            lastAction: .none,
            lastActionAtUtc: nil,
            currentShiftStartedAtUtc: nil,
            activeBreakType: .none,
            lastCompletedShiftWorkedSeconds: 0,
            workedThisMonthSeconds: 0,
            history: [],
            workdayEvents: [],
            breakSessions: [],
            apiAvailableActions: nil
        )
    }
}

struct UserSessionDocument: Equatable, Sendable, Codable {
    var state: AuthenticationState
    var userId: String?
    var displayName: String?
    var email: String?
    var signedInAtUtc: Date?
    var profilePhotoFileName: String?

    enum CodingKeys: String, CodingKey {
        case state = "State"
        case userId = "UserId"
        case displayName = "DisplayName"
        case email = "Email"
        case signedInAtUtc = "SignedInAtUtc"
        case profilePhotoFileName = "ProfilePhotoFileName"
    }
}

struct TimeTrackingCommandResult: Sendable {
    let success: Bool
    let snapshot: TimeTrackingSnapshot
    let message: String?

    static func success(_ snapshot: TimeTrackingSnapshot) -> TimeTrackingCommandResult {
        TimeTrackingCommandResult(success: true, snapshot: snapshot, message: nil)
    }

    static func failure(_ snapshot: TimeTrackingSnapshot, _ message: String) -> TimeTrackingCommandResult {
        TimeTrackingCommandResult(success: false, snapshot: snapshot, message: message)
    }
}
