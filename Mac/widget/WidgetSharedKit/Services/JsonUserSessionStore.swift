import Foundation

struct JsonUserSessionStore: Sendable {
    private let decoder = WidgetJsonCoding.makeDecoder()
    private let encoder = WidgetJsonCoding.makeEncoder()

    func loadDocument() throws -> UserSessionDocument? {
        let url = LocalStatePaths.sessionFileURL
        if FileManager.default.fileExists(atPath: url.path),
           let data = try? Data(contentsOf: url),
           let doc = try? decoder.decode(UserSessionDocument.self, from: data)
        {
            return doc
        }
        if let data = WidgetSessionMirror.loadUserSessionJSONData(),
           let doc = try? decoder.decode(UserSessionDocument.self, from: data)
        {
            return doc
        }
        return nil
    }

    func saveDocument(_ document: UserSessionDocument) throws {
        try LocalStatePaths.ensureStorageDirectory()
        let url = LocalStatePaths.sessionFileURL
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
        WidgetSessionMirror.sync(from: document)
        WidgetSessionMirror.persistUserSessionJSONData(data)

        let trackingURL = LocalStatePaths.timeTrackingFileURL
        let trackingData = (try? Data(contentsOf: trackingURL)) ?? WidgetSessionMirror.loadTimeTrackingJSONData()
        WidgetAppGroupFileReplication.replicateJSONFilesAfterSave(sessionData: data, timeTrackingData: trackingData)
    }
}
