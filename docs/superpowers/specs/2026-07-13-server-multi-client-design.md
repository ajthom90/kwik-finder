# KwikFinder: Server-Backed Multi-Client Architecture

**Date:** 2026-07-13  
**Status:** Approved for implementation planning  
**Scope:** Restructure KwikFinder from a single iOS app that calls Kwik Trip APIs directly into a monorepo with a caching server, iOS client, and Android client. Future web client is in-scope for the API contract only.

## Problem

The current iOS app calls Kwik Trip’s public locator endpoints and ships a bundled snapshot generated from PDFs. If the app gains users, those clients would multiply traffic against Kwik Trip’s undocumented endpoints. Family-restroom and EV (KwikCharge) data only exist as PDFs and are baked into a snapshot at build time.

## Goals

1. **Single upstream consumer:** Only the KwikFinder server talks to Kwik Trip (JSON locator + Maps & Downloads PDFs).
2. **Cache and normalize:** Server stores a full store catalog (including fuel prices, hours, amenities, PDF-derived flags) and serves it to clients.
3. **Multi-client:** iOS, Android, and (later) web share the same versioned HTTP API.
4. **Offline-friendly clients:** Apps persist the last successful catalog and work when the server is unreachable.
5. **Portable deploy:** Docker image/compose runs on TrueNAS first, and on any VPS or Docker host later.
6. **Feature parity:** Android matches the existing iOS product surface (map, list, filters, favorites, detail, offline banners).

## Non-goals (v1)

- User accounts, auth, or server-side favorites
- Server-side geo search or filter queries (clients filter locally after full dump)
- Shipping a web UI in the first implementation pass
- Google Maps on Android (use OpenStreetMap instead)
- Guaranteeing pump-accurate fuel prices (same caveat as today: informational only)
- High-availability multi-node server topology

## Key Decisions

| Decision | Choice | Rationale |
| --- | --- | --- |
| Architecture | Full-dump API + server-side scheduled refresh (Approach A) | Predictable Kwik Trip load; simple clients; enough for ~900–1000 stores |
| Backend | Python / FastAPI + SQLite + in-process scheduler | Matches existing PDF script; low ops; one container |
| Repo layout | Monorepo: `ios/`, `android/`, `server/`, `docs/` | Solo/small-team; keep API contract next to clients |
| iOS maps | Apple MapKit | Already in use; platform-native |
| Android maps | OpenStreetMap (no Google Maps key) | Avoid Google billing/keys for self-hosted hobby scale |
| Client offline | Disk cache of last successful `GET /v1/stores` | Works offline after first successful fetch; no mandatory bundled snapshot |
| Hosting | Docker + volume for SQLite; TrueNAS first | Portable to VPS later without redesign |
| Web | Same API + CORS; no web app in v1 | Future client without API changes |
| Auth | None on public read API for v1 | Data is already public via Kwik Trip’s site; optional rate limits if exposed beyond LAN |

