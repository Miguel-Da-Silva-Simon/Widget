import Foundation

/// Log de depuración del proceso del widget: App Group si existe, si no `/tmp`, y siempre `NSLog`.
enum WidgetDebugLog {
    private static let maxLogSize = 64 * 1024

    private static var primaryLogURL: URL? {
        WidgetGroupContainerResolver.preferredGroupContainerBaseURL()?
            .appendingPathComponent("Widget.TimeTracking/widget-debug.log")
    }

    private static var fallbackLogURL: URL {
        URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            .appendingPathComponent("fichaje-widget-debug.log")
    }

    static func log(_ message: String) {
        let timestamp = ISO8601DateFormatter().string(from: Date())
        let line = "[\(timestamp)] \(message)"
        NSLog("[FichajeWidget] %@", line)

        let data = Data((line + "\n").utf8)
        let url = primaryLogURL ?? fallbackLogURL
        let fm = FileManager.default
        let dir = url.deletingLastPathComponent()
        try? fm.createDirectory(at: dir, withIntermediateDirectories: true)

        if fm.fileExists(atPath: url.path),
           let attrs = try? fm.attributesOfItem(atPath: url.path),
           let size = attrs[.size] as? Int, size > maxLogSize
        {
            try? fm.removeItem(at: url)
        }

        if let handle = try? FileHandle(forWritingTo: url) {
            handle.seekToEndOfFile()
            handle.write(data)
            handle.closeFile()
        } else {
            try? data.write(to: url, options: .atomic)
        }
    }
}
