import Foundation
import Security

/// Guarda el JWT en el llavero por defecto de la app y lo espeja en múltiples canales
/// (UserDefaults, CFPreferences, fichero en disco) para maximizar la probabilidad de que la
/// extensión del widget pueda leerlo aunque el App Group no esté perfectamente compartido.
enum ClockingJWTKeychain {
    private static let service = "com.gabriel.widget.clocking.jwt"
    private static let mirrorKey = "fichaje.mirror.jwt"
    private static var cfAppID: CFString { AppGroupConfiguration.identifier as CFString }
    private static let jwtFileName = "jwt-mirror.txt"

    // MARK: - Read

    static func readToken() -> String? {
        if let token = readFromKeychain() { return token }
        if let token = readFromUserDefaults() { return token }
        if let token = readFromCFPreferences() { return token }
        if let token = readFromPlistOnDisk() { return token }
        if let token = readFromFileOnDisk() { return token }
        return nil
    }

    // MARK: - Save

    static func saveToken(_ token: String) throws {
        saveMirrors(token)
        saveToKeychain(token)
    }

    // MARK: - Delete

    static func deleteToken() {
        deleteFromKeychain()
        deleteMirrors()
    }

    // MARK: - All mirror writes

    private static func saveMirrors(_ token: String) {
        // 1. UserDefaults (App Group suite)
        if let ud = mirrorDefaults {
            ud.set(token, forKey: mirrorKey)
            ud.synchronize()
        }

        // 2. CFPreferences (cross-process plist)
        CFPreferencesSetAppValue(mirrorKey as CFString, token as CFString, cfAppID)
        CFPreferencesAppSynchronize(cfAppID)

        // 3. File on disk in all known group container roots
        let data = Data(token.utf8)
        for root in WidgetGroupContainerResolver.allGroupContainerBaseURLsForWriteMirroring() {
            let dir = root.appendingPathComponent("Widget.TimeTracking", isDirectory: true)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            LocalStatePaths.makeDirectoryGroupAccessible(at: root)
            LocalStatePaths.makeDirectoryGroupAccessible(at: dir)
            let url = dir.appendingPathComponent(jwtFileName)
            try? data.write(to: url, options: .atomic)
            LocalStatePaths.makeFileGroupAccessible(at: url)
        }
    }

    private static func deleteMirrors() {
        if let ud = mirrorDefaults {
            ud.removeObject(forKey: mirrorKey)
            ud.synchronize()
        }

        CFPreferencesSetAppValue(mirrorKey as CFString, nil, cfAppID)
        CFPreferencesAppSynchronize(cfAppID)

        for root in WidgetGroupContainerResolver.allGroupContainerBaseURLsForWriteMirroring() {
            let url = root
                .appendingPathComponent("Widget.TimeTracking", isDirectory: true)
                .appendingPathComponent(jwtFileName)
            try? FileManager.default.removeItem(at: url)
        }
    }

    // MARK: - Keychain

    private static func saveToKeychain(_ token: String) {
        deleteFromKeychain()

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecValueData as String: Data(token.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]

        var status = SecItemAdd(query as CFDictionary, nil)

        if status == errSecDuplicateItem {
            let searchQuery: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: service,
            ]
            let updateAttrs: [String: Any] = [
                kSecValueData as String: Data(token.utf8),
                kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
            ]
            status = SecItemUpdate(searchQuery as CFDictionary, updateAttrs as CFDictionary)
        }

        if status != errSecSuccess {
            NSLog("[ClockingJWTKeychain] Keychain write failed (OSStatus %d), using mirrors only.", status)
        }
    }

    private static func readFromKeychain() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess,
              let data = result as? Data,
              let token = String(data: data, encoding: .utf8),
              !token.isEmpty
        else { return nil }
        return token
    }

    private static func deleteFromKeychain() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - UserDefaults mirror

    private static var mirrorDefaults: UserDefaults? {
        UserDefaults(suiteName: AppGroupConfiguration.identifier)
    }

    private static func readFromUserDefaults() -> String? {
        guard let token = mirrorDefaults?.string(forKey: mirrorKey), !token.isEmpty else { return nil }
        return token
    }

    // MARK: - CFPreferences mirror

    private static func readFromCFPreferences() -> String? {
        CFPreferencesAppSynchronize(cfAppID)

        if let value = CFPreferencesCopyAppValue(mirrorKey as CFString, cfAppID),
           CFGetTypeID(value) == CFStringGetTypeID()
        {
            let token = value as! CFString as String
            if !token.isEmpty { return token }
        }

        if let value = CFPreferencesCopyValue(
            mirrorKey as CFString, cfAppID,
            kCFPreferencesCurrentUser, kCFPreferencesAnyHost
        ),
           CFGetTypeID(value) == CFStringGetTypeID()
        {
            let token = value as! CFString as String
            if !token.isEmpty { return token }
        }

        return nil
    }

    // MARK: - Plist on disk

    private static func readFromPlistOnDisk() -> String? {
        let groupId = AppGroupConfiguration.identifier
        for base in WidgetGroupContainerResolver.allGroupContainerBaseURLs() {
            let plistURL = base.appendingPathComponent("Library/Preferences/\(groupId).plist")
            guard let dict = NSDictionary(contentsOf: plistURL) as? [String: Any],
                  let token = dict[mirrorKey] as? String,
                  !token.isEmpty
            else { continue }
            return token
        }
        return nil
    }

    // MARK: - File on disk

    private static func readFromFileOnDisk() -> String? {
        for root in WidgetGroupContainerResolver.allGroupContainerBaseURLs() {
            let url = root
                .appendingPathComponent("Widget.TimeTracking", isDirectory: true)
                .appendingPathComponent(jwtFileName)
            guard FileManager.default.fileExists(atPath: url.path),
                  let data = try? Data(contentsOf: url),
                  let token = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !token.isEmpty
            else { continue }
            return token
        }
        return nil
    }
}
