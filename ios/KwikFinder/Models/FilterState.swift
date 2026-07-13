import Foundation
import Observation

/// The set of features a store must have (all of them) to appear in results.
@Observable
final class FilterState {
    var selected: Set<StoreFeature> = []

    var isActive: Bool { !selected.isEmpty }

    func matches(_ store: Store) -> Bool {
        selected.allSatisfy { store.has($0) }
    }

    func isSelected(_ feature: StoreFeature) -> Bool {
        selected.contains(feature)
    }

    func toggle(_ feature: StoreFeature) {
        if selected.contains(feature) {
            selected.remove(feature)
        } else {
            selected.insert(feature)
        }
    }

    func clear() {
        selected.removeAll()
    }
}
