#!/usr/bin/env python3
"""Generate the bundled store snapshot for KwikFinder.

Merges three sources published by Kwik Trip:
  1. Store locator API (kwiktrip.com/storelistproxy.php) — all stores with coordinates.
  2. Store details API (kwiktrip.com/storeinformationsproxy.php) — per-store fuel
     types/prices, amenities (scale, DEF, showers, truck parking, ...), and hours.
  3. Maps & Downloads PDFs (kwiktrip.com/maps-downloads) — the only published
     sources for family restrooms and KwikCharge EV charging locations.

Output: KwikFinder/Resources/stores_snapshot.json (ships inside the app bundle;
the app refreshes store details live at runtime and uses this as its offline
baseline and as the sole source for family-restroom / EV flags).

Usage:
  python3 Scripts/generate_dataset.py            # writes the snapshot
  python3 Scripts/generate_dataset.py --check    # parse PDFs only, print stats

Requires: requests-free stdlib networking (urllib) + pypdf for PDF parsing.
"""

import io
import json
import re
import sys
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

BASE = "https://www.kwiktrip.com"
STORE_LIST_URL = f"{BASE}/storelistproxy.php"
STORE_INFO_URL = f"{BASE}/storeinformationsproxy.php?ids={{ids}}"
MAPS_DOWNLOADS_URL = f"{BASE}/maps-downloads"

# The maps-downloads page links these with dated upload paths that change on
# each revision, so they are discovered from the page HTML at run time.
FAMILY_RESTROOM_LINK = re.compile(r'href="([^"]*Family-Restroom[^"]*\.pdf)"', re.I)
EV_CHARGING_LINK = re.compile(r'href="([^"]*kwikcharge[^"]*\.pdf)"', re.I)

STATE_CODES = r"(?:WI|MN|IA|IL|MI|SD)"
BATCH_SIZE = 10  # the details endpoint rejects requests with more than 10 ids
OUTPUT = Path(__file__).resolve().parent.parent / "KwikFinder" / "Resources" / "stores_snapshot.json"

UA = {"User-Agent": "KwikFinder dataset generator (personal project)"}


def fetch(url: str, retries: int = 3) -> bytes:
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=60) as resp:
                return resp.read()
        except Exception as e:
            if attempt == retries - 1:
                raise
            wait = 2 ** (attempt + 1)
            print(f"  retry {url} in {wait}s ({e})", file=sys.stderr)
            time.sleep(wait)
    raise RuntimeError("unreachable")


def fetch_json(url: str):
    return json.loads(fetch(url))


def pdf_text(data: bytes) -> str:
    from pypdf import PdfReader

    reader = PdfReader(io.BytesIO(data))
    return "\n".join(page.extract_text() or "" for page in reader.pages)


def discover_pdf_urls() -> tuple[str, str]:
    html = fetch(MAPS_DOWNLOADS_URL).decode("utf-8", errors="replace")
    fam = FAMILY_RESTROOM_LINK.search(html)
    ev = EV_CHARGING_LINK.search(html)
    if not fam or not ev:
        raise RuntimeError("Could not find Family Restroom / KwikCharge PDF links on maps-downloads page")
    to_abs = lambda href: href if href.startswith("http") else BASE + href
    return to_abs(fam.group(1)), to_abs(ev.group(1))


def parse_family_restrooms(text: str) -> set[int]:
    """The PDF is a multi-column table of (site number, city, state) triples."""
    triples = re.findall(rf"(\d{{1,4}})\s+([A-Z][A-Z .'\-/]*?)\s+({STATE_CODES})\b", text)
    return {int(num) for num, _city, _state in triples}


def parse_ev_locations(text: str) -> dict[int, str]:
    """Table rows: 'StoreNumber Address City State Status' with Status Open/Coming Soon."""
    result: dict[int, str] = {}
    row = re.compile(rf"^\s*(\d{{1,4}})\s+.*\b{STATE_CODES}\s+(Open|Coming Soon)\s*$")
    for line in text.splitlines():
        m = row.match(line)
        if m:
            result[int(m.group(1))] = "open" if m.group(2) == "Open" else "comingSoon"
    return result


def normalize_detail(detail: dict, family: set[int], ev: dict[int, str]) -> dict:
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
    amenities = []
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
        "open24Hours": bool(detail.get("open24Hours")),
        "hours": detail.get("hours"),
        "fuels": fuels,
        "amenities": sorted(set(amenities)),
        "truckParkingSpaces": truck_parking,
        "familyRestroom": number in family,
        "evCharging": ev.get(number),
    }


def main() -> None:
    check_only = "--check" in sys.argv

    print("Discovering PDF URLs from maps-downloads page...")
    fam_url, ev_url = discover_pdf_urls()
    print(f"  family restrooms: {fam_url}")
    print(f"  ev charging:      {ev_url}")

    family = parse_family_restrooms(pdf_text(fetch(fam_url)))
    ev = parse_ev_locations(pdf_text(fetch(ev_url)))
    print(f"  parsed {len(family)} family-restroom stores, {len(ev)} EV stores "
          f"({sum(1 for v in ev.values() if v == 'open')} open)")
    if check_only:
        return

    print("Fetching store list...")
    store_list = fetch_json(STORE_LIST_URL)["stores"]
    ids = [s["id"] for s in store_list]
    print(f"  {len(ids)} stores")

    stores: list[dict] = []
    seen: set[int] = set()
    for i in range(0, len(ids), BATCH_SIZE):
        batch = ids[i : i + BATCH_SIZE]
        data = fetch_json(STORE_INFO_URL.format(ids=",".join(map(str, batch))))
        for detail in data.get("stores", []):
            record = normalize_detail(detail, family, ev)
            if record["latitude"] is None or record["id"] in seen:
                continue
            seen.add(record["id"])
            stores.append(record)
        print(f"  details {min(i + BATCH_SIZE, len(ids))}/{len(ids)}", end="\r")
        time.sleep(0.3)
    print()

    # The details endpoint occasionally omits a store; keep it with list-level
    # data so the map is complete even if amenities are unknown.
    for s in store_list:
        if s["id"] in seen:
            continue
        addr = s.get("address") or {}
        stores.append({
            "id": s["id"],
            "name": s.get("name"),
            "latitude": s.get("latitude"),
            "longitude": s.get("longitude"),
            "address1": addr.get("address1"),
            "city": addr.get("city"),
            "county": None,
            "state": addr.get("state"),
            "zip": str(addr.get("zip") or ""),
            "phone": s.get("phone"),
            "open24Hours": False,
            "hours": None,
            "fuels": [],
            "amenities": [],
            "truckParkingSpaces": 0,
            "familyRestroom": s["id"] in family,
            "evCharging": ev.get(s["id"]),
        })

    stores.sort(key=lambda s: s["id"])
    snapshot = {
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "source": "kwiktrip.com store locator API + maps-downloads PDFs",
        "familyRestroomSource": fam_url,
        "evChargingSource": ev_url,
        "stores": stores,
    }

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(snapshot, indent=1))
    fam_matched = sum(1 for s in stores if s["familyRestroom"])
    ev_matched = sum(1 for s in stores if s["evCharging"])
    print(f"Wrote {OUTPUT} ({len(stores)} stores, "
          f"{fam_matched} with family restrooms, {ev_matched} with EV charging)")


if __name__ == "__main__":
    main()
