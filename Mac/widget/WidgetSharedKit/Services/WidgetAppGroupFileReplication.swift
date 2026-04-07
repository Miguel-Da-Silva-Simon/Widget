import Foundation

/// En macOS, el host y la extensión pueden tener rutas distintas bajo `~/Library/Containers/.../Data/Library/Group Containers/`.
/// Tras cada guardado, replicamos los mismos bytes a todas las raíces del grupo que este proceso pueda escribir.
enum WidgetAppGroupFileReplication {
    private static let folder = "Widget.TimeTracking"

    static func replicateJSONFilesAfterSave(sessionData: Data?, timeTrackingData: Data?) {
        let sessionName = LocalStatePaths.sessionFileName
        let trackingName = LocalStatePaths.timeTrackingFileName

        for root in WidgetGroupContainerResolver.allGroupContainerBaseURLsForWriteMirroring() {
            let dir = root.appendingPathComponent(folder, isDirectory: true)
            do {
                try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
                LocalStatePaths.makeDirectoryGroupAccessible(at: root)
                LocalStatePaths.makeDirectoryGroupAccessible(at: dir)
                if let s = sessionData, !s.isEmpty {
                    let url = dir.appendingPathComponent(sessionName)
                    try s.write(to: url, options: .atomic)
                    LocalStatePaths.makeFileGroupAccessible(at: url)
                }
                if let t = timeTrackingData, !t.isEmpty {
                    let url = dir.appendingPathComponent(trackingName)
                    try t.write(to: url, options: .atomic)
                    LocalStatePaths.makeFileGroupAccessible(at: url)
                }
            } catch {
                // El sandbox puede impedir escribir en la raíz asociada a otro bundle; se intentan el resto.
            }
        }
    }
}
