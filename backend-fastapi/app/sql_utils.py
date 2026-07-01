"""Shared SQL helpers: schema-qualified table names + employee-id resolution.

Two independent namespaces so the auth tables and the production tables never
couple to one another:

  Production tables  -> PROD_SCHEMA (default 'dbo') + optional PROD_DB
  Auth/login tables  -> AUTH_SCHEMA (default 'dbo') + optional AUTH_DB

If a *_DB env var is empty, the connection's default database is used.
"""
from __future__ import annotations

import os

from sqlalchemy import text
from sqlalchemy.orm import Session


def _qualify(table: str, schema_env: str, db_env: str) -> str:
    schema = (os.getenv(schema_env) or "dbo").strip()
    parts = [f"[{schema}]", f"[{table}]"]
    database = (os.getenv(db_env) or "").strip()
    if database:
        parts.insert(0, f"[{database}]")
    return ".".join(parts)


def prod_table(table: str) -> str:
    """[PROD_DB].[PROD_SCHEMA].[table] for production tables (default [dbo].[table])."""
    return _qualify(table, "PROD_SCHEMA", "PROD_DB")


def auth_table(table: str) -> str:
    """[AUTH_DB].[AUTH_SCHEMA].[table] for auth/login tables (default [dbo].[table])."""
    return _qualify(table, "AUTH_SCHEMA", "AUTH_DB")


def resolve_employee_id(db: Session, username: str | None) -> str | None:
    """Map an AD username -> USER_EN via USERLOGIN; pass through if already all-digits."""
    u = (username or "").strip()
    if not u:
        return None
    if u.isdigit():
        return u
    row = db.execute(
        text(
            f"""
            SELECT TOP (1) CAST(USER_EN AS varchar(50)) AS user_en
            FROM {auth_table('USERLOGIN')}
            WHERE USER_LOGIN_NAME = :u OR USER_EN = :u
            """
        ),
        {"u": u},
    ).scalar()
    return str(row).strip() if row else None
