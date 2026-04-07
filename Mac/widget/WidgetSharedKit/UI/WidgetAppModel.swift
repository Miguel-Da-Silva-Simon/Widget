import AppKit
import Foundation
import SwiftUI
import WidgetKit

@MainActor
public final class WidgetAppModel: ObservableObject {
    private static let appGroupMissingUserMessage =
        "Sin contenedor de App Group: la app y el widget no pueden compartir sesión. En Xcode, targets widget y TimeTrackingWidgetExtension → Signing & Capabilities → App Groups (1) y el mismo Team que en developer.apple.com."

    private let timeTracking = LocalJsonTimeTrackingService()
    private let sessionService = KeychainBackedUserSessionService()
    private let remote = ClockingRemoteSessionService()

    @Published public private(set) var snapshot: TimeTrackingSnapshot = .initial()
    @Published public private(set) var session: UserSession = .signedOut()
    @Published public var lastError: String?
    @Published public var authInProgress = false

    /// - Parameter skipInitialRefresh: `true` para la vista del widget que recibe datos del `TimelineProvider` (evita doble lectura).
    public init(skipInitialRefresh: Bool = false) {
        if !skipInitialRefresh {
            refresh()
        }
    }

    public func refresh() {
        Task { await refreshAsync() }
    }

    private func refreshAsync() async {
        LocalStatePaths.repairGroupContainerPermissions()
        WidgetSessionMirror.backfillMirrorsFromSharedFilesIfNeeded()
        var hadSyncError = false
        if ClockingJWTKeychain.readToken() != nil {
            do {
                _ = try await remote.restoreSessionAndSync()
            } catch ClockingApiError.unauthorized {
                remote.clearLocalCredentialsOnly()
                lastError = ClockingApiError.unauthorized.localizedDescription
                hadSyncError = true
            } catch {
                lastError = error.localizedDescription
                hadSyncError = true
            }
        }
        reloadFromDisk(hadSyncError: hadSyncError)
    }

    private var isAppGroupActive: Bool {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: AppGroupConfiguration.identifier) != nil
    }

    private func reloadFromDisk(hadSyncError: Bool) {
        do {
            session = try sessionService.getCurrentSession()
            snapshot = try timeTracking.getState()
            if !isAppGroupActive {
                lastError = Self.appGroupMissingUserMessage
            } else if !hadSyncError {
                lastError = nil
            }
        } catch {
            lastError = error.localizedDescription
        }
    }

    func presentation(now: Date) -> TimeTrackingWidgetPresentation {
        let profile = try? sessionService.profileImage()
        return TimeTrackingWidgetViewModelMapper.map(
            session: session,
            snapshot: snapshot,
            profileImage: profile,
            now: now
        )
    }

    public func openApp() {
        #if os(macOS)
        NSApplication.shared.activate(ignoringOtherApps: true)
        #endif
    }

    public func applyDeepLinkAction(_ action: WidgetDeepLinkAction) {
        switch action {
        case .openLogin:
            openApp()
        case .clockIn:
            clockIn()
        case .clockOut:
            clockOut()
        case .toggleCoffee:
            toggleCoffee()
        case .toggleFood:
            toggleFood()
        case .signOut:
            signOut()
        }
    }

    public func signIn(email: String, password: String) {
        authInProgress = true
        lastError = nil
        Task { @MainActor in
            defer { authInProgress = false }
            do {
                try await remote.login(email: email, password: password)
                reloadFromDisk(hadSyncError: false)
                if isAppGroupActive { lastError = nil }
                reloadWidgetTimelines()
            } catch {
                lastError = error.localizedDescription
            }
        }
    }

    public func signOut() {
        Task { @MainActor in
            await remote.logout()
            lastError = nil
            reloadFromDisk(hadSyncError: false)
            reloadWidgetTimelines()
        }
    }

    public func clockIn() {
        runRemoteAction(.clockIn)
    }

    public func clockOut() {
        runRemoteAction(.clockOut)
    }

    public func toggleCoffee() {
        if snapshot.activeBreakType == .coffee {
            runRemoteAction(.endCoffeeBreak)
        } else {
            runRemoteAction(.startCoffeeBreak)
        }
    }

    public func toggleFood() {
        if snapshot.activeBreakType == .food {
            runRemoteAction(.endFoodBreak)
        } else {
            runRemoteAction(.startFoodBreak)
        }
    }

    private func runRemoteAction(_ action: TimeTrackingAction) {
        Task { @MainActor in
            do {
                try await remote.performTimeTrackingAction(action)
                reloadFromDisk(hadSyncError: false)
                if isAppGroupActive { lastError = nil }
                reloadWidgetTimelines()
            } catch ClockingApiError.unauthorized {
                remote.clearLocalCredentialsOnly()
                lastError = ClockingApiError.unauthorized.localizedDescription
                reloadFromDisk(hadSyncError: true)
                reloadWidgetTimelines()
            } catch {
                lastError = error.localizedDescription
                reloadWidgetTimelines()
            }
        }
    }

    private func reloadWidgetTimelines() {
        let kind = TimeTrackingWidgetKind.value
        WidgetCenter.shared.reloadTimelines(ofKind: kind)
        WidgetCenter.shared.reloadAllTimelines()
        #if os(macOS)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            WidgetCenter.shared.reloadTimelines(ofKind: kind)
            WidgetCenter.shared.reloadAllTimelines()
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
            WidgetCenter.shared.reloadTimelines(ofKind: kind)
        }
        #endif
    }
}
