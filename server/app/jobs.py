"""Scheduled full-catalog refresh jobs (list, details, PDFs)."""

from __future__ import annotations

import logging
import threading
from datetime import datetime, timezone
from typing import Any

from apscheduler.schedulers.background import BackgroundScheduler

from app import db
from app.config import get_settings
from app.ingest.kwiktrip import BATCH_SIZE, fetch_store_details, fetch_store_list
from app.ingest.normalize import normalize_detail
from app.ingest.pdfs import refresh_pdf_flags
from app import repository as repo

logger = logging.getLogger(__name__)

# Details job: process this many batches per scheduled tick (≤10 IDs each).
DETAILS_BATCHES_PER_TICK = 20


def _now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def _mark_failure(exc: BaseException) -> None:
    repo.set_refresh_meta(degraded=True, lastError=str(exc))


def _mark_success(**timestamps: Any) -> None:
    repo.set_refresh_meta(degraded=False, lastError=None, **timestamps)


def _skeleton_from_list_entry(entry: dict[str, Any]) -> dict[str, Any] | None:
    lat = entry.get("latitude")
    lon = entry.get("longitude")
    if lat is None or lon is None:
        return None
    store_id = int(entry["id"])
    addr = entry.get("address") or {}
    zip_val = addr.get("zip")
    return {
        "id": store_id,
        "name": entry.get("name") or f"KWIK TRIP #{store_id}",
        "latitude": float(lat),
        "longitude": float(lon),
        "address1": addr.get("address1"),
        "city": addr.get("city"),
        "county": None,
        "state": addr.get("state"),
        "zip": str(zip_val) if zip_val is not None else None,
        "phone": entry.get("phone") or "",
        "open24Hours": False,
        "hours": [],
        "fuels": [],
        "amenities": [],
        "truckParkingSpaces": 0,
        "familyRestroom": False,
        "evCharging": None,
        "features": {},
    }


def _insert_skeleton(conn: Any, store: dict[str, Any]) -> None:
    """Insert a list-only row with ``updated_at`` NULL so details prefer it."""
    conn.execute(
        """
        INSERT INTO stores (
          id, name, latitude, longitude, address1, city, county, state, zip, phone,
          open24_hours, hours_json, fuels_json, amenities_json,
          truck_parking_spaces, family_restroom, ev_charging, features_json, updated_at
        ) VALUES (
          ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
          0, '[]', '[]', '[]',
          0, 0, NULL, '{}', NULL
        )
        """,
        (
            int(store["id"]),
            store.get("name") or "",
            float(store["latitude"]),
            float(store["longitude"]),
            store.get("address1"),
            store.get("city"),
            store.get("county"),
            store.get("state"),
            store.get("zip"),
            store.get("phone") or "",
        ),
    )


def refresh_store_list() -> int:
    """Fetch store list; insert skeletons for new IDs; update name/coords for existing.

    Returns the number of list entries applied (new + updated). Sets
    ``listRefreshedAt`` and ``dataVersion``. On failure marks degraded and re-raises
    without wiping the DB.
    """
    try:
        entries = fetch_store_list()
        with db.get_connection() as conn:
            existing = {
                int(row["id"])
                for row in conn.execute("SELECT id FROM stores").fetchall()
            }
            applied = 0
            for entry in entries:
                skeleton = _skeleton_from_list_entry(entry)
                if skeleton is None:
                    continue
                store_id = int(skeleton["id"])
                if store_id in existing:
                    conn.execute(
                        """
                        UPDATE stores
                        SET name = ?, latitude = ?, longitude = ?
                        WHERE id = ?
                        """,
                        (
                            skeleton["name"],
                            skeleton["latitude"],
                            skeleton["longitude"],
                            store_id,
                        ),
                    )
                else:
                    _insert_skeleton(conn, skeleton)
                    existing.add(store_id)
                applied += 1

        now = _now_iso()
        _mark_success(listRefreshedAt=now, dataVersion=now)
        return applied
    except Exception as e:
        logger.exception("refresh_store_list failed")
        _mark_failure(e)
        raise


def _pdf_sets_from_db() -> tuple[set[int], dict[int, str]]:
    family: set[int] = set()
    ev: dict[int, str] = {}
    for store in repo.get_all_stores():
        sid = int(store["id"])
        if store.get("familyRestroom"):
            family.add(sid)
        if store.get("evCharging") is not None:
            ev[sid] = store["evCharging"]
    return family, ev


