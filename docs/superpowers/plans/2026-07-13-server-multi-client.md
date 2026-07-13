# Server Multi-Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure KwikFinder into a monorepo with a FastAPI caching server (sole Kwik Trip consumer), an iOS client that talks only to that server, and a feature-parity Android client (Compose + OSM).

**Architecture:** Server periodically ingests Kwik Trip store list/details JSON and Maps & Downloads PDFs into SQLite, exposes `GET /v1/meta` and `GET /v1/stores`. Mobile apps fetch the full catalog, cache it on disk, and filter/sort locally. See `docs/superpowers/specs/2026-07-13-server-multi-client-design.md`.

**Tech Stack:** Python 3.12+, FastAPI, uvicorn, APScheduler, SQLite (stdlib/`sqlite3`), pypdf, httpx; iOS SwiftUI/MapKit; Android Kotlin, Jetpack Compose, Material 3, OSM (osmdroid or MapLibre OSM tiles).

## Global Constraints

- Clients must never call `kwiktrip.com` after migration.
- Only the server talks to Kwik Trip; max 10 store IDs per details request.
- Docker single-container deploy; SQLite on a named volume under `DATA_DIR` (default `/data`).
- API base path `/v1`; no auth in v1; CORS configurable via `CORS_ORIGINS`.
- `features` object is computed on the server; clients filter on that + headline fields.
- iOS maps: MapKit. Android maps: OpenStreetMap (no Google Maps).
- Client offline: last successful `GET /v1/stores` on disk; cold install needs network once.
- Port PDF logic from `Scripts/generate_dataset.py`; preserve family/EV flags across detail merges.
- Fuel prices are informational only.

---

## File Structure (target)

```
kwik-finder/
├── docker-compose.yml
├── README.md
├── docs/
│   ├── api/openapi.yaml
│   ├── superpowers/specs/2026-07-13-server-multi-client-design.md
│   └── superpowers/plans/2026-07-13-server-multi-client.md
├── server/
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── requirements-dev.txt
│   ├── pyproject.toml          # optional; pytest config ok here or pytest.ini
│   ├── app/
│   │   ├── __init__.py
│   │   ├── main.py             # FastAPI app factory, CORS, lifespan (scheduler)
│   │   ├── config.py           # env settings
│   │   ├── db.py               # SQLite connection helpers, schema init
│   │   ├── models.py           # dataclasses / typed dicts for API responses
│   │   ├── features.py         # amenity/fuel → features flags
│   │   ├── api/
│   │   │   ├── __init__.py
│   │   │   ├── health.py
│   │   │   └── stores.py
│   │   ├── ingest/
│   │   │   ├── __init__.py
│   │   │   ├── http.py         # fetch with UA + retries
│   │   │   ├── kwiktrip.py     # list + details clients
│   │   │   ├── pdfs.py         # discover + parse family/EV PDFs
│   │   │   └── normalize.py    # raw detail → store row
│   │   ├── repository.py       # CRUD against SQLite
│   │   └── jobs.py             # scheduled refresh jobs
│   └── tests/
│       ├── conftest.py
│       ├── fixtures/           # sample JSON/PDF text
│       ├── test_features.py
│       ├── test_pdfs.py
│       ├── test_normalize.py
│       ├── test_api.py
│       └── test_repository.py
├── ios/
│   ├── project.yml
│   ├── KwikFinder.xcodeproj
│   └── KwikFinder/             # moved from root KwikFinder/
│       ├── Services/
│       │   ├── KwikFinderAPI.swift   # replaces KwikTripAPI.swift
│       │   ├── StoreRepository.swift
│       │   └── LocationService.swift
│       ├── Models/
│       └── Views/
└── android/
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── gradle.properties
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/ajthom90/kwikfinder/
            │   ├── KwikFinderApp.kt
            │   ├── data/           # API, cache, repository, models
            │   ├── ui/             # Compose screens
            │   └── location/
            └── res/
```

---

### Task 1: Server skeleton, config, health, Docker

**Files:**
- Create: `server/requirements.txt`
- Create: `server/requirements-dev.txt`
- Create: `server/app/__init__.py`
- Create: `server/app/config.py`
- Create: `server/app/main.py`
- Create: `server/app/api/__init__.py`
- Create: `server/app/api/health.py`
- Create: `server/Dockerfile`
- Create: `docker-compose.yml`
- Create: `server/tests/conftest.py`
- Create: `server/tests/test_health.py`
- Create: `server/pytest.ini`

