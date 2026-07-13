"""Kwik Trip store list and details clients (batch size ≤ 10)."""

from __future__ import annotations

import time
from typing import Any

from app.ingest.http import fetch_json

BASE = "https://www.kwiktrip.com"
STORE_LIST_URL = f"{BASE}/storelistproxy.php"
STORE_INFO_URL = f"{BASE}/storeinformationsproxy.php?ids={{ids}}"
BATCH_SIZE = 10  # details endpoint rejects more than 10 ids
BATCH_SLEEP_SECONDS = 0.3


def fetch_store_list() -> list[dict[str, Any]]:
    """Return raw list entries from ``storelistproxy.php``."""
    data = fetch_json(STORE_LIST_URL)
    stores = data.get("stores") if isinstance(data, dict) else None
    return list(stores or [])


def fetch_store_details(
    ids: list[int],
    *,
    batch_sleep: float = BATCH_SLEEP_SECONDS,
) -> list[dict[str, Any]]:
    """Fetch store details in chunks of ``BATCH_SIZE``, sleeping between batches.

    ``batch_sleep`` is overridable for tests (set to 0 to avoid delays).
    """
    details: list[dict[str, Any]] = []
    if not ids:
        return details

    for i in range(0, len(ids), BATCH_SIZE):
        if i > 0 and batch_sleep > 0:
            time.sleep(batch_sleep)
        batch = ids[i : i + BATCH_SIZE]
        url = STORE_INFO_URL.format(ids=",".join(map(str, batch)))
        data = fetch_json(url)
        stores = data.get("stores") if isinstance(data, dict) else None
        details.extend(stores or [])
    return details
