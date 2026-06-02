# StarsOne.Api

Backend API for the SMT handheld app.

Security rule: Android must call this API. Android must not store SQL Server host, port, username, or password.

## Secret config

The API reads database settings from:

```text
C:\STarsone\db.env
```

Do not commit that file.

Required keys:

```text
DB_PROVIDER
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
DB_ENCRYPT
DB_TRUST_SERVER_CERTIFICATE
DB_SCHEMA
```

## Run

```powershell
dotnet restore
dotnet run --project backend\StarsOne.Api\StarsOne.Api.csproj
```

## Endpoints

```text
GET  /health
POST /api/workflow/validate-scan
GET  /api/production/runcards/{runcardNo}
GET  /api/production/runcards/{runcardNo}/opers
POST /api/production/save
```

## Query mapping

Table names are not hardcoded in C#.
Update `appsettings.json` query templates to match the real SQL Server schema.

The initial mapping is based on `Flow_Timeline.xlsx` / `SQL Table`:

- Runcard detail: `Runcard_Detail`
- Production/OPER transaction: `RC_Transection`
- Work order: `WORKORDER`
- Routing operation: `OPERATION`
- Standard RC master: `STD_RC_Master`

Important: `ValidateUser` and `ValidateMachine` are best-effort mappings because the workbook did not show dedicated user or machine master tables. Replace those two queries with the real master tables when available.
