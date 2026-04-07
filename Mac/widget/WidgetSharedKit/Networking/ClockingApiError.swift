import Foundation

enum ClockingApiError: Error, LocalizedError {
    case invalidURL
    case noData
    case decodingFailed(String)
    case httpStatus(code: Int, message: String?)
    case unauthorized
    case network(Error)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "URL del servidor no válida."
        case .noData:
            return "El servidor no devolvió datos."
        case .decodingFailed(let detail):
            return "Respuesta del servidor ilegible: \(detail)"
        case .httpStatus(let code, let message):
            if let message, !message.isEmpty {
                return "Error del servidor (\(code)): \(message)"
            }
            return "Error del servidor (\(code))."
        case .unauthorized:
            return "No autorizado: revisa email y contraseña, o vuelve a iniciar sesión si el token caducó."
        case .network(let err):
            return err.localizedDescription
        }
    }
}
