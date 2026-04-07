import Foundation

enum WidgetJsonCoding {
    static func makeDecoder() -> JSONDecoder {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let string = try container.decode(String.self)
            if let date = parseIso8601(string) {
                return date
            }
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid ISO8601 date: \(string)")
        }
        return d
    }

    static func makeEncoder() -> JSONEncoder {
        let e = JSONEncoder()
        e.outputFormatting = [.prettyPrinted, .sortedKeys]
        e.dateEncodingStrategy = .custom { date, encoder in
            var container = encoder.singleValueContainer()
            try container.encode(formatIso8601(date))
        }
        return e
    }

    private static func parseIso8601(_ string: String) -> Date? {
        let f1 = ISO8601DateFormatter()
        f1.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = f1.date(from: string) { return d }
        let f2 = ISO8601DateFormatter()
        f2.formatOptions = [.withInternetDateTime]
        if let d = f2.date(from: string) { return d }
        return nil
    }

    /// Valores heterogéneos en JSON (p. ej. fecha ISO en string o epoch en número) para recuperación tolerante en el widget.
    static func dateFromLooseJsonValue(_ value: Any?) -> Date? {
        switch value {
        case let s as String:
            return parseIso8601(s)
        case let n as Double:
            return Date(timeIntervalSince1970: n)
        case let n as Int:
            return Date(timeIntervalSince1970: TimeInterval(n))
        case let n as NSNumber:
            return Date(timeIntervalSince1970: n.doubleValue)
        default:
            return nil
        }
    }

    private static func formatIso8601(_ date: Date) -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f.string(from: date)
    }
}
