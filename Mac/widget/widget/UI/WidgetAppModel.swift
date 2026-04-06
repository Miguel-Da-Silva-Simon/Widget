import AppKit
import Foundation
import SwiftUI

@MainActor
final class WidgetAppModel: ObservableObject {
    private let timeTracking = LocalJsonTimeTrackingService()
    private let sessionService = MockUserSessionService()

    @Published private(set) var snapshot: TimeTrackingSnapshot = .initial()
    @Published private(set) var session: UserSession = .signedOut()
    @Published var showLoginSheet = false
    @Published var lastError: String?

    init() {
        try? refresh()
    }

    func refresh() {
        do {
            session = try sessionService.getCurrentSession()
            snapshot = try timeTracking.getState()
            lastError = nil
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

    func openApp() {
        showLoginSheet = true
    }

    func signInMock(displayName: String) {
        do {
            _ = try sessionService.signIn(displayName: displayName.isEmpty ? nil : displayName)
            refresh()
            showLoginSheet = false
        } catch {
            lastError = error.localizedDescription
        }
    }

    func signOutMock() {
        do {
            try sessionService.signOut()
            refresh()
        } catch {
            lastError = error.localizedDescription
        }
    }

    func clockIn() { perform { try timeTracking.clockIn() } }
    func clockOut() { perform { try timeTracking.clockOut() } }
    func toggleCoffee() {
        perform {
            if snapshot.activeBreakType == .coffee {
                try timeTracking.endCoffeeBreak()
            } else {
                try timeTracking.startCoffeeBreak()
            }
        }
    }

    func toggleFood() {
        perform {
            if snapshot.activeBreakType == .food {
                try timeTracking.endFoodBreak()
            } else {
                try timeTracking.startFoodBreak()
            }
        }
    }

    private func perform(_ block: () throws -> TimeTrackingCommandResult) {
        do {
            let result = try block()
            snapshot = result.snapshot
            if !result.success, let message = result.message {
                lastError = message
            } else {
                lastError = nil
            }
        } catch {
            lastError = error.localizedDescription
        }
    }
}
