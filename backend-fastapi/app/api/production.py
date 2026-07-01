"""Production transaction APIs."""
from __future__ import annotations

import logging
import re
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import text
from sqlalchemy.exc import DBAPIError
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.runcard_system import RCTransection, RuncardDetail
from app.schemas.production import (
    MergeCombineProcedureRequest,
    ProductionDetailResponse,
    ProductionConfirmRequest,
    RejectRequest,
    SplitRequest,
)
from app.sql_utils import prod_table

logger = logging.getLogger("uvicorn.error")
router = APIRouter(prefix="/api/production", tags=["production"])


def _raise_procedure_http_error(exc: DBAPIError) -> None:
    """Map SQL Server business THROW codes to stable API responses."""
    raw_message = str(getattr(exc, "orig", exc))
    match = re.search(r"\b(515\d{2})\b", raw_message)
    code = int(match.group(1)) if match else None
    status_code = 400 if code and 51500 <= code <= 51512 else 500
    logger.exception("merge/combine stored procedure failed")
    raise HTTPException(status_code=status_code, detail=raw_message) from exc


def _procedure_result(rows: list[dict]) -> dict:
    """Shape the stored procedure rowset into the Android response contract."""
    if not rows:
        raise HTTPException(status_code=500, detail="Stored procedure did not return a result.")

    first = rows[0]
    return {
        "hostRuncard": first["hostRuncard"],
        "resultQty": first["resultQty"],
        "hostQtyBefore": first["hostQtyBefore"],
        "sourceQtyTotal": first["sourceQtyTotal"],
        "workOrder": first["workOrder"],
        "workCenter": first["workCenter"],
        "operation": first["operation"],
        "actionType": first["actionType"],
        "operatorId": first["operatorId"],
        "mergedAt": first["mergedAt"],
        "status": first["status"],
        "sources": [
            {
                "runcard": row["sourceRuncard"],
                "workOrder": row["sourceWorkOrder"],
                "qty": row["sourceQty"],
            }
            for row in rows
        ],
    }


def _execute_merge_combine_procedure(
    db: Session,
    host_runcard: str,
    source_runcards: list[str],
    operator_id: str,
    action_type: str,
) -> dict:
    """Execute dbo.usp_api_merge_combine_runcards and return its API payload."""
    sources = [source.strip() for source in source_runcards if source and source.strip()]
    if not sources:
        raise HTTPException(status_code=400, detail="At least one source runcard is required.")

    try:
        rows = db.execute(
            text(
                """
                EXEC dbo.usp_api_merge_combine_runcards
                    @HostRuncard = :host_runcard,
                    @SourceRcs = :source_rcs,
                    @OperatorId = :operator_id,
                    @ActionType = :action_type
                """
            ),
            {
                "host_runcard": host_runcard,
                "source_rcs": ",".join(sources),
                "operator_id": operator_id,
                "action_type": action_type,
            },
        ).mappings().all()
        db.commit()
        return _procedure_result([dict(row) for row in rows])
    except DBAPIError as exc:
        db.rollback()
        _raise_procedure_http_error(exc)
    except HTTPException:
        db.rollback()
        raise
    except Exception as exc:
        db.rollback()
        logger.exception("merge/combine procedure endpoint failed")
        raise HTTPException(status_code=500, detail=f"Merge/combine failed: {exc}") from exc


@router.get("/runcards/{rc}/merge-candidates")
def get_merge_candidates(rc: str, db: Session = Depends(get_db)) -> list[dict]:
    """Return same-WO merge candidates from the stored procedure."""
    try:
        rows = db.execute(
            text("EXEC dbo.usp_api_get_merge_candidates @Runcard = :runcard"),
            {"runcard": rc},
        ).mappings().all()
        return [dict(row) for row in rows]
    except DBAPIError as exc:
        _raise_procedure_http_error(exc)


@router.get("/runcards/{rc}/combine-candidates")
def get_combine_candidates(rc: str, db: Session = Depends(get_db)) -> list[dict]:
    """Return cross-WO capable combine candidates from the stored procedure."""
    try:
        rows = db.execute(
            text("EXEC dbo.usp_api_get_combine_candidates @Runcard = :runcard"),
            {"runcard": rc},
        ).mappings().all()
        return [dict(row) for row in rows]
    except DBAPIError as exc:
        _raise_procedure_http_error(exc)


