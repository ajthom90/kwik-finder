import Foundation

// MARK: - Response types (OpenAPI CatalogMeta / StoresResponse)

struct CatalogMeta: Codable, Hashable {
    let dataVersion: String?
    let storeCount: Int?
    let listRefreshedAt: String?
    let detailsRefreshedAt: String?
    let pdfEnrichedAt: String?
}

struct CatalogResponse: Codable {
    let meta: CatalogMeta
    let stores: [Store]
}

// MARK: - Client

/// HTTP client for the KwikFinder catalog server (`/v1/*`).
/// Contract: `docs/api/openapi.yaml`.
struct KwikFinderAPI {
    static let shared = KwikFinderAPI()

    private let session: URLSession
    private let baseURL: URL
    private let decoder: JSONDecoder

    init(baseURL: URL = APIConfig.baseURL, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
        self.decoder = JSONDecoder()
    }

    /// Lightweight catalog version probe (`GET /v1/meta`).
    func fetchMeta() async throws -> CatalogMeta {
        try await get(path: "v1/meta")
    }

    /// Full catalog: meta + stores (`GET /v1/stores`).
    func fetchStores() async throws -> CatalogResponse {
        try await get(path: "v1/stores")
    }

    // MARK: - Internals

    private func get<T: Decodable>(path: String) async throws -> T {
        let url = baseURL.appendingPathComponent(path)
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw KwikFinderAPIError.invalidResponse
        }
        guard (200..<300).contains(http.statusCode) else {
            throw KwikFinderAPIError.httpStatus(http.statusCode)
        }
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw KwikFinderAPIError.decoding(error)
        }
    }
}

enum KwikFinderAPIError: Error, LocalizedError {
    case invalidResponse
    case httpStatus(Int)
    case decoding(Error)

    var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return "Invalid response from KwikFinder server."
        case .httpStatus(let code):
            return "KwikFinder server returned HTTP \(code)."
        case .decoding(let error):
            return "Could not decode KwikFinder response: \(error.localizedDescription)"
        }
    }
}
