"""SQLite-backed store catalog repository."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any

from app import db

META_KEYS = (
    "listRefreshedAt",
    "detailsRefreshedAt",
    "pdfEnrichedAt",
    "dataVersion",
    "lastError",
    "degraded",
)

_DEFAULT_META: dict[str, Any] = {
    "listRefreshedAt": None,
    "detailsRefreshedAt": None,
    "pdfEnrichedAt": None,
    "dataVersion": None,
    "lastError": None,
    "degraded": False,
}


def _now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def _bool_int(value: Any) -> int:
    return 1 if value else 0


def _row_to_store(row: Any) -> dict[str, Any]:
    return {
        "id": row["id"],
        "name": row["name"],
        "latitude": row["latitude"],
        "longitude": row["longitude"],
        "address1": row["address1"],
        "city": row["city"],
        "county": row["county"],
        "state": row["state"],
        "zip": row["zip"],
        "phone": row["phone"],
        "open24Hours": bool(row["open24_hours"]),
        "hours": json.loads(row["hours_json"] or "[]"),
        "fuels": json.loads(row["fuels_json"] or "[]"),
        "amenities": json.loads(row["amenities_json"] or "[]"),
        "truckParkingSpaces": row["truck_parking_spaces"] or 0,
        "familyRestroom": bool(row["family_restroom"]),
        "evCharging": row["ev_charging"],
        "features": json.loads(row["features_json"] or "{}"),
    }


def init_db() -> None:
    with db.get_connection() as conn:
        db.init_schema(conn)


def upsert_stores(stores: list[dict], *, preserve_pdf_flags: bool = False) -> None:
    if not stores:
        return

    with db.get_connection() as conn:
        existing_flags: dict[int, tuple[int, str | None]] = {}
        if preserve_pdf_flags:
            ids = [int(s["id"]) for s in stores]
            placeholders = ",".join("?" * len(ids))
            rows = conn.execute(
                f"SELECT id, family_restroom, ev_charging FROM stores WHERE id IN ({placeholders})",
                ids,
            ).fetchall()
            for row in rows:
                existing_flags[int(row["id"])] = (
                    int(row["family_restroom"] or 0),
                    row["ev_charging"],
                )

        updated_at = _now_iso()
        for store in stores:
            store_id = int(store["id"])
            family = bool(store.get("familyRestroom"))
            ev = store.get("evCharging")

            if preserve_pdf_flags and store_id in existing_flags:
                prev_family, prev_ev = existing_flags[store_id]
                # Keep existing PDF flags when incoming is false/null
                if not family and prev_family:
                    family = True
                if ev is None and prev_ev is not None:
                    ev = prev_ev

            conn.execute(
                """
                INSERT INTO stores (
                  id, name, latitude, longitude, address1, city, county, state, zip, phone,
                  open24_hours, hours_json, fuels_json, amenities_json,
                  truck_parking_spaces, family_restroom, ev_charging, features_json, updated_at
                ) VALUES (
                  ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                  ?, ?, ?, ?,
                  ?, ?, ?, ?, ?
                )
                ON CONFLICT(id) DO UPDATE SET
                  name = excluded.name,
                  latitude = excluded.latitude,
                  longitude = excluded.longitude,
                  address1 = excluded.address1,
                  city = excluded.city,
                  county = excluded.county,
                  state = excluded.state,
                  zip = excluded.zip,
                  phone = excluded.phone,
                  open24_hours = excluded.open24_hours,
                  hours_json = excluded.hours_json,
                  fuels_json = excluded.fuels_json,
                  amenities_json = excluded.amenities_json,
                  truck_parking_spaces = excluded.truck_parking_spaces,
                  family_restroom = excluded.family_restroom,
                  ev_charging = excluded.ev_charging,
                  features_json = excluded.features_json,
                  updated_at = excluded.updated_at
                """,
                (
                    store_id,
                    store.get("name") or "",
                    float(store["latitude"]),
                    float(store["longitude"]),
                    store.get("address1"),
                    store.get("city"),
                    store.get("county"),
                    store.get("state"),
                    store.get("zip"),
                    store.get("phone"),
                    _bool_int(store.get("open24Hours")),
                    json.dumps(store.get("hours") or []),
                    json.dumps(store.get("fuels") or []),
                    json.dumps(store.get("amenities") or []),
                    int(store.get("truckParkingSpaces") or 0),
                    _bool_int(family),
                    ev,
                    json.dumps(store.get("features") or {}),
                    updated_at,
                ),
            )
        conn.commit()


def get_all_stores() -> list[dict]:
    with db.get_connection() as conn:
        rows = conn.execute("SELECT * FROM stores ORDER BY id").fetchall()
        return [_row_to_store(row) for row in rows]


def get_store(store_id: int) -> dict | None:
    with db.get_connection() as conn:
        row = conn.execute(
            "SELECT * FROM stores WHERE id = ?",
            (int(store_id),),
        ).fetchone()
        if row is None:
            return None
        return _row_to_store(row)


def get_refresh_meta() -> dict:
    meta = dict(_DEFAULT_META)
    try:
        with db.get_connection() as conn:
            # Ensure schema exists so health can call before/without init_db in edge cases
            rows = conn.execute("SELECT key, value FROM meta").fetchall()
    except Exception:
        return meta

    for row in rows:
        key = row["key"]
        if key not in meta:
            continue
        value = row["value"]
        if key == "degraded":
            meta[key] = value in ("1", "true", "True", "TRUE")
        else:
            meta[key] = value
    return meta


def set_refresh_meta(**kwargs: Any) -> None:
    if not kwargs:
        return
    with db.get_connection() as conn:
        db.init_schema(conn)
        for key, value in kwargs.items():
            if key not in META_KEYS:
                continue
            if key == "degraded":
                stored = "1" if value else "0"
            elif value is None:
                stored = None
            else:
                stored = str(value)
            if stored is None:
                conn.execute("DELETE FROM meta WHERE key = ?", (key,))
            else:
                conn.execute(
                    """
                    INSERT INTO meta (key, value) VALUES (?, ?)
                    ON CONFLICT(key) DO UPDATE SET value = excluded.value
                    """,
                    (key, stored),
                )
        conn.commit()


def apply_pdf_flags(family: set[int], ev: dict[int, str]) -> None:
    with db.get_connection() as conn:
        for store_id in family:
            conn.execute(
                "UPDATE stores SET family_restroom = 1, updated_at = ? WHERE id = ?",
                (_now_iso(), int(store_id)),
            )
        for store_id, status in ev.items():
            conn.execute(
                "UPDATE stores SET ev_charging = ?, updated_at = ? WHERE id = ?",
                (status, _now_iso(), int(store_id)),
            )
        conn.execute(
            """
            INSERT INTO meta (key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """,
            ("pdfEnrichedAt", _now_iso()),
        )
        conn.commit()
