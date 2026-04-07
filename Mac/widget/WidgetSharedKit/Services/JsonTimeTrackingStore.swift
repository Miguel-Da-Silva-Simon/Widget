import Foundation

struct JsonTimeTrackingStore: Sendable {
    private let decoder = WidgetJsonCoding.makeDecoder()
    private let encoder = WidgetJsonCoding.makeEncoder()

    func load() throws -> TimeTrackingStateDocument {
        let url = LocalStatePaths.timeTrackingFileURL
        if FileManager.default.fileExists(atPath: url.path),
           let data = try? Data(contentsOf: url),
           let doc = try? decoder.decode(TimeTrackingStateDocument.self, from: data)
        {
            return doc
        }
        if let data = WidgetSessionMirror.loadTimeTrackingJSONData(),
           let doc = try? decoder.decode(TimeTrackingStateDocument.self, from: data)
        {
            return doc
        }
        return .createDefault()
    }

    func save(_ document: TimeTrackingStateDocument) throws {
        try LocalStatePaths.ensureStorageDirectory()
        let url = LocalStatePaths.timeTrackingFileURL
        let temp = url.appendingPathExtension("tmp")
        let data = try encoder.encode(document)
        try data.write(to: temp, options: .atomic)
        LocalStatePaths.makeFileGroupAccessible(at: temp)
        if FileManager.default.fileExists(atPath: url.path) {
            _ = try FileManager.default.replaceItemAt(url, withItemAt: temp)
        } else {
            try FileManager.default.moveItem(at: temp, to: url)
        }
        LocalStatePaths.makeFileGroupAccessible(at: url)
        WidgetSessionMirror.persistTimeTrackingJSONData(data)

        let sessionURL = LocalStatePaths.sessionFileURL
        let sessionData = (try? Data(contentsOf: sessionURL)) ?? WidgetSessionMirror.loadUserSessionJSONData()
        WidgetAppGroupFileReplication.replicateJSONFilesAfterSave(sessionData: sessionData, timeTrackingData: data)
    }
}