## Target architecture

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  iOS app    │  │ Android app │  │ Future web  │
│  (MapKit)   │  │ (OSM)       │  │             │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │
       │    HTTPS GET /v1/*              │
       └────────────────┼────────────────┘
                        ▼
              ┌───────────────────┐
              │  KwikFinder API   │
              │  FastAPI + SQLite │
              │  + scheduler      │
              └─────────┬─────────┘
                        │ (only this process)
                        ▼
              ┌───────────────────┐
              │  Kwik Trip site   │
              │  list/details JSON│
              │  + Maps PDFs      │
              └───────────────────┘
```

## Monorepo layout

```
kwik-finder/
├── ios/                      # SwiftUI app (moved from KwikFinder/)
│   ├── KwikFinder/
│   ├── project.yml
│   └── KwikFinder.xcodeproj  # generated via XcodeGen
├── android/                  # Kotlin + Jetpack Compose
├── server/                   # FastAPI service
│   ├── app/
│   │   ├── main.py           # FastAPI app, routes
│   │   ├── models.py         # SQLAlchemy / schema
│   │   ├── api/              # /v1 routes
│   │   ├── ingest/           # Kwik Trip JSON + PDF pipeline
│   │   ├── jobs.py           # scheduled refresh
│   │   └── config.py
│   ├── tests/
│   ├── Dockerfile
│   ├── requirements.txt
│   └── pyproject.toml        # optional
├── docs/
│   ├── api/openapi.yaml      # source of truth for client contract
│   └── superpowers/specs/    # design docs
├── docker-compose.yml        # API + volume for SQLite data
├── Scripts/                  # may migrate PDF logic into server/ingest
└── README.md
```

The existing root `KwikFinder/` tree moves under `ios/` during implementation. Historical `Scripts/generate_dataset.py` logic is ported into `server/app/ingest/` (list, details, PDF parse) so the server is the only producer of store data.

## Server design

### Responsibilities

1. **Ingest store list** from Kwik Trip `storelistproxy.php`.
2. **Ingest store details** from `storeinformationsproxy.php` in batches of ≤10 IDs, rotating through the catalog and preferring stale rows.
3. **Ingest PDF-only fields** (family restroom list, EV charging open/coming soon) from Maps & Downloads PDFs (port of current Python snapshot generator).
4. **Normalize and persist** into SQLite.
5. **Serve** versioned JSON to clients.
6. **Never require clients** to contact Kwik Trip.

### Process model (v1)

- Single Docker container.
- FastAPI (uvicorn) handles HTTP.
- APScheduler (or equivalent) runs in-process:
  - Store list: every **15 minutes** (configurable).
  - Store details: continuous/batched; target full-catalog freshness **15–30 minutes** (configurable).
  - PDF enrich: every **24 hours** (configurable), plus manual trigger endpoint optional later.
- SQLite file on a Docker named volume (e.g. `/data/kwikfinder.db`).

### Configuration (environment)

| Variable | Purpose | Example |
| --- | --- | --- |
| `DATA_DIR` | SQLite + temp PDF path | `/data` |
| `LIST_REFRESH_SECONDS` | List poll interval | `900` |
| `DETAILS_REFRESH_SECONDS` | Details cycle target | `1200` |
| `PDF_REFRESH_SECONDS` | PDF re-parse interval | `86400` |
| `CORS_ORIGINS` | Browser clients | `*` or explicit origins |
| `LOG_LEVEL` | Logging | `INFO` |

### Degradation

- If a Kwik Trip fetch fails, keep last good rows; mark health as degraded.
- If PDF parse fails, keep previous family/EV flags.
- Empty DB on first boot: run ingest immediately before serving empty catalog if possible; otherwise return empty list + meta indicating never refreshed.

### Public HTTP API

Base path: `/v1`

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/v1/health` | Process up; last success/error timestamps for list, details, PDF jobs |
| `GET` | `/v1/meta` | `dataVersion`, store count, refresh timestamps — clients use to skip re-download |
| `GET` | `/v1/stores` | Full normalized catalog (`meta` + `stores[]`) |
| `GET` | `/v1/stores/{id}` | Single store (optional convenience; clients primarily use full dump) |

No write endpoints for v1. No API keys for v1. Optional lightweight rate limiting if the API is exposed beyond a private network.

### Response shape (normative sketch)

`GET /v1/stores`:

```json
{
  "meta": {
    "dataVersion": "2026-07-13T12:00:00Z",
    "storeCount": 950,
    "listRefreshedAt": "2026-07-13T12:00:00Z",
    "detailsRefreshedAt": "2026-07-13T12:05:00Z",
    "pdfEnrichedAt": "2026-07-13T06:00:00Z"
  },
  "stores": [
    {
      "id": 123,
      "name": "KWIK TRIP #123",
      "latitude": 44.9,
      "longitude": -93.2,
      "address1": "1 Main St",
      "city": "La Crosse",
      "county": null,
      "state": "WI",
      "zip": "54601",
      "phone": "608-555-0100",
      "open24Hours": true,
      "familyRestroom": true,
      "evCharging": "open",
      "fuels": [
        {
          "type": "DIESEL #2",
          "description": null,
          "price": 3.299
        }
      ],
      "hours": [
        {
          "dayOfWeek": "Monday",
          "openTime": "00:00:00",
          "closeTime": "00:00:00"
        }
      ],
      "amenities": ["Wi-Fi", "ATM", "Car Wash"],
      "features": {
        "diesel": true,
        "premiumDiesel": false,
        "def": true,
        "e85": false,
        "cng": false,
        "noEthanolGas": false,
        "unleaded88": true,
        "scale": true,
        "showers": true,
        "truckParking": true,
        "truckParkingStalls": 12,
        "transFlo": false,
        "fleetCards": true,
        "truckFriendly": true,
        "carWash": true,
        "atm": true,
        "bitcoinATM": false,
        "wifi": true,
        "restaurant": false
      }
    }
  ]
}
```

Notes:

- `evCharging`: `null` | `"open"` | `"comingSoon"` (aligned with today’s iOS model).
- `dataVersion`: ISO-8601 timestamp of the last successful material change (or max of refresh timestamps). Clients compare against cached version.
- `features` mirrors filterable `StoreFeature` flags so clients do not re-implement Kwik Trip amenity string mapping. Server owns feature extraction.
- Exact schema is locked in `docs/api/openapi.yaml` during implementation; clients must not invent fields.

### Feature mapping ownership

Today, iOS maps raw amenity/fuel strings to `StoreFeature` in `Store.has(_:)`. After the restructure:

- **Server** performs amenity/fuel/PDF → feature flags (and still exposes raw `fuels`, `hours`, `amenities` for detail UI).
- **Clients** filter using the stable `features` object (and headline fields like `familyRestroom`, `open24Hours`, `evCharging`).

This keeps Android and iOS filter logic thin and consistent.

## Client design (shared behavior)

### Network

- Configurable base URL (build config and/or debug setting) so TrueNAS LAN IP, reverse proxy hostname, or future public host can change without code edits.
- Flow:
  1. `GET /v1/meta`
  2. If `dataVersion` differs from local cache (or no cache), `GET /v1/stores`
  3. Persist full response to disk
  4. Update in-memory store list
- Refresh triggers: app launch, return to foreground, pull-to-refresh. No client-side duty to throttle for Kwik Trip’s sake; optional short client debounce only to avoid redundant self-traffic.

### Offline

- Last successful catalog is the offline source of truth.
- UI status: cached / refreshing / live / offline (same spirit as current `LiveDataStatus`).
- Cold install with no network: empty state explaining one successful connection is required.

### Local-only state

- Favorites (store IDs)
- List sort (nearest vs favorites first)
- Location permission and distance sort remain on-device

### Product surface (parity)

- Map + bottom sheet list (nearest-first)
- Search (name, city, address, store number)
- Filters with AND semantics across popular / fuel / truck / amenity groups
- Store detail: prices, hours, amenities, EV badge, call, directions
- Not affiliated disclaimer remains in README / about if present

## iOS-specific

- Move project under `ios/`; keep XcodeGen `project.yml` as source of truth.
- Replace `KwikTripAPI` with client for KwikFinder server.
- `StoreRepository` loads disk cache first, then refreshes from server.
- Remove dependence on bundled `stores_snapshot.json` for primary data path.
- Delete or stop shipping direct Kwik Trip URL usage.
- MapKit remains for map and directions.

## Android-specific

- New module under `android/`: Kotlin, Jetpack Compose, Material 3.
- Map: OpenStreetMap (e.g. osmdroid or equivalent Compose-friendly OSM stack).
- Directions: open geo intent / preferred maps app.
- Same screens and filter model as iOS.
- Persist favorites and catalog cache with DataStore / app files dir.
- Min SDK: modern baseline (API 26+ recommended unless a reason to go lower).

## Web (future)

- Not built in v1.
- API design includes CORS configuration and pure JSON so a browser SPA can call the same endpoints.
- No mobile-only auth headers required.

## Docker / TrueNAS

```yaml
# conceptual docker-compose
services:
  api:
    build: ./server
    ports:
      - "8080:8080"
    environment:
      DATA_DIR: /data
    volumes:
      - kwikfinder-data:/data
    restart: unless-stopped

volumes:
  kwikfinder-data:
```

- TrueNAS: deploy as custom app / stack from compose; map host port; optional reverse proxy (Caddy/Traefik/Nginx) for TLS on LAN or WAN.
- Backup: volume containing SQLite file.
- Resource footprint: low (one process, small DB).

## Testing strategy

| Layer | What |
| --- | --- |
| Server unit | PDF parsers, feature mapping from amenity/fuel fixtures |
| Server API | `/v1/meta`, `/v1/stores` against fixture DB |
| Server ingest | Mock Kwik Trip HTTP responses; assert merge + preserve PDF flags |
| iOS | Decode fixtures; filter AND logic; repository cache behavior |
| Android | Decode fixtures; filter logic; cache behavior |
| Manual | Docker up on LAN; both apps point at base URL; airplane mode cache |

## Migration from current app

1. Introduce `server/` with ingest (port `generate_dataset.py` + live list/details).
2. Publish OpenAPI; stabilize JSON.
3. Move iOS into `ios/`; switch repository to server client + disk cache; remove direct Kwik Trip calls.
4. Scaffold Android with feature parity.
5. Update root README for monorepo, Docker, and multi-client.
6. Optionally remove root `Scripts/generate_dataset.py` once server ingest is the single source (or thin wrapper that calls server code).

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Kwik Trip changes undocumented APIs | Defensive parsing (as today); health degraded; last-good cache |
| PDF format changes | Isolated parsers; keep previous flags on failure; logging |
| Full dump size grows | Still small for ~1k stores; if needed later add gzip, ETag, or delta |
| TrueNAS only LAN access | Configurable base URL; document reverse proxy for remote use |
| Stale fuel prices | Document refresh cadence; pull-to-refresh still only hits *our* server (server may return last cached details) |
| Legal / ToS of scraping | Same public sources as current app; not affiliated disclaimer; rate-limit jobs gently |

## Open Questions

None blocking. Resolved during design:

- Backend: FastAPI  
- Android: native Compose + OSM  
- Monorepo  
- Offline: last successful cache  
- Hosting: portable Docker (TrueNAS first)  
- Approach: full-dump + scheduled ingest  
- Web: API-ready, UI later  

## Implementation order (high level)

1. **Server foundation** — project layout, SQLite schema, health/meta/stores endpoints, Docker compose  
2. **Ingest** — list, details batches, PDF enrich, scheduler, feature mapping  
3. **OpenAPI + fixtures** — contract tests  
4. **iOS migration** — monorepo move, new API client, disk cache, remove Kwik Trip direct access  
5. **Android app** — Compose UI parity against same API  
6. **Docs** — deploy on TrueNAS, client base URL configuration  

Detailed task breakdown belongs in the implementation plan (next step after this spec is accepted as written).

## PR Plan

### PR 1: Monorepo scaffold + server skeleton
- **Affects:** root layout, `server/`, `docker-compose.yml`, root README skeleton  
- **Depends on:** none  
- **Description:** Create `server/` FastAPI app with health endpoint, config, Dockerfile, empty SQLite bootstrap. Move docs as needed. Do not move iOS yet if that confuses review; prefer creating `server/` + `docs/` first, or move `ios/` in same PR if clean.

### PR 2: Server ingest + SQLite catalog
- **Affects:** `server/app/ingest/*`, `jobs.py`, models  
- **Depends on:** PR 1  
- **Description:** Port list/details clients and PDF parsers; scheduled jobs; populate SQLite; preserve family/EV flags across detail merges.

### PR 3: Public `/v1` store API + OpenAPI
- **Affects:** `server/app/api/*`, `docs/api/openapi.yaml`, server tests  
- **Depends on:** PR 2  
- **Description:** `GET /v1/meta`, `GET /v1/stores`, `GET /v1/stores/{id}`; normalized JSON including `features`; contract tests with fixtures.

### PR 4: iOS → monorepo + server client
- **Affects:** move to `ios/`, replace `KwikTripAPI`, `StoreRepository`, project paths, README  
- **Depends on:** PR 3  
- **Description:** Configurable base URL; disk cache; remove bundled snapshot as primary path and all direct Kwik Trip URLs; keep MapKit UX.

### PR 5: Android app (Compose + OSM)
- **Affects:** `android/**`  
- **Depends on:** PR 3 (can start after PR 3 in parallel with PR 4)  
- **Description:** Map + list sheet, filters, search, favorites, detail, cache, same API decoding.

### PR 6: Polish + deploy docs
- **Affects:** root README, TrueNAS/Docker notes, optional rate limit/CORS defaults  
- **Depends on:** PR 4, PR 5  
- **Description:** End-to-end documentation, default ports, backup of volume, known limitations.

## Success criteria

- With Docker running, neither iOS nor Android process contacts `kwiktrip.com`.
- Server alone refreshes list, details, and PDF-derived fields on schedule.
- Both apps show nearest stores, filter by features, favorite stores, and open detail with prices/hours when data is present.
- Airplane mode after one successful sync still shows cached stores.
- Compose stack runs on TrueNAS and is documented for VPS portability.
- OpenAPI describes the contract a future web app can use without server changes.