@router.post("/runcards/{rc}/merge")
def merge_runcards_with_procedure(
    rc: str,
    request: MergeCombineProcedureRequest,
    db: Session = Depends(get_db),
) -> dict:
    """Execute MERGE through dbo.usp_api_merge_combine_runcards."""
    return _execute_merge_combine_procedure(
        db=db,
        host_runcard=rc,
        source_runcards=request.source_runcards,
        operator_id=request.operator_id,
        action_type="MERGE",
    )


@router.post("/runcards/{rc}/combine")
def combine_runcards_with_procedure(
    rc: str,
    request: MergeCombineProcedureRequest,
    db: Session = Depends(get_db),
) -> dict:
    """Execute COMBINE through dbo.usp_api_merge_combine_runcards."""
    return _execute_merge_combine_procedure(
        db=db,
        host_runcard=rc,
        source_runcards=request.source_runcards,
        operator_id=request.operator_id,
        action_type="COMBINE",
    )


@router.get("/detail/{runcard_no}", response_model=ProductionDetailResponse)
def get_production_detail(runcard_no: str, db: Session = Depends(get_db)) -> ProductionDetailResponse:
    """Return production detail for one runcard using the legacy response shape."""
    try:
        row = db.execute(
            text(
                f"""
                SELECT TOP (1)
                    COALESCE(wo.MATERIAL_DESC, rt.MATERIAL_DESC, op.DESCRIPTION, '') AS description,
                    rd.MATERIAL AS material,
                    CONVERT(varchar(20), rd.QTY) AS rc_quantity,
                    CONVERT(varchar(20), rd.QTY) AS qty_rc,
                    wo.TARGET_QUANTITY AS qty_wo,
                    rd.DATE_CODE AS date_code,
                    rd.WO AS work_order,
                    std.MPQ_REEL AS mpq,
                    rd.ASSY_LOT AS assy_lot,
                    std.WF_LOT AS wafer_lot,
                    wo.ORDER_TYPE AS order_type,
                    wo.UNIT AS uom,
                    rd.LOT_TYPE AS lot_type,
                    CAST(NULL AS varchar(100)) AS reel_number
                FROM {prod_table('Runcard_Detail')} rd
                LEFT JOIN {prod_table('WORKORDER')} wo ON wo.WO = rd.WO
                LEFT JOIN {prod_table('RC_Transection')} rt
                    ON rt.RUNCARD = rd.RUNCARD
                   AND rt.ORDERID = rd.WO
                LEFT JOIN {prod_table('OPERATION')} op ON op.ROUTING_NO = wo.ROUTING_NO
                LEFT JOIN {prod_table('STD_RC_Master')} std ON std.WO = rd.WO
                WHERE rd.RUNCARD = :runcard
                ORDER BY rt.OPERATION
                """
            ),
            {"runcard": runcard_no},
        ).mappings().first()

        if row is None:
            raise HTTPException(status_code=404, detail="Runcard not found.")

        return ProductionDetailResponse(**dict(row))
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("get_production_detail failed")
        raise HTTPException(status_code=500, detail=f"Get production detail failed: {exc}") from exc


