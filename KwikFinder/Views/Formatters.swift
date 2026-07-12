import CoreLocation
import Foundation

enum Format {
    static func distance(_ meters: CLLocationDistance) -> String {
        let miles = meters / 1609.344
        if miles < 0.2 {
            return String(format: "%.0f ft", meters * 3.28084)
        }
        return miles < 10 ? String(format: "%.1f mi", miles) : String(format: "%.0f mi", miles)
    }

    static func asOf(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
