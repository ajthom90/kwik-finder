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
