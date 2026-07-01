"""Validation + work-order lookup APIs."""
from __future__ import annotations

import logging

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.database import get_db
from app.sql_utils import prod_table

logger = logging.getLogger("uvicorn.error")
router = APIRouter(tags=["validation"])


class ValidateScanRequest(BaseModel):
    userId: str | None = None
    machineId: str | None = None
    runcardNo: str | None = None


def _exists(db: Session, sql: str, params: dict) -> bool:
    return db.execute(text(sql), params).scalar() is not None


@router.post("/api/workflow/validate-scan")
def validate_scan(req: ValidateScanRequest, db: Session = Depends(get_db)) -> dict:
    try:
        user_id = (req.userId or "").strip()
        machine_id = (req.machineId or "").strip()
        runcard_no = (req.runcardNo or "").strip()
        tx = prod_table("RC_Transection")
        oper = prod_table("OPERATION")
        rd = prod_table("Runcard_Detail")

        user_valid = bool(user_id) and _exists(
            db,
            f"SELECT TOP (1) 1 FROM {tx} WHERE CBY = :id OR QA_EN = :id",
            {"id": user_id},
        )
        machine_valid = bool(machine_id) and _exists(
            db,
            f"SELECT TOP (1) 1 FROM {oper} WHERE WORK_CENTER = :wc",
            {"wc": machine_id},
        )
        runcard_valid = bool(runcard_no) and _exists(
            db,
            f"SELECT TOP (1) 1 FROM {rd} WHERE RUNCARD = :rc AND ISNULL(FLAG, '') <> 'C'",
            {"rc": runcard_no},
        )

        runcard_flag = ""
        if runcard_no:
            runcard_flag = (
                db.execute(
                    text(
                        f"""
                        SELECT TOP (1) ISNULL(FLAG, '') AS flag
                        FROM {rd}
                        WHERE RUNCARD = :rc
                        ORDER BY ID DESC
                        """
                    ),
                    {"rc": runcard_no},
                ).scalar()
                or ""
            )

        flag_allowed = runcard_valid and str(runcard_flag).upper() not in {"C", "X", "H"}
        at_work_center = bool(runcard_no) and bool(machine_id) and _exists(
            db,
            f"""
            SELECT TOP (1) 1
            FROM {tx}
            WHERE RUNCARD = :rc
              AND WORK_CENTER = :wc
              AND ISNULL(CANC_FLAG, '') <> 'X'
            """,
            {"rc": runcard_no, "wc": machine_id},
        )
        pending_activities = bool(runcard_no) and _exists(
            db,
            f"""
            SELECT TOP (1) 1
            FROM {tx}
            WHERE RUNCARD = :rc
              AND ISNULL(CANC_FLAG, '') <> 'X'
              AND (
                    UPPER(ISNULL(QA_STATUS, '')) IN ('WAIT', 'PENDING')
                 OR UPPER(ISNULL(RECIPE_FLAG, '')) = 'P'
                 OR UPPER(ISNULL(PRD_STATUS, '')) IN ('MACHINE_STEP', 'TIMEOUT_PENDING')
              )
            """,
            {"rc": runcard_no},
        )
        active_blocks = bool(runcard_no) and _exists(
            db,
            f"""
            SELECT TOP (1) 1
            FROM {tx}
            WHERE RUNCARD = :rc
              AND ISNULL(CANC_FLAG, '') <> 'X'
              AND (
                    UPPER(ISNULL(B2B_STATUS, '')) IN ('HOLD', 'BLOCK', 'Y')
                 OR ISNULL(ERROR, '') <> ''
              )
            """,
            {"rc": runcard_no},
        )

        allowed = (
            user_valid
            and machine_valid
            and runcard_valid
            and flag_allowed
            and at_work_center
            and not pending_activities
            and not active_blocks
        )
        message = "Scan data is valid." if allowed else (
            f"Validation failed - User:{'OK' if user_valid else 'FAIL'} | "
            f"Machine:{'OK' if machine_valid else 'FAIL'} | "
            f"Runcard:{'OK' if runcard_valid else 'FAIL'} | "
            f"Flag:{'OK' if flag_allowed else 'FAIL'} | "
            f"WorkCenter:{'OK' if at_work_center else 'FAIL'} | "
            f"Pending:{'FAIL' if pending_activities else 'OK'} | "
            f"Block:{'FAIL' if active_blocks else 'OK'}"
        )
        return {
            "isAllowed": allowed,
            "message": message,
            "userValid": user_valid,
            "machineValid": machine_valid,
            "runcardValid": runcard_valid,
            "runcardFlag": runcard_flag,
            "flagAllowed": flag_allowed,
            "atWorkCenter": at_work_center,
            "pendingActivities": pending_activities,
            "activeBlocks": active_blocks,
        }
    except Exception as exc:
        logger.exception("validate_scan failed")
        raise HTTPException(status_code=500, detail=f"Validate scan failed: {exc}") from exc


@router.get("/api/production/workorders/{work_order_no}/runcards")
def get_runcards_by_work_order(work_order_no: str, db: Session = Depends(get_db)) -> list[dict]:
    try:
        rd = prod_table("Runcard_Detail")
        tx = prod_table("RC_Transection")
        rows = db.execute(
            text(
                f"""
                WITH BaseRuncards AS (
                    SELECT rd.RC_TYPE AS Type, rd.RUNCARD AS Rc, rd.ASSY_LOT AS AssyLot,
                           rd.MATERIAL AS Material, rd.QTY AS Qty
                    FROM {rd} rd
                    WHERE rd.WO = :wo AND rd.FLAG = '1'
                )
                SELECT br.Type AS type, br.Rc AS rc,
                       COALESCE(NULLIF(br.AssyLot, ''), NULLIF(br.Material, '')) AS assy,
                       CONVERT(varchar(20), br.Qty) AS qty,
                       tx.RcAction AS rcAction,
                       CONCAT('Completion ', CONVERT(varchar(10), COALESCE(tx.CompletionPercent, 0)), '%') AS status
                FROM BaseRuncards br
                OUTER APPLY (
                    SELECT NULLIF(MAX(rt.OPERATION), '') AS RcAction,
                           CASE WHEN COUNT(rt.RUNCARD) = 0 THEN 0
                                ELSE SUM(CASE WHEN rt.CONFIRM_DATE IS NOT NULL THEN 1 ELSE 0 END) * 100
                                     / COUNT(rt.RUNCARD) END AS CompletionPercent
                    FROM {tx} rt WHERE rt.RUNCARD = br.Rc
                ) tx
                ORDER BY br.Rc
                """
            ),
            {"wo": work_order_no},
        ).mappings().all()
        return [dict(r) for r in rows]
    except Exception as exc:
        logger.exception("get_runcards_by_work_order failed")
        raise HTTPException(status_code=500, detail=f"Get runcards by work order failed: {exc}") from exc