**Interfaces:**
- Consumes: none
- Produces: `Settings` from `app.config.get_settings()`; FastAPI app with `GET /v1/health`; Docker image listening on `8080`

- [ ] **Step 1: Create dependency files**

`server/requirements.txt`:
```
fastapi==0.115.6
uvicorn[standard]==0.34.0
httpx==0.28.1
apscheduler==3.10.4
pypdf==5.1.0
pydantic-settings==2.7.0
```

`server/requirements-dev.txt`:
```
-r requirements.txt
pytest==8.3.4
pytest-asyncio==0.25.0
```

`server/pytest.ini`:
```ini
[pytest]
asyncio_mode = auto
testpaths = tests
```

- [ ] **Step 2: Write failing health test**

`server/tests/conftest.py`:
```python
import os
import tempfile
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

# Ensure tests use a temp data dir before app import
@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    # Clear settings cache if using lru_cache
    from app.config import get_settings
    get_settings.cache_clear()
    from app.main import create_app
    app = create_app(start_scheduler=False)
    with TestClient(app) as c:
        yield c
```

`server/tests/test_health.py`:
```python
def test_health_ok(client):
    r = client.get("/v1/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] in ("ok", "degraded", "starting")
    assert "listRefreshedAt" in body
    assert "detailsRefreshedAt" in body
    assert "pdfEnrichedAt" in body
```

- [ ] **Step 3: Run test — expect fail**

```bash
cd server && python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
PYTHONPATH=. pytest tests/test_health.py -v
```

Expected: FAIL (module `app` not found or import error)

- [ ] **Step 4: Implement config, app, health**

`server/app/config.py`:
```python
from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    data_dir: str = "./data"
    list_refresh_seconds: int = 900
    details_refresh_seconds: int = 1200
    pdf_refresh_seconds: int = 86400
    cors_origins: str = "*"
    log_level: str = "INFO"
    host: str = "0.0.0.0"
    port: int = 8080


@lru_cache
def get_settings() -> Settings:
    return Settings()
```

`server/app/api/health.py`:
```python
from fastapi import APIRouter

from app.repository import get_refresh_meta

router = APIRouter(tags=["health"])


@router.get("/v1/health")
def health():
    meta = get_refresh_meta()
    status = "ok"
    if meta.get("listRefreshedAt") is None:
        status = "starting"
    elif meta.get("degraded"):
        status = "degraded"
    return {
        "status": status,
        "listRefreshedAt": meta.get("listRefreshedAt"),
        "detailsRefreshedAt": meta.get("detailsRefreshedAt"),
        "pdfEnrichedAt": meta.get("pdfEnrichedAt"),
        "lastError": meta.get("lastError"),
    }
```

For Task 1 only, stub `app/repository.py`:
```python
def get_refresh_meta() -> dict:
    return {
        "listRefreshedAt": None,
        "detailsRefreshedAt": None,
        "pdfEnrichedAt": None,
        "degraded": False,
        "lastError": None,
    }


def init_db() -> None:
    pass
```

`server/app/main.py`:
```python
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import health
from app.config import get_settings
from app.repository import init_db


def create_app(*, start_scheduler: bool = True) -> FastAPI:
    settings = get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        init_db()
        scheduler = None
        if start_scheduler:
            from app.jobs import start_scheduler as _start
            scheduler = _start()
        yield
        if scheduler is not None:
            scheduler.shutdown(wait=False)

    app = FastAPI(title="KwikFinder API", version="1.0.0", lifespan=lifespan)
    origins = [o.strip() for o in settings.cors_origins.split(",") if o.strip()]
    app.add_middleware(
        CORSMiddleware,
        allow_origins=origins if origins != ["*"] else ["*"],
        allow_methods=["GET"],
        allow_headers=["*"],
    )
    app.include_router(health.router)
    return app


# For uvicorn: app.main:app — use create_app with scheduler in production
app = create_app(start_scheduler=True)
```

Stub `server/app/jobs.py` for import safety:
```python
def start_scheduler():
    class _Noop:
        def shutdown(self, wait=False):
            pass
    return _Noop()
```

