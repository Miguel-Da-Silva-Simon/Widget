import CoreFoundation
import Foundation
#if os(macOS)
import Darwin
#endif

/// Lectura de estado para la extensión del widget del escritorio.
/// Orden: ficheros JSON en **todas** las rutas candidatas al App Group → UserDefaults → CFPreferences → plist en disco.
enum WidgetExtensionStateReader {
    private static var groupId: String { AppGroupConfiguration.identifier }
    private static var cfAppID: CFString { groupId as CFString }

    static func resolvedGroupContainerURL() -> URL? {
        WidgetGroupContainerResolver.preferredGroupContainerBaseURL()
    }

    static func synchronizeAppGroupPreferences() {
        CFPreferencesAppSynchronize(cfAppID)
        _ = UserDefaults(suiteName: groupId)?.synchronize()
    }

    // MARK: - CFPreferences read helpers

    /// Entre procesos (app ↔ extensión del widget) a veces `CopyAppValue` no ve datos escritos por el host;
    /// `CopyValue` con `kCFPreferencesAnyHost` suele coincidir con lo que `UserDefaults(suiteName:)` persiste en disco.
    private static func cfCopyData(_ key: String) -> Data? {
        func dataFromPlistValue(_ value: CFPropertyList) -> Data? {
            if CFGetTypeID(value) == CFDataGetTypeID() {
                return (value as! CFData) as Data
            }
            if let ns = value as? NSData {
                return ns as Data
            }
            return nil
        }

        if let value = CFPreferencesCopyAppValue(key as CFString, cfAppID),
           let d = dataFromPlistValue(value)
        {
            return d
        }

        if let value = CFPreferencesCopyValue(
            key as CFString,
            cfAppID,
            kCFPreferencesCurrentUser,
            kCFPreferencesAnyHost
        ),
           let d = dataFromPlistValue(value)
        {
            return d
        }

        return nil
    }

    private static func cfCopyBool(_ key: String) -> Bool? {
        func fromValue(_ value: CFPropertyList) -> Bool? {
            if CFGetTypeID(value) == CFBooleanGetTypeID() {
                return CFBooleanGetValue(value as! CFBoolean)
            }
            if CFGetTypeID(value) == CFNumberGetTypeID() {
                var result: Int32 = 0
                CFNumberGetValue(value as! CFNumber, .sInt32Type, &result)
                return result != 0
            }
            return nil
        }

        if let value = CFPreferencesCopyAppValue(key as CFString, cfAppID),
           let b = fromValue(value)
        {
            return b
        }
        if let value = CFPreferencesCopyValue(
            key as CFString,
            cfAppID,
            kCFPreferencesCurrentUser,
            kCFPreferencesAnyHost
        ),
           let b = fromValue(value)
        {
            return b
        }
        return nil
    }

    private static func cfCopyString(_ key: String) -> String? {
        func fromValue(_ value: CFPropertyList) -> String? {
            if CFGetTypeID(value) == CFStringGetTypeID() {
                return value as! CFString as String
            }
            return nil
        }

        if let value = CFPreferencesCopyAppValue(key as CFString, cfAppID),
           let s = fromValue(value)
        {
            return s
        }
        if let value = CFPreferencesCopyValue(
            key as CFString,
            cfAppID,
            kCFPreferencesCurrentUser,
            kCFPreferencesAnyHost
        ),
           let s = fromValue(value)
        {
            return s
        }
        return nil
    }

    // MARK: - Plist on disk

    private static func loadRawPlistDictionary() -> [String: Any]? {
        let candidates = WidgetGroupContainerResolver.allGroupContainerBaseURLs().map {
            $0.appendingPathComponent("Library/Preferences/\(groupId).plist")
        }
        for url in candidates {
            if let dict = NSDictionary(contentsOf: url) as? [String: Any] {
                return dict
            }
        }
        return nil
    }

    private static func plistBool(_ dict: [String: Any], _ key: String) -> Bool {
        switch dict[key] {
        case let b as Bool: return b
        case let n as NSNumber: return n.boolValue
        case let i as Int: return i != 0
        default: return false
        }
    }

    private static func plistString(_ dict: [String: Any], _ key: String) -> String? {
        dict[key] as? String
    }

    private static func dataFromPlist(key: String) -> Data? {
        guard let dict = loadRawPlistDictionary() else { return nil }
        if let d = dict[key] as? Data { return d }
        if let d = dict[key] as? NSData { return d as Data }
        return nil
    }

    // MARK: - JSON files (raíces del API del sistema; ver `groupContainerRootsForJSONFileRead`)

