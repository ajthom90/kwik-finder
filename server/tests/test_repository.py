from app.config import get_settings
from app import repository as repo


def test_upsert_and_get(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    get_settings.cache_clear()
    repo.init_db()
    repo.upsert_stores([{
        "id": 1,
        "name": "KWIK TRIP #1",
        "latitude": 44.0,
        "longitude": -93.0,
        "address1": "1 Main",
        "city": "Town",
        "county": None,
        "state": "MN",
        "zip": "55000",
        "phone": "555",
        "open24Hours": True,
        "hours": [{"dayOfWeek": "Monday", "openTime": "00:00:00", "closeTime": "00:00:00"}],
        "fuels": [{"type": "DIESEL #2", "description": None, "price": 3.2}],
        "amenities": ["ATM", "WI-FI"],
        "truckParkingSpaces": 0,
        "familyRestroom": False,
        "evCharging": None,
        "features": {"atm": True, "wifi": True, "diesel": True},
    }])
    stores = repo.get_all_stores()
    assert len(stores) == 1
    assert stores[0]["id"] == 1
    assert stores[0]["fuels"][0]["type"] == "DIESEL #2"
    one = repo.get_store(1)
    assert one is not None
    assert repo.get_store(999) is None


def test_pdf_flags_preserved_on_detail_upsert(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    get_settings.cache_clear()
    repo.init_db()
    base = {
        "id": 5,
        "name": "KWIK TRIP #5",
        "latitude": 1.0,
        "longitude": 2.0,
        "address1": "a",
        "city": "c",
        "county": None,
        "state": "WI",
        "zip": "1",
        "phone": "",
        "open24Hours": False,
        "hours": [],
        "fuels": [],
        "amenities": [],
        "truckParkingSpaces": 0,
        "familyRestroom": False,
        "evCharging": None,
        "features": {},
    }
    repo.upsert_stores([base])
    repo.apply_pdf_flags({5}, {5: "open"})
    s = repo.get_store(5)
    assert s["familyRestroom"] is True
    assert s["evCharging"] == "open"
    # Detail refresh must not wipe PDF flags when incoming has False/None
    updated = {**base, "name": "KWIK TRIP #5 UPDATED", "familyRestroom": False, "evCharging": None}
    repo.upsert_stores([updated], preserve_pdf_flags=True)
    s2 = repo.get_store(5)
    assert s2["name"] == "KWIK TRIP #5 UPDATED"
    assert s2["familyRestroom"] is True
    assert s2["evCharging"] == "open"