- [ ] **Step 5: Dockerfile + compose**

`server/Dockerfile`:
```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
ENV DATA_DIR=/data
ENV PYTHONPATH=/app
EXPOSE 8080
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
```

`docker-compose.yml` (repo root):
```yaml
services:
  api:
    build: ./server
    ports:
      - "8080:8080"
    environment:
      DATA_DIR: /data
      CORS_ORIGINS: "*"
    volumes:
      - kwikfinder-data:/data
    restart: unless-stopped

volumes:
  kwikfinder-data:
```

- [ ] **Step 6: Run tests and smoke Docker**

```bash
cd server && source .venv/bin/activate
PYTHONPATH=. pytest tests/test_health.py -v
# Expected: PASS

cd .. && docker compose build && docker compose up -d
curl -s http://localhost:8080/v1/health | head
docker compose down
```

- [ ] **Step 7: Commit**

```bash
git add server docker-compose.yml
git commit -m "feat(server): scaffold FastAPI health endpoint and Docker"
```

---

### Task 2: SQLite schema and repository

**Files:**
- Create: `server/app/db.py`
- Modify: `server/app/repository.py` (full implementation)
- Create: `server/tests/test_repository.py`

**Interfaces:**
- Consumes: `Settings.data_dir`
- Produces:
  - `init_db() -> None`
  - `upsert_stores(stores: list[dict]) -> None`
  - `get_all_stores() -> list[dict]`
  - `get_store(store_id: int) -> dict | None`
  - `get_refresh_meta() -> dict`
  - `set_refresh_meta(**kwargs) -> None`
  - `apply_pdf_flags(family: set[int], ev: dict[int, str]) -> None`

- [ ] **Step 1: Write repository tests**

`server/tests/test_repository.py`:
```python
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
```

- [ ] **Step 2: Run — expect fail**

```bash
cd server && source .venv/bin/activate
PYTHONPATH=. pytest tests/test_repository.py -v
```

Expected: FAIL

- [ ] **Step 3: Implement `db.py` + `repository.py`**

Schema (SQLite):

```sql
CREATE TABLE IF NOT EXISTS stores (
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  latitude REAL NOT NULL,
  longitude REAL NOT NULL,
  address1 TEXT,
  city TEXT,
  county TEXT,
  state TEXT,
  zip TEXT,
  phone TEXT,
  open24_hours INTEGER NOT NULL DEFAULT 0,
  hours_json TEXT NOT NULL DEFAULT '[]',
  fuels_json TEXT NOT NULL DEFAULT '[]',
  amenities_json TEXT NOT NULL DEFAULT '[]',
  truck_parking_spaces INTEGER NOT NULL DEFAULT 0,
  family_restroom INTEGER NOT NULL DEFAULT 0,
  ev_charging TEXT,
  features_json TEXT NOT NULL DEFAULT '{}',
  updated_at TEXT
);

CREATE TABLE IF NOT EXISTS meta (
  key TEXT PRIMARY KEY,
  value TEXT
);
```

Implementation notes:
- Store JSON columns with `json.dumps` / `json.loads`.
- `upsert_stores(..., preserve_pdf_flags=True)`: for each row, if existing has `family_restroom` or `ev_charging`, keep them when incoming is false/null.
- `apply_pdf_flags`: `UPDATE stores SET family_restroom=..., ev_charging=...` by id; set meta `pdfEnrichedAt`.
- `get_refresh_meta` / `set_refresh_meta` read/write keys: `listRefreshedAt`, `detailsRefreshedAt`, `pdfEnrichedAt`, `dataVersion`, `lastError`, `degraded`.
- `init_db` creates `DATA_DIR`, opens `kwikfinder.db`, runs schema.
- Return store dicts with **camelCase API keys** (`open24Hours`, `truckParkingSpaces`, `familyRestroom`, `evCharging`) matching the design doc.

- [ ] **Step 4: Tests pass**

```bash
PYTHONPATH=. pytest tests/test_repository.py -v
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/app/db.py server/app/repository.py server/tests/test_repository.py
git commit -m "feat(server): SQLite store catalog repository"
```

---

### Task 3: Feature mapping (port iOS `Store.has`)

**Files:**
- Create: `server/app/features.py`
- Create: `server/tests/test_features.py`