    private static func readFileDataResolvingSymlinks(url: URL) -> Data? {
        let fm = FileManager.default
        let resolved = url.resolvingSymlinksInPath()
        let path = resolved.path
        guard fm.fileExists(atPath: path) else { return nil }

        if !fm.isReadableFile(atPath: path) {
            WidgetDebugLog.log("file not marked readable: \(path)")
        }

        if let d = fm.contents(atPath: path), !d.isEmpty {
            return d
        }
        do {
            let d = try Data(contentsOf: resolved, options: [.mappedIfSafe])
            return d.isEmpty ? nil : d
        } catch {
            WidgetDebugLog.log("Data(contentsOf:) err=\((error as NSError).code) \(error.localizedDescription) path=\(path)")
        }
        do {
            let fh = try FileHandle(forReadingFrom: resolved)
            defer { try? fh.close() }
            let d = try fh.readToEnd() ?? Data()
            return d.isEmpty ? nil : d
        } catch {
            WidgetDebugLog.log("FileHandle err=\((error as NSError).code) \(error.localizedDescription) path=\(path)")
        }
        return nil
    }

    private static func loadDataFromRelativePath(_ folder: String, fileName: String) -> Data? {
        for base in WidgetGroupContainerResolver.groupContainerRootsForJSONFileRead() {
            let url = base.appendingPathComponent(folder, isDirectory: true).appendingPathComponent(fileName)
            let path = url.path
            guard FileManager.default.fileExists(atPath: path) else { continue }
            if let data = readFileDataResolvingSymlinks(url: url) {
                return data
            }
            WidgetDebugLog.log("relative file exists but unreadable: \(folder)/\(fileName) @ \(path)")
        }
        return nil
    }

    private static func stripUTF8BOM(_ data: Data) -> Data {
        guard data.count >= 3, data[data.startIndex] == 0xEF, data[data.startIndex + 1] == 0xBB, data[data.startIndex + 2] == 0xBF else {
            return data
        }
        return data.dropFirst(3)
    }

