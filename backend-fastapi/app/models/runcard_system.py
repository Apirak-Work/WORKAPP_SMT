"""SQLAlchemy ORM models for the StarsOne production database.

Column names/types mirror the actual MS SQL Server schema (no business logic).
Sources: DDL for Runcard_Detail, RC_Transection, WORKORDER, RUNCARD_HISTORY,
REJECT_DETAIL. USERLOGIN is inferred from query usage only (see caveat).

NOTE: several qty/yield fields are stored as varchar in the DB and are therefore
mapped as String here to match reality, not as numeric types.
"""
from __future__ import annotations

from sqlalchemy import Column, DateTime, Float, Integer, String

from app.database import Base


class RuncardDetail(Base):
    """Maps to dbo.Runcard_Detail (lot master rows used by Split/Merge)."""
    __tablename__ = "Runcard_Detail"

    ID = Column(Integer, primary_key=True, autoincrement=True)
    PLANT = Column(String(10))
    WO = Column(String(20))
    MATERIAL = Column(String(50))
    RC_TYPE = Column(String(50))
    RUNCARD = Column(String(10), index=True)
    ASSY_LOT = Column(String(50))
    ASSY_LOT_FULL = Column(String(50))
    LOT_TYPE = Column(String(50))
    LOT_GROUP = Column(String(50))
    TEST_LOT = Column(String(50))
    SEMI_PART = Column(String(100))
    PART_ID = Column(String(100))
    DATE_CODE = Column(String(10))
    WAFER_FAB = Column(String(50))
    QTY = Column(Integer)
    OLD_QTY = Column(Integer)
    MOTHER = Column(String(50))
    MOTHER_QTY = Column(Integer)
    MODEL = Column(String(100))
    MARKING1 = Column(String(100))
    MARKING2 = Column(String(100))
    MI_TEST = Column(String(100))
    CUST_LOT_NUM = Column(String(50))
    SRC_LOT_NUMBER = Column(String(50))
    TSMSpec = Column(String(150))
    BSMSpec = Column(String(150))
    FLAG = Column(String(1))
    WC_HOLD = Column(String(10))
    CONF_FIN = Column(String(1))
    CONF_YIELD = Column(Integer)
    CONF_SCRAP = Column(Integer)
    GR_LOCATION = Column(String(50))
    GR_FIN = Column(String(1))
    CDATE = Column(DateTime)
    CBY = Column(String(6))
    B2B_STATUS = Column(String(1))
    SRC_LOT_QTY = Column(Integer)
    FLAG_COMBINE = Column(String(5))
    START_WC = Column(String(10))
    CONFFINDATE = Column(DateTime)
    EXCEPTION = Column(String(50))


class RCTransection(Base):
    """Maps to dbo.RC_Transection (per-operation routing/transaction rows)."""
    __tablename__ = "RC_Transection"

    ID = Column(Integer, primary_key=True, autoincrement=True)
    PLANT = Column(String(5))
    ORDERID = Column(String(50))
    ROUTING_NO = Column(String(50))
    MATERIAL = Column(String(50))
    MATERIAL_DESC = Column(String(255))
    PD_TYPE = Column(String(50))
    RUNCARD = Column(String(20), index=True)
    OPERATION = Column(String(5))
    WORK_CENTER = Column(String(50))
    WORK_CENTER_TEXT = Column(String(255))
    FIN_CONF = Column(String(2))
    EXEC_START_DATE = Column(String(50))
    EXEC_START_TIME = Column(String(50))
    RECEIVE_QTY = Column(String(20))   # varchar in DB
    RECEIVE_BY = Column(String(6))
    POSTG_DATE = Column(String(50))
    YIELD = Column(String(20))         # varchar in DB
    SCRAP = Column(String(20))         # varchar in DB
    REWORK = Column(String(20))
    MOVE_QTY = Column(String(20))      # varchar in DB
    YieldTrig = Column(Float)
    CONF_QUAN_UNIT = Column(String(10))
    CONF_ACTIVITY1 = Column(String(20))
    CONF_ACTI_UNIT1 = Column(String(20))
    CONF_ACTIVITY2 = Column(String(20))
    CONF_ACTI_UNIT2 = Column(String(20))
    CONF_ACTIVITY3 = Column(String(20))
    CONF_ACTI_UNIT3 = Column(String(20))
    EXEC_FIN_DATE = Column(String(50))
    EXEC_FIN_TIME = Column(String(50))
    DEV_REASON = Column(String(4))
    BREAK_TIME = Column(String(15))
    BREAK_UNIT = Column(String(10))
    CONF_TEXT = Column(String(50))
    TYPE = Column(String(10))
    MESSAGE = Column(String(256))
    CONF_NO = Column(String(10))
    CONF_CNT = Column(String(8))
    SAP_FLAG = Column(String(1))
    FLAG = Column(String(1))
    CDATE = Column(DateTime)
    UDATE = Column(DateTime)
    COMP_NAME = Column(String(50))
    CBY = Column(String(6))
    CANC_FLAG = Column(String(1))
    CANC_DATE = Column(String(50))
    CANC_BY = Column(String(6))
    RECIPE_FLAG = Column(String(1))
    QA_FLAG = Column(String(1))
    PRD_STATUS = Column(String(20))
    EX_STATUS = Column(String(10))
    WF_STATUS = Column(String(10))
    QA_STATUS = Column(String(20))
    QA_REASON = Column(String(250))
    REASONCODE = Column(String(20))
    QA_EN = Column(String(6))
    PREVIOUS_WC = Column(String(20))
    CURRENT_WC = Column(String(20))
    NEXT_WC = Column(String(20))
    FLAG_SPLIT = Column(String(1))
    SPLIT_TO = Column(String(50))
    FLAG_MERGE = Column(String(1))
    MERGE_FROM = Column(String(250))
    DESCRIPTION = Column(String(255))
    B2B_STATUS = Column(String(50))
    ERROR = Column(String(500))
    RECEIVE_DATE = Column(DateTime)
    CONFIRM_DATE = Column(DateTime)
    LOCATION = Column(String(10))
    ICTEST_STATUS = Column(String(10))
    RP_STATUS = Column(String(10))
    RDown_STATUS = Column(String(10))


