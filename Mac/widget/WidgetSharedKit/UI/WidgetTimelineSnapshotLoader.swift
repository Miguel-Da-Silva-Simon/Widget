import AppKit
import Foundation

public enum WidgetTimelineSnapshotLoader {
    public static func loadForWidgetEntry(now: Date = Date()) -> (session: UserSession, snapshot: TimeTrackingSnapshot, profileImage: NSImage?) {
        let decoder = WidgetJsonCoding.makeDecoder()

        WidgetDebugLog.log("--- loadForWidgetEntry START ---")
        LocalStatePaths.repairGroupContainerPermissions()
        let groupId = AppGroupConfiguration.identifier
        let primaryURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupId)
        let primary = primaryURL?.path ?? "nil"
        WidgetDebugLog.log("containerURL(primary): \(primary)")
        WidgetDebugLog.log("UserDefaults(suiteName:): \(UserDefaults(suiteName: groupId) == nil ? "NIL" : "ok")")

        WidgetExtensionStateReader.synchronizeAppGroupPreferences()

        let sessionDoc = WidgetExtensionStateReader.loadUserSessionDocument(decoder: decoder)
        WidgetDebugLog.log("sessionDoc: \(sessionDoc == nil ? "nil" : "state=\(sessionDoc!.state), userId=\(sessionDoc?.userId ?? "nil")")")

        var session = WidgetExtensionStateReader.userSession(from: sessionDoc)

        if !session.isAuthenticated, let fallback = WidgetExtensionStateReader.fallbackSessionFromPlistScalars() {
            session = fallback
            WidgetDebugLog.log("session from mirror scalars fallback: authenticated=\(session.isAuthenticated)")
        }

        WidgetDebugLog.log("FINAL session: authenticated=\(session.isAuthenticated), user=\(session.user?.displayName ?? "nil")")

        let trackingDoc = WidgetExtensionStateReader.loadTimeTrackingDocument(decoder: decoder) ?? .createDefault()
        let timeTracking = LocalJsonTimeTrackingService()
        let snapshot = timeTracking.snapshot(document: trackingDoc, nowUtc: now)

        let profileImage: NSImage? = {
            guard let doc = sessionDoc,
                  doc.state == .signedIn,
                  let fileName = doc.profilePhotoFileName, !fileName.isEmpty,
                  let base = WidgetExtensionStateReader.resolvedGroupContainerURL()
            else { return nil }
            let url = base.appendingPathComponent("Widget.TimeTracking", isDirectory: true).appendingPathComponent(fileName)
            return NSImage(contentsOf: url)
        }()

        WidgetDebugLog.log("--- loadForWidgetEntry END ---")
        return (session, snapshot, profileImage)
    }

    /// Fallback de red: si la lectura local no muestra sesión activa pero hay un JWT en el Keychain,
    /// llama al API directamente y construye el resultado **completamente en memoria** sin escribir
    /// nada al filesystem (la extensión puede no tener permisos de escritura en el Group Container).
    public static func tryNetworkRestore(now: Date) async -> (session: UserSession, snapshot: TimeTrackingSnapshot, profileImage: NSImage?)? {
        let hasToken = ClockingJWTKeychain.readToken() != nil
        WidgetDebugLog.log("tryNetworkRestore: hasToken=\(hasToken)")
        guard hasToken else { return nil }

        let remote = ClockingRemoteSessionService()
        do {
            guard let result = try await remote.fetchSessionAndStateInMemory() else {
                WidgetDebugLog.log("tryNetworkRestore: API returned not authenticated")
                return nil
            }

            let timeTracking = LocalJsonTimeTrackingService()
            let snapshot = timeTracking.snapshot(document: result.trackingDoc, nowUtc: now)

            WidgetDebugLog.log("tryNetworkRestore: SUCCESS - user=\(result.session.user?.displayName ?? "?"), status=\(snapshot.status)")
            return (result.session, snapshot, nil)
        } catch {
            WidgetDebugLog.log("tryNetworkRestore: failed: \(error.localizedDescription)")
            return nil
        }
    }
}
