"""HOLD / RELEASE API — ports the legacy .NET hold logic to SQLAlchemy text()."""
from __future__ import annotations

import logging
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.database import get_db
from app.sql_utils import prod_table, resolve_employee_id

logger = logging.getLogger("uvicorn.error")
router = APIRouter(prefix="/api/production", tags=["hold"])


class HoldRequest(BaseModel):
    # Field names match the Android Gson JSON (camelCase) 1:1.
    workOrder: str | None = None
    runcard: str | None = None
    material: str | None = None
    workCenter: str | None = None
    operation: str | None = None
    cby: str | None = None
    selectReason: str | None = None
    topicDamage: str | None = None
    holdComment: str | None = None
    releaseComment: str | None = None
    actionType: str | None = None


@router.get("/reasons/hold")
def get_hold_reasons(db: Session = Depends(get_db)) -> list[dict]:
    try:
        rows = db.execute(
            text(
                f"""
                SELECT Reason_Code AS reasonCode, Description AS description
                FROM {prod_table('REASON_MASTER')}
                WHERE Reason_Group IN ('INT', 'MFG', 'STOP', 'ENG')
                  AND (Reason_Code LIKE 'H%' OR UPPER(ISNULL(Description, '')) LIKE '%HOLD%')
                ORDER BY Reason_Group, Reason_Code
                """
            )
        ).mappings().all()
        return [{"reasonCode": r["reasonCode"], "description": r["description"]} for r in rows]
    except Exception as exc:
        logger.exception("get_hold_reasons failed")
        raise HTTPException(status_code=500, detail=f"Get hold reasons failed: {exc}") from exc


def _write_hold(db: Session, req: HoldRequest, action_type: str) -> int:
    """Apply the status update + history insert; returns rows affected by the update."""
    employee_id = resolve_employee_id(db, req.cby) or req.cby
    now = datetime.now()

    if action_type == "HOLD":
        update_sql = f"""
            UPDATE {prod_table('RC_Transection')}
            SET B2B_STATUS='HOLD', PRD_STATUS='HOLD', QA_REASON=:reason,
                REASONCODE=:reason, UDATE=:cdate, CBY=:cby
            WHERE RUNCARD=:runcard AND ISNULL(CANC_FLAG,'')<>'X'
              AND (:wc IS NULL OR :wc='' OR WORK_CENTER=:wc)
        """
        update_params = {"reason": req.selectReason, "cdate": now, "cby": employee_id,
                         "runcard": req.runcard, "wc": req.workCenter}
    else:  # RELEASE
        update_sql = f"""
            UPDATE {prod_table('RC_Transection')}
            SET B2B_STATUS=NULL,
                PRD_STATUS=CASE WHEN PRD_STATUS='HOLD' THEN NULL ELSE PRD_STATUS END,
                QA_REASON=NULL, REASONCODE=NULL, UDATE=:cdate, CBY=:cby
            WHERE RUNCARD=:runcard AND ISNULL(CANC_FLAG,'')<>'X'
              AND (:wc IS NULL OR :wc='' OR WORK_CENTER=:wc)
        """
        update_params = {"cdate": now, "cby": employee_id,
                         "runcard": req.runcard, "wc": req.workCenter}

    affected = db.execute(text(update_sql), update_params).rowcount

    db.execute(
        text(
            f"""
            INSERT INTO {prod_table('RUNCARD_HISTORY')}
                (RCTYPE, MOTHERWO, MOTHERRUNCARD, CHILDRUNCARD, WORKCENTER, OPERATION,
                 HOLDREMARK, HOLDCOMMENT, RELEASECOMMENT, CBY, CDATE)
            VALUES (:action, :wo, :runcard, :runcard, :wc, :oper,
                    :reason, :hold_comment, :release_comment, :cby, :cdate)
            """
        ),
        {"action": action_type, "wo": req.workOrder, "runcard": req.runcard,
         "wc": req.workCenter, "oper": req.operation, "reason": req.selectReason,
         "hold_comment": req.holdComment, "release_comment": req.releaseComment,
         "cby": employee_id, "cdate": now},
    )
    return affected


@router.post("/hold")
def save_hold(req: HoldRequest, db: Session = Depends(get_db)) -> dict:
    if not (req.runcard or "").strip():
        raise HTTPException(status_code=400, detail="Runcard is required.")
    if not (req.selectReason or "").strip() or not (req.holdComment or "").strip():
        raise HTTPException(status_code=400, detail="Reason and hold comment are required.")
    try:
        affected = _write_hold(db, req, "HOLD")
        if affected <= 0:
            db.rollback()
            raise HTTPException(status_code=400, detail="No active transaction row was placed on hold.")
        db.commit()
        return {"success": True, "message": "HOLD saved."}
    except HTTPException:
        db.rollback()
        raise
    except Exception as exc:
        db.rollback()
        logger.exception("save_hold failed")
        raise HTTPException(status_code=500, detail=f"Hold failed: {exc}") from exc


@router.post("/release")
def release_hold(req: HoldRequest, db: Session = Depends(get_db)) -> dict:
    if not (req.runcard or "").strip():
        raise HTTPException(status_code=400, detail="Runcard is required.")
    if not (req.releaseComment or "").strip():
        raise HTTPException(status_code=400, detail="Release hold comment is required.")
    try:
        affected = _write_hold(db, req, "RELEASE")
        if affected <= 0:
            db.rollback()
            raise HTTPException(status_code=400, detail="No active hold transaction row was released.")
        db.commit()
        return {"success": True, "message": "RELEASE saved."}
    except HTTPException:
        db.rollback()
        raise
    except Exception as exc:
        db.rollback()
        logger.exception("release_hold failed")
        raise HTTPException(status_code=500, detail=f"Release failed: {exc}") from exc
