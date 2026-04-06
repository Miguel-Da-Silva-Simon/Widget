import SwiftUI

enum BrandColors {
    static let white = Color(red: 1, green: 1, blue: 1)
    static let primaryBlue = Color(red: 0x5F / 255, green: 0x96 / 255, blue: 0xF9 / 255)
    static let textPrimary = Color(red: 0x1F / 255, green: 0x1F / 255, blue: 0x1F / 255)
    static let textSecondary = Color(red: 0x5C / 255, green: 0x5C / 255, blue: 0x5C / 255)
    static let surfaceTint = Color(red: 0xE5 / 255, green: 0xF1 / 255, blue: 0xFF / 255)
    static let cardBorder = Color(red: 0xD5 / 255, green: 0xE5 / 255, blue: 0xFA / 255)
    static let chipBorder = Color(red: 0xD2 / 255, green: 0xEC / 255, blue: 0xFF / 255)
    static let shadowSoft = Color(red: 0xB5 / 255, green: 0xCC / 255, blue: 0xF5 / 255).opacity(0.35)
    /// Bloque “Acciones registradas”: equivalente al `style: emphasis` del Adaptive Card en Windows (gris muy claro, no el controlBackground del sistema).
    static let timelinePanelBackground = Color(red: 0xF3 / 255, green: 0xF5 / 255, blue: 0xF8 / 255)
    static let timerActive = Color(red: 0x10 / 255, green: 0xB9 / 255, blue: 0x81 / 255)
    static let timerInactive = Color(red: 0xEF / 255, green: 0x44 / 255, blue: 0x44 / 255)
}
