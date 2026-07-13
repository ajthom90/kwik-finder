"""Tests for scheduled refresh jobs (no live network)."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.config import get_settings
from app import repository as repo

FIXTURES = Path(__file__).parent / "fixtures"


@pytest.fixture()
def db_ready(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    get_settings.cache_clear()
    repo.init_db()
    yield tmp_path


@pytest.fixture()
def sample_detail() -> dict:
    return json.loads((FIXTURES / "store_detail_sample.json").read_text())


def _list_entry(
    store_id: int,
    *,
    name: str | None = None,
    lat: float | None = 44.0,
    lon: float | None = -93.0,
    phone: str = "555",
) -> dict:
    return {
        "id": store_id,
        "name": name if name is not None else f"KWIK TRIP #{store_id}",
        "latitude": lat,
        "longitude": lon,
        "phone": phone,
        "address": {
            "address1": f"{store_id} Main",
            "city": "Town",
            "state": "MN",
            "zip": "55000",
        },
    }


def test_refresh_store_list_inserts_skeletons_and_sets_meta(db_ready, monkeypatch):
    from app import jobs

    monkeypatch.setattr(
        jobs,
        "fetch_store_list",
        lambda: [
            _list_entry(1),
            _list_entry(2, lat=None, lon=None),  # skipped — no coords
            _list_entry(3, lat=45.0, lon=-94.0, name="KWIK STAR #3"),
        ],
    )

    n = jobs.refresh_store_list()
    assert n == 2

    stores = {s["id"]: s for s in repo.get_all_stores()}
    assert set(stores) == {1, 3}
    assert stores[1]["name"] == "KWIK TRIP #1"
    assert stores[1]["latitude"] == 44.0
    assert stores[1]["fuels"] == []
    assert stores[3]["name"] == "KWIK STAR #3"
    assert stores[3]["city"] == "Town"

    meta = repo.get_refresh_meta()
    assert meta["listRefreshedAt"] is not None
    assert meta["dataVersion"] is not None
    assert meta["degraded"] is False
    assert meta["lastError"] is None


def test_refresh_store_list_updates_name_coords_preserves_details(db_ready, monkeypatch):
    from app import jobs

    repo.upsert_stores(
        [
            {
                "id": 10,
                "name": "OLD NAME",
                "latitude": 1.0,
                "longitude": 2.0,
                "address1": "1 Main",
                "city": "Town",
                "county": None,
                "state": "WI",
                "zip": "1",
                "phone": "555",
                "open24Hours": True,
                "hours": [],
                "fuels": [{"type": "DIESEL #2", "description": None, "price": 3.0}],
                "amenities": ["ATM"],
                "truckParkingSpaces": 2,
                "familyRestroom": True,
                "evCharging": "open",
                "features": {"diesel": True, "atm": True},
            }
        ]
    )

    monkeypatch.setattr(
        jobs,
        "fetch_store_list",
        lambda: [_list_entry(10, name="NEW NAME", lat=9.0, lon=8.0)],
    )

    n = jobs.refresh_store_list()
    assert n == 1

    s = repo.get_store(10)
    assert s is not None
    assert s["name"] == "NEW NAME"
    assert s["latitude"] == 9.0
    assert s["longitude"] == 8.0
    # Detail fields preserved
    assert s["fuels"] == [{"type": "DIESEL #2", "description": None, "price": 3.0}]
    assert s["amenities"] == ["ATM"]
    assert s["familyRestroom"] is True
    assert s["evCharging"] == "open"
    assert s["open24Hours"] is True


def test_refresh_store_list_failure_sets_degraded_keeps_db(db_ready, monkeypatch):
    from app import jobs

    repo.upsert_stores(
        [
            {
                "id": 1,
                "name": "KWIK TRIP #1",
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
        ]
    )

    def boom():
        raise RuntimeError("list down")

    monkeypatch.setattr(jobs, "fetch_store_list", boom)

    with pytest.raises(RuntimeError, match="list down"):
        jobs.refresh_store_list()

    meta = repo.get_refresh_meta()
    assert meta["degraded"] is True
    assert "list down" in (meta["lastError"] or "")
    assert len(repo.get_all_stores()) == 1


def test_refresh_store_details_normalizes_and_preserves_pdf_flags(
    db_ready, monkeypatch, sample_detail
):
    from app import jobs

    # Seed skeleton + PDF flags
    jobs_list = [
        {
            "id": 103,
            "name": "KWIK STAR #103",
            "latitude": 42.5,
            "longitude": -93.2,
            "address1": "old",
            "city": "IOWA FALLS",
            "county": None,
            "state": "IA",
            "zip": "50126",
            "phone": "",
            "open24Hours": False,
            "hours": [],
            "fuels": [],
            "amenities": [],
            "truckParkingSpaces": 0,
            "familyRestroom": True,
            "evCharging": "open",
            "features": {},
        }
    ]
    repo.upsert_stores(jobs_list)

    monkeypatch.setattr(
        jobs,
        "fetch_store_details",
        lambda ids, **kw: [sample_detail] if 103 in ids else [],
    )

    n = jobs.refresh_store_details(max_batches=1)
    assert n == 1

    s = repo.get_store(103)
    assert s is not None
    assert s["address1"] == "701 S OAK ST"
    assert s["open24Hours"] is True
    assert any(f["type"] == "DIESEL #2" for f in s["fuels"])
    assert "ATM" in s["amenities"]
    assert s["familyRestroom"] is True  # preserved
    assert s["evCharging"] == "open"  # preserved
    assert s["features"].get("diesel") is True
    assert s["features"].get("atm") is True

    meta = repo.get_refresh_meta()
    assert meta["detailsRefreshedAt"] is not None
    assert meta["dataVersion"] is not None
    assert meta["degraded"] is False


def test_refresh_store_details_respects_max_batches(db_ready, monkeypatch):
    from app import jobs

    for i in range(25):
        repo.upsert_stores(
            [
                {
                    "id": i + 1,
                    "name": f"KWIK TRIP #{i + 1}",
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
            ]
        )

    fetched_ids: list[int] = []

    def fake_details(ids, **kw):
        fetched_ids.extend(ids)
        return [
            {
                "storeNumber": sid,
                "name": f"KWIK TRIP #{sid}",
                "phone": "",
                "open24Hours": False,
                "hours": None,
                "address": {
                    "address1": "a",
                    "city": "c",
                    "county": None,
                    "state": "WI",
                    "zip": "1",
                    "latitude": 1.0,
                    "longitude": 2.0,
                },
                "fuel": [],
                "properties": [],
            }
            for sid in ids
        ]

    monkeypatch.setattr(jobs, "fetch_store_details", fake_details)

    n = jobs.refresh_store_details(max_batches=2)
    assert n == 20  # 2 batches * 10
    assert len(fetched_ids) == 20


def test_refresh_store_details_prefers_null_updated_at(db_ready, monkeypatch):
    from app import jobs
    from app import db

    # Insert two stores: one with updated_at set, one null (staler)
    with db.get_connection() as conn:
        for store_id, updated in ((1, "2020-01-01T00:00:00+00:00"), (2, None)):
            conn.execute(
                """
                INSERT INTO stores (
                  id, name, latitude, longitude, address1, city, county, state, zip, phone,
                  open24_hours, hours_json, fuels_json, amenities_json,
                  truck_parking_spaces, family_restroom, ev_charging, features_json, updated_at
                ) VALUES (?, ?, 1.0, 2.0, 'a', 'c', NULL, 'WI', '1', '',
                  0, '[]', '[]', '[]', 0, 0, NULL, '{}', ?)
                """,
                (store_id, f"KWIK TRIP #{store_id}", updated),
            )

    order: list[int] = []

    def fake_details(ids, **kw):
        order.extend(ids)
        return [
            {
                "storeNumber": sid,
                "name": f"N{sid}",
                "phone": "",
                "open24Hours": False,
                "hours": None,
                "address": {
                    "address1": "a",
                    "city": "c",
                    "county": None,
                    "state": "WI",
                    "zip": "1",
                    "latitude": 1.0,
                    "longitude": 2.0,
                },
                "fuel": [],
                "properties": [],
            }
            for sid in ids
        ]

    monkeypatch.setattr(jobs, "fetch_store_details", fake_details)
    jobs.refresh_store_details(max_batches=1)
    assert order[0] == 2  # null updated_at first


def test_refresh_store_details_failure_sets_degraded(db_ready, monkeypatch):
    from app import jobs

    repo.upsert_stores(
        [
            {
                "id": 1,
                "name": "KWIK TRIP #1",
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
        ]
    )

    def boom(ids, **kw):
        raise RuntimeError("details down")

    monkeypatch.setattr(jobs, "fetch_store_details", boom)

    with pytest.raises(RuntimeError, match="details down"):
        jobs.refresh_store_details(max_batches=1)

    meta = repo.get_refresh_meta()
    assert meta["degraded"] is True
    assert "details down" in (meta["lastError"] or "")
    assert repo.get_store(1) is not None


def test_refresh_pdfs_calls_refresh_pdf_flags(db_ready, monkeypatch):
    from app import jobs

    called = {"n": 0}

    def fake_refresh():
        called["n"] += 1
        repo.apply_pdf_flags(set(), {})
        return {"familyCount": 0, "evCount": 0}

    monkeypatch.setattr(jobs, "refresh_pdf_flags", fake_refresh)
    jobs.refresh_pdfs()
    assert called["n"] == 1
    meta = repo.get_refresh_meta()
    assert meta["pdfEnrichedAt"] is not None
    assert meta["degraded"] is False


def test_refresh_pdfs_failure_sets_degraded(db_ready, monkeypatch):
    from app import jobs

    def boom():
        raise RuntimeError("pdf down")

    monkeypatch.setattr(jobs, "refresh_pdf_flags", boom)

    with pytest.raises(RuntimeError, match="pdf down"):
        jobs.refresh_pdfs()

    meta = repo.get_refresh_meta()
    assert meta["degraded"] is True
    assert "pdf down" in (meta["lastError"] or "")


def test_start_scheduler_returns_scheduler_with_jobs(db_ready, monkeypatch):
    from app import jobs
    from apscheduler.schedulers.background import BackgroundScheduler

    # Avoid real initial refresh network/work
    monkeypatch.setattr(jobs, "refresh_store_list", lambda: 0)
    monkeypatch.setattr(jobs, "refresh_store_details", lambda max_batches=None: 0)
    monkeypatch.setattr(jobs, "refresh_pdfs", lambda: None)
    monkeypatch.setenv("LIST_REFRESH_SECONDS", "900")
    monkeypatch.setenv("DETAILS_REFRESH_SECONDS", "120")
    monkeypatch.setenv("PDF_REFRESH_SECONDS", "86400")
    get_settings.cache_clear()

    scheduler = jobs.start_scheduler()
    try:
        assert isinstance(scheduler, BackgroundScheduler)
        assert scheduler.running
        job_ids = {j.id for j in scheduler.get_jobs()}
        assert "refresh_store_list" in job_ids
        assert "refresh_store_details" in job_ids
        assert "refresh_pdfs" in job_ids
    finally:
        scheduler.shutdown(wait=False)