class Workorder(Base):
    """Maps to dbo.WORKORDER (SAP work-order header)."""
    __tablename__ = "WORKORDER"

    id = Column(Integer, primary_key=True, autoincrement=True)
    PLANT = Column(String(50))
    WO = Column(String(50), index=True)
    MATERIAL = Column(String(50))
    MATERIAL_DESC = Column(String(255))
    ORDER_TYPE = Column(String(50))
    RESERVATION_NUMBER = Column(String(50))
    ROUTING_NO = Column(String(50))
    SALES_ORDER = Column(String(50))
    SALES_ORDER_ITEM = Column(String(50))
    TARGET_QUANTITY = Column(String(50))   # nvarchar in DB
    YIELD = Column(String(50))             # nvarchar in DB
    UNIT = Column(String(50))
    UNIT_ISO = Column(String(50))
    SYSTEM_STATUS = Column(String(50))
    START_DATE = Column(String(50))
    START_TIME = Column(String(50))
    FINISH_DATE = Column(String(50))
    FINISH_TIME = Column(String(50))
    PRODUCTION_START_DATE = Column(String(50))
    PRODUCTION_FINISH_DATE = Column(String(50))
    SCHED_RELEASE_DATE = Column(String(50))
    SCHED_START_TIME = Column(String(50))
    SCHED_FIN_TIME = Column(String(50))
    ENTERED_BY = Column(String(50))
    ENTER_DATE = Column(String(50))
    CDATE = Column(DateTime)
    CBY = Column(String(50))


class RuncardHistory(Base):
    """Maps to dbo.RUNCARD_HISTORY (Split/Merge/Combine audit rows)."""
    __tablename__ = "RUNCARD_HISTORY"

    ID = Column(Integer, primary_key=True, autoincrement=True)
    RCTYPE = Column(String(50))
    CHILDWO = Column(String(20))
    CHILDRUNCARD = Column(String(10), index=True)
    CHILDASSYRUNCARD = Column(String(50))
    CHILDQTY = Column(Integer)
    MOTHERWO = Column(String(20))
    MOTHERRUNCARD = Column(String(10), index=True)
    MOTHERASSYRUNCARD = Column(String(50))
    MOTHERQTY = Column(Integer)
    SUMQTY = Column(Integer)
    B2BQTY = Column(Integer)
    WORKCENTER = Column(String(20))
    OPERATION = Column(String(5))
    HOLDREMARK = Column(String(250))
    HOLDCOMMENT = Column(String(500))
    RELEASECOMMENT = Column(String(500))
    CBY = Column(String(6))
    CDATE = Column(DateTime)


class RejectDetail(Base):
    """Maps to dbo.REJECT_DETAIL (per-lot reject rows)."""
    __tablename__ = "REJECT_DETAIL"

    id = Column(Integer, primary_key=True, autoincrement=True)
    wo = Column(String(50))
    runcard = Column(String(20), index=True)
    wc_no = Column(String(10))
    operation = Column(String(5))
    reject_qty = Column(String(20))        # varchar in DB
    reason_code = Column(String(10))
    reason_desc = Column(String(200))
    date = Column(DateTime)                # DDL column is `date`
    cby = Column(String(6))
    MotherCombine = Column(String(20))
    STATION = Column(String(10))


class UserLogin(Base):
    """Maps to dbo.USERLOGIN.

    CAVEAT: not present in the DDL sheet. Columns/PK are inferred from query
    usage only (USER_LOGIN, USER_EN); lengths are best-guess and the primary
    key is assumed. Confirm the real schema before relying on this model.
    """
    __tablename__ = "USERLOGIN"

    USER_EN = Column(String(6), primary_key=True)   # assumed PK (employee no.)
    USER_LOGIN = Column(String(50))                 # AD username; length guessed
