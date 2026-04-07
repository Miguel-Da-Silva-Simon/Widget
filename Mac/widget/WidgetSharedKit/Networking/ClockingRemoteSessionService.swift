import Foundation

/// Orquesta login, sincronización `clockings/today` y acciones `attendance/actions` contra el backend Docker.
final class ClockingRemoteSessionService: @unchecked Sendable {
    private let client: ClockingURLSessionClient
    private let trackingStore = JsonTimeTrackingStore()
    private let userStore = KeychainBackedUserSessionService()

    init() {
        client = ClockingURLSessionClient {
            ClockingJWTKeychain.readToken()
        }
    }

    func login(email: String, password: String) async throws {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let res = try await client.login(email: trimmedEmail, password: password)
        do {
            try ClockingJWTKeychain.saveToken(res.token)
            try userStore.saveSignedInUserFromServer(user: res.user)
            try await pullTodayAndPersist()
        } catch {
            ClockingJWTKeychain.deleteToken()
            try? userStore.signOutClearingDocument()
            try? trackingStore.save(.createDefault())
            throw error
        }
    }

    /// Valida token con `GET /auth/session` y actualiza usuario + estado del día.
    func restoreSessionAndSync() async throws -> Bool {
        guard ClockingJWTKeychain.readToken() != nil else { return false }
        let sess = try await client.session()
        if sess.authenticated == false { return false }
        try userStore.updateUserFromSession(sess)
        try await pullTodayAndPersist()
        return true
    }

    /// Obtiene sesión y estado del día desde el API **sin escribir nada en disco**.
    /// Diseñado para la extensión del widget, que a veces no tiene permisos de escritura
    /// en el Group Container.
    func fetchSessionAndStateInMemory() async throws -> (session: UserSession, trackingDoc: TimeTrackingStateDocument)? {
        guard ClockingJWTKeychain.readToken() != nil else { return nil }
        let sess = try await client.session()
        if sess.authenticated == false { return nil }

        let user = CurrentUser(
            userId: String(sess.user.id),
            displayName: sess.user.name,
            email: sess.user.email
        )
        let session = UserSession(state: .signedIn, user: user, signedInAtUtc: Date())

        let dto = try await client.today()
        let trackingDoc = ServerClockingStateMapper.document(from: dto)

        return (session, trackingDoc)
    }

    func pullTodayAndPersist() async throws {
        let dto = try await client.today()
        try persist(dto)
    }

    func logout() async {
        try? await client.logout()
        ClockingJWTKeychain.deleteToken()
        try? userStore.signOutClearingDocument()
        try? trackingStore.save(.createDefault())
    }

    func clearLocalCredentialsOnly() {
        ClockingJWTKeychain.deleteToken()
        try? userStore.signOutClearingDocument()
        try? trackingStore.save(.createDefault())
    }

    func performTimeTrackingAction(_ action: TimeTrackingAction) async throws {
        let name = Self.serverActionName(for: action)
        let dto = try await client.attendanceAction(name)
        do {
            try persist(dto)
        } catch {
            NSLog("[FichajeWidget] persist after action failed (sandbox?): \(error.localizedDescription)")
        }
    }

    private func persist(_ dto: ClockingStateDTO) throws {
        let doc = ServerClockingStateMapper.document(from: dto)
        try trackingStore.save(doc)
    }

    private static func serverActionName(for action: TimeTrackingAction) -> String {
        switch action {
        case .clockIn: return "CLOCK_IN"
        case .clockOut: return "CLOCK_OUT"
        case .startBreak, .startCoffeeBreak: return "BREAK_START"
        case .endBreak, .endCoffeeBreak: return "BREAK_END"
        case .startFoodBreak: return "MEAL_START"
        case .endFoodBreak: return "MEAL_END"
        case .none: return "CLOCK_IN"
        }
    }
}
