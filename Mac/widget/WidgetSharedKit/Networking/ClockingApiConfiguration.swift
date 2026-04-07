import Foundation

public enum ClockingApiConfiguration {
    private static let userDefaultsKey = "clocking.api.baseURL"

    /// Misma convención que Android debug: backend Docker en el host en el puerto 8080.
    public static var defaultBaseURLString: String { "http://127.0.0.1:8080" }

    private static var sharedDefaults: UserDefaults? {
        UserDefaults(suiteName: AppGroupConfiguration.identifier)
    }

    public static var baseURL: URL {
        let raw = (sharedDefaults?.string(forKey: userDefaultsKey)
                   ?? UserDefaults.standard.string(forKey: userDefaultsKey))?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let s = (raw?.isEmpty == false) ? raw! : defaultBaseURLString
        let normalized = s.hasSuffix("/") ? s : s + "/"
        guard let url = URL(string: normalized) else {
            return URL(string: defaultBaseURLString + "/")!
        }
        return url
    }

    public static func setBaseURLStringForTesting(_ value: String?) {
        if let value, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            UserDefaults.standard.set(value, forKey: userDefaultsKey)
            sharedDefaults?.set(value, forKey: userDefaultsKey)
        } else {
            UserDefaults.standard.removeObject(forKey: userDefaultsKey)
            sharedDefaults?.removeObject(forKey: userDefaultsKey)
        }
    }
}