**Interfaces:**
- Consumes: fuels list + amenities list + open24 + family + ev
- Produces: `compute_features(fuels, amenities, *, open24_hours, family_restroom, ev_charging) -> dict[str, bool | int | None]`

Feature keys (bool unless noted):  
`diesel`, `premiumDiesel`, `def`, `e85`, `cng`, `noEthanolGas`, `unleaded88`, `scale`, `showers`, `truckParking`, `truckParkingStalls` (int), `transFlo`, `fleetCards`, `truckFriendly`, `carWash`, `atm`, `bitcoinATM`, `wifi`, `restaurant`

Mapping (must match iOS `Store.has` in `KwikFinder/Models/Store.swift`):

| Key | Rule |
| --- | --- |
| diesel | fuel type prefix `DIESEL #` or `PREMIUM DIESEL` |
| premiumDiesel | `PREMIUM DIESEL` |
| def | `DIESEL EXHAUST FLUID` |
| e85 | `E-85` |
| cng | `COMPRESSED NATURAL GAS` |
| noEthanolGas | `UNLEADED 87 (0% ETH)` |
| unleaded88 | `UNLEADED 88` |
| scale | amenity `SCALE` |
| showers | `SHOWERS` |
| truckParking | `TRUCK-PARKING` |
| truckParkingStalls | quantity from normalize (pass in) |
| transFlo | `TRANS-FLO` |
| fleetCards | `TRENDAR` |
| truckFriendly | `TRUCK-FRIENDLY` |
| carWash | `CAR-WASH` |
| atm | exact `ATM` only |
| bitcoinATM | `BITCOIN ATM` |
| wifi | `WI-FI` |
| restaurant | `RESTAURANT` |

- [ ] **Step 1: Write tests**

```python
from app.features import compute_features

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
```

- [ ] **Step 2: Implement `features.py` and pass tests**

- [ ] **Step 3: Commit**

```bash
git add server/app/features.py server/tests/test_features.py
git commit -m "feat(server): compute store feature flags for filters"
```

---

### Task 4: Kwik Trip HTTP ingest + normalize

**Files:**
- Create: `server/app/ingest/__init__.py`
- Create: `server/app/ingest/http.py`
- Create: `server/app/ingest/kwiktrip.py`
- Create: `server/app/ingest/normalize.py`
- Create: `server/tests/fixtures/store_detail_sample.json`
- Create: `server/tests/test_normalize.py`

**Interfaces:**
- `fetch_bytes(url: str) -> bytes`
- `fetch_json(url: str) -> Any`
- `fetch_store_list() -> list[dict]`  # raw list entries
- `fetch_store_details(ids: list[int]) -> list[dict]`  # chunks of 10
- `normalize_detail(detail: dict, *, family: set[int], ev: dict[int, str]) -> dict`  
  Output includes `features` via `compute_features`.

Port URL constants and batch size from `Scripts/generate_dataset.py` / iOS `KwikTripAPI.swift`:
- `https://www.kwiktrip.com/storelistproxy.php`
- `https://www.kwiktrip.com/storeinformationsproxy.php?ids=`
- User-Agent: `KwikFinder server (personal project)`
- Sleep ~0.3s between detail batches in full refresh

- [ ] **Step 1: Fixture + normalize tests from real shape**

Use a minimal fixture matching Kwik Trip detail fields (`storeNumber`, `address`, `fuel` with `currentPrice`, `properties` with `hasProperty`/`name`/`quantity`, `hours`, `open24Hours`, `phone`, `name`).

Assert `normalize_detail` produces API camelCase fields + `features`.

- [ ] **Step 2: Implement http, kwiktrip, normalize**

Port `normalize_detail` body from `Scripts/generate_dataset.py` lines 101–148, then attach `features=compute_features(...)`.

- [ ] **Step 3: Unit tests pass (no live network required)**

```bash
PYTHONPATH=. pytest tests/test_normalize.py -v
```

- [ ] **Step 4: Commit**

```bash
git add server/app/ingest server/tests/test_normalize.py server/tests/fixtures
git commit -m "feat(server): Kwik Trip list/details ingest and normalize"
```

---

### Task 5: PDF discovery and parsing

**Files:**
- Create: `server/app/ingest/pdfs.py`
- Create: `server/tests/test_pdfs.py`
- Create: `server/tests/fixtures/family_restroom_sample.txt`
- Create: `server/tests/fixtures/ev_charging_sample.txt`

