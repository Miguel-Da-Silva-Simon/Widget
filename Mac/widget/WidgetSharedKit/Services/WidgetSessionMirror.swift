import CoreFoundation
import Foundation

/// Copia mínima de sesión en `UserDefaults` + `CFPreferences` del App Group.
/// La extensión del escritorio a veces no ve `UserDefaults(suiteName:)` a tiempo;
/// `CFPreferencesSetAppValue` escribe al plist sin intermediarios y se sincroniza al disco de inmediato.
enum WidgetSessionMirror {
    private static var suiteName: String { AppGroupConfiguration.identifier }
    private static let signedInKey = "fichaje.mirror.signedIn"
    private static let displayNameKey = "fichaje.mirror.displayName"
    private static let userIdKey = "fichaje.mirror.userId"
    private static let emailKey = "fichaje.mirror.email"
    private static let userSessionJsonDataKey = "fichaje.mirror.userSessionJsonData"
    private static let timeTrackingJsonDataKey = "fichaje.mirror.timeTrackingJsonData"

    // MARK: - CFPreferences helpers

    private static var cfAppID: CFString { suiteName as CFString }

    private static func cfSet(_ key: String, _ value: CFPropertyList?) {
        CFPreferencesSetAppValue(key as CFString, value, cfAppID)
    }

    private static func cfSync() {
        CFPreferencesAppSynchronize(cfAppID)
    }

    private static func cfCopyDataValue(forKey key: String) -> Data? {
        func asData(_ value: CFPropertyList) -> Data? {
            if CFGetTypeID(value) == CFDataGetTypeID() {
                return (value as! CFData) as Data
            }
            if let ns = value as? NSData {
                return ns as Data
            }
            return nil
        }
        cfSync()
        if let value = CFPreferencesCopyAppValue(key as CFString, cfAppID),
           let d = asData(value), !d.isEmpty
        {
            return d
        }
        if let value = CFPreferencesCopyValue(
            key as CFString,
            cfAppID,
            kCFPreferencesCurrentUser,
            kCFPreferencesAnyHost
        ),
           let d = asData(value), !d.isEmpty
        {
            return d
        }
        return nil
    }

    // MARK: - Sync scalars

    static func sync(from document: UserSessionDocument) {
        let signedIn =
            document.state == .signedIn
            && document.userId.map { !$0.isEmpty } == true
            && document.displayName.map { !$0.isEmpty } == true

        let ud = UserDefaults(suiteName: suiteName)
        ud?.set(signedIn, forKey: signedInKey)
        cfSet(signedInKey, signedIn as CFBoolean)

        if signedIn, let name = document.displayName {
            ud?.set(name, forKey: displayNameKey)
            cfSet(displayNameKey, name as CFString)

            if let uid = document.userId, !uid.isEmpty {
                ud?.set(uid, forKey: userIdKey)
                cfSet(userIdKey, uid as CFString)
            } else {
                ud?.removeObject(forKey: userIdKey)
                cfSet(userIdKey, nil)
            }
            if let email = document.email, !email.isEmpty {
                ud?.set(email, forKey: emailKey)
                cfSet(emailKey, email as CFString)
            } else {
                ud?.removeObject(forKey: emailKey)
                cfSet(emailKey, nil)
            }
        } else {
            ud?.removeObject(forKey: displayNameKey)
            ud?.removeObject(forKey: userIdKey)
            ud?.removeObject(forKey: emailKey)
            cfSet(displayNameKey, nil)
            cfSet(userIdKey, nil)
            cfSet(emailKey, nil)
        }
        ud?.synchronize()
        cfSync()
    }

    // MARK: - JSON blob persistence

    static func persistUserSessionJSONData(_ data: Data?) {
        let ud = UserDefaults(suiteName: suiteName)
        if let data {
            ud?.set(data, forKey: userSessionJsonDataKey)
            cfSet(userSessionJsonDataKey, data as CFData)
        } else {
            ud?.removeObject(forKey: userSessionJsonDataKey)
            cfSet(userSessionJsonDataKey, nil)
        }
        ud?.synchronize()
        cfSync()
    }

    static func loadUserSessionJSONData() -> Data? {
        if let d = UserDefaults(suiteName: suiteName)?.data(forKey: userSessionJsonDataKey), !d.isEmpty {
            return d
        }
        return cfCopyDataValue(forKey: userSessionJsonDataKey)
    }

    static func persistTimeTrackingJSONData(_ data: Data?) {
        let ud = UserDefaults(suiteName: suiteName)
        if let data {
            ud?.set(data, forKey: timeTrackingJsonDataKey)
            cfSet(timeTrackingJsonDataKey, data as CFData)
        } else {
            ud?.removeObject(forKey: timeTrackingJsonDataKey)
            cfSet(timeTrackingJsonDataKey, nil)
        }
        ud?.synchronize()
        cfSync()
    }

    static func loadTimeTrackingJSONData() -> Data? {
        if let d = UserDefaults(suiteName: suiteName)?.data(forKey: timeTrackingJsonDataKey), !d.isEmpty {
            return d
        }
        return cfCopyDataValue(forKey: timeTrackingJsonDataKey)
    }

    /// Tras una actualización: si el JSON ya está en el contenedor del App Group pero el espejo UserDefaults está vacío,
    /// copia el contenido para que el widget del escritorio lo lea sin volver a iniciar sesión.
    static func backfillMirrorsFromSharedFilesIfNeeded() {
        if loadUserSessionJSONData() == nil {
            let url = LocalStatePaths.sessionFileURL
            if FileManager.default.fileExists(atPath: url.path), let data = try? Data(contentsOf: url), !data.isEmpty {
                persistUserSessionJSONData(data)
            }
        }
        if loadTimeTrackingJSONData() == nil {
            let url = LocalStatePaths.timeTrackingFileURL
            if FileManager.default.fileExists(atPath: url.path), let data = try? Data(contentsOf: url), !data.isEmpty {
                persistTimeTrackingJSONData(data)
            }
        }
    }

    private static func userDefaultsBool(_ ud: UserDefaults, _ key: String) -> Bool {
        switch ud.object(forKey: key) {
        case let b as Bool: return b
        case let n as NSNumber: return n.boolValue
        case let i as Int: return i != 0
        default: return ud.bool(forKey: key)
        }
    }

    /// Si el JSON aún no es legible para la extensión, el espejo puede reflejar la sesión recién guardada.
    static func fallbackSessionIfMirrorSignedIn() -> UserSession? {
        guard let ud = UserDefaults(suiteName: suiteName),
              userDefaultsBool(ud, signedInKey),
              let name = ud.string(forKey: displayNameKey)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !name.isEmpty
        else { return nil }
        let uid = ud.string(forKey: userIdKey)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let emailRaw = ud.string(forKey: emailKey)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedEmail = emailRaw.flatMap { $0.isEmpty ? nil : $0 }
        let resolvedUserId: String
        if let uid, !uid.isEmpty {
            resolvedUserId = uid
        } else {
            resolvedUserId = name
        }
        let user = CurrentUser(userId: resolvedUserId, displayName: name, email: resolvedEmail)
        return UserSession(state: .signedIn, user: user, signedInAtUtc: Date())
    }
}