@router.get("/runcards/{runcard_no}/confirm-context")
def get_confirm_context(runcard_no: str, db: Session = Depends(get_db)) -> dict:
    try:
        row = db.execute(
            text(
                f"""
                SELECT TOP (1)
                    rd.RUNCARD AS runcardNo,
                    rd.WO AS workOrder,
                    COALESCE(NULLIF(tx.ROUTING_NO, ''), NULLIF(wo.ROUTING_NO, '')) AS routingNo,
                    COALESCE(NULLIF(rd.MATERIAL, ''), NULLIF(tx.MATERIAL, ''), NULLIF(wo.MATERIAL, '')) AS material,
                    COALESCE(NULLIF(wo.MATERIAL_DESC, ''), NULLIF(tx.MATERIAL_DESC, '')) AS materialDesc,
                    tx.OPERATION AS operation,
                    tx.WORK_CENTER AS workCenter,
                    tx.WORK_CENTER_TEXT AS workCenterText,
                    CAST(ISNULL(rd.QTY, 0) AS int) AS receiveQty,
                    CAST(ISNULL(rd.QTY, 0) AS int) AS qtyRc,
                    wo.TARGET_QUANTITY AS qtyWo,
                    wo.UNIT AS unit,
                    tx.PLANT AS plant,
                    tx.ID AS transactionId
                FROM {prod_table('Runcard_Detail')} rd
                LEFT JOIN {prod_table('WORKORDER')} wo ON wo.WO = rd.WO
                OUTER APPLY (
                    SELECT TOP (1) rt.*
                    FROM {prod_table('RC_Transection')} rt
                    WHERE rt.RUNCARD = rd.RUNCARD
                      AND ISNULL(rt.CANC_FLAG, '') <> 'X'
                    ORDER BY
                        CASE WHEN rt.YIELD IS NULL OR rt.YIELD = '' THEN 0 ELSE 1 END,
                        rt.ID ASC
                ) tx
                WHERE rd.RUNCARD = :runcard
                  AND ISNULL(rd.FLAG, '') <> 'X'
                ORDER BY rd.ID DESC
                """
            ),
            {"runcard": runcard_no},
        ).mappings().first()

        if row is None:
            raise HTTPException(status_code=404, detail="Active runcard context not found.")

        return dict(row)
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("get_confirm_context failed")
        raise HTTPException(status_code=500, detail=f"Get confirm context failed: {exc}") from exc


@router.get("/runcards/{runcard_no}/split-history")
def get_split_history(runcard_no: str, db: Session = Depends(get_db)) -> list[dict]:
    try:
        rows = db.execute(
            text(
                f"""
                SELECT
                    CHILDRUNCARD AS childRuncard,
                    CHILDASSYRUNCARD AS childAssyLot,
                    CHILDQTY AS childQty,
                    MOTHERRUNCARD AS motherRuncard,
                    MOTHERASSYRUNCARD AS motherAssyLot,
                    MOTHERQTY AS motherQty,
                    SUMQTY AS sumQty,
                    WORKCENTER AS workCenter,
                    OPERATION AS operation,
                    CBY AS cby,
                    CDATE AS cdate
                FROM {prod_table('RUNCARD_HISTORY')}
                WHERE RCTYPE = 'SPLIT'
                  AND (MOTHERRUNCARD = :runcard OR CHILDRUNCARD = :runcard)
                ORDER BY CDATE DESC
                """
            ),
            {"runcard": runcard_no},
        ).mappings().all()

        result = []
        for row in rows:
            item = dict(row)
            item.update(
                {
                    "runcard": row["childRuncard"] or "",
                    "assyLot": row["childAssyLot"] or "",
                    "qty": row["childQty"],
                    "mother": row["motherRuncard"] or "",
                    "motherQty": row["motherQty"],
                    "wc": row["workCenter"] or "",
                    "cdate": "" if row["cdate"] is None else str(row["cdate"]),
                }
            )
            result.append(item)
        return result
    except Exception as exc:
        logger.exception("get_split_history failed")
        raise HTTPException(status_code=500, detail=f"Get split history failed: {exc}") from exc


