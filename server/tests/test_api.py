from app import repository as repo

SAMPLE_STORE = {
    "id": 123,
    "name": "KWIK TRIP #123",
    "latitude": 44.9,
    "longitude": -93.2,
    "address1": "1 Main St",
    "city": "La Crosse",
    "county": None,
    "state": "WI",
    "zip": "54601",
    "phone": "608-555-0100",
    "open24Hours": True,
    "hours": [
        {"dayOfWeek": "Monday", "openTime": "00:00:00", "closeTime": "00:00:00"}
    ],
    "fuels": [
        {"type": "DIESEL #2", "description": None, "price": 3.299}
    ],
    "amenities": ["ATM", "WI-FI"],
    "truckParkingSpaces": 12,
    "familyRestroom": True,
    "evCharging": "open",
    "features": {
        "diesel": True,
        "atm": True,
        "wifi": True,
        "truckParking": True,
        "truckParkingStalls": 12,
    },
}


def test_stores_empty(client):
    r = client.get("/v1/stores")
    assert r.status_code == 200
    body = r.json()
    assert body["stores"] == []
    assert "meta" in body
    meta = body["meta"]
    assert meta["storeCount"] == 0
    assert "dataVersion" in meta
    assert "listRefreshedAt" in meta
    assert "detailsRefreshedAt" in meta
    assert "pdfEnrichedAt" in meta


def test_meta_empty(client):
    r = client.get("/v1/meta")
    assert r.status_code == 200
    body = r.json()
    assert body["storeCount"] == 0
    assert body["dataVersion"] is None
    assert body["listRefreshedAt"] is None
    assert body["detailsRefreshedAt"] is None
    assert body["pdfEnrichedAt"] is None


def test_meta_and_get_one(client):
    repo.upsert_stores([SAMPLE_STORE])
    repo.set_refresh_meta(
        dataVersion="2026-07-13T12:00:00+00:00",
        listRefreshedAt="2026-07-13T12:00:00+00:00",
        detailsRefreshedAt="2026-07-13T12:05:00+00:00",
        pdfEnrichedAt="2026-07-13T06:00:00+00:00",
    )

    r = client.get("/v1/meta")
    assert r.status_code == 200
    meta = r.json()
    assert meta["dataVersion"] == "2026-07-13T12:00:00+00:00"
    assert meta["storeCount"] == 1
    assert meta["listRefreshedAt"] == "2026-07-13T12:00:00+00:00"
    assert meta["detailsRefreshedAt"] == "2026-07-13T12:05:00+00:00"
    assert meta["pdfEnrichedAt"] == "2026-07-13T06:00:00+00:00"

    r = client.get("/v1/stores")
    assert r.status_code == 200
    body = r.json()
    assert body["meta"]["storeCount"] == 1
    assert body["meta"]["dataVersion"] == "2026-07-13T12:00:00+00:00"
    assert len(body["stores"]) == 1
    store = body["stores"][0]
    assert store["id"] == 123
    assert store["name"] == "KWIK TRIP #123"
    assert store["open24Hours"] is True
    assert store["familyRestroom"] is True
    assert store["evCharging"] == "open"
    assert store["truckParkingSpaces"] == 12
    assert store["fuels"][0]["type"] == "DIESEL #2"
    assert store["features"]["diesel"] is True

    r = client.get("/v1/stores/123")
    assert r.status_code == 200
    one = r.json()
    assert one["id"] == 123
    assert one["city"] == "La Crosse"
    assert one["state"] == "WI"
    assert one["features"]["atm"] is True


def test_store_not_found(client):
    r = client.get("/v1/stores/999")
    assert r.status_code == 404
