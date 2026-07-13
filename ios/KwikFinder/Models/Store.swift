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

struct StoreHours: Codable, Hashable, Identifiable {
    let openTime: String
    let closeTime: String
    let dayOfWeek: String

    /// Day + open/close keeps ForEach identity stable if the API ever returns
    /// duplicate day labels (same pattern as FuelOffering).
    var id: String { "\(dayOfWeek)|\(openTime)|\(closeTime)" }

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

/// One Kwik Trip / Kwik Star store from the KwikFinder catalog (`GET /v1/stores`).
/// OpenAPI allows null for address/phone (and possibly coordinates); decode
/// defaults missing/null strings to `""` and coordinates to `0` so a full
/// catalog never fails on sparse rows.
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
    /// Server-computed filter flags. Optional so partial/legacy payloads decode.
    var features: StoreFeatureFlags?

    enum CodingKeys: String, CodingKey {
        case id, name, latitude, longitude
        case address1, city, county, state, zip, phone
        case open24Hours, hours, fuels, amenities
        case truckParkingSpaces, familyRestroom, evCharging, features
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        latitude = try c.decodeIfPresent(Double.self, forKey: .latitude) ?? 0
        longitude = try c.decodeIfPresent(Double.self, forKey: .longitude) ?? 0
        address1 = try c.decodeIfPresent(String.self, forKey: .address1) ?? ""
        city = try c.decodeIfPresent(String.self, forKey: .city) ?? ""
        county = try c.decodeIfPresent(String.self, forKey: .county)
        state = try c.decodeIfPresent(String.self, forKey: .state) ?? ""
        zip = try c.decodeIfPresent(String.self, forKey: .zip) ?? ""
        phone = try c.decodeIfPresent(String.self, forKey: .phone) ?? ""
        open24Hours = try c.decodeIfPresent(Bool.self, forKey: .open24Hours) ?? false
        hours = try c.decodeIfPresent([StoreHours].self, forKey: .hours)
        fuels = try c.decodeIfPresent([FuelOffering].self, forKey: .fuels) ?? []
        amenities = try c.decodeIfPresent([String].self, forKey: .amenities) ?? []
        truckParkingSpaces = try c.decodeIfPresent(Int.self, forKey: .truckParkingSpaces) ?? 0
        familyRestroom = try c.decodeIfPresent(Bool.self, forKey: .familyRestroom) ?? false
        evCharging = try c.decodeIfPresent(EVChargingStatus.self, forKey: .evCharging)
        features = try c.decodeIfPresent(StoreFeatureFlags.self, forKey: .features)
    }

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

    /// Prefer server `features` flags when present; otherwise map fuels/amenities
    /// (legacy rows without `features`). Headlines always use top-level store
    /// fields (`familyRestroom`, `evCharging`, `open24Hours`).
    func has(_ feature: StoreFeature) -> Bool {
        switch feature {
        case .familyRestroom:
            return familyRestroom
        case .evCharging:
            return evCharging != nil
        case .open24Hours:
            return open24Hours
        default:
            break
        }

        if let features, let flag = features.flag(for: feature) {
            return flag
        }

        return legacyHas(feature)
    }

    /// Pre-server mapping of raw Kwik Trip fuel/amenity strings.
    private func legacyHas(_ feature: StoreFeature) -> Bool {
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
            // Exact amenity name only — "BITCOIN ATM" is a separate feature.
            amenities.contains("ATM")
        case .bitcoinATM:
            amenities.contains("BITCOIN ATM")
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

    /// Loose text match for the list search field (name, city, address, store #).
    func matchesSearch(_ query: String) -> Bool {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return true }
        if let number = Int(q), number == id { return true }
        let haystack = [name, brandedName, city, address1, state, zip, "#\(id)", "\(id)"]
            .joined(separator: " ")
        return haystack.localizedCaseInsensitiveContains(q)
    }
}
