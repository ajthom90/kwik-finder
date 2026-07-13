"""Discover and parse Kwik Trip family-restroom and KwikCharge EV PDFs.

Regexes and parse logic ported from ``Scripts/generate_dataset.py``.
"""

from __future__ import annotations

import io
import re
from typing import Any

from app.ingest.http import fetch_bytes
from app import repository as repo

BASE = "https://www.kwiktrip.com"
MAPS_DOWNLOADS_URL = f"{BASE}/maps-downloads"

# The maps-downloads page links these with dated upload paths that change on
# each revision, so they are discovered from the page HTML at run time.
FAMILY_RESTROOM_LINK = re.compile(r'href="([^"]*Family-Restroom[^"]*\.pdf)"', re.I)
EV_CHARGING_LINK = re.compile(r'href="([^"]*kwikcharge[^"]*\.pdf)"', re.I)

STATE_CODES = r"(?:WI|MN|IA|IL|MI|SD)"


def pdf_text(data: bytes) -> str:
    from pypdf import PdfReader

    reader = PdfReader(io.BytesIO(data))
    return "\n".join(page.extract_text() or "" for page in reader.pages)


def discover_pdf_urls() -> tuple[str, str]:
    """Return absolute (family_restroom_pdf_url, ev_charging_pdf_url) from maps-downloads."""
    html = fetch_bytes(MAPS_DOWNLOADS_URL).decode("utf-8", errors="replace")
    fam = FAMILY_RESTROOM_LINK.search(html)
    ev = EV_CHARGING_LINK.search(html)
    if not fam or not ev:
        raise RuntimeError(
            "Could not find Family Restroom / KwikCharge PDF links on maps-downloads page"
        )

    def to_abs(href: str) -> str:
        return href if href.startswith("http") else BASE + href

    return to_abs(fam.group(1)), to_abs(ev.group(1))


def parse_family_restrooms(text: str) -> set[int]:
    """The PDF is a multi-column table of (site number, city, state) triples."""
    triples = re.findall(
        rf"(\d{{1,4}})\s+([A-Z][A-Z .'\-/]*?)\s+({STATE_CODES})\b",
        text,
    )
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


def refresh_pdf_flags() -> dict[str, Any]:
    """Discover PDF URLs, fetch/parse, apply flags to the DB, return stats.

    Uses ``repository.apply_pdf_flags`` which also sets meta ``pdfEnrichedAt``.
    """
    fam_url, ev_url = discover_pdf_urls()
    family = parse_family_restrooms(pdf_text(fetch_bytes(fam_url)))
    ev = parse_ev_locations(pdf_text(fetch_bytes(ev_url)))
    repo.apply_pdf_flags(family, ev)
    return {
        "familyUrl": fam_url,
        "evUrl": ev_url,
        "familyCount": len(family),
        "evCount": len(ev),
        "evOpenCount": sum(1 for v in ev.values() if v == "open"),
    }
