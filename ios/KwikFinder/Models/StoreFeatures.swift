import Foundation

/// Server-owned filter flags from the KwikFinder catalog API (`features` object).
/// Mirrors `StoreFeatures` in `docs/api/openapi.yaml`. Clients filter on these
/// instead of re-mapping Kwik Trip amenity/fuel strings.
///
/// All fields are optional so partial payloads still decode cleanly.
struct StoreFeatureFlags: Codable, Hashable {
    var diesel: Bool?
    var premiumDiesel: Bool?
    var def: Bool?
    var e85: Bool?
    var cng: Bool?
    var noEthanolGas: Bool?
    var unleaded88: Bool?
    var scale: Bool?
    var showers: Bool?
    var truckParking: Bool?
    var truckParkingStalls: Int?
    var transFlo: Bool?
    var fleetCards: Bool?
    var truckFriendly: Bool?
    var carWash: Bool?
    var atm: Bool?
    var bitcoinATM: Bool?
    var wifi: Bool?
    var restaurant: Bool?

    /// Resolves a filterable `StoreFeature` against these flags.
    /// Headline features (family restroom, EV, 24h) live on `Store`, not here.
    /// Returns `nil` when the corresponding flag is absent so callers can fall back.
    func flag(for feature: StoreFeature) -> Bool? {
        switch feature {
        case .familyRestroom, .evCharging, .open24Hours:
            return nil
        case .diesel: return diesel
        case .premiumDiesel: return premiumDiesel
        case .def: return def
        case .e85: return e85
        case .cng: return cng
        case .noEthanolGas: return noEthanolGas
        case .unleaded88: return unleaded88
        case .scale: return scale
        case .showers: return showers
        case .truckParking: return truckParking
        case .transFlo: return transFlo
        case .fleetCards: return fleetCards
        case .truckFriendly: return truckFriendly
        case .carWash: return carWash
        case .atm: return atm
        case .bitcoinATM: return bitcoinATM
        case .wifi: return wifi
        case .restaurant: return restaurant
        }
    }
}