**Interfaces:**
- `discover_pdf_urls() -> tuple[str, str]`  # family, ev (live HTTP)
- `parse_family_restrooms(text: str) -> set[int]`
- `parse_ev_locations(text: str) -> dict[int, str]`  # open | comingSoon
- `refresh_pdf_flags() -> dict`  # discover, fetch, parse, `apply_pdf_flags`, set meta

Port regexes exactly from `Scripts/generate_dataset.py`:
- `FAMILY_RESTROOM_LINK`, `EV_CHARGING_LINK`, `STATE_CODES`, `parse_family_restrooms`, `parse_ev_locations`

- [ ] **Step 1: Tests with fixture text** (no network)

Copy sample lines into fixture text files matching the PDF extract patterns; assert store IDs.

- [ ] **Step 2: Implement `pdfs.py`**

- [ ] **Step 3: Pass tests; optional manual `--check` path later**

- [ ] **Step 4: Commit**

```bash
git add server/app/ingest/pdfs.py server/tests/test_pdfs.py server/tests/fixtures
git commit -m "feat(server): parse family restroom and EV PDFs"
```

---

### Task 6: Jobs — scheduled full refresh

**Files:**
- Modify: `server/app/jobs.py`
- Create: `server/tests/test_jobs.py` (mock httpx/fetch)

**Interfaces:**
- `refresh_store_list() -> int`  # upserts skeleton rows from list if missing; sets listRefreshedAt
- `refresh_store_details(max_batches: int | None = None) -> int`  # details for IDs needing refresh
- `refresh_pdfs() -> None`
- `start_scheduler() -> BackgroundScheduler`  # intervals from Settings
- On app lifespan: run one background thread or async task for initial refresh (list → details batch → pdf if empty)

Behavior:
1. `refresh_store_list`: fetch list; for each store with lat/lon, upsert minimal record if new; update names/coords for existing; `set_refresh_meta(listRefreshedAt=now, dataVersion=now)`.
2. `refresh_store_details`: select store IDs ordered by oldest `updated_at` (nulls first); batch 10; normalize with current PDF sets from DB (read family/ev from rows or separate); `upsert_stores(..., preserve_pdf_flags=True)`; set `detailsRefreshedAt` and `dataVersion`.
3. `refresh_pdfs`: call `refresh_pdf_flags`; recompute features for affected stores if needed (family/ev are headline fields; filters use them via client headline + features — recompute features after flag apply).
4. On failure: `set_refresh_meta(degraded=True, lastError=str(e))`; do not wipe DB.
5. Scheduler intervals from env; also schedule details more frequently in smaller batches if preferred (e.g. every 2 min, 20 batches).

- [ ] **Step 1: Test with monkeypatched `fetch_json` returning fixtures**

- [ ] **Step 2: Implement jobs**

- [ ] **Step 3: Pass tests**

- [ ] **Step 4: Commit**

```bash
git add server/app/jobs.py server/tests/test_jobs.py
git commit -m "feat(server): scheduled list, details, and PDF refresh jobs"
```

---

### Task 7: Public `/v1` stores API + OpenAPI

**Files:**
- Create: `server/app/api/stores.py`
- Modify: `server/app/main.py` (include router)
- Create: `server/tests/test_api.py`
- Create: `docs/api/openapi.yaml`

**Interfaces:**
- `GET /v1/meta` → `{ dataVersion, storeCount, listRefreshedAt, detailsRefreshedAt, pdfEnrichedAt }`
- `GET /v1/stores` → `{ meta, stores: [...] }`
- `GET /v1/stores/{id}` → single store or 404

- [ ] **Step 1: API tests with preloaded repository**

```python
def test_stores_empty(client):
    r = client.get("/v1/stores")
    assert r.status_code == 200
    assert r.json()["stores"] == []
    assert "meta" in r.json()

def test_meta_and_get_one(client, tmp_path, monkeypatch):
    # seed via repository then GET
    ...
```

- [ ] **Step 2: Implement routes**

