import CoreLocation
import MapKit
import SwiftUI

enum Route: Hashable {
    case store(Int)
    case filters
}

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase

    @State private var repository = StoreRepository()
    @State private var locationService = LocationService()
    @State private var filters = FilterState()
    @State private var favorites = FavoritesStore()
    @State private var searchText = ""
    @State private var sortOrder: StoreSortOrder = .nearest

    @State private var camera: MapCameraPosition = .automatic
    @State private var visibleRegion: MKCoordinateRegion?
    @State private var mapSelection: Int?
    @State private var navPath: [Route] = []
    @State private var sheetPresented = true
    @State private var sheetDetent: PresentationDetent = Self.midDetent
    @State private var hasCenteredOnUser = false

    private static let midDetent = PresentationDetent.fraction(0.45)
    private static let compactDetent = PresentationDetent.height(96)

    /// Cap on simultaneously rendered markers to keep the map responsive when
    /// the unfiltered set is huge. When filters/search shrink the set under
    /// this cap, every matching store is shown.
    private static let markerLimit = 350

    private var filteredStores: [Store] {
        let reference = locationService.effectiveLocation
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        let matches = repository.stores
            .filter { filters.matches($0) }
            .filter { query.isEmpty || $0.matchesSearch(query) }

        switch sortOrder {
        case .nearest:
            return matches.sorted { $0.distance(from: reference) < $1.distance(from: reference) }
        case .favoritesFirst:
            return matches.sorted { lhs, rhs in
                let leftFav = favorites.contains(lhs.id)
                let rightFav = favorites.contains(rhs.id)
                if leftFav != rightFav { return leftFav && !rightFav }
                return lhs.distance(from: reference) < rhs.distance(from: reference)
            }
        }
    }

    private var mapStores: [Store] {
        let stores = filteredStores
        guard stores.count > Self.markerLimit else { return stores }
        let center = visibleRegion.map {
            CLLocation(latitude: $0.center.latitude, longitude: $0.center.longitude)
        } ?? locationService.effectiveLocation
        // Map always prefers geographic nearest for markers, regardless of list sort.
        return Array(
            stores
                .sorted { $0.distance(from: center) < $1.distance(from: center) }
                .prefix(Self.markerLimit)
        )
    }

    var body: some View {
        Map(position: $camera, selection: $mapSelection) {
            UserAnnotation()
            ForEach(mapStores) { store in
                Marker(store.brandedName, systemImage: markerSymbol(for: store), coordinate: store.coordinate)
                    .tint(markerTint(for: store))
                    .tag(store.id)
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .mapControls {
            MapUserLocationButton()
            MapCompass()
        }
        .onMapCameraChange(frequency: .onEnd) { context in
            visibleRegion = context.region
        }
        .sheet(isPresented: $sheetPresented) {
            sheetContent
                .presentationDetents([Self.compactDetent, Self.midDetent, .large], selection: $sheetDetent)
                .presentationBackgroundInteraction(.enabled(upThrough: Self.midDetent))
                .presentationDragIndicator(.visible)
                .interactiveDismissDisabled()
        }
        .task {
            locationService.requestPermission()
            await repository.refreshWhileActive(around: locationService.effectiveLocation, force: true)
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task {
                await repository.refreshWhileActive(around: locationService.effectiveLocation)
            }
        }
        .onChange(of: mapSelection) { _, selected in
            guard let id = selected else { return }
            navPath = [.store(id)]
            if sheetDetent == Self.compactDetent {
                sheetDetent = Self.midDetent
            }
            // Clear so tapping the same marker again re-opens its detail.
            mapSelection = nil
        }
        .onChange(of: locationService.location) { _, newLocation in
            guard let newLocation, !hasCenteredOnUser else { return }
            hasCenteredOnUser = true
            withAnimation {
                camera = .region(
                    MKCoordinateRegion(
                        center: newLocation.coordinate,
                        latitudinalMeters: 40_000,
                        longitudinalMeters: 40_000
                    )
                )
            }
            Task {
                await repository.refreshWhileActive(around: newLocation, force: true)
            }
        }
    }

    private var sheetContent: some View {
        NavigationStack(path: $navPath) {
            StoreListView(
                stores: filteredStores,
                referenceLocation: locationService.effectiveLocation,
                usingActualLocation: locationService.location != nil,
                searchText: $searchText,
                sortOrder: $sortOrder,
                onRefresh: {
                    await repository.refreshWhileActive(
                        around: locationService.effectiveLocation,
                        force: true
                    )
                }
            )
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .store(let id):
                    StoreDetailView(storeID: id)
                case .filters:
                    FilterView()
                }
            }
        }
        .environment(repository)
        .environment(locationService)
        .environment(filters)
        .environment(favorites)
    }

    private func markerSymbol(for store: Store) -> String {
        if store.evCharging != nil { return "bolt.car.fill" }
        return "fuelpump.fill"
    }

    private func markerTint(for store: Store) -> Color {
        switch store.evCharging {
        case .open: .green
        case .comingSoon: .orange
        case nil: Color(red: 0.78, green: 0.06, blue: 0.18) // Kwik Trip red
        }
    }
}
