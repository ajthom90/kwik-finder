"""Amenity/fuel string → feature flags (port of iOS Store.has)."""

from __future__ import annotations

from typing import Any


def compute_features(
    fuels: list[dict[str, Any]],
    amenities: list[str],
    *,
    open24_hours: bool,
    family_restroom: bool,
    ev_charging: str | None,
    truck_parking_spaces: int = 0,
) -> dict[str, bool | int | None]:
    """Map raw fuels/amenities (and headline flags) to stable filter feature keys.

    Headline fields ``open24_hours``, ``family_restroom``, and ``ev_charging``
    are accepted for a uniform call site with normalize; they remain store-level
    fields and are not included in the returned ``features`` dict (clients use
    top-level ``open24Hours`` / ``familyRestroom`` / ``evCharging``).
    """
    # Headline inputs are owned by the store payload, not features.
    _ = (open24_hours, family_restroom, ev_charging)

    amenity_set = set(amenities or [])
    fuel_types = [
        str(f.get("type") or "")
        for f in (fuels or [])
    ]

    diesel = any(
        t.startswith("DIESEL #") or t == "PREMIUM DIESEL" for t in fuel_types
    )
    premium_diesel = any(t == "PREMIUM DIESEL" for t in fuel_types)

    return {
        "diesel": diesel,
        "premiumDiesel": premium_diesel,
        "def": any(t == "DIESEL EXHAUST FLUID" for t in fuel_types),
        "e85": any(t == "E-85" for t in fuel_types),
        "cng": any(t == "COMPRESSED NATURAL GAS" for t in fuel_types),
        "noEthanolGas": any(t == "UNLEADED 87 (0% ETH)" for t in fuel_types),
        "unleaded88": any(t == "UNLEADED 88" for t in fuel_types),
        "scale": "SCALE" in amenity_set,
        "showers": "SHOWERS" in amenity_set,
        "truckParking": "TRUCK-PARKING" in amenity_set,
        "truckParkingStalls": int(truck_parking_spaces or 0),
        "transFlo": "TRANS-FLO" in amenity_set,
        "fleetCards": "TRENDAR" in amenity_set,
        "truckFriendly": "TRUCK-FRIENDLY" in amenity_set,
        "carWash": "CAR-WASH" in amenity_set,
        # Exact amenity name only — "BITCOIN ATM" is a separate feature.
        "atm": "ATM" in amenity_set,
        "bitcoinATM": "BITCOIN ATM" in amenity_set,
        "wifi": "WI-FI" in amenity_set,
        "restaurant": "RESTAURANT" in amenity_set,
    }