@router.get("/runcards/{runcard_no}/opers")
def get_runcard_operations(runcard_no: str, db: Session = Depends(get_db)) -> list[dict]:
    """Return operation tracking rows for one runcard."""
    runcard = db.query(RuncardDetail).filter(RuncardDetail.RUNCARD == runcard_no).first()

    if runcard is None:
        raise HTTPException(status_code=404, detail="Runcard not found.")

    transactions = (
        db.query(RCTransection)
        .filter(RCTransection.RUNCARD == runcard_no)
        .order_by(RCTransection.OPERATION.asc(), RCTransection.ID.asc())
        .all()
    )

    rows = []
    for transaction in transactions:
        status = transaction.PRD_STATUS or transaction.B2B_STATUS or transaction.EX_STATUS or ""
        rows.append(
            {
                "operation": transaction.OPERATION or "",
                "work_center": transaction.WORK_CENTER or "",
                "work_center_text": transaction.WORK_CENTER_TEXT or "",
                "status": status,
                "oper": transaction.OPERATION or "",
                "wc": transaction.WORK_CENTER or "",
                "description": transaction.DESCRIPTION or transaction.WORK_CENTER_TEXT or "",
                "workCenter": transaction.WORK_CENTER or "",
                "receive": transaction.RECEIVE_QTY or "",
                "yield": transaction.YIELD or "",
                "scrap": transaction.SCRAP or "",
                "move": transaction.MOVE_QTY or "",
                "percentYield": "" if transaction.YieldTrig is None else str(transaction.YieldTrig),
                "receiveDate": "" if transaction.RECEIVE_DATE is None else str(transaction.RECEIVE_DATE),
                "confirmDate": "" if transaction.CONFIRM_DATE is None else str(transaction.CONFIRM_DATE),
                "en": transaction.CBY or transaction.RECEIVE_BY or "",
            }
        )

    return rows


@router.post("/confirm")
def save_production_confirm(request: ProductionConfirmRequest, db: Session = Depends(get_db)) -> dict:
    """Save production confirmation, advance routing, and write a transaction log."""
    try:
        now = datetime.now()
        with db.begin():
            current = db.execute(
                text(
                    f"""
                    SELECT TOP (1)
                        rd.RUNCARD AS runcard_no,
                        rd.WO AS work_order,
                        rd.MATERIAL AS material,
                        CAST(ISNULL(rd.QTY, 0) AS int) AS current_qty,
                        rd.START_WC AS start_wc,
                        COALESCE(NULLIF(wo.ROUTING_NO, ''), NULLIF(tx.ROUTING_NO, '')) AS routing_no
                    FROM {prod_table('Runcard_Detail')} rd WITH (UPDLOCK, HOLDLOCK)
                    LEFT JOIN {prod_table('WORKORDER')} wo ON wo.WO = rd.WO
                    OUTER APPLY (
                        SELECT TOP (1) rt.ROUTING_NO
                        FROM {prod_table('RC_Transection')} rt
                        WHERE rt.RUNCARD = rd.RUNCARD
                          AND ISNULL(rt.CANC_FLAG, '') <> 'X'
                        ORDER BY rt.ID DESC
                    ) tx
                    WHERE rd.RUNCARD = :runcard
                      AND ISNULL(rd.FLAG, '') <> 'X'
                    ORDER BY rd.ID DESC
                    """
                ),
                {"runcard": request.runcard_no},
            ).mappings().first()

            if current is None:
                raise HTTPException(status_code=404, detail="Active runcard not found.")

            routing_no = (current["routing_no"] or "").strip()
            next_route = None
            if routing_no:
                next_route = db.execute(
                    text(
                        f"""
                        SELECT TOP (1)
                            OPERATION_NUMBER AS next_oper,
                            WORK_CENTER AS next_work_center
                        FROM {prod_table('OPERATION')}
                        WHERE ROUTING_NO = :routing_no
                          AND (
                                TRY_CONVERT(int, OPERATION_NUMBER) > TRY_CONVERT(int, :current_oper)
                             OR (
                                    TRY_CONVERT(int, OPERATION_NUMBER) IS NULL
                                 OR TRY_CONVERT(int, :current_oper) IS NULL
                                )
                                AND OPERATION_NUMBER > :current_oper
                          )
                        ORDER BY
                            CASE WHEN TRY_CONVERT(int, OPERATION_NUMBER) IS NULL THEN 1 ELSE 0 END,
                            TRY_CONVERT(int, OPERATION_NUMBER),
                            OPERATION_NUMBER
                        """
                    ),
                    {"routing_no": routing_no, "current_oper": request.current_oper},
                ).mappings().first()

            next_oper = str((next_route or {}).get("next_oper") or request.current_oper).strip()
            next_work_center = str((next_route or {}).get("next_work_center") or current["start_wc"] or "").strip()
            updated = db.execute(
                text(
                    f"""
                    UPDATE {prod_table('Runcard_Detail')}
                    SET QTY = :good_qty,
                        START_WC = :next_work_center,
                        OLD_QTY = COALESCE(OLD_QTY, QTY),
                        CBY = :emp_id
                    WHERE RUNCARD = :runcard
                      AND ISNULL(FLAG, '') <> 'X'
                    """
                ),
                {
                    "good_qty": request.good_qty,
                    "next_work_center": next_work_center,
                    "emp_id": request.emp_id,
                    "runcard": request.runcard_no,
                },
            ).rowcount

            if updated <= 0:
                raise HTTPException(status_code=400, detail="Runcard quantity was not updated.")

            db.execute(
                text(
                    f"""
                    INSERT INTO {prod_table('RC_Transection')}
                        (ORDERID, ROUTING_NO, MATERIAL, RUNCARD, OPERATION,
                         RECEIVE_QTY, YIELD, SCRAP, MOVE_QTY, PRD_STATUS,
                         TYPE, MESSAGE, FLAG, CBY, RECEIVE_BY, CDATE, UDATE, CONFIRM_DATE)
                    VALUES
                        (:work_order, :routing_no, :material, :runcard, :current_oper,
                         :receive_qty, :good_qty, :reject_qty, :good_qty, 'CONFIRM',
                         'SAVE_CONF', 'Save Confirm from handheld', '1', :emp_id, :emp_id,
                         :now, :now, :now)
                    """
                ),
                {
                    "work_order": current["work_order"],
                    "routing_no": routing_no,
                    "material": current["material"],
                    "runcard": request.runcard_no,
                    "current_oper": request.current_oper,
                    "receive_qty": request.good_qty + request.reject_qty,
                    "good_qty": str(request.good_qty),
                    "reject_qty": str(request.reject_qty),
                    "emp_id": request.emp_id,
                    "now": now,
                },
            )

        return {
            "success": True,
            "message": "Save Confirm completed.",
            "runcardNo": request.runcard_no,
            "currentOper": request.current_oper,
            "nextOper": next_oper,
            "goodQty": request.good_qty,
            "rejectQty": request.reject_qty,
        }

    except HTTPException:
        db.rollback()
        raise
    except Exception as exc:
        db.rollback()
        logger.exception("save_production_confirm failed")
        raise HTTPException(status_code=500, detail=f"Save production confirmation failed: {exc}") from exc