```python
# stores.py sketch
@router.get("/v1/meta")
def meta():
    m = get_refresh_meta()
    stores = get_all_stores()
    return {
        "dataVersion": m.get("dataVersion"),
        "storeCount": len(stores),
        "listRefreshedAt": m.get("listRefreshedAt"),
        "detailsRefreshedAt": m.get("detailsRefreshedAt"),
        "pdfEnrichedAt": m.get("pdfEnrichedAt"),
    }

@router.get("/v1/stores")
def list_stores():
    m = meta()
    return {"meta": m, "stores": get_all_stores()}
```

- [ ] **Step 3: Write `docs/api/openapi.yaml`** matching JSON field names from design doc exactly

- [ ] **Step 4: Full server test suite**

```bash
cd server && source .venv/bin/activate
PYTHONPATH=. pytest -v
```

Expected: all PASS

- [ ] **Step 5: Manual live smoke (optional, network)**

```bash
docker compose up --build -d
# wait ~1–2 min for first ingest
curl -s http://localhost:8080/v1/meta
curl -s http://localhost:8080/v1/stores | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['meta'], len(d['stores']))"
```

- [ ] **Step 6: Commit**

```bash
git add server/app/api/stores.py server/app/main.py server/tests/test_api.py docs/api/openapi.yaml
git commit -m "feat(server): expose /v1 meta and stores API with OpenAPI"
```

---

### Task 8: Move iOS into `ios/` monorepo path

**Files:**
- Move: `KwikFinder/` → `ios/KwikFinder/`
- Move: `project.yml` → `ios/project.yml`
- Move: `KwikFinder.xcodeproj` → regenerate under `ios/`
- Modify: `ios/project.yml` paths if needed
- Modify: root `README.md` (point to monorepo; can be partial)

**Interfaces:** Produces buildable iOS project at `ios/` with no behavior change yet.

- [ ] **Step 1: Move directories**

```bash
mkdir -p ios
git mv KwikFinder ios/KwikFinder
git mv project.yml ios/project.yml
# Remove old xcodeproj from root after regenerating
rm -rf KwikFinder.xcodeproj
```

- [ ] **Step 2: Update `ios/project.yml`**

Ensure `sources: - path: KwikFinder` still valid relative to `ios/`. Bundle id unchanged: `com.ajthom90.KwikFinder`.

- [ ] **Step 3: Regenerate and build**

```bash
cd ios && xcodegen generate
xcodebuild -project KwikFinder.xcodeproj -scheme KwikFinder \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -configuration Debug build CODE_SIGNING_ALLOWED=NO
```

Expected: BUILD SUCCEEDED (adjust simulator name if needed)

- [ ] **Step 4: Commit**

```bash
git add ios README.md
git commit -m "refactor: move iOS app under ios/ monorepo path"
```

---

### Task 9: iOS KwikFinder API client + models for server JSON

**Files:**
- Create: `ios/KwikFinder/Services/KwikFinderAPI.swift`
- Create: `ios/KwikFinder/Models/StoreFeatures.swift` (or embed in Store)
- Modify: `ios/KwikFinder/Models/Store.swift` — decode `features`; keep `has(_:)` preferring `features` when present
- Delete: `ios/KwikFinder/Services/KwikTripAPI.swift` (after switch)
- Create: `ios/KwikFinder/Config/APIConfig.swift` — base URL

**Interfaces:**
- `APIConfig.baseURL` — Debug default `http://localhost:8080` or machine LAN; Release from Info.plist key `KwikFinderAPIBaseURL`
- `KwikFinderAPI.fetchMeta() async throws -> CatalogMeta`
- `KwikFinderAPI.fetchStores() async throws -> CatalogResponse`  // meta + stores

- [ ] **Step 1: Add `APIConfig` and `KwikFinderAPI` decoding types matching OpenAPI**

```swift
struct CatalogMeta: Codable, Hashable {
    let dataVersion: String?
    let storeCount: Int?
    let listRefreshedAt: String?
    let detailsRefreshedAt: String?
    let pdfEnrichedAt: String?
}

struct CatalogResponse: Codable {
    let meta: CatalogMeta
    let stores: [Store]
}

struct StoreFeatureFlags: Codable, Hashable {
    var diesel: Bool?
    var premiumDiesel: Bool?
    // ... all keys from server
    var truckParkingStalls: Int?
}
```

Extend `Store` with `var features: StoreFeatureFlags?` and update `has(_:)` to use flags when non-nil, else fall back to legacy amenity/fuel checks during transition.

