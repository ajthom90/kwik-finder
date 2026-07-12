import CoreLocation
import Foundation
import Observation

@Observable
final class LocationService: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()

    private(set) var location: CLLocation?
    private(set) var authorization: CLAuthorizationStatus = .notDetermined

    /// Center of the Kwik Trip footprint (western Wisconsin); used to sort and
    /// frame the map until a real fix arrives or when permission is denied.
    static let fallbackLocation = CLLocation(latitude: 44.25, longitude: -91.5)

    var effectiveLocation: CLLocation {
        location ?? Self.fallbackLocation
    }

    var isAuthorized: Bool {
        authorization == .authorizedWhenInUse || authorization == .authorizedAlways
    }

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.distanceFilter = 250
    }

    func requestPermission() {
        if manager.authorizationStatus == .notDetermined {
            manager.requestWhenInUseAuthorization()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorization = manager.authorizationStatus
        if isAuthorized {
            manager.startUpdatingLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let latest = locations.last {
            location = latest
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Transient failures are fine; the fallback location keeps the UI usable.
    }
}
