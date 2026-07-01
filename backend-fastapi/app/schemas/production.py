"""Pydantic schemas for production transaction APIs."""
from __future__ import annotations

from pydantic import BaseModel, Field


class ProductionDetailResponse(BaseModel):
    """Response body for the Android production detail screen."""

    description: str | None = None
    material: str | None = None
    rc_quantity: str | None = None
    qty_rc: str | None = None
    qty_wo: str | None = None
    date_code: str | None = None
    work_order: str | None = None
    mpq: str | None = None
    assy_lot: str | None = None
    wafer_lot: str | None = Field(default=None, serialization_alias="waferLot")
    order_type: str | None = Field(default=None, serialization_alias="orderType")
    uom: str | None = Field(default=None, serialization_alias="uom")
    lot_type: str | None = None
    reel_number: str | None = None


class SplitRequest(BaseModel):
    """Request body for Android Split Runcard using camelCase keys."""

    runcard_no: str = Field(..., alias="runcardNo")
    current_oper: str = Field(..., alias="currentOper")
    emp_id: str = Field(..., alias="empId")
    split_qty: int = Field(..., alias="splitQty", gt=0)

    class Config:
        populate_by_name = True


class ProductionConfirmRequest(BaseModel):
    """Request body for Android Save Confirm using camelCase keys."""

    runcard_no: str = Field(..., alias="runcardNo")
    current_oper: str = Field(..., alias="currentOper")
    emp_id: str = Field(..., alias="empId")
    good_qty: int = Field(..., alias="goodQty")
    reject_qty: int = Field(..., alias="rejectQty")

    class Config:
        populate_by_name = True


class RejectRequest(BaseModel):
    """Request body for Android Reject/Scrap using camelCase keys."""

    runcard_no: str = Field(..., alias="runcardNo")
    current_oper: str = Field(..., alias="currentOper")
    emp_id: str = Field(..., alias="empId")
    reject_qty: float = Field(..., alias="rejectQty", gt=0)
    reason_code: str = Field(..., alias="reasonCode")

    class Config:
        populate_by_name = True


class MergeCombineProcedureRequest(BaseModel):
    """Request body for stored-procedure based merge/combine actions."""

    source_runcards: list[str] = Field(..., alias="sourceRuncards", min_length=1)
    operator_id: str = Field(..., alias="operatorId")

    class Config:
        populate_by_name = True