def _store_ids_by_staleness() -> list[int]:
    """Oldest ``updated_at`` first; nulls first so never-detailed rows win."""
    with db.get_connection() as conn:
        rows = conn.execute(
            """
            SELECT id FROM stores
            ORDER BY (updated_at IS NOT NULL), updated_at ASC, id ASC
            """
        ).fetchall()
        return [int(r["id"]) for r in rows]


def refresh_store_details(max_batches: int | None = None) -> int:
    """Fetch and normalize details for the stalest store IDs (batch size 10).

    ``max_batches`` limits how many Kwik Trip detail batches to run (each ≤10 IDs).
    ``None`` means all stores. Preserves PDF flags via ``preserve_pdf_flags=True``.
    Returns the number of store IDs requested for refresh.
    """
    try:
        ids = _store_ids_by_staleness()
        if max_batches is not None:
            ids = ids[: max_batches * BATCH_SIZE]
        if not ids:
            now = _now_iso()
            _mark_success(detailsRefreshedAt=now, dataVersion=now)
            return 0

        family, ev = _pdf_sets_from_db()
        # batch_sleep=0 in tests is achieved by monkeypatch; production uses default
        details = fetch_store_details(ids)
        normalized = [
            normalize_detail(d, family=family, ev=ev)
            for d in details
            if d.get("storeNumber") is not None
            and (d.get("address") or {}).get("latitude") is not None
            and (d.get("address") or {}).get("longitude") is not None
        ]
        # Drop rows without usable coordinates after normalize as well
        normalized = [
            s
            for s in normalized
            if s.get("latitude") is not None and s.get("longitude") is not None
        ]
        repo.upsert_stores(normalized, preserve_pdf_flags=True)

        now = _now_iso()
        _mark_success(detailsRefreshedAt=now, dataVersion=now)
        return len(ids)
    except Exception as e:
        logger.exception("refresh_store_details failed")
        _mark_failure(e)
        raise


def refresh_pdfs() -> None:
    """Refresh family-restroom and EV flags from Maps & Downloads PDFs.

    Headline fields are updated via ``refresh_pdf_flags`` / ``apply_pdf_flags``.
    ``features`` does not encode family/EV (client uses headline fields), so no
    feature recompute is required for filter parity.
    """
    try:
        refresh_pdf_flags()
        now = _now_iso()
        # pdfEnrichedAt set inside apply_pdf_flags; bump dataVersion + clear degraded
        _mark_success(dataVersion=now)
    except Exception as e:
        logger.exception("refresh_pdfs failed")
        _mark_failure(e)
        raise


def _initial_refresh() -> None:
    """Cold-start path: list → details batch → PDFs if never enriched."""
    try:
        refresh_store_list()
    except Exception:
        logger.exception("initial refresh_store_list failed")
    try:
        refresh_store_details(max_batches=DETAILS_BATCHES_PER_TICK)
    except Exception:
        logger.exception("initial refresh_store_details failed")
    try:
        meta = repo.get_refresh_meta()
        if meta.get("pdfEnrichedAt") is None:
            refresh_pdfs()
    except Exception:
        logger.exception("initial refresh_pdfs failed")


def start_scheduler() -> BackgroundScheduler:
    """Start APScheduler interval jobs and kick off a background initial refresh."""
    settings = get_settings()
    scheduler = BackgroundScheduler()

    scheduler.add_job(
        refresh_store_list,
        "interval",
        seconds=settings.list_refresh_seconds,
        id="refresh_store_list",
        replace_existing=True,
        max_instances=1,
        coalesce=True,
    )
    scheduler.add_job(
        refresh_store_details,
        "interval",
        seconds=settings.details_refresh_seconds,
        kwargs={"max_batches": DETAILS_BATCHES_PER_TICK},
        id="refresh_store_details",
        replace_existing=True,
        max_instances=1,
        coalesce=True,
    )
    scheduler.add_job(
        refresh_pdfs,
        "interval",
        seconds=settings.pdf_refresh_seconds,
        id="refresh_pdfs",
        replace_existing=True,
        max_instances=1,
        coalesce=True,
    )

    scheduler.start()

    thread = threading.Thread(
        target=_initial_refresh,
        name="kwikfinder-initial-refresh",
        daemon=True,
    )
    thread.start()

    return scheduler
