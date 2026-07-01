"""Authentication API: employee verification for the handheld Login screen.

Mirrors the legacy .NET GET /api/auth/verify/{empId}:
  1) USERLOGIN: map a username/USER_EN -> employee number (USER_EN)
  2) USER_DETAIL (preferred) -> empId / empName / position; fallback to USERLOGIN
Returns JSON { empId, empName, position } — exactly the keys EmployeeProfile.java expects.

USERLOGIN / USER_DETAIL may live in a different database/schema than the
production tables, so their location is configurable via env (AUTH_DB / AUTH_SCHEMA).
"""
from __future__ import annotations

import os

from fastapi import APIRouter, Depends, HTTPException, Path
from sqlalchemy import text
from sqlalchemy.exc import ProgrammingError
from sqlalchemy.orm import Session

from app.database import get_db

router = APIRouter(prefix="/api/auth", tags=["auth"])


def _qualified_name(table: str) -> str:
    """Build [Database].[Schema].[Table].

    AUTH_DB     -> database holding USERLOGIN / USER_DETAIL (optional; default = connected DB)
    AUTH_SCHEMA -> schema (optional; default 'dbo')
    """
    schema = (os.getenv("AUTH_SCHEMA") or "dbo").strip()
    parts = [f"[{schema}]", f"[{table}]"]
    auth_db = (os.getenv("AUTH_DB") or "").strip()
    if auth_db:
        parts.insert(0, f"[{auth_db}]")
    return ".".join(parts)


@router.get("/verify/{emp_id}")
def verify_employee(
    emp_id: str = Path(..., description="Employee ID (USER_EN) or username (USER_LOGIN)"),
    db: Session = Depends(get_db),
) -> dict:
    emp_id = (emp_id or "").strip()
    if not emp_id:
        raise HTTPException(status_code=400, detail="emp_id is required.")

    userlogin = _qualified_name("USERLOGIN")
    user_detail = _qualified_name("USER_DETAIL")

    try:
        # Step 1 (GetEmployeeIdByUsername): username/USER_EN -> employee number.
        actual_emp_id = db.execute(
            text(
                f"""
                SELECT TOP (1) CAST(USER_EN AS varchar(50)) AS user_en
                FROM {userlogin}
                WHERE USER_LOGIN_NAME = :key OR USER_EN = :key
                """
            ),
            {"key": emp_id},
        ).scalar() or emp_id

        # Step 2 (VerifyEmployeeProfile): prefer USER_DETAIL, cascade to USERLOGIN.
        profile = db.execute(
            text(
                f"""
                IF OBJECT_ID(N'{user_detail}', N'U') IS NOT NULL
                   AND COL_LENGTH('{user_detail}', 'USER_EN') IS NOT NULL
                   AND COL_LENGTH('{user_detail}', 'USER_NAME_ENG') IS NOT NULL
                   AND COL_LENGTH('{user_detail}', 'USER_POSITION') IS NOT NULL
                BEGIN
                    SELECT TOP (1)
                        CAST(USER_EN AS varchar(50))                AS empId,
                        CAST(ISNULL(USER_NAME_ENG, '') AS varchar(100)) AS empName,
                        CAST(ISNULL(USER_POSITION, '') AS varchar(100)) AS position
                    FROM {user_detail}
                    WHERE CAST(USER_EN AS varchar(50)) = :emp_id;
                    RETURN
                END

                -- Fallback: USERLOGIN only (no profile detail available).
                SELECT TOP (1)
                    CAST(USER_EN AS varchar(50)) AS empId,
                    CAST(USER_EN AS varchar(50)) AS empName,
                    CAST('' AS varchar(100))     AS position
                FROM {userlogin}
                WHERE CAST(USER_EN AS varchar(50)) = :emp_id
                """
            ),
            {"emp_id": actual_emp_id},
        ).mappings().first()

        if not profile:
            raise HTTPException(status_code=404, detail="User not found.")

        # Return exactly the keys the Android app expects.
        return {
            "empId": profile["empId"],
            "empName": profile["empName"],
            "position": profile["position"],
        }

    except HTTPException:
        raise
    except ProgrammingError as exc:
        # SQL 208 "Invalid object name" -> tables not in the connected DB/schema.
        raise HTTPException(
            status_code=500,
            detail=(
                f"Cannot resolve user tables ({userlogin} / {user_detail}). "
                "Confirm they exist there, or set AUTH_DB / AUTH_SCHEMA in db.env. "
                f"[{exc.orig}]"
            ),
        ) from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Auth query failed: {exc}") from exc
