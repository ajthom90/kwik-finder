"""Tests for Kwik Trip store detail → normalized store row (+ features)."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.ingest.normalize import normalize_detail

FIXTURES = Path(__file__).parent / "fixtures"


@pytest.fixture()
def sample_detail() -> dict:
    return json.loads((FIXTURES / "store_detail_sample.json").read_text())


def test_normalize_detail_maps_core_fields(sample_detail):
    out = normalize_detail(sample_detail, family={103}, ev={103: "open"})

    assert out["id"] == 103
    assert out["name"] == "KWIK STAR #103"
    assert out["latitude"] == 42.50639
    assert out["longitude"] == -93.2633
    assert out["address1"] == "701 S OAK ST"
    assert out["city"] == "IOWA FALLS"
    assert out["county"] == "HARDIN"
    assert out["state"] == "IA"
    assert out["zip"] == "50126"
    assert out["phone"] == "(641) 648-6707"
    assert out["open24Hours"] is True
    assert out["hours"] is None
    assert out["truckParkingSpaces"] == 8
    assert out["familyRestroom"] is True
    assert out["evCharging"] == "open"


def test_normalize_detail_fuels_map_current_price_to_price(sample_detail):
    out = normalize_detail(sample_detail, family=set(), ev={})
    fuels = out["fuels"]

    assert all("currentPrice" not in f for f in fuels)
    assert all("price" in f for f in fuels)
    # Fuel entries without type are dropped
    types = [f["type"] for f in fuels]
    assert None not in types
    assert "DIESEL #2" in types
    diesel = next(f for f in fuels if f["type"] == "DIESEL #2")
    assert diesel["price"] == 4.489
    assert diesel["description"] == "B11 #2 ULSD"


def test_normalize_detail_amenities_from_has_property(sample_detail):
    out = normalize_detail(sample_detail, family=set(), ev={})
    # Sorted unique raw Kwik Trip names; hasProperty false / null name skipped
    assert out["amenities"] == [
        "ATM",
        "TRENDAR",
        "TRUCK-FRIENDLY",
        "TRUCK-PARKING",
        "WI-FI",
    ]
    assert "SHOWERS" not in out["amenities"]


def test_normalize_detail_pdf_flags_absent_when_not_in_sets(sample_detail):
    out = normalize_detail(sample_detail, family={999}, ev={999: "comingSoon"})
    assert out["familyRestroom"] is False
    assert out["evCharging"] is None


def test_normalize_detail_ev_coming_soon(sample_detail):
    out = normalize_detail(sample_detail, family=set(), ev={103: "comingSoon"})
    assert out["evCharging"] == "comingSoon"


def test_normalize_detail_default_name_when_missing(sample_detail):
    sample_detail = {**sample_detail, "name": None}
    out = normalize_detail(sample_detail, family=set(), ev={})
    assert out["name"] == "KWIK TRIP #103"


def test_normalize_detail_includes_features(sample_detail):
    out = normalize_detail(sample_detail, family={103}, ev={103: "open"})
    features = out["features"]

    assert features["diesel"] is True
    assert features["premiumDiesel"] is True
    assert features["def"] is True
    assert features["unleaded88"] is True
    assert features["atm"] is True
    assert features["wifi"] is True
    assert features["fleetCards"] is True
    assert features["truckFriendly"] is True
    assert features["truckParking"] is True
    assert features["truckParkingStalls"] == 8
    # Headline fields stay top-level, not in features
    assert "open24Hours" not in features
    assert "familyRestroom" not in features
    assert "evCharging" not in features


def test_normalize_detail_empty_fuel_and_properties():
    detail = {
        "storeNumber": 1,
        "name": "KWIK TRIP #1",
        "address": {"latitude": 1.0, "longitude": 2.0, "city": "X", "state": "WI"},
        "open24Hours": False,
    }
    out = normalize_detail(detail, family=set(), ev={})
    assert out["fuels"] == []
    assert out["amenities"] == []
    assert out["truckParkingSpaces"] == 0
    assert out["features"]["diesel"] is False
    assert out["features"]["truckParkingStalls"] == 0


def test_normalize_detail_missing_address():
    detail = {"storeNumber": 42, "name": None}
    out = normalize_detail(detail, family=set(), ev={})
    assert out["id"] == 42
    assert out["name"] == "KWIK TRIP #42"
    assert out["latitude"] is None
    assert out["longitude"] is None
    assert out["address1"] is None
