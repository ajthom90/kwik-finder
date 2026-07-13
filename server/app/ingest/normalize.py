"""Normalize raw Kwik Trip store detail JSON into API store rows (+ features)."""

from __future__ import annotations

from typing import Any

from app.features import compute_features


def normalize_detail(
    detail: dict[str, Any],
    *,
    family: set[int],
    ev: dict[int, str],
) -> dict[str, Any]:
    """Map a Kwik Trip detail payload to camelCase store fields and ``features``.

    Port of ``Scripts/generate_dataset.py`` ``normalize_detail``, plus
    ``features`` from :func:`app.features.compute_features`.
    """
    number = detail["storeNumber"]
    addr = detail.get("address") or {}
    fuels = [
        {
            "type": f.get("type"),
            "description": f.get("description"),
            "price": f.get("currentPrice"),
        }
        for f in detail.get("fuel") or []
        if f.get("type")
    ]
    amenities: list[str] = []
    truck_parking = 0
    for p in detail.get("properties") or []:
        if not p.get("hasProperty"):
            continue
        name = p.get("name")
        if not name:
            continue
        amenities.append(name)
        if name == "TRUCK-PARKING":
            truck_parking = p.get("quantity") or 0

    open24 = bool(detail.get("open24Hours"))
    family_restroom = number in family
    ev_charging = ev.get(number)
    amenity_list = sorted(set(amenities))

    features = compute_features(
        fuels,
        amenity_list,
        open24_hours=open24,
        family_restroom=family_restroom,
        ev_charging=ev_charging,
        truck_parking_spaces=truck_parking,
    )

    return {
        "id": number,
        "name": detail.get("name") or f"KWIK TRIP #{number}",
        "latitude": addr.get("latitude"),
        "longitude": addr.get("longitude"),
        "address1": addr.get("address1"),
        "city": addr.get("city"),
        "county": addr.get("county"),
        "state": addr.get("state"),
        "zip": addr.get("zip"),
        "phone": detail.get("phone"),
        "open24Hours": open24,
        "hours": detail.get("hours"),
        "fuels": fuels,
        "amenities": amenity_list,
        "truckParkingSpaces": truck_parking,
        "familyRestroom": family_restroom,
        "evCharging": ev_charging,
        "features": features,
    }