- [ ] **Step 2: ATS exception for local HTTP** (Debug)

In `project.yml` Info.plist keys, for local dev only allow arbitrary loads **or** document HTTPS reverse proxy. Prefer:

```yaml
INFOPLIST_KEY_NSAppTransportSecurity: ... 
```

Use `NSAllowsLocalNetworking` = YES for LAN HTTP to TrueNAS without fully disabling ATS.

- [ ] **Step 3: Build**

```bash
cd ios && xcodegen generate && xcodebuild ... build CODE_SIGNING_ALLOWED=NO
```

- [ ] **Step 4: Commit**

```bash
git add ios/KwikFinder
git commit -m "feat(ios): add KwikFinder server API client and feature flags"
```

---

### Task 10: iOS StoreRepository — disk cache, remove Kwik Trip + bundled snapshot

**Files:**
- Modify: `ios/KwikFinder/Services/StoreRepository.swift`
- Delete: `ios/KwikFinder/Resources/stores_snapshot.json` (stop shipping primary snapshot)
- Delete: `ios/KwikFinder/Services/KwikTripAPI.swift`
- Modify: UI status copy if needed (`StoreListView`)
- Modify: `ios/project.yml` if resource removed

**Interfaces:**
- On init: load `Caches/catalog.json` if present
- `refresh(force:)`: meta → if version differs or force, fetch stores → write cache → update `storesByID`
- `liveStatus`: snapshotOnly → rename mentally to `cached` / `refreshing` / `live` / `offline` (keep enum cases or rename carefully for UI)

- [ ] **Step 1: Implement disk cache helpers**

```swift
private var cacheURL: URL {
    FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("catalog.json")
}
```

- [ ] **Step 2: Rewrite refresh to use `KwikFinderAPI` only**

Remove list throttle constants aimed at Kwik Trip (or keep mild 30s debounce for meta only).

- [ ] **Step 3: Remove snapshot resource and dead code**

- [ ] **Step 4: Build + manual test against `docker compose up`**

- [ ] **Step 5: Commit**

```bash
git add ios/KwikFinder ios/project.yml
git commit -m "feat(ios): load catalog from KwikFinder server with disk cache"
```

---

### Task 11: Android project scaffold

**Files:**
- Create: entire `android/` Gradle project (Kotlin DSL)
- Package: `com.ajthom90.kwikfinder`
- Min SDK 26, target/compile 35
- Dependencies: Compose BOM, Navigation, lifecycle, Retrofit or Ktor, kotlinx.serialization, DataStore preferences, osmdroid (or MapLibre)

**Interfaces:** App launches to placeholder "KwikFinder" screen; builds via `./gradlew :app:assembleDebug`.

- [ ] **Step 1: Create Android app with Android Studio structure or `gradle` init**

Minimum:
- `MainActivity` + `KwikFinderApp` composable
- `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` permissions
- `usesCleartextTraffic=true` for debug LAN HTTP (or network security config for private IPs)

- [ ] **Step 2: Build**

```bash
cd android && ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android
git commit -m "feat(android): scaffold Compose app module"
```

---

### Task 12: Android data layer (API, cache, models, favorites)

**Files:**
- Create: `android/app/src/main/java/com/ajthom90/kwikfinder/data/Models.kt`
- Create: `.../data/KwikFinderApi.kt`
- Create: `.../data/CatalogCache.kt`
- Create: `.../data/StoreRepository.kt`
- Create: `.../data/FavoritesStore.kt`
- Create: `.../data/APIConfig.kt`
- Create: unit tests under `android/app/src/test/...`

**Interfaces:** Mirror iOS:
- Models: `Store`, `CatalogMeta`, `CatalogResponse`, `FuelOffering`, `StoreHours`, `StoreFeatures`
- `StoreRepository.refresh()`, `stores: StateFlow<List<Store>>`, `status: StateFlow<LiveDataStatus>`
- Favorites: Set of Int IDs in DataStore
- Cache file: `filesDir/catalog.json`

- [ ] **Step 1: Write unit test for feature AND filter and JSON decode from fixture**

- [ ] **Step 2: Implement serialization + repository**

- [ ] **Step 3: `./gradlew :app:testDebugUnitTest`**

- [ ] **Step 4: Commit**

