import Foundation

// MARK: - Auth & user

struct ClockingLoginRequestBody: Encodable {
    let email: String
    let password: String
}

struct ClockingLoginResponseBody: Decodable {
    let token: String
    let sessionId: Int64
    let expiresAt: String
    let user: ClockingUserDTO
}

struct ClockingSessionResponseBody: Decodable {
    let authenticated: Bool?
    let sessionId: Int64
    let expiresAt: String
    let user: ClockingUserDTO
}

struct ClockingUserDTO: Decodable {
    let id: Int64
    let name: String
    let email: String
}

// MARK: - Clocking

enum ClockingModeDTO: String, Codable, Sendable {
    case WITH_MEAL
    case TWO_BREAKS
}

struct ClockingStateDTO: Decodable, Sendable {
    let mode: ClockingModeDTO
    let currentStepIndex: Int
    let finished: Bool
    let lastActionLabel: String
    let lastActionTime: String
    let nextStepLabel: String
    let currentState: String?
    let nextAllowedAction: String?
    let enabledActions: [String]
    let lastEventType: String?
    let elapsedSeconds: Int64
}

struct AttendanceActionRequestBody: Encodable {
    let action: String
}

struct SetModeRequestBody: Encodable {
    let mode: ClockingModeDTO
}
