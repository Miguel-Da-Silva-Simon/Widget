import Foundation

struct JsonTimeTrackingStore: Sendable {
    private let decoder = WidgetJsonCoding.makeDecoder()
    private let encoder = WidgetJsonCoding.makeEncoder()

    func load() throws -> TimeTrackingStateDocument {
        let url = LocalStatePaths.timeTrackingFileURL
        guard FileManager.default.fileExists(atPath: url.path) else {
            return .createDefault()
        }
        do {
            let data = try Data(contentsOf: url)
            return try decoder.decode(TimeTrackingStateDocument.self, from: data)
        } catch is DecodingError {
            return .createDefault()
        } catch {
            return .createDefault()
        }
    }

    func save(_ document: TimeTrackingStateDocument) throws {
        try LocalStatePaths.ensureStorageDirectory()
        let url = LocalStatePaths.timeTrackingFileURL
        let temp = url.appendingPathExtension("tmp")
        let data = try encoder.encode(document)
        try data.write(to: temp, options: .atomic)
        if FileManager.default.fileExists(atPath: url.path) {
            _ = try FileManager.default.replaceItemAt(url, withItemAt: temp)
        } else {
            try FileManager.default.moveItem(at: temp, to: url)
        }
    }
}
