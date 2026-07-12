import CoreLocation
import SwiftUI

struct StoreListView: View {
    @Environment(FilterState.self) private var filters
    @Environment(StoreRepository.self) private var repository

    let stores: [Store]
    let referenceLocation: CLLocation
    let usingActualLocation: Bool
    @Binding var searchText: String
    var onRefresh: (() async -> Void)?

    var body: some View {
        List {
            liveStatusSection

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
                    emptyTitle,
                    systemImage: emptySystemImage,
                    description: Text(emptyDescription)
                )
            } else {
                Section {
                    ForEach(stores) { store in
                        NavigationLink(value: Route.store(store.id)) {
                            StoreRow(store: store, distanceMeters: store.distance(from: referenceLocation))
                        }
                    }
                } header: {
                    Text(listHeader)
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Kwik Trip Finder")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $searchText, prompt: "Name, city, or store #")
        .refreshable {
            if let onRefresh {
                await onRefresh()
            }
        }
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

    @ViewBuilder
    private var liveStatusSection: some View {
        switch repository.liveStatus {
        case .snapshotOnly:
            Section {
                Label(
                    "Using bundled store data. Connect to refresh live prices and new stores.",
                    systemImage: "externaldrive"
                )
                .font(.footnote)
                .foregroundStyle(.secondary)
            }
        case .refreshing:
            Section {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("Updating live store data…")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        case .live(let date):
            Section {
                Label(
                    "Live data updated \(Format.asOf(date))",
                    systemImage: "checkmark.circle"
                )
                .font(.footnote)
                .foregroundStyle(.secondary)
            }
        case .offline(_, let message):
            Section {
                Label(message, systemImage: "wifi.exclamationmark")
                    .font(.footnote)
                    .foregroundStyle(.orange)
            }
        }
    }

    private var listHeader: String {
        var parts: [String] = ["\(stores.count)"]
        if filters.isActive || !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            parts.append("matching")
        }
        parts.append("stores, nearest first")
        return parts.joined(separator: " ")
    }

    private var emptyTitle: String {
        if !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return "No stores found"
        }
        return "No matching stores"
    }

    private var emptySystemImage: String {
        if !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return "magnifyingglass"
        }
        return "line.3.horizontal.decrease.circle"
    }

    private var emptyDescription: String {
        if !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return "Try a different name, city, or store number."
        }
        return "Try removing a filter — no store has every selected feature."
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
        if feature == .bitcoinATM {
            return .orange
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
