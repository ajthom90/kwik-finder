from fastapi import APIRouter, HTTPException

from app.repository import get_all_stores, get_refresh_meta, get_store

router = APIRouter(tags=["stores"])


def _catalog_meta() -> dict:
    m = get_refresh_meta()
    stores = get_all_stores()
    return {
        "dataVersion": m.get("dataVersion"),
        "storeCount": len(stores),
        "listRefreshedAt": m.get("listRefreshedAt"),
        "detailsRefreshedAt": m.get("detailsRefreshedAt"),
        "pdfEnrichedAt": m.get("pdfEnrichedAt"),
    }


@router.get("/v1/meta")
def meta():
    return _catalog_meta()


@router.get("/v1/stores")
def list_stores():
    stores = get_all_stores()
    m = get_refresh_meta()
    return {
        "meta": {
            "dataVersion": m.get("dataVersion"),
            "storeCount": len(stores),
            "listRefreshedAt": m.get("listRefreshedAt"),
            "detailsRefreshedAt": m.get("detailsRefreshedAt"),
            "pdfEnrichedAt": m.get("pdfEnrichedAt"),
        },
        "stores": stores,
    }


@router.get("/v1/stores/{store_id}")
def get_store_by_id(store_id: int):
    store = get_store(store_id)
    if store is None:
        raise HTTPException(status_code=404, detail="Store not found")
    return store
