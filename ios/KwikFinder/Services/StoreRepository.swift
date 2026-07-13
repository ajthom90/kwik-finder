import Foundation
import Observation

/// High-level status of catalog data, for UI banners.
enum LiveDataStatus: Equatable {
    /// Disk cache (or empty cold start); no successful network refresh this session yet.
    case snapshotOnly
    /// A catalog refresh is in flight.
    case refreshing
    /// Catalog was fetched successfully at the given time.
    case live(Date)
    /// Last attempt failed; optional date of the last success if any.
    case offline(lastSuccess: Date?, message: String)
}

/// Owns all store data. Loads `Caches/catalog.json` on init, then refreshes
/// from the KwikFinder server (`GET /v1/meta`, then `/v1/stores` when needed).
/// Clients never contact kwiktrip.com — only the KwikFinder catalog API.
@MainActor
@Observable
final class StoreRepository {
    private(set) var storesByID: [Int: Store] = [:]
    private(set) var catalogMeta: CatalogMeta?
    /// `dataVersion` from the last applied catalog (disk or network).
    private(set) var cachedDataVersion: String?
    /// When the on-disk catalog was last written (or loaded with a known meta timestamp).
    private(set) var cacheWrittenAt: Date?

    /// User-visible catalog-refresh status.
    private(set) var liveStatus: LiveDataStatus = .snapshotOnly
    private(set) var lastSuccessfulRefresh: Date?
    private(set) var lastErrorMessage: String?

    private var lastMetaCheckAt: Date?
    private var refreshGeneration = 0

    /// Mild debounce so launch + foreground + location settle don't stampede meta.
    private static let metaMinInterval: TimeInterval = 30

    private let api: KwikFinderAPI
    private let fileManager: FileManager

    private var cacheURL: URL {
        fileManager.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("catalog.json")
    }

    var stores: [Store] { Array(storesByID.values) }

    init(api: KwikFinderAPI = .shared, fileManager: FileManager = .default) {
        self.api = api
        self.fileManager = fileManager
        loadDiskCache()
    }

    // MARK: - Disk cache

    private func loadDiskCache() {
        let url = cacheURL
        guard fileManager.fileExists(atPath: url.path),
              let data = try? Data(contentsOf: url),
              let catalog = try? JSONDecoder().decode(CatalogResponse.self, from: data)
        else {
            liveStatus = .snapshotOnly
            return
        }
        applyCatalog(catalog, fromNetwork: false)
        if let attrs = try? fileManager.attributesOfItem(atPath: url.path),
           let modified = attrs[.modificationDate] as? Date {
            cacheWrittenAt = modified
        }
        liveStatus = .snapshotOnly
    }

    private func writeDiskCache(_ catalog: CatalogResponse) {
        do {
            let data = try JSONEncoder().encode(catalog)
            try data.write(to: cacheURL, options: .atomic)
            cacheWrittenAt = Date()
        } catch {
            // Cache write failures are non-fatal; in-memory catalog still works.
            lastErrorMessage = "Couldn't save catalog cache: \(error.localizedDescription)"
        }
    }

    private func applyCatalog(_ catalog: CatalogResponse, fromNetwork: Bool) {
        storesByID = Dictionary(uniqueKeysWithValues: catalog.stores.map { ($0.id, $0) })
        catalogMeta = catalog.meta
        cachedDataVersion = catalog.meta.dataVersion
        if fromNetwork {
            writeDiskCache(catalog)
        }
    }

    // MARK: - Server refresh

    /// Probe meta, then download full catalog when version differs, cache is empty, or forced.
    /// - Parameter force: skip debounce and always re-download stores.
    @discardableResult
    func refresh(force: Bool = false) async -> Bool {
        if !force,
           let last = lastMetaCheckAt,
           Date().timeIntervalSince(last) < Self.metaMinInterval,
           !storesByID.isEmpty {
            if let lastSuccess = lastSuccessfulRefresh {
                liveStatus = .live(lastSuccess)
                return true
            }
            return false
        }

        refreshGeneration += 1
        let generation = refreshGeneration
        lastMetaCheckAt = Date()
        liveStatus = .refreshing

        do {
            let meta = try await api.fetchMeta()
            guard generation == refreshGeneration else { return false }

            let remoteVersion = meta.dataVersion
            let needsDownload = force
                || storesByID.isEmpty
                || remoteVersion == nil
                || remoteVersion != cachedDataVersion

            if needsDownload {
                let catalog = try await api.fetchStores()
                guard generation == refreshGeneration else { return false }
                applyCatalog(catalog, fromNetwork: true)
            } else {
                // Meta matches cache — keep stores, refresh meta timestamps in memory.
                catalogMeta = meta
            }

            markLiveSuccess()
            return true
        } catch {
            guard generation == refreshGeneration else { return false }
            let message = storesByID.isEmpty
                ? "Couldn't load stores. Connect once to download the catalog."
                : "Couldn't refresh the catalog. Showing saved data."
            markLiveFailure(message: message)
            return false
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

    // MARK: - Price freshness helpers (detail UI)

    /// When fuel prices in the catalog were last confirmed (server details refresh or local fetch).
    func pricesAsOf(_ id: Int) -> Date? {
        _ = id
        if let lastSuccessfulRefresh { return lastSuccessfulRefresh }
        if let details = catalogMeta?.detailsRefreshedAt.flatMap(Self.parseISO8601) {
            return details
        }
        return cacheWrittenAt
    }

    /// True after a successful network refresh this session (or when status is live).
    func isLive(_ id: Int) -> Bool {
        _ = id
        if case .live = liveStatus { return true }
        return lastSuccessfulRefresh != nil
    }

    private static func parseISO8601(_ string: String) -> Date? {
        let withFractional = ISO8601DateFormatter()
        withFractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = withFractional.date(from: string) { return date }
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        return plain.date(from: string)
    }
}
