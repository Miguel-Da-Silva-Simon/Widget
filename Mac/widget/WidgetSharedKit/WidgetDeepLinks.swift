import Foundation

/// Mismo valor que en developer.apple.com y en los `.entitlements` de app y extensión.
public enum AppGroupConfiguration {
    public static let identifier = "group.com.siwebai.widget.fichaje"
}

public enum WidgetDeepLinks {
    public static let scheme = "com.gabriel.widget"

    public static var openApp: URL { URL(string: "\(scheme)://open")! }
    public static var clockIn: URL { URL(string: "\(scheme)://clockIn")! }
    public static var clockOut: URL { URL(string: "\(scheme)://clockOut")! }
    public static var toggleCoffee: URL { URL(string: "\(scheme)://toggleCoffee")! }
    public static var toggleFood: URL { URL(string: "\(scheme)://toggleFood")! }
    public static var signOut: URL { URL(string: "\(scheme)://signOut")! }
}

public enum WidgetDeepLinkAction: Equatable {
    case openLogin
    case clockIn
    case clockOut
    case toggleCoffee
    case toggleFood
    case signOut
}

public enum WidgetDeepLinkParser {
    public static func parse(_ url: URL) -> WidgetDeepLinkAction? {
        guard url.scheme == WidgetDeepLinks.scheme else { return nil }
        switch url.host {
        case "open": return .openLogin
        case "clockIn": return .clockIn
        case "clockOut": return .clockOut
        case "toggleCoffee": return .toggleCoffee
        case "toggleFood": return .toggleFood
        case "signOut": return .signOut
        default: return nil
        }
    }
}

public enum TimeTrackingWidgetKind {
    public static let value = "TimeTrackingWidget"
}
