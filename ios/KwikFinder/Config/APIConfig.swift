import Foundation

/// Base URL for the KwikFinder catalog server.
///
/// - Debug: defaults to `http://localhost:8080` (Docker Compose / local server).
///   Override via Info.plist key `KwikFinderAPIBaseURL` (e.g. LAN IP for a physical device).
/// - Release: requires `KwikFinderAPIBaseURL` in Info.plist; falls back to localhost only
///   if the key is missing (misconfiguration safeguard).
enum APIConfig {
    static let baseURLInfoPlistKey = "KwikFinderAPIBaseURL"

    static var baseURL: URL {
        if let configured = configuredBaseURL {
            return configured
        }
        #if DEBUG
        return URL(string: "http://localhost:8080")!
        #else
        // Release builds should set KwikFinderAPIBaseURL in the generated Info.plist.
        assertionFailure("KwikFinderAPIBaseURL missing from Info.plist; using localhost fallback")
        return URL(string: "http://localhost:8080")!
        #endif
    }

    private static var configuredBaseURL: URL? {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: baseURLInfoPlistKey) as? String
        else { return nil }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return URL(string: trimmed)
    }
}
