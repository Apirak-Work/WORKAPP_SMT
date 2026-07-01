"""SQLAlchemy engine + session wiring for the FastAPI backend.

Reads DB settings from the secure env file (never committed, never echoed).
"""
from __future__ import annotations

import os
import urllib.parse

from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base

# Strictly load the secure local env file. Do not create a dummy one.
ENV_PATH = r"C:\Users\apirak-mo\Desktop\Safe\db.env"
load_dotenv(ENV_PATH)


def _required(key: str) -> str:
    value = os.getenv(key)
    if value is None or value.strip() == "":
        raise RuntimeError(f"Missing required env var '{key}' in db.env")
    return value.strip()


def _yes_no(key: str, default: bool) -> str:
    raw = os.getenv(key)
    if raw is None or raw.strip() == "":
        return "yes" if default else "no"
    return "yes" if raw.strip().lower() in {"1", "true", "yes", "y"} else "no"


def _build_connection_url() -> str:
    host = _required("DB_HOST")
    port = _required("DB_PORT")
    database = _required("DB_NAME")
    user = _required("DB_USER")
    password = _required("DB_PASSWORD")
    encrypt = _yes_no("DB_ENCRYPT", default=True)
    trust_cert = _yes_no("DB_TRUST_SERVER_CERTIFICATE", default=True)

    odbc_str = (
        "DRIVER={ODBC Driver 17 for SQL Server};"
        f"SERVER={host},{port};"
        f"DATABASE={database};"
        f"UID={user};"
        f"PWD={password};"
        f"Encrypt={encrypt};"
        f"TrustServerCertificate={trust_cert};"
        "Connection Timeout=15;"
    )
    return "mssql+pyodbc:///?odbc_connect=" + urllib.parse.quote_plus(odbc_str)


# Engine is created once at import; pool_pre_ping recycles dead connections.
engine = create_engine(_build_connection_url(), pool_pre_ping=True, future=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, future=True)
Base = declarative_base()


def get_db():
    """FastAPI dependency: yields a session and always closes it."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
