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