@router.post("/split")
def split_runcard(request: SplitRequest, db: Session = Depends(get_db)) -> dict:
    """Split a runcard, auto-generate the child number, and write split logs."""
    try:
        now = datetime.now()
        date_prefix = request.runcard_no[:6]
        mother_reference = request.runcard_no[:10]

        if len(date_prefix) != 6 or not date_prefix.isdigit():
            raise HTTPException(status_code=400, detail="Parent Runcard must start with a 6-digit date prefix.")

        with db.begin():
            parent = db.execute(
                text(f"""
                    SELECT TOP (1)
                        RUNCARD,
                        WO,
                        MATERIAL,
                        START_WC,
                        ASSY_LOT,
                        ASSY_LOT_FULL,
                        CAST(ISNULL(QTY, 0) AS int) AS parent_qty
                    FROM {prod_table('Runcard_Detail')} WITH (UPDLOCK, HOLDLOCK)
                    WHERE RUNCARD = :runcard_no
                      AND ISNULL(FLAG, '') <> 'X'
                    ORDER BY ID DESC
                """),
                {"runcard_no": request.runcard_no},
            ).mappings().first()

            if parent is None:
                raise HTTPException(status_code=404, detail="Parent Runcard not found.")

            parent_qty = int(parent["parent_qty"] or 0)

            if parent_qty <= request.split_qty:
                raise HTTPException(
                    status_code=400,
                    detail="Parent Runcard must have at least 1 quantity left.",
                )

            max_runcard = db.execute(
                text(f"""
                    SELECT MAX(RUNCARD)
                    FROM {prod_table('Runcard_Detail')} WITH (UPDLOCK, HOLDLOCK)
                    WHERE RUNCARD LIKE :date_prefix_pattern
                      AND LEN(RUNCARD) = 10
                      AND RUNCARD NOT LIKE '%[^0-9]%'
                """),
                {"date_prefix_pattern": f"{date_prefix}%"},
            ).scalar()

            next_sequence = int(str(max_runcard)[-4:]) + 1 if max_runcard else 1

            if next_sequence > 9999:
                raise HTTPException(status_code=400, detail="Runcard sequence has exceeded 9999 for this date prefix.")

            new_10_digit_runcard = f"{date_prefix}{next_sequence:04d}"
            parent_assy_lot_full = str(parent["ASSY_LOT_FULL"] or parent["ASSY_LOT"] or request.runcard_no)
            parent_assy_lot = str(parent["ASSY_LOT"] or parent_assy_lot_full)
            assy_lot_base = parent_assy_lot[:10]
            assy_full_base = parent_assy_lot_full
            parent_suffix = 0

            if "-" in parent_assy_lot_full:
                base_part, suffix_part = parent_assy_lot_full.rsplit("-", 1)
                if suffix_part.isdigit():
                    assy_full_base = base_part
                    parent_suffix = int(suffix_part)

            sibling_assy_full_rows = db.execute(
                text(f"""
                    SELECT ASSY_LOT_FULL
                    FROM {prod_table('Runcard_Detail')} WITH (UPDLOCK, HOLDLOCK)
                    WHERE ASSY_LOT_FULL LIKE :assy_full_pattern
                """),
                {"assy_full_pattern": f"{assy_full_base}-%"},
            ).scalars().all()

            highest_suffix = parent_suffix
            for assy_lot_full in sibling_assy_full_rows:
                suffix = str(assy_lot_full or "").rsplit("-", 1)[-1]
                if suffix.isdigit():
                    highest_suffix = max(highest_suffix, int(suffix))

            new_assy_lot_full = f"{assy_full_base}-{highest_suffix + 1:02d}"
            remaining_qty = parent_qty - request.split_qty

            db.execute(
                text(f"""
                    UPDATE {prod_table('Runcard_Detail')}
                    SET QTY = QTY - :split_qty,
                        OLD_QTY = COALESCE(OLD_QTY, :parent_qty),
                        CBY = :emp_id
                    WHERE RUNCARD = :runcard_no
                      AND ISNULL(FLAG, '') <> 'X'
                """),
                {
                    "split_qty": request.split_qty,
                    "parent_qty": parent_qty,
                    "emp_id": request.emp_id,
                    "runcard_no": request.runcard_no,
                },
            )

            sql_insert_child = f"""
                INSERT INTO {prod_table('Runcard_Detail')}
                    (PLANT, WO, MATERIAL, RC_TYPE, RUNCARD, ASSY_LOT,
                     ASSY_LOT_FULL, LOT_TYPE, LOT_GROUP, TEST_LOT, SEMI_PART,
                     PART_ID, DATE_CODE, WAFER_FAB, QTY, OLD_QTY, MOTHER,
                     MOTHER_QTY, MODEL, MARKING1, MARKING2, MI_TEST,
                     CUST_LOT_NUM, SRC_LOT_NUMBER, TSMSpec, BSMSpec, FLAG,
                     WC_HOLD, CONF_FIN, CONF_YIELD, CONF_SCRAP, GR_LOCATION,
                     GR_FIN, CDATE, CBY, B2B_STATUS, SRC_LOT_QTY,
                     FLAG_COMBINE, START_WC, CONFFINDATE, EXCEPTION)
                SELECT
                     PLANT, WO, MATERIAL, RC_TYPE, :new_runcard,
                     :assy_lot_base, :new_assy_lot_full, LOT_TYPE,
                     LOT_GROUP, TEST_LOT, SEMI_PART, PART_ID, DATE_CODE,
                     WAFER_FAB, :split_qty, :parent_qty, :mother_ref,
                     :parent_qty, MODEL, MARKING1, MARKING2, MI_TEST,
                     CUST_LOT_NUM, SRC_LOT_NUMBER, TSMSpec, BSMSpec, '1',
                     WC_HOLD, CONF_FIN, CONF_YIELD, CONF_SCRAP, GR_LOCATION,
                     GR_FIN, :now, :emp_id, B2B_STATUS, SRC_LOT_QTY,
                     FLAG_COMBINE, START_WC, CONFFINDATE, EXCEPTION
                FROM {prod_table('Runcard_Detail')}
                WHERE RUNCARD = :parent_runcard
                  AND ISNULL(FLAG, '') <> 'X'
            """
            db.execute(
                text(sql_insert_child),
                {
                    "new_runcard": new_10_digit_runcard,
                    "new_assy_lot_full": new_assy_lot_full,
                    "assy_lot_base": assy_lot_base,
                    "split_qty": request.split_qty,
                    "parent_qty": parent_qty,
                    "mother_ref": mother_reference,
                    "emp_id": request.emp_id,
                    "parent_runcard": request.runcard_no,
                    "now": now,
                },
            )

            db.execute(
                text(f"""
                    INSERT INTO {prod_table('RC_Transection')}
                        (ORDERID, MATERIAL, RUNCARD, OPERATION, WORK_CENTER,
                         RECEIVE_QTY, YIELD, SCRAP, MOVE_QTY, TYPE, MESSAGE,
                         FLAG, CBY, CDATE, UDATE)
                    VALUES
                        (:work_order, :material, :parent_runcard, :current_oper,
                         :work_center, :parent_qty, :remaining_qty, '0',
                         :remaining_qty, 'SPLIT', 'SPLIT', '1', :emp_id, :now, :now),
                        (:work_order, :material, :child_runcard, :current_oper,
                         :work_center, :split_qty, :split_qty, '0',
                         :split_qty, 'SPLIT', 'SPLIT', '1', :emp_id, :now, :now)
                """),
                {
                    "work_order": parent["WO"],
                    "material": parent["MATERIAL"],
                    "parent_runcard": request.runcard_no,
                    "child_runcard": new_10_digit_runcard,
                    "current_oper": request.current_oper,
                    "work_center": parent["START_WC"],
                    "parent_qty": str(parent_qty),
                    "remaining_qty": str(remaining_qty),
                    "split_qty": str(request.split_qty),
                    "emp_id": request.emp_id,
                    "now": now,
                },
            )

        return {
            "success": True,
            "childRuncardNo": new_10_digit_runcard,
            "childAssyLotFull": new_assy_lot_full,
        }

    except HTTPException:
        db.rollback()
        raise
    except Exception as exc:
        db.rollback()
        logger.exception("split_runcard failed")
        raise HTTPException(status_code=500, detail=f"Split runcard failed: {exc}") from exc


