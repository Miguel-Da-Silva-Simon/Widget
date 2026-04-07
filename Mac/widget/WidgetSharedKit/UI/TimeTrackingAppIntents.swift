import Foundation
import WidgetKit

// MARK: - Public facade for intent business logic
// AppIntent structs are defined in the widget extension target
// (TimeTrackingWidgetExtension/WidgetActionIntents.swift) so that
// macOS discovers the metadata in the extension bundle.

public enum WidgetIntentActions {
    public static func performAction(_ action: TimeTrackingAction) async {
        NSLog("[FichajeWidget] WidgetIntentActions.performAction(%@) — pid=%d bundle=%@",
              action.rawValue, ProcessInfo.processInfo.processIdentifier,
              Bundle.main.bundleIdentifier ?? "?")

        guard ClockingJWTKeychain.readToken() != nil else {
            NSLog("[FichajeWidget] ABORTED %@ — no JWT token found in any channel", action.rawValue)
            WidgetCenter.shared.reloadAllTimelines()
            return
        }

        let remote = ClockingRemoteSessionService()
        do {
            try await remote.performTimeTrackingAction(action)
            NSLog("[FichajeWidget] Action %@ SUCCEEDED — reloading timelines", action.rawValue)
        } catch {
            NSLog("[FichajeWidget] Action %@ FAILED: %@", action.rawValue, "\(error)")
        }

        WidgetCenter.shared.reloadAllTimelines()
    }
}
