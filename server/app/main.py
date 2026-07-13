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
