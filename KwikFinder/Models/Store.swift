import CoreLocation
import Foundation

/// KwikCharge EV charging status, sourced from the Maps & Downloads
/// "EV Charging Locations" PDF (the only place Kwik Trip publishes it).
enum EVChargingStatus: String, Codable, Hashable {
    case open
    case comingSoon

    var label: String {
        switch self {
        case .open: "EV Charging"
        case .comingSoon: "EV Coming Soon"
        }
    }
}

struct FuelOffering: Codable, Hashable, Identifiable {
    let type: String
    let description: String?
    let price: Double?

    /// Some stores list the same fuel type twice (e.g. two "DIESEL #2"
    /// entries with different descriptions), so type alone can't be the id.
    var id: String { "\(type)|\(description ?? "")" }

    private static let displayNames: [String: String] = [
        "UNLEADED 87 (10% ETH)": "Unleaded 87",
        "UNLEADED PREMIUM": "Premium Unleaded",
        "UNLEADED 87 (0% ETH)": "Unleaded 87 (No Ethanol)",
        "UNLEADED 88": "Unleaded 88",
        "UNLEADED PLUS": "Unleaded Plus",
        "DIESEL #1": "Diesel #1",
        "DIESEL #2": "Diesel #2",
        "PREMIUM DIESEL": "Premium Diesel",
        "DIESEL #2 OFF-ROAD": "Off-Road Diesel",
        "DIESEL EXHAUST FLUID": "DEF",
        "E-85": "E-85",
        "COMPRESSED NATURAL GAS": "CNG",
        "RACING FUEL": "Racing Fuel",
    ]

    var displayName: String {
        Self.displayNames[type] ?? type.capitalized
    }

    var formattedPrice: String? {
        guard let price else { return nil }
        return String(format: "$%.3f", price)
    }
}

struct StoreHours: Codable, Hashable {
    let openTime: String
    let closeTime: String
    let dayOfWeek: String

    /// "05:00:00" -> "5:00 AM"; midnight close ("00:00:00") reads as "Midnight".
    private static func friendly(_ time: String) -> String? {
        let parts = time.split(separator: ":").compactMap { Int($0) }
        guard parts.count >= 2 else { return nil }
        let (hour, minute) = (parts[0], parts[1])
        if hour == 0 && minute == 0 { return "Midnight" }
        let suffix = hour < 12 ? "AM" : "PM"
        let displayHour = hour % 12 == 0 ? 12 : hour % 12
        return String(format: "%d:%02d %@", displayHour, minute, suffix)
    }

    var display: String {
        guard let open = Self.friendly(openTime), let close = Self.friendly(closeTime) else {
            return "Hours unavailable"
        }
        return "\(open) – \(close)"
    }
}

/// One Kwik Trip / Kwik Star store, decoded from the bundled snapshot and
/// updated in place from the live locator API.
struct Store: Codable, Identifiable, Hashable {
    let id: Int
    var name: String
    var latitude: Double
    var longitude: Double
    var address1: String
    var city: String
    var county: String?
    var state: String
    var zip: String
    var phone: String
    var open24Hours: Bool
    var hours: [StoreHours]?
    var fuels: [FuelOffering]
    var amenities: [String]
    var truckParkingSpaces: Int
    var familyRestroom: Bool
    var evCharging: EVChargingStatus?

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    var location: CLLocation {
        CLLocation(latitude: latitude, longitude: longitude)
    }

    /// "KWIK TRIP #103" -> "Kwik Trip #103", "KWIK STAR #1918" -> "Kwik Star #1918"
    var brandedName: String {
        name.split(separator: " ")
            .map { $0.allSatisfy(\.isNumber) || $0.hasPrefix("#") ? String($0) : String($0).capitalized }
            .joined(separator: " ")
    }

    var shortAddress: String {
        "\(address1.localizedCapitalized), \(city.localizedCapitalized), \(state)"
    }

    var fullAddress: String {
        "\(address1.localizedCapitalized), \(city.localizedCapitalized), \(state) \(zip)"
    }

    func distance(from other: CLLocation) -> CLLocationDistance {
        location.distance(from: other)
    }

    func has(_ feature: StoreFeature) -> Bool {
        switch feature {
        case .familyRestroom:
            familyRestroom
        case .evCharging:
            evCharging != nil
        case .open24Hours:
            open24Hours
        case .diesel:
            fuels.contains { $0.type.hasPrefix("DIESEL #") || $0.type == "PREMIUM DIESEL" }
        case .premiumDiesel:
            fuels.contains { $0.type == "PREMIUM DIESEL" }
        case .def:
            fuels.contains { $0.type == "DIESEL EXHAUST FLUID" }
        case .e85:
            fuels.contains { $0.type == "E-85" }
        case .cng:
            fuels.contains { $0.type == "COMPRESSED NATURAL GAS" }
        case .noEthanolGas:
            fuels.contains { $0.type == "UNLEADED 87 (0% ETH)" }
        case .unleaded88:
            fuels.contains { $0.type == "UNLEADED 88" }
        case .scale:
            amenities.contains("SCALE")
        case .showers:
            amenities.contains("SHOWERS")
        case .truckParking:
            amenities.contains("TRUCK-PARKING")
        case .transFlo:
            amenities.contains("TRANS-FLO")
        case .fleetCards:
            amenities.contains("TRENDAR")
        case .truckFriendly:
            amenities.contains("TRUCK-FRIENDLY")
        case .carWash:
            amenities.contains("CAR-WASH")
        case .atm:
            amenities.contains("ATM")
        case .wifi:
            amenities.contains("WI-FI")
        case .restaurant:
            amenities.contains("RESTAURANT")
        }
    }

    /// Features worth surfacing as badges on list rows, in display order.
    var badgeFeatures: [StoreFeature] {
        StoreFeature.badgeOrder.filter { has($0) }
    }
}