    /// Cuando el `JSONDecoder` falla (fechas como número, variantes de claves, etc.).
    private static func userSessionDocumentFromLenientJSONObject(_ obj: [String: Any]) -> UserSessionDocument? {
        func string(for keys: [String]) -> String? {
            for k in keys {
                if let s = obj[k] as? String {
                    let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !t.isEmpty { return t }
                }
            }
            return nil
        }

        let stateRaw = (["State", "state", "AuthenticationState", "authenticationState"].compactMap { obj[$0] as? String }.first ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        let state: AuthenticationState
        switch stateRaw {
        case "signedin": state = .signedIn
        case "signedout": state = .signedOut
        case "unknown": state = .unknown
        default: state = .unknown
        }

        let signedInAtUtc: Date? = {
            for k in ["SignedInAtUtc", "signedInAtUtc"] {
                if let d = WidgetJsonCoding.dateFromLooseJsonValue(obj[k]) { return d }
            }
            return nil
        }()

        return UserSessionDocument(
            state: state,
            userId: string(for: ["UserId", "userId"]),
            displayName: string(for: ["DisplayName", "displayName"]),
            email: string(for: ["Email", "email"]),
            signedInAtUtc: signedInAtUtc,
            profilePhotoFileName: string(for: ["ProfilePhotoFileName", "profilePhotoFileName"])
        )
    }

    // MARK: - Document loading

    static func loadUserSessionDocument(decoder: JSONDecoder) -> UserSessionDocument? {
        synchronizeAppGroupPreferences()

        func decodeSession(_ data: Data, source: String) -> UserSessionDocument? {
            let cleaned = stripUTF8BOM(data)
            do {
                return try decoder.decode(UserSessionDocument.self, from: cleaned)
            } catch {
                NSLog("[FichajeWidget] user-session decode failed (\(source)): \(error.localizedDescription)")
                WidgetDebugLog.log("user-session strict decode failed (\(source)): \(error)")
                if let preview = String(data: Data(cleaned.prefix(400)), encoding: .utf8) {
                    WidgetDebugLog.log("user-session json preview (\(source)): \(preview)")
                }
                if let any = try? JSONSerialization.jsonObject(with: cleaned),
                   let obj = any as? [String: Any],
                   let doc = userSessionDocumentFromLenientJSONObject(obj)
                {
                    WidgetDebugLog.log("user-session lenient OK (\(source)): state=\(doc.state)")
                    return doc
                }
                return nil
            }
        }

        // 1–3. Espejo primero: en macOS la extensión a veces no puede leer `Widget.TimeTracking/*.json` (existe y tiene tamaño, pero lectura falla);
        // el plist del App Group / CFPreferences suele seguir siendo accesible con los mismos bytes.
        if let data = cfCopyData("fichaje.mirror.userSessionJsonData"),
           let doc = decodeSession(data, source: "CFPreferences")
        {
            return doc
        }

        if let data = WidgetSessionMirror.loadUserSessionJSONData(),
           let doc = decodeSession(data, source: "UserDefaults")
        {
            return doc
        }

        if let data = dataFromPlist(key: "fichaje.mirror.userSessionJsonData"),
           let doc = decodeSession(data, source: "plist")
        {
            return doc
        }

        // 4. Fichero JSON (último recurso)
        if let data = loadDataFromRelativePath("Widget.TimeTracking", fileName: "user-session.json"),
           let doc = decodeSession(data, source: "file")
        {
            return doc
        }

        return nil
    }

    static func loadTimeTrackingDocument(decoder: JSONDecoder) -> TimeTrackingStateDocument? {
        synchronizeAppGroupPreferences()

        if let data = cfCopyData("fichaje.mirror.timeTrackingJsonData"),
           let doc = try? decoder.decode(TimeTrackingStateDocument.self, from: data)
        {
            return doc
        }

        if let data = WidgetSessionMirror.loadTimeTrackingJSONData(),
           let doc = try? decoder.decode(TimeTrackingStateDocument.self, from: data)
        {
            return doc
        }

        if let data = dataFromPlist(key: "fichaje.mirror.timeTrackingJsonData"),
           let doc = try? decoder.decode(TimeTrackingStateDocument.self, from: data)
        {
            return doc
        }

        if let data = loadDataFromRelativePath("Widget.TimeTracking", fileName: "time-tracking-state.json"),
           let doc = try? decoder.decode(TimeTrackingStateDocument.self, from: data)
        {
            return doc
        }

        return nil
    }

    // MARK: - UserSession helpers

    static func userSession(from document: UserSessionDocument?) -> UserSession {
        guard let document, document.state == .signedIn,
              let userId = document.userId, !userId.isEmpty,
              let name = document.displayName, !name.isEmpty
        else { return .signedOut() }
        return UserSession(
            state: .signedIn,
            user: CurrentUser(userId: userId, displayName: name, email: document.email),
            signedInAtUtc: document.signedInAtUtc
        )
    }

    /// Escalares: UserDefaults → CFPreferences → plist (misma información que el espejo).
    static func fallbackSessionFromPlistScalars() -> UserSession? {
        synchronizeAppGroupPreferences()

        if let s = WidgetSessionMirror.fallbackSessionIfMirrorSignedIn() {
            return s
        }

        if let signedIn = cfCopyBool("fichaje.mirror.signedIn"), signedIn,
           let name = cfCopyString("fichaje.mirror.displayName")?.trimmingCharacters(in: .whitespacesAndNewlines),
           !name.isEmpty
        {
            let uid = cfCopyString("fichaje.mirror.userId")?.trimmingCharacters(in: .whitespacesAndNewlines)
            let emailRaw = cfCopyString("fichaje.mirror.email")?.trimmingCharacters(in: .whitespacesAndNewlines)
            let resolvedUserId = (uid?.isEmpty == false) ? uid! : name
            let email = emailRaw.flatMap { $0.isEmpty ? nil : $0 }
            let user = CurrentUser(userId: resolvedUserId, displayName: name, email: email)
            return UserSession(state: .signedIn, user: user, signedInAtUtc: Date())
        }

        if let dict = loadRawPlistDictionary(), plistBool(dict, "fichaje.mirror.signedIn"),
           let name = plistString(dict, "fichaje.mirror.displayName")?.trimmingCharacters(in: .whitespacesAndNewlines),
           !name.isEmpty
        {
            let uid = plistString(dict, "fichaje.mirror.userId")?.trimmingCharacters(in: .whitespacesAndNewlines)
            let emailRaw = plistString(dict, "fichaje.mirror.email")?.trimmingCharacters(in: .whitespacesAndNewlines)
            let resolvedUserId = (uid?.isEmpty == false) ? uid! : name
            let email = emailRaw.flatMap { $0.isEmpty ? nil : $0 }
            let user = CurrentUser(userId: resolvedUserId, displayName: name, email: email)
            return UserSession(state: .signedIn, user: user, signedInAtUtc: Date())
        }

        return nil
    }
}
