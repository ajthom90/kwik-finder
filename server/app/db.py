"""SQLite connection helpers for the KwikFinder store catalog."""

from __future__ import annotations

import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator

from app.config import get_settings

SCHEMA_SQL = """
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
"""


def db_path() -> Path:
    settings = get_settings()
    return Path(settings.data_dir) / "kwikfinder.db"


def connect() -> sqlite3.Connection:
    path = db_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(path))
    conn.row_factory = sqlite3.Row
    return conn


@contextmanager
def get_connection() -> Iterator[sqlite3.Connection]:
    conn = connect()
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def init_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(SCHEMA_SQL)