@router.post("/reject")
def reject_runcard(request: RejectRequest, db: Session = Depends(get_db)) -> dict:
    """Deduct reject quantity, accumulate scrap, and write reject audit rows."""
    try:
        reject_qty_float = float(request.reject_qty)
        reject_qty_int = int(reject_qty_float)
        reason_code = request.reason_code.strip()
        emp_id = request.emp_id.strip()

        if reject_qty_float != reject_qty_int:
            raise HTTPException(status_code=400, detail="Reject quantity must be a whole number.")
        if reject_qty_int <= 0:
            raise HTTPException(status_code=400, detail="Reject quantity must be greater than zero.")
        if not reason_code:
            raise HTTPException(status_code=400, detail="Reason code is required.")
        if len(reason_code) > 10:
            raise HTTPException(status_code=400, detail="Reason code must not exceed 10 characters.")
        if not emp_id:
            raise HTTPException(status_code=400, detail="Employee ID is required.")
        if len(emp_id) > 6:
            raise HTTPException(status_code=400, detail="Employee ID must not exceed 6 characters.")

        now = datetime.now()

        with db.begin():
            parent = db.execute(
                text(f"""
                    SELECT TOP (1)
                        RUNCARD,
                        WO,
                        MATERIAL,
                        START_WC,
                        ASSY_LOT,
                        ASSY_LOT_FULL,
                        CAST(ISNULL(QTY, 0) AS int) AS parent_qty
                    FROM {prod_table('Runcard_Detail')} WITH (UPDLOCK, HOLDLOCK)
                    WHERE RUNCARD = :runcard_no
                      AND ISNULL(FLAG, '') <> 'X'
                    ORDER BY ID DESC
                """),
                {"runcard_no": request.runcard_no},
            ).mappings().first()

            if parent is None:
                raise HTTPException(status_code=404, detail="Parent Runcard not found.")

            parent_qty = int(parent["parent_qty"] or 0)

            if parent_qty < reject_qty_int:
                raise HTTPException(status_code=400, detail="Reject quantity must not exceed current runcard quantity.")

            reason_desc = db.execute(
                text(f"""
                    SELECT TOP (1) Description
                    FROM {prod_table('REJECT_REASON')}
                    WHERE ReasonCode = :reason_code
                """),
                {"reason_code": reason_code},
            ).scalar() or ""

            remaining_qty = parent_qty - reject_qty_int

            updated = db.execute(
                text(f"""
                    UPDATE {prod_table('Runcard_Detail')}
                    SET QTY = QTY - :reject_qty,
                        CONF_SCRAP = ISNULL(CONF_SCRAP, 0) + :reject_qty,
                        OLD_QTY = COALESCE(OLD_QTY, :parent_qty),
                        CBY = :emp_id
                    WHERE RUNCARD = :runcard_no
                      AND ISNULL(FLAG, '') <> 'X'
                      AND QTY >= :reject_qty
                """),
                {
                    "reject_qty": reject_qty_int,
                    "parent_qty": parent_qty,
                    "emp_id": emp_id,
                    "runcard_no": request.runcard_no,
                },
            ).rowcount

            if updated <= 0:
                raise HTTPException(status_code=400, detail="Runcard quantity was not updated. Reload and try again.")

            db.execute(
                text(f"""
                    INSERT INTO {prod_table('REJECT_DETAIL')}
                        (wo, runcard, wc_no, operation, reject_qty, reason_code,
                         reason_desc, [date], cby, STATION)
                    VALUES
                        (:wo, :runcard, :wc_no, :operation, :reject_qty, :reason_code,
                         :reason_desc, :now, :emp_id, :station)
                """),
                {
                    "wo": parent["WO"],
                    "runcard": request.runcard_no,
                    "wc_no": parent["START_WC"],
                    "operation": request.current_oper,
                    "reject_qty": str(reject_qty_int),
                    "reason_code": reason_code,
                    "reason_desc": str(reason_desc)[:200],
                    "now": now,
                    "emp_id": emp_id,
                    "station": parent["START_WC"],
                },
            )

            db.execute(
                text(f"""
                    INSERT INTO {prod_table('RC_Transection')}
                        (ORDERID, MATERIAL, RUNCARD, OPERATION, WORK_CENTER,
                         RECEIVE_QTY, YIELD, SCRAP, MOVE_QTY, TYPE, MESSAGE,
                         FLAG, CBY, CDATE, UDATE)
                    VALUES
                        (:work_order, :material, :runcard, :current_oper,
                         :work_center, :parent_qty, :remaining_qty, :reject_qty,
                         :remaining_qty, 'REJECT', 'REJECT', '1', :emp_id, :now, :now)
                """),
                {
                    "work_order": parent["WO"],
                    "material": parent["MATERIAL"],
                    "runcard": request.runcard_no,
                    "current_oper": request.current_oper,
                    "work_center": parent["START_WC"],
                    "parent_qty": str(parent_qty),
                    "remaining_qty": str(remaining_qty),
                    "reject_qty": str(reject_qty_int),
                    "emp_id": emp_id,
                    "now": now,
                },
            )

        return {
            "success": True,
            "message": "Reject saved.",
            "runcardNo": request.runcard_no,
            "rejectQty": reject_qty_int,
            "remainingQty": remaining_qty,
        }

    except HTTPException:
        db.rollback()
        raise
    except Exception as exc:
        db.rollback()
        logger.exception("reject_runcard failed")
        raise HTTPException(status_code=500, detail=f"Reject runcard failed: {exc}") from exc
