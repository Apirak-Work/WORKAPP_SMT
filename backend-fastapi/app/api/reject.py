"""Reject reason lookup API."""
from __future__ import annotations

import logging

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.database import get_db
from app.sql_utils import prod_table

logger = logging.getLogger("uvicorn.error")
router = APIRouter(prefix="/api/production", tags=["reject"])


@router.get("/reasons/reject")
def get_reject_reasons(db: Session = Depends(get_db)) -> list[dict]:
    try:
        rows = db.execute(
            text(
                f"""
                SELECT ReasonCode AS reasonCode, Description AS description, ReasonGroup AS reasonGroup
                FROM {prod_table('REJECT_REASON')}
                ORDER BY ReasonGroup, ReasonCode
                """
            )
        ).mappings().all()
        return [
            {"reasonCode": row["reasonCode"], "description": row["description"], "reasonGroup": row["reasonGroup"]}
            for row in rows
        ]
    except Exception as exc:
        logger.exception("get_reject_reasons failed")
        raise HTTPException(status_code=500, detail=f"Get reject reasons failed: {exc}") from exc
