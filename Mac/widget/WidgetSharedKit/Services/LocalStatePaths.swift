import Foundation

enum LocalStatePaths {
    /// Debe coincidir con el App Group en developer.apple.com y en los entitlements de app + extensión.
    private static var appGroupIdentifier: String { AppGroupConfiguration.identifier }
    private static let folderName = "Widget.TimeTracking"
    static let timeTrackingFileName = "time-tracking-state.json"
    static let sessionFileName = "user-session.json"
    static let maxHistoryEntries = 20

    // MARK: - POSIX permissions for App Group cross-process access

    /// rw-rw-rw- so both the host app and widget extension processes can read/write.
    static let sharedFilePermissions: Int16 = 0o666
    /// rwxrwxrwx for directories.
    static let sharedDirectoryPermissions: Int16 = 0o777

    static func makeFileGroupAccessible(at url: URL) {
        try? FileManager.default.setAttributes(
            [.posixPermissions: NSNumber(value: sharedFilePermissions)],
            ofItemAtPath: url.path
        )
    }

    static func makeDirectoryGroupAccessible(at url: URL) {
        try? FileManager.default.setAttributes(
            [.posixPermissions: NSNumber(value: sharedDirectoryPermissions)],
            ofItemAtPath: url.path
        )
    }

    /// Fixes permissions on the storage directory and all known files inside it.
    static func repairGroupContainerPermissions() {
        let fm = FileManager.default
        for root in WidgetGroupContainerResolver.allGroupContainerBaseURLs() {
            let dir = root.appendingPathComponent(folderName, isDirectory: true)
            guard fm.fileExists(atPath: dir.path) else { continue }
            makeDirectoryGroupAccessible(at: root)
            makeDirectoryGroupAccessible(at: dir)
            for name in [sessionFileName, timeTrackingFileName, "jwt-mirror.txt"] {
                let file = dir.appendingPathComponent(name)
                if fm.fileExists(atPath: file.path) {
                    makeFileGroupAccessible(at: file)
                }
            }
        }
    }

    // MARK: - Legacy migration

    private static var legacySandboxStorageDirectory: URL {
        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        return appSupport.appendingPathComponent(folderName, isDirectory: true)
    }

    private static var didMigrateLegacyIntoGroup = false

    private static func migrateLegacyIntoGroupIfNeeded(groupStorage: URL) {
        guard !didMigrateLegacyIntoGroup else { return }
        didMigrateLegacyIntoGroup = true
        let fm = FileManager.default
        let legacy = legacySandboxStorageDirectory
        let legacySession = legacy.appendingPathComponent(sessionFileName)
        let groupSession = groupStorage.appendingPathComponent(sessionFileName)
        guard !fm.fileExists(atPath: groupSession.path), fm.fileExists(atPath: legacySession.path) else { return }
        try? fm.createDirectory(at: groupStorage, withIntermediateDirectories: true)
        makeDirectoryGroupAccessible(at: groupStorage)
        let legacyTracking = legacy.appendingPathComponent(timeTrackingFileName)
        let groupTracking = groupStorage.appendingPathComponent(timeTrackingFileName)
        try? fm.copyItem(at: legacySession, to: groupSession)
        makeFileGroupAccessible(at: groupSession)
        if fm.fileExists(atPath: legacyTracking.path) {
            try? fm.copyItem(at: legacyTracking, to: groupTracking)
            makeFileGroupAccessible(at: groupTracking)
        }
    }

    // MARK: - Paths

    static var storageDirectory: URL {
        if let groupURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupIdentifier) {
            let dir = groupURL.appendingPathComponent(folderName, isDirectory: true)
            migrateLegacyIntoGroupIfNeeded(groupStorage: dir)
            return dir
        }
        return legacySandboxStorageDirectory
    }

    static var timeTrackingFileURL: URL {
        storageDirectory.appendingPathComponent(timeTrackingFileName)
    }

    static var sessionFileURL: URL {
        storageDirectory.appendingPathComponent(sessionFileName)
    }

    static func ensureStorageDirectory() throws {
        let dir = storageDirectory
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        makeDirectoryGroupAccessible(at: dir)
        if let parent = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupIdentifier) {
            makeDirectoryGroupAccessible(at: parent)
        }
    }
}
