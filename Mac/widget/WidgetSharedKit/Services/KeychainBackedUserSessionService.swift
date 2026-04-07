import AppKit
import Foundation

/// Sesión en JSON del App Group + JWT en llavero (compartible con la extensión si el access group coincide).
final class KeychainBackedUserSessionService: @unchecked Sendable {
    private let store = JsonUserSessionStore()

    func getCurrentSession() throws -> UserSession {
        guard let document = try store.loadDocument() else {
            return .signedOut()
        }
        guard document.state == .signedIn else {
            return .signedOut()
        }
        // La UI (app y widget) solo mira el documento. Las llamadas a la API siguen exigiendo JWT en
        // ClockingURLSessionClient / refreshAsync; no usar Bundle.main ni token aquí: en macOS el proceso
        // del widget de escritorio a veces no coincide con `.appex` y el llavero del host no es visible.
        return toSession(document)
    }

    func getProfilePhotoDataUri() throws -> String? {
        guard let document = try store.loadDocument(),
              document.state == .signedIn,
              let fileName = document.profilePhotoFileName, !fileName.isEmpty
        else { return nil }

        let path = LocalStatePaths.storageDirectory.appendingPathComponent(fileName).path
        guard FileManager.default.fileExists(atPath: path),
              let data = FileManager.default.contents(atPath: path),
              !data.isEmpty
        else { return nil }

        let mime: String
        if fileName.lowercased().hasSuffix(".png") {
            mime = "image/png"
        } else {
            mime = "image/jpeg"
        }
        return "data:\(mime);base64,\(data.base64EncodedString())"
    }

    func profileImage() throws -> NSImage? {
        guard let document = try store.loadDocument(),
              document.state == .signedIn,
              let fileName = document.profilePhotoFileName, !fileName.isEmpty
        else { return nil }
        let url = LocalStatePaths.storageDirectory.appendingPathComponent(fileName)
        return NSImage(contentsOf: url)
    }

    func saveSignedInUserFromServer(user: ClockingUserDTO) throws {
        var document = UserSessionDocument(
            state: .signedIn,
            userId: String(user.id),
            displayName: user.name,
            email: user.email,
            signedInAtUtc: Date(),
            profilePhotoFileName: nil
        )
        try carryOverProfilePhoto(into: &document)
        try store.saveDocument(document)
    }

    func updateUserFromSession(_ body: ClockingSessionResponseBody) throws {
        let u = body.user
        var document = UserSessionDocument(
            state: .signedIn,
            userId: String(u.id),
            displayName: u.name,
            email: u.email,
            signedInAtUtc: Date(),
            profilePhotoFileName: nil
        )
        if let previous = try store.loadDocument(), let photo = previous.profilePhotoFileName {
            document.profilePhotoFileName = photo
        }
        try store.saveDocument(document)
    }

    func signOutClearingDocument() throws {
        try deleteStoredProfileIfAny()
        let cleared = UserSessionDocument(
            state: .signedOut,
            userId: nil,
            displayName: nil,
            email: nil,
            signedInAtUtc: nil,
            profilePhotoFileName: nil
        )
        try store.saveDocument(cleared)
    }

    private func toSession(_ document: UserSessionDocument) -> UserSession {
        guard document.state == .signedIn,
              let userId = document.userId, !userId.isEmpty,
              let name = document.displayName, !name.isEmpty
        else {
            return .signedOut()
        }
        return UserSession(
            state: .signedIn,
            user: CurrentUser(userId: userId, displayName: name, email: document.email),
            signedInAtUtc: document.signedInAtUtc
        )
    }

    private func carryOverProfilePhoto(into document: inout UserSessionDocument) throws {
        guard let previous = try store.loadDocument(),
              let fileName = previous.profilePhotoFileName, !fileName.isEmpty
        else { return }
        let path = LocalStatePaths.storageDirectory.appendingPathComponent(fileName).path
        if FileManager.default.fileExists(atPath: path) {
            document.profilePhotoFileName = fileName
        }
    }

    private func deleteStoredProfileIfAny() throws {
        guard let previous = try store.loadDocument(),
              let fileName = previous.profilePhotoFileName, !fileName.isEmpty
        else { return }
        let url = LocalStatePaths.storageDirectory.appendingPathComponent(fileName)
        try? FileManager.default.removeItem(at: url)
    }
}
