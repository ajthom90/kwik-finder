import Foundation
import Observation

/// Persisted set of favorited store IDs (UserDefaults / AppStorage-compatible key).
@Observable
final class FavoritesStore {
    static let storageKey = "favoriteStoreIDs"

    private(set) var ids: Set<Int>

    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
        if let saved = userDefaults.array(forKey: Self.storageKey) as? [Int] {
            ids = Set(saved)
        } else {
            ids = []
        }
    }

    private let userDefaults: UserDefaults

    func contains(_ id: Int) -> Bool {
        ids.contains(id)
    }

    func toggle(_ id: Int) {
        if ids.contains(id) {
            ids.remove(id)
        } else {
            ids.insert(id)
        }
        persist()
    }

    func add(_ id: Int) {
        guard !ids.contains(id) else { return }
        ids.insert(id)
        persist()
    }

    func remove(_ id: Int) {
        guard ids.contains(id) else { return }
        ids.remove(id)
        persist()
    }

    private func persist() {
        userDefaults.set(Array(ids).sorted(), forKey: Self.storageKey)
    }
}

/// How the store list is ordered.
enum StoreSortOrder: String, CaseIterable, Identifiable {
    case nearest
    case favoritesFirst

    var id: String { rawValue }

    var label: String {
        switch self {
        case .nearest: "Nearest"
        case .favoritesFirst: "Favorites first"
        }
    }

    var systemImage: String {
        switch self {
        case .nearest: "location"
        case .favoritesFirst: "star"
        }
    }
}
