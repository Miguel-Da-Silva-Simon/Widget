import Foundation

struct JsonUserSessionStore: Sendable {
    private let decoder = WidgetJsonCoding.makeDecoder()
    private let encoder = WidgetJsonCoding.makeEncoder()

    func loadDocument() throws -> UserSessionDocument? {
        let url = LocalStatePaths.sessionFileURL
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        do {
            let data = try Data(contentsOf: url)
            return try decoder.decode(UserSessionDocument.self, from: data)
        } catch {
            return nil
        }
    }

    func saveDocument(_ document: UserSessionDocument) throws {
        try LocalStatePaths.ensureStorageDirectory()
        let url = LocalStatePaths.sessionFileURL
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
