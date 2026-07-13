import CoreLocation
import Foundation
import Observation

/// High-level status of live data, for UI banners.
enum LiveDataStatus: Equatable {
    /// Only the bundled snapshot has been loaded; no successful live fetch yet.
    case snapshotOnly
    /// A live refresh is in flight.
    case refreshing
    /// Live data was fetched successfully at the given time.
    case live(Date)
    /// Last attempt failed; optional date of the last success if any.
    case offline(lastSuccess: Date?, message: String)
}

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

    /// User-visible live-refresh status (list + nearest details combined).
    private(set) var liveStatus: LiveDataStatus = .snapshotOnly
    private(set) var lastSuccessfulRefresh: Date?
    private(set) var lastErrorMessage: String?

    private var lastListRefreshAt: Date?
    private var lastNearestDetailsAt: Date?
    private var refreshGeneration = 0

    /// Minimum gaps between automatic network refreshes (avoids hammering).
    private static let listMinInterval: TimeInterval = 5 * 60
    private static let nearestDetailsMinInterval: TimeInterval = 3 * 60
    /// How many nearest stores get detail refresh (API batch cap is 10).
    static let nearestDetailBatchSize = 10

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
    /// - Parameter force: when true, ignores the list throttle (e.g. user pull).
    @discardableResult
    func refreshStoreList(force: Bool = false) async -> Bool {
        if !force, let last = lastListRefreshAt, Date().timeIntervalSince(last) < Self.listMinInterval {
            return lastSuccessfulRefresh != nil
        }
        lastListRefreshAt = Date()
        liveStatus = .refreshing

        do {
            let list = try await KwikTripAPI.shared.storeList()
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
            markLiveSuccess()
            return true
        } catch {
            markLiveFailure(message: "Couldn't refresh the store list. Showing saved data.")
            return false
        }
    }

    /// Refreshes fuel prices, hours, and amenities for the given stores.
    /// - Parameter force: when true, always hits the network (store detail open).
    @discardableResult
    func refreshDetails(ids: [Int], force: Bool = true) async -> Bool {
        let wanted = ids.filter { storesByID[$0] != nil }
        guard !wanted.isEmpty else { return true }

        // Single-store opens and explicit force always go through.
        // Batch nearest refreshes may be throttled by the caller via force: false.
        do {
            let details = try await KwikTripAPI.shared.storeDetails(ids: wanted)
            let now = Date()
            for detail in details {
                apply(detail, fetchedAt: now)
            }
            markLiveSuccess()
            return true
        } catch {
            markLiveFailure(message: "Couldn't update live prices. Showing last known data.")
            return false
        }
    }

    /// Lightweight policy used while the app is active: refresh the store list
    /// and re-fetch details for the nearest stores (API 10-id cap), throttled.
    func refreshWhileActive(around location: CLLocation, force: Bool = false) async {
        refreshGeneration += 1
        let generation = refreshGeneration
        liveStatus = .refreshing

        let listOK = await refreshStoreList(force: force)
        guard generation == refreshGeneration else { return }

        let shouldRefreshNearest = force
            || lastNearestDetailsAt.map { Date().timeIntervalSince($0) >= Self.nearestDetailsMinInterval } ?? true

        if shouldRefreshNearest {
            lastNearestDetailsAt = Date()
            let nearest = stores
                .sorted { $0.distance(from: location) < $1.distance(from: location) }
                .prefix(Self.nearestDetailBatchSize)
                .map(\.id)
            _ = await refreshDetails(ids: nearest, force: true)
        } else if listOK, lastSuccessfulRefresh != nil {
            // List ok and details still fresh — keep live status.
            if let last = lastSuccessfulRefresh {
                liveStatus = .live(last)
            }
        }
    }

    private func markLiveSuccess() {
        let now = Date()
        lastSuccessfulRefresh = now
        lastErrorMessage = nil
        liveStatus = .live(now)
    }

    private func markLiveFailure(message: String) {
        lastErrorMessage = message
        liveStatus = .offline(lastSuccess: lastSuccessfulRefresh, message: message)
    }

    private func apply(_ detail: StoreDetail, fetchedAt: Date) {
        guard var store = storesByID[detail.storeNumber] else { return }

        // Preserve PDF-only fields across live merges.
        let preservedFamilyRestroom = store.familyRestroom
        let preservedEV = store.evCharging

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

        store.familyRestroom = preservedFamilyRestroom
        store.evCharging = preservedEV

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
