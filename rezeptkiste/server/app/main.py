"""Einstiegspunkt der Rezeptkiste-API."""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from .api import router
from .bootstrap import bootstrap
from .db import session

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


@asynccontextmanager
async def lifespan(app: FastAPI):
    with session() as db:
        bootstrap(db)
    yield


def create_app(run_bootstrap: bool = True) -> FastAPI:
    app = FastAPI(
        title="Rezeptkiste API",
        version="0.1.0",
        lifespan=lifespan if run_bootstrap else None,
    )
    app.include_router(router)
    return app


app = create_app()
