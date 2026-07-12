import SwiftUI

struct FilterView: View {
    @Environment(StoreRepository.self) private var repository
    @Environment(FilterState.self) private var filters
    @Environment(\.dismiss) private var dismiss

    private var matchCount: Int {
        repository.stores.filter { filters.matches($0) }.count
    }

    var body: some View {
        List {
            section("Popular", features: StoreFeature.headline)
            section("Fuel Types", features: StoreFeature.fuel)
            section("Professional Driver Services", features: StoreFeature.truck)
            section("Amenities", features: StoreFeature.amenity)

            Section {
                EmptyView()
            } footer: {
                Text(
                    "Stores must have every selected feature. Family restroom and EV charging "
                    + "data come from Kwik Trip's published location lists; EV results include "
                    + "sites marked “Coming Soon.”"
                )
            }
        }
        .navigationTitle("Filters")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if filters.isActive {
                    Button("Clear All") {
                        filters.clear()
                    }
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            Button {
                dismiss()
            } label: {
                Text(filters.isActive ? "Show \(matchCount) Stores" : "Show All \(matchCount) Stores")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .padding()
            .background(.thinMaterial)
        }
    }

    private func section(_ title: String, features: [StoreFeature]) -> some View {
        Section(title) {
            ForEach(features) { feature in
                Button {
                    filters.toggle(feature)
                } label: {
                    HStack {
                        Label {
                            Text(feature.label)
                                .foregroundStyle(.primary)
                        } icon: {
                            Image(systemName: feature.systemImage)
                        }
                        Spacer()
                        if filters.isSelected(feature) {
                            Image(systemName: "checkmark")
                                .fontWeight(.semibold)
                        }
                    }
                }
            }
        }
    }
}
