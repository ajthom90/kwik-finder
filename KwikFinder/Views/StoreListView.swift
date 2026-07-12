import CoreLocation
import SwiftUI

struct StoreListView: View {
    @Environment(FilterState.self) private var filters

    let stores: [Store]
    let referenceLocation: CLLocation
    let usingActualLocation: Bool

    var body: some View {
        List {
            if !usingActualLocation {
                Section {
                    Label(
                        "Location is off — distances are measured from the middle of Kwik Trip country. Allow location access in Settings for real distances.",
                        systemImage: "location.slash"
                    )
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                }
            }

            if stores.isEmpty {
                ContentUnavailableView(
                    "No matching stores",
                    systemImage: "line.3.horizontal.decrease.circle",
                    description: Text("Try removing a filter — no store has every selected feature.")
                )
            } else {
                Section {
                    ForEach(stores) { store in
                        NavigationLink(value: Route.store(store.id)) {
                            StoreRow(store: store, distanceMeters: store.distance(from: referenceLocation))
                        }
                    }
                } header: {
                    Text("\(stores.count) \(filters.isActive ? "matching " : "")stores, nearest first")
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Kwik Trip Finder")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(value: Route.filters) {
                    Label(
                        filters.isActive ? "Filters (\(filters.selected.count))" : "Filters",
                        systemImage: filters.isActive
                            ? "line.3.horizontal.decrease.circle.fill"
                            : "line.3.horizontal.decrease.circle"
                    )
                    .labelStyle(.titleAndIcon)
                }
            }
        }
    }
}

struct StoreRow: View {
    let store: Store
    let distanceMeters: CLLocationDistance

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(store.brandedName)
                    .font(.headline)
                Text(store.shortAddress)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                if !store.badgeFeatures.isEmpty {
                    HStack(spacing: 5) {
                        ForEach(store.badgeFeatures.prefix(7)) { feature in
                            FeatureBadge(feature: feature, evStatus: store.evCharging)
                        }
                    }
                }
            }
            Spacer(minLength: 0)
            VStack(alignment: .trailing, spacing: 4) {
                Text(Format.distance(distanceMeters))
                    .font(.subheadline.weight(.semibold))
                if store.open24Hours {
                    Text("24 hrs")
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(.green)
                }
            }
        }
        .padding(.vertical, 2)
    }
}

/// Compact icon chip for a feature a store has.
struct FeatureBadge: View {
    let feature: StoreFeature
    var evStatus: EVChargingStatus?

    private var tint: Color {
        if feature == .evCharging {
            return evStatus == .open ? .green : .orange
        }
        return .accentColor
    }

    var body: some View {
        Image(systemName: feature.systemImage)
            .font(.caption2)
            .foregroundStyle(tint)
            .frame(width: 22, height: 22)
            .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: 6))
            .accessibilityLabel(feature == .evCharging ? (evStatus?.label ?? feature.label) : feature.label)
    }
}
