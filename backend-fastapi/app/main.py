"""FastAPI application entrypoint. Phase 1: DB connectivity only."""
from __future__ import annotations

from fastapi import Depends, FastAPI
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.api.production import router as production_router
from app.api.auth import router as auth_router
from app.api.hold import router as hold_router
from app.api.reject import router as reject_router
from app.api.validation import router as validation_router
from app.database import get_db

app = FastAPI(title="StarsOne FastAPI", version="0.1.0")

app.include_router(production_router)
app.include_router(auth_router)
app.include_router(hold_router)
app.include_router(reject_router)
app.include_router(validation_router)

@app.get("/health")
def health(db: Session = Depends(get_db)) -> dict:
    """Liveness + DB check: runs SELECT 1 through the SQLAlchemy session."""
    result = db.execute(text("SELECT 1")).scalar()
    return {"status": "ok", "database": "up" if result == 1 else "unexpected"}
