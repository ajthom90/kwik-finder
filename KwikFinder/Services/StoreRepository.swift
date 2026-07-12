import Foundation
import Observation

/// Owns all store data. Loads the bundled snapshot immediately, then layers
/// live locator-API data on top:
///   - the live store list adds stores opened after the snapshot was built
///   - live store details refresh fuel prices, hours, and amenities on demand
/// Family-restroom and EV-charging flags come only from the snapshot (they are
/// published as PDFs, not through the API), so live merges always preserve them.
@MainActor
@Observable
final class StoreRepository {
    private(set) var storesByID: [Int: Store] = [:]
    private(set) var snapshotDate: Date?
    private(set) var liveDetailFetchedAt: [Int: Date] = [:]

    var stores: [Store] { Array(storesByID.values) }

    init() {
        loadSnapshot()
    }

    // MARK: - Bundled snapshot

    private struct Snapshot: Decodable {
        let generatedAt: String
        let stores: [Store]
    }

    private func loadSnapshot() {
        guard let url = Bundle.main.url(forResource: "stores_snapshot", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let snapshot = try? JSONDecoder().decode(Snapshot.self, from: data)
        else {
            assertionFailure("Bundled stores_snapshot.json is missing or malformed")
            return
        }
        snapshotDate = ISO8601DateFormatter().date(from: snapshot.generatedAt)
        storesByID = Dictionary(uniqueKeysWithValues: snapshot.stores.map { ($0.id, $0) })
    }

    // MARK: - Live updates

    /// Picks up stores opened since the snapshot was generated.
    func refreshStoreList() async {
        guard let list = try? await KwikTripAPI.shared.storeList() else { return }
        for entry in list {
            guard storesByID[entry.id] == nil,
                  let latitude = entry.latitude,
                  let longitude = entry.longitude
            else { continue }
            storesByID[entry.id] = Store(
                id: entry.id,
                name: entry.name ?? "KWIK TRIP #\(entry.id)",
                latitude: latitude,
                longitude: longitude,
                address1: entry.address?.address1 ?? "",
                city: entry.address?.city ?? "",
                county: nil,
                state: entry.address?.state ?? "",
                zip: entry.address?.zip?.value ?? "",
                phone: entry.phone ?? "",
                open24Hours: false,
                hours: nil,
                fuels: [],
                amenities: [],
                truckParkingSpaces: 0,
                familyRestroom: false,
                evCharging: nil
            )
        }
    }

    /// Refreshes fuel prices, hours, and amenities for the given stores.
    func refreshDetails(ids: [Int]) async {
        let wanted = ids.filter { storesByID[$0] != nil }
        guard !wanted.isEmpty,
              let details = try? await KwikTripAPI.shared.storeDetails(ids: wanted)
        else { return }
        let now = Date()
        for detail in details {
            apply(detail, fetchedAt: now)
        }
    }

    private func apply(_ detail: StoreDetail, fetchedAt: Date) {
        guard var store = storesByID[detail.storeNumber] else { return }

        if let name = detail.name { store.name = name }
        if let phone = detail.phone { store.phone = phone }
        if let open24 = detail.open24Hours { store.open24Hours = open24 }
        if let hours = detail.hours { store.hours = hours }
        if let address = detail.address {
            if let latitude = address.latitude { store.latitude = latitude }
            if let longitude = address.longitude { store.longitude = longitude }
            if let address1 = address.address1 { store.address1 = address1 }
            if let city = address.city { store.city = city }
            if let state = address.state { store.state = state }
            if let zip = address.zip { store.zip = zip.value }
            store.county = address.county ?? store.county
        }
        if let fuel = detail.fuel {
            store.fuels = fuel.compactMap { entry in
                guard let type = entry.type else { return nil }
                return FuelOffering(type: type, description: entry.description, price: entry.currentPrice)
            }
        }
        if let properties = detail.properties {
            store.amenities = properties
                .filter { $0.hasProperty == true }
                .compactMap(\.name)
                .sorted()
            store.truckParkingSpaces = properties
                .first { $0.name == "TRUCK-PARKING" }?.quantity ?? store.truckParkingSpaces
        }
        // familyRestroom / evCharging intentionally untouched: PDF-sourced.

        storesByID[detail.storeNumber] = store
        liveDetailFetchedAt[detail.storeNumber] = fetchedAt
    }

    /// When the fuel prices shown for a store were last confirmed.
    func pricesAsOf(_ id: Int) -> Date? {
        liveDetailFetchedAt[id] ?? snapshotDate
    }

    func isLive(_ id: Int) -> Bool {
        liveDetailFetchedAt[id] != nil
    }
}
