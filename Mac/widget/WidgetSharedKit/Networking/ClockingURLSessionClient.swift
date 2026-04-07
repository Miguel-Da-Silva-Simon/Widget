import Foundation

/// Cliente HTTP alineado con [ClockingApiService.kt](Android/androidApp/.../ClockingApiService.kt).
final class ClockingURLSessionClient: @unchecked Sendable {
    private let session: URLSession
    private let jsonDecoder: JSONDecoder
    private let jsonEncoder: JSONEncoder
    private let tokenProvider: @Sendable () -> String?

    init(session: URLSession = .shared, tokenProvider: @escaping @Sendable () -> String?) {
        self.session = session
        self.tokenProvider = tokenProvider
        jsonDecoder = JSONDecoder()
        jsonEncoder = JSONEncoder()
    }

    func login(email: String, password: String) async throws -> ClockingLoginResponseBody {
        try await post(
            path: "auth/login",
            body: ClockingLoginRequestBody(email: email, password: password),
            authorized: false
        )
    }

    func session() async throws -> ClockingSessionResponseBody {
        try await get(path: "auth/session", authorized: true)
    }

    func logout() async throws {
        try await postExpectingNoBody(path: "auth/logout", authorized: true)
    }

    func me() async throws -> ClockingUserDTO {
        try await get(path: "me", authorized: true)
    }

    func today() async throws -> ClockingStateDTO {
        try await get(path: "clockings/today", authorized: true)
    }

    func attendanceAction(_ action: String) async throws -> ClockingStateDTO {
        try await post(
            path: "attendance/actions",
            body: AttendanceActionRequestBody(action: action),
            authorized: true
        )
    }

    func reset() async throws -> ClockingStateDTO {
        try await post(path: "clockings/reset", body: EmptyJSONBody(), authorized: true)
    }

    // MARK: - Core

    private func get<T: Decodable>(path: String, authorized: Bool) async throws -> T {
        let url = try makeURL(path: path)
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        if authorized { try attachBearer(&request) }
        return try await send(request)
    }

    private func post<B: Encodable, T: Decodable>(path: String, body: B, authorized: Bool) async throws -> T {
        let url = try makeURL(path: path)
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try jsonEncoder.encode(body)
        if authorized { try attachBearer(&request) }
        return try await send(request)
    }

    private struct EmptyJSONBody: Encodable {}

    private func postExpectingNoBody(path: String, authorized: Bool) async throws {
        let url = try makeURL(path: path)
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data("{}".utf8)
        if authorized { try attachBearer(&request) }
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw ClockingApiError.network(error)
        }
        guard let http = response as? HTTPURLResponse else {
            throw ClockingApiError.noData
        }
        if http.statusCode == 401 {
            throw ClockingApiError.unauthorized
        }
        if http.statusCode < 200 || http.statusCode >= 300 {
            let message = String(data: data, encoding: .utf8)
            throw ClockingApiError.httpStatus(code: http.statusCode, message: message)
        }
    }

    private func makeURL(path: String) throws -> URL {
        let base = ClockingApiConfiguration.baseURL
        let trimmed = path.hasPrefix("/") ? String(path.dropFirst()) : path
        guard let url = URL(string: trimmed, relativeTo: base)?.absoluteURL else {
            throw ClockingApiError.invalidURL
        }
        return url
    }

    private func attachBearer(_ request: inout URLRequest) throws {
        guard let token = tokenProvider(), !token.isEmpty else {
            throw ClockingApiError.unauthorized
        }
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
    }

    private func send<T: Decodable>(_ request: URLRequest) async throws -> T {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw ClockingApiError.network(error)
        }
        guard let http = response as? HTTPURLResponse else {
            throw ClockingApiError.noData
        }
        if http.statusCode == 401 {
            throw ClockingApiError.unauthorized
        }
        if http.statusCode < 200 || http.statusCode >= 300 {
            let message = String(data: data, encoding: .utf8)
            throw ClockingApiError.httpStatus(code: http.statusCode, message: message)
        }
        do {
            return try jsonDecoder.decode(T.self, from: data)
        } catch {
            let snippet = String(data: data.prefix(512), encoding: .utf8) ?? ""
            throw ClockingApiError.decodingFailed("\(error.localizedDescription) \(snippet)")
        }
    }
}
