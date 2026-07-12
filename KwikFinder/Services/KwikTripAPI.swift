import Foundation

/// Client for the public JSON endpoints behind kwiktrip.com's store locator.
/// These are the same endpoints the locator web page calls; they are not a
/// documented API, so every response field is decoded defensively.
struct KwikTripAPI {
    static let shared = KwikTripAPI()

    private let session = URLSession.shared
    private static let storeListURL = URL(string: "https://www.kwiktrip.com/storelistproxy.php")!
    private static let storeInfoBase = "https://www.kwiktrip.com/storeinformationsproxy.php?ids="
    /// The details endpoint rejects requests with more than 10 ids.
    private static let batchLimit = 10

    func storeList() async throws -> [ListStore] {
        let (data, _) = try await session.data(from: Self.storeListURL)
        return try JSONDecoder().decode(ListResponse.self, from: data).stores
    }

    func storeDetails(ids: [Int]) async throws -> [StoreDetail] {
        var details: [StoreDetail] = []
        for chunkStart in stride(from: 0, to: ids.count, by: Self.batchLimit) {
            let chunk = ids[chunkStart..<min(chunkStart + Self.batchLimit, ids.count)]
            let url = URL(string: Self.storeInfoBase + chunk.map(String.init).joined(separator: ","))!
            let (data, _) = try await session.data(from: url)
            details += try JSONDecoder().decode(DetailResponse.self, from: data).stores
        }
        return details
    }
}

// MARK: - Wire types

/// Decodes a value the API sometimes sends as a number and sometimes as a string.
struct LenientString: Decodable, Hashable {
    let value: String

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let string = try? container.decode(String.self) {
            value = string
        } else if let int = try? container.decode(Int.self) {
            value = String(int)
        } else if let double = try? container.decode(Double.self) {
            value = String(double)
        } else {
            value = ""
        }
    }
}

struct ListResponse: Decodable {
    let stores: [ListStore]
}

struct ListStore: Decodable {
    let id: Int
    let name: String?
    let latitude: Double?
    let longitude: Double?
    let phone: String?
    let address: ListAddress?
}

struct ListAddress: Decodable {
    let address1: String?
    let city: String?
    let state: String?
    let zip: LenientString?
}

struct DetailResponse: Decodable {
    let stores: [StoreDetail]
}

struct StoreDetail: Decodable {
    let storeNumber: Int
    let name: String?
    let address: DetailAddress?
    let hours: [StoreHours]?
    let open24Hours: Bool?
    let phone: String?
    let fuel: [DetailFuel]?
    let properties: [DetailProperty]?
}

struct DetailAddress: Decodable {
    let address1: String?
    let address2: String?
    let city: String?
    let county: String?
    let state: String?
    let zip: LenientString?
    let latitude: Double?
    let longitude: Double?
}

struct DetailFuel: Decodable {
    let type: String?
    let description: String?
    let currentPrice: Double?
}

struct DetailProperty: Decodable {
    let name: String?
    let hasProperty: Bool?
    let quantity: Int?
}
