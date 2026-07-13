def test_health_ok(client):
    r = client.get("/v1/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] in ("ok", "degraded", "starting")
    assert "listRefreshedAt" in body
    assert "detailsRefreshedAt" in body
    assert "pdfEnrichedAt" in body
