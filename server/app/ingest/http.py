"""HTTP fetch helpers with User-Agent and retries (no live network in unit tests)."""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from typing import Any

USER_AGENT = "KwikFinder server (personal project)"
DEFAULT_TIMEOUT = 60
DEFAULT_RETRIES = 3


def fetch_bytes(url: str, *, retries: int = DEFAULT_RETRIES, timeout: float = DEFAULT_TIMEOUT) -> bytes:
    """GET ``url`` and return response body bytes, retrying transient failures."""
    headers = {"User-Agent": USER_AGENT}
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return resp.read()
        except (urllib.error.URLError, TimeoutError, OSError) as e:
            last_error = e
            if attempt == retries - 1:
                raise
            time.sleep(2 ** (attempt + 1))
    assert last_error is not None
    raise last_error


def fetch_json(url: str, **kwargs: Any) -> Any:
    """GET ``url`` and parse JSON body."""
    return json.loads(fetch_bytes(url, **kwargs))
