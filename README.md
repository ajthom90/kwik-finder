# KwikFinder

Monorepo for finding the nearest **Kwik Trip / Kwik Star** store with the
features you need — family restrooms, specific fuel types, truck stop services
(scale, DEF, showers, truck parking), and KwikCharge EV charging.

**Not affiliated with or endorsed by Kwik Trip, Inc.** All store data is derived
from Kwik Trip's public website. Fuel prices are informational; the price at the
pump governs.

| Path | Contents |
| --- | --- |
| [`server/`](server/) | FastAPI caching API — **sole** Kwik Trip consumer |
| [`ios/`](ios/) | SwiftUI iPhone client (MapKit + disk cache) |
| [`android/`](android/) | Jetpack Compose client (osmdroid + disk cache) |
| [`docs/api/openapi.yaml`](docs/api/openapi.yaml) | HTTP API contract |
| [`docker-compose.yml`](docker-compose.yml) | Local / TrueNAS-friendly deploy |
| [`Scripts/`](Scripts/) | **Deprecated** legacy snapshot generator (see below) |

## Architecture

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  iOS app    │  │ Android app │  │ Future web  │
│  (MapKit)   │  │ (OSM)       │  │             │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │
       │         GET /v1/*               │
       └────────────────┼────────────────┘
                        ▼
              ┌───────────────────┐
              │  KwikFinder API   │
              │  FastAPI + SQLite │
              │  + scheduler      │
              └─────────┬─────────┘
                        │ (only this process talks upstream)
                        ▼
              ┌───────────────────┐
              │  Kwik Trip site   │
              │  list/details JSON│
              │  + Maps PDFs      │
              └───────────────────┘
```

- **Server** schedules list/details/PDF ingest, normalizes features, and serves a
  full-catalog dump (`GET /v1/stores`) plus meta/health.
- **Clients** never call Kwik Trip. They poll the server, filter/sort locally,
  and keep a disk cache of the last successful catalog for offline use.
- **No auth** on the public read API for v1 (data is already public on Kwik Trip's
  site). Prefer LAN / reverse-proxy access if you expose it beyond home lab.

## Features (clients)

- **Map + nearest-first list** of all ~900+ Kwik Trip / Kwik Star stores.
- **Favorites**: star stores from list or detail; sort **Nearest** or **Favorites first**.
- **Filters** (AND semantics — a store must have *every* selected feature):
  - *Popular:* Family Restroom, EV Charging, Open 24 Hours
  - *Fuel types:* Diesel, Premium Diesel, DEF at the pump, E-85, CNG,
    No-Ethanol Gas, Unleaded 88
  - *Professional driver services:* CAT Scale, Showers, Truck Parking,
    TransFlo, Fleet Cards, Truck Friendly
  - *Amenities:* Car Wash, ATM, Bitcoin ATM, Wi-Fi, Restaurant
- **Search** by store name, city, address, or store number.
- **Store detail** with fuel prices, hours, amenities, directions, and call.
- **EV charging status**: KwikCharge sites badged open vs coming soon.
- **Offline**: last successful catalog on disk; status banners for offline /
  refreshing / degraded server data.

Map markers are capped at the **nearest 120** matches for pan performance; the
list always shows the full filtered set.

## Where the data comes from

**Only the server** talks to Kwik Trip. Clients use the KwikFinder API
([OpenAPI](docs/api/openapi.yaml)).

| Data | Upstream source | Server usage |
| --- | --- | --- |
| Store list (id, name, coordinates, phone) | `kwiktrip.com/storelistproxy.php` | Scheduled list ingest |
| Fuel types & prices, amenities, hours, truck services | `kwiktrip.com/storeinformationsproxy.php?ids=…` (≤10 ids/request) | Batched details ingest |
| **Family restrooms** | "Family Restroom Store List" PDF on [Maps & Downloads](https://www.kwiktrip.com/maps-downloads) | Daily PDF enrich |
| **EV charging (KwikCharge)** | "EV Charging Locations" PDF on Maps & Downloads | Daily PDF enrich |

Kwik Trip's locator endpoints are undocumented public site APIs and could change;
the server keeps last-good SQLite rows and reports degraded health on failures.

### Legacy snapshot script (deprecated)

`Scripts/generate_dataset.py` used to write a bundled `stores_snapshot.json` for
the pre-server iOS app. **Ingest and PDF parsing now live in
`server/app/ingest/`.** Do not use the script for new workflows.

The script is retained only for historical reference / emergency offline
snapshot generation. Its default output path
(`KwikFinder/Resources/stores_snapshot.json`) no longer exists in this monorepo
layout. Prefer running the server.

## Running the server

### Docker Compose (recommended)

```bash
docker compose up --build -d
curl -sf http://localhost:8080/v1/health | jq .
curl -sf http://localhost:8080/v1/meta | jq .
```

- Image builds from [`server/Dockerfile`](server/Dockerfile).
- SQLite and temp PDF files live under `DATA_DIR` (`/data` in the container),
  backed by the `kwikfinder-data` named volume.
- Host port **8080** → container 8080 (change the left side of the mapping in
  `docker-compose.yml` if 8080 is already in use).

### Configuration (environment)

| Variable | Purpose | Default |
| --- | --- | --- |
| `DATA_DIR` | SQLite + temp PDF path | `./data` (image: `/data`) |
| `LIST_REFRESH_SECONDS` | Store list poll interval | `900` |
| `DETAILS_REFRESH_SECONDS` | Details cycle target | `1200` |
| `PDF_REFRESH_SECONDS` | PDF re-parse interval | `86400` |
| `CORS_ORIGINS` | Browser CORS allowlist (`*` or comma-separated) | `*` |
| `LOG_LEVEL` | Logging level | `INFO` |
| `HOST` / `PORT` | Bind address (local runs) | `0.0.0.0` / `8080` |

### Local development (without Docker)

```bash
cd server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt
PYTHONPATH=. uvicorn app.main:app --host 0.0.0.0 --port 8080
# tests:
PYTHONPATH=. pytest -v
```

### TrueNAS / always-on host notes

1. Deploy with Docker Compose (Apps / custom compose stack) or any Docker host.
2. Persist the volume (`kwikfinder-data` or a host path bind-mount to `/data`) so
   the catalog survives container recreation.
3. Expose port 8080 on your LAN (or reverse-proxy with TLS). No API keys for v1.
4. Point phone clients at the host LAN IP or DNS name, e.g.
   `http://192.168.1.50:8080` (cleartext HTTP is fine on trusted LAN; use HTTPS
   if you terminate TLS at a reverse proxy).
5. First boot may return an empty catalog until the first list/details/PDF jobs
   complete — watch `GET /v1/health` and `GET /v1/meta`.

## Building (iOS)

The Xcode project lives under [`ios/`](ios/) and is generated from
[`ios/project.yml`](ios/project.yml) with
[XcodeGen](https://github.com/yonaskolb/XcodeGen).

### Prerequisites

- **Xcode 16+**, iOS 17.0+ (iPhone)
- **XcodeGen** if you change `project.yml`:

```bash
brew install xcodegen
```

### Generate, open, build

```bash
cd ios
xcodegen generate
open KwikFinder.xcodeproj
```

Command-line build (Simulator example):

```bash
cd ios
xcodebuild -project KwikFinder.xcodeproj -scheme KwikFinder \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -configuration Debug build CODE_SIGNING_ALLOWED=NO
```

No third-party Swift packages — SwiftUI, MapKit, CoreLocation, Observation only.
Do not hand-edit `project.pbxproj`; edit `project.yml` and re-run XcodeGen.

### iOS API base URL

Configured via Info.plist key **`KwikFinderAPIBaseURL`**
([`ios/KwikFinder/Info.plist`](ios/KwikFinder/Info.plist) /
[`APIConfig.swift`](ios/KwikFinder/Config/APIConfig.swift)):

| Build | Default |
| --- | --- |
| Debug | `http://localhost:8080` (Simulator → host Docker) |
| Physical device | Set `KwikFinderAPIBaseURL` to your LAN host, e.g. `http://192.168.1.50:8080` |
| Release | Set `KwikFinderAPIBaseURL` explicitly (localhost fallback is a misconfig safeguard) |

Local networking (cleartext to LAN) is allowed via
`NSAppTransportSecurity` → `NSAllowsLocalNetworking`.

## Building (Android)

Kotlin + Jetpack Compose + **osmdroid** (OpenStreetMap — no Google Maps key).

### Prerequisites

- **JDK 17+**
- Android SDK (via Android Studio or command-line tools)
- Emulator or device with API level matching the app `minSdk`

### Build

```bash
cd android
./gradlew :app:assembleDebug
# unit tests:
./gradlew :app:testDebugUnitTest
```

Open the `android/` folder in Android Studio for the emulator UI.

`local.properties` (SDK path) is generated by Android Studio and is gitignored.

### Android API base URL

[`APIConfig.kt`](android/app/src/main/java/com/ajthom90/kwikfinder/data/APIConfig.kt):

| Environment | URL |
| --- | --- |
| Emulator default | `http://10.0.2.2:8080` (alias for host loopback) |
| Physical device / TrueNAS | Set `APIConfig.baseUrl` before building the client, e.g. `http://192.168.1.50:8080` |

Debug builds allow cleartext HTTP via the debug network security config so LAN
and emulator hosts work without TLS.

## HTTP API (summary)

Contract: [`docs/api/openapi.yaml`](docs/api/openapi.yaml)

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/v1/health` | Process up; last success/error for list, details, PDF jobs |
| `GET` | `/v1/meta` | `dataVersion`, store count, refresh timestamps |
| `GET` | `/v1/stores` | Full normalized catalog (`meta` + `stores[]`) |
| `GET` | `/v1/stores/{id}` | Single store |

Clients should probe `/v1/meta` and only re-download `/v1/stores` when
`dataVersion` changes.

## Repo layout

```
kwik-finder/
├── server/                 FastAPI + SQLite + ingest jobs
│   ├── app/
│   │   ├── api/            /v1 routes
│   │   ├── ingest/         Kwik Trip JSON + PDF pipeline
│   │   ├── jobs.py         scheduled refresh
│   │   └── ...
│   ├── tests/
│   ├── Dockerfile
│   └── requirements*.txt
├── ios/                    SwiftUI client
│   ├── KwikFinder/
│   ├── project.yml
│   └── KwikFinder.xcodeproj
├── android/                Compose + osmdroid client
├── docs/
│   ├── api/openapi.yaml
│   └── superpowers/        design + plans
├── docker-compose.yml
├── Scripts/                deprecated snapshot generator
└── README.md
```

## Notes & known limitations

- Clients never contact Kwik Trip; only the server does.
- Family-restroom and EV flags refresh on the server PDF schedule (default daily).
- Map markers: nearest **120** for performance; list is complete.
- Fuel prices are informational; pump price governs.
- No user accounts, auth, or server-side favorites in v1.
- Undocumented upstream endpoints may change; server degrades to last-good data.
