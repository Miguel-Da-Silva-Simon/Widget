import Foundation

struct CurrentUser: Equatable, Sendable {
    let userId: String
    let displayName: String
    let email: String?
}

struct UserSession: Equatable, Sendable {
    let state: AuthenticationState
    let user: CurrentUser?
    let signedInAtUtc: Date?

    var isAuthenticated: Bool {
        state == .signedIn && user != nil
    }

    static func signedOut() -> UserSession {
        UserSession(state: .signedOut, user: nil, signedInAtUtc: nil)
    }
}

struct BreakSession: Equatable, Sendable, Codable {
    var type: BreakType
    var startedAtUtc: Date
    var endedAtUtc: Date?

    enum CodingKeys: String, CodingKey {
        case type = "Type"
        case startedAtUtc = "StartedAtUtc"
        case endedAtUtc = "EndedAtUtc"
    }

    var isActive: Bool { endedAtUtc == nil }

    func duration(nowUtc: Date) -> TimeInterval {
        let end = endedAtUtc ?? nowUtc
        guard end > startedAtUtc else { return 0 }
        return end.timeIntervalSince(startedAtUtc)
    }
}

struct WorkdayEvent: Equatable, Sendable, Codable {
    var occurredAtUtc: Date
    var eventType: WorkdayEventType
    var statusAfterEvent: TimeTrackingStatus
    var breakType: BreakType

    enum CodingKeys: String, CodingKey {
        case occurredAtUtc = "OccurredAtUtc"
        case eventType = "EventType"
        case statusAfterEvent = "StatusAfterEvent"
        case breakType = "BreakType"
    }
}

struct TimeTrackingHistoryEntry: Equatable, Sendable, Codable {
    var occurredAtUtc: Date
    var action: TimeTrackingAction
    var status: TimeTrackingStatus
    var breakType: BreakType

    enum CodingKeys: String, CodingKey {
        case occurredAtUtc = "OccurredAtUtc"
        case action = "Action"
        case status = "Status"
        case breakType = "BreakType"
    }
}

struct DailyWorkSummary: Equatable, Sendable {
    let dayStart: Date
    let currentShiftWorkedDuration: TimeInterval
    let lastCompletedShiftWorkedDuration: TimeInterval
    let workedThisMonthDuration: TimeInterval
    let coffeeBreakDurationToday: TimeInterval
    let foodBreakDurationToday: TimeInterval

    static func empty(dayStart: Date) -> DailyWorkSummary {
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

struct TimeTrackingSnapshot: Equatable, Sendable {
    let status: TimeTrackingStatus
    let lastAction: TimeTrackingAction
    let lastActionAtUtc: Date?
    /// Inicio del turno actual; necesario para el crono en vivo (el resumen se congela hasta la próxima acción).
    let currentShiftStartedAtUtc: Date?
    let activeBreakType: BreakType
    let activeBreak: BreakSession?
    let history: [TimeTrackingHistoryEntry]
    let workdayEvents: [WorkdayEvent]
    let breakSessions: [BreakSession]
    let summary: DailyWorkSummary
    let availableActions: [TimeTrackingAction]
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
            breakSessions: []
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
