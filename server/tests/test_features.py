"""Tests for amenity/fuel → feature flag mapping (port of iOS Store.has)."""

from app.features import compute_features


def _compute(
    fuels=None,
    amenities=None,
    *,
    open24_hours=False,
    family_restroom=False,
    ev_charging=None,
    truck_parking_spaces=0,
):
    return compute_features(
        fuels if fuels is not None else [],
        amenities if amenities is not None else [],
        open24_hours=open24_hours,
        family_restroom=family_restroom,
        ev_charging=ev_charging,
        truck_parking_spaces=truck_parking_spaces,
    )


def test_diesel_and_atm_not_bitcoin():
    f = compute_features(
        fuels=[{"type": "DIESEL #2", "description": None, "price": 1.0}],
        amenities=["ATM", "BITCOIN ATM", "WI-FI"],
        open24_hours=True,
        family_restroom=True,
        ev_charging="open",
        truck_parking_spaces=4,
    )
    assert f["diesel"] is True
    assert f["atm"] is True
    assert f["bitcoinATM"] is True
    assert f["wifi"] is True
    assert f["truckParkingStalls"] == 4


def test_empty_inputs_all_false_or_zero():
    f = _compute()
    assert f["diesel"] is False
    assert f["premiumDiesel"] is False
    assert f["def"] is False
    assert f["e85"] is False
    assert f["cng"] is False
    assert f["noEthanolGas"] is False
    assert f["unleaded88"] is False
    assert f["scale"] is False
    assert f["showers"] is False
    assert f["truckParking"] is False
    assert f["truckParkingStalls"] == 0
    assert f["transFlo"] is False
    assert f["fleetCards"] is False
    assert f["truckFriendly"] is False
    assert f["carWash"] is False
    assert f["atm"] is False
    assert f["bitcoinATM"] is False
    assert f["wifi"] is False
    assert f["restaurant"] is False


def test_premium_diesel_also_sets_diesel():
    f = _compute(fuels=[{"type": "PREMIUM DIESEL", "description": None, "price": 2.0}])
    assert f["diesel"] is True
    assert f["premiumDiesel"] is True


def test_diesel_hash_prefix_not_premium():
    f = _compute(fuels=[{"type": "DIESEL #2", "description": None, "price": 1.0}])
    assert f["diesel"] is True
    assert f["premiumDiesel"] is False


def test_regular_diesel_without_hash_prefix_is_not_diesel():
    """Only DIESEL #* or exact PREMIUM DIESEL count — bare DIESEL does not."""
    f = _compute(fuels=[{"type": "DIESEL", "description": None, "price": 1.0}])
    assert f["diesel"] is False
    assert f["premiumDiesel"] is False


def test_all_fuel_types():
    f = _compute(
        fuels=[
            {"type": "DIESEL EXHAUST FLUID", "description": None, "price": 1.0},
            {"type": "E-85", "description": None, "price": 1.0},
            {"type": "COMPRESSED NATURAL GAS", "description": None, "price": 1.0},
            {"type": "UNLEADED 87 (0% ETH)", "description": None, "price": 1.0},
            {"type": "UNLEADED 88", "description": None, "price": 1.0},
        ]
    )
    assert f["def"] is True
    assert f["e85"] is True
    assert f["cng"] is True
    assert f["noEthanolGas"] is True
    assert f["unleaded88"] is True
    assert f["diesel"] is False


def test_all_amenities():
    f = _compute(
        amenities=[
            "SCALE",
            "SHOWERS",
            "TRUCK-PARKING",
            "TRANS-FLO",
            "TRENDAR",
            "TRUCK-FRIENDLY",
            "CAR-WASH",
            "ATM",
            "BITCOIN ATM",
            "WI-FI",
            "RESTAURANT",
        ],
        truck_parking_spaces=12,
    )
    assert f["scale"] is True
    assert f["showers"] is True
    assert f["truckParking"] is True
    assert f["transFlo"] is True
    assert f["fleetCards"] is True
    assert f["truckFriendly"] is True
    assert f["carWash"] is True
    assert f["atm"] is True
    assert f["bitcoinATM"] is True
    assert f["wifi"] is True
    assert f["restaurant"] is True
    assert f["truckParkingStalls"] == 12


def test_bitcoin_atm_does_not_imply_atm():
    f = _compute(amenities=["BITCOIN ATM"])
    assert f["bitcoinATM"] is True
    assert f["atm"] is False


def test_atm_exact_only():
    f = _compute(amenities=["ATM MACHINE", "BITCOIN ATM"])
    assert f["atm"] is False
    assert f["bitcoinATM"] is True


def test_amenity_case_and_display_names_do_not_match():
    """Mapping uses raw Kwik Trip strings, not display labels."""
    f = _compute(amenities=["Wi-Fi", "Car Wash", "Scale", "Showers"])
    assert f["wifi"] is False
    assert f["carWash"] is False
    assert f["scale"] is False
    assert f["showers"] is False


def test_feature_keys_present():
    f = _compute()
    expected = {
        "diesel",
        "premiumDiesel",
        "def",
        "e85",
        "cng",
        "noEthanolGas",
        "unleaded88",
        "scale",
        "showers",
        "truckParking",
        "truckParkingStalls",
        "transFlo",
        "fleetCards",
        "truckFriendly",
        "carWash",
        "atm",
        "bitcoinATM",
        "wifi",
        "restaurant",
    }
    assert set(f.keys()) == expected


def test_fuel_type_missing_treated_as_empty():
    f = _compute(fuels=[{"description": None, "price": 1.0}, {"type": None}])
    assert f["diesel"] is False
