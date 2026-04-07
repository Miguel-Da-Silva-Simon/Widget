import Foundation

public enum TimeTrackingStatus: String, Codable, CaseIterable, Sendable {
    case notClockedIn = "NotClockedIn"
    case working = "Working"
    case onBreak = "OnBreak"
    case offDuty = "OffDuty"
}

public enum TimeTrackingAction: String, Codable, CaseIterable, Sendable {
    case none = "None"
    case clockIn = "ClockIn"
    case startBreak = "StartBreak"
    case endBreak = "EndBreak"
    case clockOut = "ClockOut"
    case startCoffeeBreak = "StartCoffeeBreak"
    case endCoffeeBreak = "EndCoffeeBreak"
    case startFoodBreak = "StartFoodBreak"
    case endFoodBreak = "EndFoodBreak"
}

public enum WorkdayEventType: String, Codable, CaseIterable, Sendable {
    case clockIn = "ClockIn"
    case startCoffeeBreak = "StartCoffeeBreak"
    case endCoffeeBreak = "EndCoffeeBreak"
    case startFoodBreak = "StartFoodBreak"
    case endFoodBreak = "EndFoodBreak"
    case clockOut = "ClockOut"
}

public enum BreakType: String, Codable, CaseIterable, Sendable {
    case none = "None"
    case coffee = "Coffee"
    case food = "Food"
}

public enum AuthenticationState: String, Codable, Sendable {
    case unknown = "Unknown"
    case signedOut = "SignedOut"
    case signedIn = "SignedIn"
}
