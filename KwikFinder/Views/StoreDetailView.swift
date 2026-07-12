import CoreLocation
import MapKit
import SwiftUI

struct StoreDetailView: View {
    @Environment(StoreRepository.self) private var repository
    @Environment(LocationService.self) private var locationService

    let storeID: Int

    var body: some View {
        if let store = repository.storesByID[storeID] {
            List {
                headerSection(store)
                fuelSection(store)
                truckSection(store)
                amenitySection(store)
                hoursSection(store)
            }
            .navigationTitle(store.brandedName)
            .navigationBarTitleDisplayMode(.inline)
            .task(id: storeID) {
                await repository.refreshDetails(ids: [storeID])
            }
        } else {
            ContentUnavailableView("Store not found", systemImage: "questionmark.circle")
        }
    }

    // MARK: - Sections

    private func headerSection(_ store: Store) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 6) {
                Text(store.fullAddress)
                    .font(.subheadline)
                if locationService.location != nil {
                    Text("\(Format.distance(store.distance(from: locationService.effectiveLocation))) away")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
                headlineBadges(store)
            }
            .padding(.vertical, 2)

            HStack(spacing: 12) {
                Button {
                    openInMaps(store)
                } label: {
                    Label("Directions", systemImage: "arrow.triangle.turn.up.right.circle.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)

                if let phoneURL = telURL(store.phone) {
                    Link(destination: phoneURL) {
                        Label("Call", systemImage: "phone.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
            }
            .buttonBorderShape(.capsule)
            .listRowSeparator(.hidden)
        }
    }

    @ViewBuilder
    private func headlineBadges(_ store: Store) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if store.familyRestroom {
                Label("Family restroom available", systemImage: StoreFeature.familyRestroom.systemImage)
                    .font(.subheadline)
                    .foregroundStyle(.blue)
            }
            if let ev = store.evCharging {
                Label(
                    ev == .open ? "KwikCharge EV charging" : "KwikCharge EV charging coming soon",
                    systemImage: StoreFeature.evCharging.systemImage
                )
                .font(.subheadline)
                .foregroundStyle(ev == .open ? .green : .orange)
            }
            if store.open24Hours {
                Label("Open 24 hours", systemImage: "clock.fill")
                    .font(.subheadline)
                    .foregroundStyle(.green)
            }
        }
    }

    private func fuelSection(_ store: Store) -> some View {
        Section {
            if store.fuels.isEmpty {
                Text("No fuel information for this store.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(store.fuels) { fuel in
                    HStack {
                        Text(fuel.displayName)
                        Spacer()
                        if let price = fuel.formattedPrice {
                            Text(price)
                                .monospacedDigit()
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
        } header: {
            Text("Fuel")
        } footer: {
            VStack(alignment: .leading, spacing: 4) {
                if let asOf = repository.pricesAsOf(store.id) {
                    Text(
                        "Prices \(repository.isLive(store.id) ? "updated" : "from snapshot") \(Format.asOf(asOf)). "
                        + "The price posted at the pump always governs."
                    )
                }
                if case .offline(_, let message) = repository.liveStatus, !repository.isLive(store.id) {
                    Text(message)
                }
            }
        }
    }

    private func truckSection(_ store: Store) -> some View {
        let present = StoreFeature.truck.filter { store.has($0) } + (store.has(.def) ? [.def] : [])
        return Section("Professional Driver Services") {
            if present.isEmpty {
                Text("No truck stop services at this store.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(present) { feature in
                    HStack {
                        Label(feature.label, systemImage: feature.systemImage)
                        Spacer()
                        if feature == .truckParking, store.truckParkingSpaces > 0 {
                            Text("\(store.truckParkingSpaces) stalls")
                                .foregroundStyle(.secondary)
                        } else {
                            Image(systemName: "checkmark")
                                .foregroundStyle(.green)
                        }
                    }
                }
            }
        }
    }

    private func amenitySection(_ store: Store) -> some View {
        let present = StoreFeature.amenity.filter { store.has($0) }
        return Section("Amenities") {
            if present.isEmpty {
                Text("No listed amenities.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(present) { feature in
                    Label(feature.label, systemImage: feature.systemImage)
                }
            }
        }
    }

    @ViewBuilder
    private func hoursSection(_ store: Store) -> some View {
        Section("Hours") {
            if store.open24Hours {
                Label("Open 24 hours", systemImage: "clock.fill")
            } else if let hours = store.hours, !hours.isEmpty {
                ForEach(Array(hours.enumerated()), id: \.offset) { _, day in
                    HStack {
                        Text(day.dayOfWeek.isEmpty ? "Hours" : day.dayOfWeek)
                        Spacer()
                        Text(day.display)
                            .foregroundStyle(.secondary)
                    }
                }
            } else {
                Text("Hours unavailable — call ahead.")
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - Actions

    private func openInMaps(_ store: Store) {
        let item = MKMapItem(placemark: MKPlacemark(coordinate: store.coordinate))
        item.name = store.brandedName
        item.openInMaps(launchOptions: [
            MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDriving
        ])
    }

    private func telURL(_ phone: String) -> URL? {
        let digits = phone.filter(\.isNumber)
        guard !digits.isEmpty else { return nil }
        return URL(string: "tel:\(digits)")
    }
}
