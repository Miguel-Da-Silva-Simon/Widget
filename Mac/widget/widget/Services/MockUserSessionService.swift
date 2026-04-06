import AppKit
import Foundation

final class MockUserSessionService: @unchecked Sendable {
    private let store = JsonUserSessionStore()

    func getCurrentSession() throws -> UserSession {
        guard let document = try store.loadDocument() else {
            return .signedOut()
        }
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

    func signIn(displayName: String?) throws -> UserSession {
        let rawName = displayName?.trimmingCharacters(in: .whitespacesAndNewlines)
        let fallback = ProcessInfo.processInfo.userName.trimmingCharacters(in: .whitespacesAndNewlines)
        let safeName = (rawName?.isEmpty == false) ? rawName! : (fallback.isEmpty ? "local-user" : fallback)
        let userId = safeName

        var document = UserSessionDocument(
            state: .signedIn,
            userId: userId,
            displayName: safeName,
            email: nil,
            signedInAtUtc: Date(),
            profilePhotoFileName: nil
        )
        try carryOverProfilePhoto(into: &document)
        try store.saveDocument(document)
        return toSession(document)
    }

    func signOut() throws {
        try deleteStoredProfilePhotoIfAny()
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

    private func deleteStoredProfilePhotoIfAny() throws {
        guard let previous = try store.loadDocument(),
              let fileName = previous.profilePhotoFileName, !fileName.isEmpty
        else { return }
        let url = LocalStatePaths.storageDirectory.appendingPathComponent(fileName)
        try? FileManager.default.removeItem(at: url)
    }
}
