import Foundation

enum LocalStatePaths {
    private static let folderName = "Widget.TimeTracking"
    static let timeTrackingFileName = "time-tracking-state.json"
    static let sessionFileName = "user-session.json"
    static let maxHistoryEntries = 20

    static var storageDirectory: URL {
        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        return appSupport.appendingPathComponent(folderName, isDirectory: true)
    }

    static var timeTrackingFileURL: URL {
        storageDirectory.appendingPathComponent(timeTrackingFileName)
    }

    static var sessionFileURL: URL {
        storageDirectory.appendingPathComponent(sessionFileName)
    }

    static func ensureStorageDirectory() throws {
        try FileManager.default.createDirectory(at: storageDirectory, withIntermediateDirectories: true)
    }
}
