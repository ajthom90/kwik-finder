import CoreLocation
import SwiftUI

struct StoreListView: View {
    @Environment(FilterState.self) private var filters
    @Environment(StoreRepository.self) private var repository
    @Environment(FavoritesStore.self) private var favorites

    let stores: [Store]
    let referenceLocation: CLLocation
    let usingActualLocation: Bool
    @Binding var searchText: String
    @Binding var sortOrder: StoreSortOrder
    var onRefresh: (() async -> Void)?

    /// Live success banner is shown briefly, then hidden so healthy state stays quiet.
    @State private var showLiveSuccessBanner = false
    @State private var liveBannerHideTask: Task<Void, Never>?

    private static let liveSuccessBannerDuration: Duration = .seconds(2.5)

    var body: some View {
        List {
            statusSection

            if !usingActualLocation {
                Section {
                    Label(
                        "Location is off — distances are measured from the middle of Kwik Trip country. Allow location access in Settings for real distances.",
                        systemImage: "location.slash"
                    )
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .accessibilityLabel("Location is off. Distances are approximate until you allow location access in Settings.")
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
                            StoreRow(
                                store: store,
                                distanceMeters: store.distance(from: referenceLocation),
                                isFavorite: favorites.contains(store.id),
                                onToggleFavorite: { favorites.toggle(store.id) }
                            )
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
            ToolbarItem(placement: .topBarLeading) {
                sortMenu
            }
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
                .accessibilityHint("Choose required store features")
            }
        }
        .onChange(of: repository.liveStatus) { _, status in
            handleLiveStatusChange(status)
        }
        .onAppear {
            handleLiveStatusChange(repository.liveStatus)
        }
        .onDisappear {
            liveBannerHideTask?.cancel()
        }
    }

    private var sortMenu: some View {
        Menu {
            Picker("Sort", selection: $sortOrder) {
                ForEach(StoreSortOrder.allCases) { order in
                    Label(order.label, systemImage: order.systemImage)
                        .tag(order)
                }
            }
        } label: {
            Label(sortOrder.label, systemImage: sortOrder.systemImage)
        }
        .accessibilityLabel("Sort order, \(sortOrder.label)")
    }

    // MARK: - Status banners

    @ViewBuilder
    private var statusSection: some View {
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
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Updating live store data")
            }
        case .live(let date):
            if showLiveSuccessBanner {
                Section {
                    Label(
                        "Live data updated \(Format.asOf(date))",
                        systemImage: "checkmark.circle"
                    )
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .accessibilityLabel("Live data updated \(Format.asOf(date))")
                }
            }
        case .offline(let lastSuccess, let message):
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Label(message, systemImage: "wifi.exclamationmark")
                        .font(.footnote)
                        .foregroundStyle(.orange)
                    if let lastSuccess {
                        Text("Last live update \(Format.asOf(lastSuccess)).")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                .accessibilityElement(children: .combine)
            }
        }
    }

    private func handleLiveStatusChange(_ status: LiveDataStatus) {
        liveBannerHideTask?.cancel()
        switch status {
        case .live:
            showLiveSuccessBanner = true
            liveBannerHideTask = Task { @MainActor in
                try? await Task.sleep(for: Self.liveSuccessBannerDuration)
                guard !Task.isCancelled else { return }
                if case .live = repository.liveStatus {
                    withAnimation(.easeOut(duration: 0.25)) {
                        showLiveSuccessBanner = false
                    }
                }
            }
        case .snapshotOnly, .refreshing, .offline:
            showLiveSuccessBanner = false
        }
    }

    // MARK: - Headers & empty copy

    private var listHeader: String {
        var parts: [String] = ["\(stores.count)"]
        if filters.isActive || hasSearchQuery {
            parts.append("matching")
        }
        parts.append("stores")
        switch sortOrder {
        case .nearest:
            parts.append("nearest first")
        case .favoritesFirst:
            parts.append("favorites first")
        }
        return parts.joined(separator: " ")
    }

    private var hasSearchQuery: Bool {
        !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var emptyTitle: String {
        if hasSearchQuery {
            return "No stores found"
        }
        if filters.isActive {
            return "No matching stores"
        }
        return "No stores"
    }

    private var emptySystemImage: String {
        if hasSearchQuery {
            return "magnifyingglass"
        }
        if filters.isActive {
            return "line.3.horizontal.decrease.circle"
        }
        return "mappin.slash"
    }

    private var emptyDescription: String {
        if hasSearchQuery && filters.isActive {
            return "Nothing matches this search and your filters. Clear the search or remove a filter."
        }
        if hasSearchQuery {
            return "Try a different name, city, or store number."
        }
        if filters.isActive {
            return "Try removing a filter — no store has every selected feature."
        }
        return "Store data hasn’t loaded yet. Pull to refresh when you’re online."
    }
}

struct StoreRow: View {
    let store: Store
    let distanceMeters: CLLocationDistance
    let isFavorite: Bool
    var onToggleFavorite: (() -> Void)?

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if let onToggleFavorite {
                Button {
                    onToggleFavorite()
                } label: {
                    Image(systemName: isFavorite ? "star.fill" : "star")
                        .font(.body)
                        .foregroundStyle(isFavorite ? .yellow : .secondary)
                        .frame(width: 28, height: 28)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(isFavorite ? "Remove from favorites" : "Add to favorites")
                .accessibilityAddTraits(isFavorite ? .isSelected : [])
            }

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(store.brandedName)
                        .font(.headline)
                    if isFavorite, onToggleFavorite == nil {
                        Image(systemName: "star.fill")
                            .font(.caption)
                            .foregroundStyle(.yellow)
                            .accessibilityHidden(true)
                    }
                }
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
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel(
                        store.badgeFeatures.prefix(7).map { feature in
                            if feature == .evCharging {
                                return store.evCharging?.label ?? feature.label
                            }
                            return feature.label
                        }.joined(separator: ", ")
                    )
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
            .accessibilityElement(children: .combine)
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

    private var accessibilityName: String {
        if feature == .evCharging {
            return evStatus?.label ?? feature.label
        }
        return feature.label
    }

    var body: some View {
        Image(systemName: feature.systemImage)
            .font(.caption2)
            .foregroundStyle(tint)
            .frame(width: 22, height: 22)
            .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: 6))
            .accessibilityLabel(accessibilityName)
            .accessibilityAddTraits(.isImage)
    }
}
