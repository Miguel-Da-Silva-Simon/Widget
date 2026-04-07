import Foundation

/// Tiempo de jornada neta (gross − descansos desde el inicio de turno), alineado con
/// `LocalJsonTimeTrackingService.calculateWorkedDurationForShift` y con `now` variable (crono en vivo).
enum TimeTrackingLiveDuration {
    static func netWorkedSeconds(
        shiftStartUtc: Date,
        breakSessions: [BreakSession],
        nowUtc: Date
    ) -> TimeInterval {
        guard nowUtc > shiftStartUtc else { return 0 }
        let gross = nowUtc.timeIntervalSince(shiftStartUtc)
        let breakTotal = breakSessions
            .filter { $0.startedAtUtc >= shiftStartUtc }
            .reduce(0) { $0 + $1.duration(nowUtc: nowUtc) }
        return max(0, gross - breakTotal)
    }
}
