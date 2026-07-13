"""Tests for PDF discovery helpers and family/EV text parsing (no live network)."""

from __future__ import annotations

from pathlib import Path

import pytest

from app.ingest import pdfs

FIXTURES = Path(__file__).parent / "fixtures"


@pytest.fixture()
def family_text() -> str:
    return (FIXTURES / "family_restroom_sample.txt").read_text()


@pytest.fixture()
def ev_text() -> str:
    return (FIXTURES / "ev_charging_sample.txt").read_text()


def test_parse_family_restrooms_extracts_store_ids(family_text):
    ids = pdfs.parse_family_restrooms(family_text)
    assert ids == {123, 45, 9999, 12, 88, 201, 450, 775}


def test_parse_family_restrooms_empty():
    assert pdfs.parse_family_restrooms("") == set()
    assert pdfs.parse_family_restrooms("no store rows here") == set()


def test_parse_ev_locations_open_and_coming_soon(ev_text):
    result = pdfs.parse_ev_locations(ev_text)
    assert result == {
        101: "open",
        202: "comingSoon",
        404: "open",
        55: "comingSoon",
        9001: "open",
    }
    # Lines without trailing Open/Coming Soon are ignored
    assert 303 not in result


def test_parse_ev_locations_empty():
    assert pdfs.parse_ev_locations("") == {}
    assert pdfs.parse_ev_locations("header only\n") == {}


def test_discover_pdf_urls_from_maps_html(monkeypatch):
    html = """
    <html><body>
      <a href="/wordpress/wp-content/uploads/2024/03/Family-Restroom-List.pdf">Family</a>
      <a href="https://www.kwiktrip.com/wordpress/wp-content/uploads/2026/07/kwikcharge_maplist.pdf">EV</a>
    </body></html>
    """

    def fake_fetch(url: str, **kwargs) -> bytes:
        assert "maps-downloads" in url
        return html.encode("utf-8")

    monkeypatch.setattr(pdfs, "fetch_bytes", fake_fetch)
    fam_url, ev_url = pdfs.discover_pdf_urls()
    assert fam_url == "https://www.kwiktrip.com/wordpress/wp-content/uploads/2024/03/Family-Restroom-List.pdf"
    assert ev_url == "https://www.kwiktrip.com/wordpress/wp-content/uploads/2026/07/kwikcharge_maplist.pdf"


def test_discover_pdf_urls_raises_when_missing(monkeypatch):
    monkeypatch.setattr(pdfs, "fetch_bytes", lambda url, **kw: b"<html>no pdfs</html>")
    with pytest.raises(RuntimeError, match="Could not find"):
        pdfs.discover_pdf_urls()


def test_refresh_pdf_flags_applies_parsed_sets(tmp_path, monkeypatch, family_text, ev_text):
    from app.config import get_settings
    from app import repository as repo

    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    get_settings.cache_clear()
    repo.init_db()

    base = {
        "id": 123,
        "name": "KWIK TRIP #123",
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
    repo.upsert_stores([
        base,
        {**base, "id": 101},
        {**base, "id": 202},
    ])

    monkeypatch.setattr(
        pdfs,
        "discover_pdf_urls",
        lambda: ("https://example.com/family.pdf", "https://example.com/ev.pdf"),
    )

    def fake_fetch(url: str, **kwargs) -> bytes:
        # Bytes are only passed to pdf_text; we stub that next.
        return b"%PDF-fake"

    monkeypatch.setattr(pdfs, "fetch_bytes", fake_fetch)

    def fake_pdf_text(data: bytes) -> str:
        # Alternate by call order: family first, then EV (discover order).
        if not hasattr(fake_pdf_text, "n"):
            fake_pdf_text.n = 0  # type: ignore[attr-defined]
        fake_pdf_text.n += 1  # type: ignore[attr-defined]
        return family_text if fake_pdf_text.n == 1 else ev_text  # type: ignore[attr-defined]

    monkeypatch.setattr(pdfs, "pdf_text", fake_pdf_text)

    stats = pdfs.refresh_pdf_flags()
    assert stats["familyCount"] == 8
    assert stats["evCount"] == 5
    assert stats["evOpenCount"] == 3
    assert stats["familyUrl"] == "https://example.com/family.pdf"
    assert stats["evUrl"] == "https://example.com/ev.pdf"

    s123 = repo.get_store(123)
    assert s123 is not None
    assert s123["familyRestroom"] is True
    assert s123["evCharging"] is None

    s101 = repo.get_store(101)
    assert s101 is not None
    assert s101["familyRestroom"] is False
    assert s101["evCharging"] == "open"

    s202 = repo.get_store(202)
    assert s202 is not None
    assert s202["evCharging"] == "comingSoon"

    meta = repo.get_refresh_meta()
    assert meta["pdfEnrichedAt"] is not None
