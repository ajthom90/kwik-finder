import Foundation

/// Everything a store can be filtered by. Raw amenity/fuel strings from the
/// locator API (plus the PDF-sourced family restroom / EV flags) are mapped
/// onto these cases in `Store.has(_:)`.
enum StoreFeature: String, CaseIterable, Identifiable, Hashable {
    // Headline features
    case familyRestroom
    case evCharging
    case open24Hours

    // Fuel types
    case diesel
    case premiumDiesel
    case def
    case e85
    case cng
    case noEthanolGas
    case unleaded88

    // Professional driver / truck stop services
    case scale
    case showers
    case truckParking
    case transFlo
    case fleetCards
    case truckFriendly

    // Other amenities
    case carWash
    case atm
    case bitcoinATM
    case wifi
    case restaurant

    var id: String { rawValue }

    var label: String {
        switch self {
        case .familyRestroom: "Family Restroom"
        case .evCharging: "EV Charging"
        case .open24Hours: "Open 24 Hours"
        case .diesel: "Diesel"
        case .premiumDiesel: "Premium Diesel"
        case .def: "DEF at the Pump"
        case .e85: "E-85"
        case .cng: "CNG"
        case .noEthanolGas: "No-Ethanol Gas"
        case .unleaded88: "Unleaded 88"
        case .scale: "CAT Scale"
        case .showers: "Showers"
        case .truckParking: "Truck Parking"
        case .transFlo: "TransFlo"
        case .fleetCards: "Fleet Cards"
        case .truckFriendly: "Truck Friendly"
        case .carWash: "Car Wash"
        case .atm: "ATM"
        case .bitcoinATM: "Bitcoin ATM"
        case .wifi: "Wi-Fi"
        case .restaurant: "Restaurant"
        }
    }

    var systemImage: String {
        switch self {
        case .familyRestroom: "figure.and.child.holdinghands"
        case .evCharging: "bolt.car.fill"
        case .open24Hours: "clock.fill"
        case .diesel: "fuelpump.fill"
        case .premiumDiesel: "fuelpump.circle.fill"
        case .def: "drop.fill"
        case .e85: "leaf.fill"
        case .cng: "wind"
        case .noEthanolGas: "drop.circle"
        case .unleaded88: "fuelpump"
        case .scale: "scalemass.fill"
        case .showers: "shower.fill"
        case .truckParking: "truck.box.fill"
        case .transFlo: "doc.text.fill"
        case .fleetCards: "creditcard.fill"
        case .truckFriendly: "road.lanes"
        case .carWash: "car.fill"
        case .atm: "banknote.fill"
        case .bitcoinATM: "bitcoinsign.circle.fill"
        case .wifi: "wifi"
        case .restaurant: "fork.knife"
        }
    }

    // Groupings used by the filter screen.
    static let headline: [StoreFeature] = [.familyRestroom, .evCharging, .open24Hours]
    static let fuel: [StoreFeature] = [.diesel, .premiumDiesel, .def, .e85, .cng, .noEthanolGas, .unleaded88]
    static let truck: [StoreFeature] = [.scale, .showers, .truckParking, .transFlo, .fleetCards, .truckFriendly]
    static let amenity: [StoreFeature] = [.carWash, .atm, .bitcoinATM, .wifi, .restaurant]

    /// Order used for the compact badge row on store list cells.
    static let badgeOrder: [StoreFeature] = [
        .familyRestroom, .evCharging, .diesel, .def, .e85, .cng,
        .scale, .showers, .truckParking, .carWash, .bitcoinATM, .open24Hours,
    ]
}
