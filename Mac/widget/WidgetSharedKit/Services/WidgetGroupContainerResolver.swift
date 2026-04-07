import Foundation
#if os(macOS)
import Darwin
#endif

/// Resuelve la URL base del App Group.
///
/// **Importante (macOS):** En la extensión del widget, `ProcessInfo.environment["HOME"]` apunta al **sandbox**
/// de la extensión (`.../TimeTrackingWidgetExtension/Data`). Si concatenamos `Library/Containers/...` sobre eso,
/// se generan rutas inválidas. Por eso **no** usamos `$HOME` para construir rutas.
enum WidgetGroupContainerResolver {
    private static var groupId: String { AppGroupConfiguration.identifier }

    private static let macOSHostBundleID = "com.gabriel.widget"
    private static let macOSWidgetExtensionBundleID = "com.gabriel.widget.TimeTrackingWidgetExtension"

    /// Raíces válidas para **este** proceso: API del sistema + resolución de symlinks.
    static func groupContainerRootsForThisProcess() -> [URL] {
        var seen = Set<String>()
        var result: [URL] = []

        func append(_ url: URL) {
            let p = url.path
            guard !seen.contains(p) else { return }
            seen.insert(p)
            result.append(url)
        }

        if let u = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupId) {
            append(u)
            let r = u.resolvingSymlinksInPath()
            if r.path != u.path { append(r) }
        }

        return result
    }

    #if os(macOS)
    /// Home del usuario real (no el «home» del sandbox de la extensión). Solo para rutas bajo `~/Library/...`.
    private static func macOSRealUserHomeDirectoryURL() -> URL? {
        guard let pw = getpwuid(getuid()), let dir = pw.pointee.pw_dir else { return nil }
        let path = String(cString: dir)
        guard !path.isEmpty else { return nil }
        return URL(fileURLWithPath: path, isDirectory: true)
    }

    /// Rutas adicionales donde macOS puede montar el mismo App Group (solo con home **real**).
    private static func extraGroupRootsFromRealUserHome() -> [URL] {
        guard let home = macOSRealUserHomeDirectoryURL() else { return [] }
        let paths: [String] = [
            "Library/Group Containers/\(groupId)",
            "Library/Containers/\(macOSHostBundleID)/Data/Library/Group Containers/\(groupId)",
            "Library/Containers/\(macOSWidgetExtensionBundleID)/Data/Library/Group Containers/\(groupId)",
        ]
        return paths.map { home.appendingPathComponent($0, isDirectory: true) }
    }
    #endif

    /// Lista usada para leer JSON/plist: proceso actual + variantes en el home real (sin `$HOME` del sandbox).
    static func allGroupContainerBaseURLs() -> [URL] {
        var seen = Set<String>()
        var result: [URL] = []

        func append(_ url: URL) {
            let p = url.path
            guard !seen.contains(p) else { return }
            seen.insert(p)
            result.append(url)
        }

        for u in groupContainerRootsForThisProcess() {
            append(u)
        }

        #if os(macOS)
        for u in extraGroupRootsFromRealUserHome() {
            append(u)
            let r = u.resolvingSymlinksInPath()
            if r.path != u.path { append(r) }
        }
        #endif

        return result
    }

    /// Lectura de JSON: primero las raíces propias del proceso, después las extra del home real.
    /// En macOS sandboxed, la lectura desde otra raíz puede fallar silenciosamente; el caller
    /// ya maneja esos fallos (fileExists=true pero Data vacío) con múltiples estrategias de lectura.
    static func groupContainerRootsForJSONFileRead() -> [URL] {
        allGroupContainerBaseURLs()
    }

    /// Igual que `allGroupContainerBaseURLs` (la replicación debe escribir en las mismas raíces que el widget puede leer).
    static func allGroupContainerBaseURLsForWriteMirroring() -> [URL] {
        allGroupContainerBaseURLs()
    }

    static func preferredGroupContainerBaseURL() -> URL? {
        let all = allGroupContainerBaseURLs()
        for u in all where FileManager.default.fileExists(atPath: u.path) {
            return u
        }
        return groupContainerRootsForThisProcess().first ?? all.first
    }

    static func userSessionJSONURL() -> URL? {
        preferredGroupContainerBaseURL()?
            .appendingPathComponent("Widget.TimeTracking", isDirectory: true)
            .appendingPathComponent("user-session.json")
    }

    static func timeTrackingJSONURL() -> URL? {
        preferredGroupContainerBaseURL()?
            .appendingPathComponent("Widget.TimeTracking", isDirectory: true)
            .appendingPathComponent("time-tracking-state.json")
    }

    static func preferencesPlistURL() -> URL? {
        preferredGroupContainerBaseURL()?.appendingPathComponent("Library/Preferences/\(groupId).plist")
    }
}