```bash
git add android
git commit -m "feat(android): API client, disk cache, and favorites"
```

---

### Task 13: Android UI parity (map, list, filters, detail)

**Files:**
- Create: `ui/MainScreen.kt` — map + bottom sheet list
- Create: `ui/StoreList.kt`, `ui/StoreDetail.kt`, `ui/FilterSheet.kt`
- Create: `ui/theme/...`
- Create: `location/LocationRepository.kt`
- OSM: osmdroid `MapView` in `AndroidView` composable

**Behavior parity with iOS:**
- Sort by distance; favorites star; sort nearest vs favorites first
- Search name/city/address/id
- Filters AND across feature groups
- Detail: fuels, hours, amenities, call intent, geo directions intent
- Status banners for cache/offline/refresh

- [ ] **Step 1: Wire ViewModel + MainScreen**

- [ ] **Step 2: Filters + detail**

- [ ] **Step 3: Manual test on emulator against host machine server (`10.0.2.2:8080` for emulator)**

Default debug base URL: `http://10.0.2.2:8080` for emulator; document TrueNAS LAN IP for device.

- [ ] **Step 4: Commit**

```bash
git add android
git commit -m "feat(android): map, list, filters, and store detail UI"
```

---

### Task 14: Root docs, retire snapshot script path, polish

**Files:**
- Modify: root `README.md` — monorepo architecture, Docker/TrueNAS, iOS/Android build, API base URL
- Modify: `Scripts/generate_dataset.py` — add deprecation notice pointing to server ingest OR delete and note in README
- Ensure `.gitignore` covers `server/.venv`, `server/data`, `android/.gradle`, `android/local.properties`, `*.iml`

- [ ] **Step 1: Rewrite README sections**

Cover:
1. What KwikFinder is
2. Architecture diagram (text)
3. Running server (`docker compose up`)
4. iOS build (`cd ios && xcodegen generate`)
5. Android build
6. Configuring API base URL on each client
7. Not affiliated disclaimer
8. Data sources (server-side only)

- [ ] **Step 2: Update `.gitignore`**

- [ ] **Step 3: Final verification**

```bash
# Server
cd server && source .venv/bin/activate && PYTHONPATH=. pytest -v

# Docker
cd .. && docker compose up --build -d
curl -sf http://localhost:8080/v1/health

# iOS build
cd ios && xcodegen generate && xcodebuild ... build CODE_SIGNING_ALLOWED=NO

# Android build
cd android && ./gradlew :app:assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add README.md .gitignore Scripts docs
git commit -m "docs: monorepo deploy guide and client configuration"
```

---

## Spec coverage checklist

| Spec requirement | Task(s) |
| --- | --- |
| FastAPI + SQLite + Docker | 1, 2 |
| Scheduled list/details/PDF ingest | 4, 5, 6 |
| Feature flags on server | 3, 4 |
| `/v1/health`, `/meta`, `/stores` | 1, 7 |
| OpenAPI | 7 |
| Monorepo `ios/`, `android/`, `server/` | 1, 8, 11 |
| iOS no direct Kwik Trip | 9, 10 |
| iOS disk cache offline | 10 |
| Android Compose + OSM | 11, 13 |
| Android parity filters/favorites | 12, 13 |
| CORS / web-ready API | 1, 7 |
| TrueNAS portable Docker | 1, 14 |
| Configurable client base URL | 9, 12, 14 |

## Execution notes

- Prefer **subagent-driven-development**: one task per subagent, review between tasks.
- Do not start Task 8 until Task 7 API shape is stable (or pin OpenAPI first).
- Tasks 11–13 can start after Task 7 in parallel with Tasks 8–10.
- Never commit secrets; no API keys required for v1.
- Live Kwik Trip calls only from server jobs / optional manual smoke — unit tests use fixtures.

---

## Self-review notes (plan author)

- No TBD placeholders left in task steps.
- Feature mapping rules copied from existing iOS `Store.has`.
- PDF parsers explicitly ported from `Scripts/generate_dataset.py`.
- Type names (`CatalogMeta`, `compute_features`, `preserve_pdf_flags`) consistent across tasks.
- Full Android UI is large; Task 13 is intentionally one deliverable with parity checklist rather than pixel-perfect mocks — implementer should mirror iOS view structure in `ios/KwikFinder/Views/*`.
