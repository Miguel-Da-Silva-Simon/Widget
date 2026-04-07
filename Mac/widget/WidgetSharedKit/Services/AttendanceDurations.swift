import Foundation

/// Duraciones estándar de descanso/comida, alineadas con Android (`AttendanceDurations.kt`).
public enum AttendanceDurations {
    /// Descanso/café: 30 minutos.
    public static let breakDuration: TimeInterval = 30 * 60
    /// Comida: 60 minutos.
    public static let mealDuration: TimeInterval = 60 * 60
}
