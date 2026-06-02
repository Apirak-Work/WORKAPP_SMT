using System.Text.RegularExpressions;
using Microsoft.Data.SqlClient;

namespace StarsOne.Api;

public sealed class ProductionRepository
{
    private readonly SqlConnectionFactory _connectionFactory;
    private readonly QueryCatalog _queries;

    public ProductionRepository(SqlConnectionFactory connectionFactory, QueryCatalog queries)
    {
        _connectionFactory = connectionFactory;
        _queries = queries;
    }

    public async Task<ValidateScanResponse> ValidateScanAsync(
        ValidateScanRequest request,
        CancellationToken cancellationToken)
    {
        // ── 1. User ID: pass if starts with "EN" (case-insensitive), no DB check ──
        var userValid = Regex.IsMatch(request.UserId?.Trim() ?? "", @"^EN", RegexOptions.IgnoreCase);

        // ── 2. Machine ID: always pass (no machine master table in DB) ──
        var machineValid = !string.IsNullOrWhiteSpace(request.MachineId);

        // ── 3. Runcard: only check existence in DB, no cross-match ──
        bool runcardValid;
        try
        {
            await using var connection = _connectionFactory.Create();
            await connection.OpenAsync(cancellationToken);
            runcardValid = await ExistsAsync(connection, _queries.Get("ValidateRuncard"), cmd =>
            {
                cmd.Parameters.AddWithValue("@RuncardNo", request.RuncardNo);
            }, cancellationToken);
        }
        catch (Exception ex)
        {
            // If DB is unreachable, report runcard as invalid with details
            return new ValidateScanResponse(
                false,
                $"Database error while validating runcard: {ex.Message}",
                userValid,
                machineValid,
                false);
        }

        // ── No Data-Mismatch check between User, Machine, and Runcard ──
        var allowed = userValid && machineValid && runcardValid;
        var message = allowed
            ? "Scan data is valid."
            : $"Validation failed — User:{(userValid ? "OK" : "FAIL (must start with EN)")} | Machine:{(machineValid ? "OK" : "FAIL (empty)")} | Runcard:{(runcardValid ? "OK" : "FAIL (not found in DB)")}";

        return new ValidateScanResponse(allowed, message, userValid, machineValid, runcardValid);
    }

    public async Task<ProductionDetailResponse?> GetProductionDetailAsync(
        string runcardNo,
        CancellationToken cancellationToken)
    {
        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        await using var command = new SqlCommand(_queries.Get("GetProductionDetail"), connection);
        command.Parameters.AddWithValue("@RuncardNo", runcardNo);

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
        {
            return null;
        }

        return new ProductionDetailResponse(
            runcardNo,
            ReadString(reader, "Description"),
            ReadString(reader, "Material"),
            ReadString(reader, "RcQuantity"),
            ReadString(reader, "QtyRc"),
            ReadString(reader, "QtyWo"),
            ReadString(reader, "DateCode"),
            ReadString(reader, "WorkOrder"),
            ReadString(reader, "Mpq"),
            ReadString(reader, "AssyLot"),
            ReadString(reader, "WaferLot"),
            ReadString(reader, "OrderType"),
            ReadString(reader, "Uom"),
            ReadString(reader, "LotType"),
            ReadString(reader, "ReelNumber"));
    }

    public async Task<IReadOnlyList<OperTrackingRow>> GetOperTrackingAsync(
        string runcardNo,
        CancellationToken cancellationToken)
    {
        var rows = new List<OperTrackingRow>();
        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        await using var command = new SqlCommand(_queries.Get("GetOperTracking"), connection);
        command.Parameters.AddWithValue("@RuncardNo", runcardNo);

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        while (await reader.ReadAsync(cancellationToken))
        {
            rows.Add(new OperTrackingRow(
                ReadString(reader, "Oper"),
                ReadString(reader, "Wc"),
                ReadString(reader, "Description"),
                ReadString(reader, "WorkCenter"),
                ReadString(reader, "Receive"),
                ReadString(reader, "Yield"),
                ReadString(reader, "Scrap"),
                ReadString(reader, "Move"),
                ReadString(reader, "PercentYield"),
                ReadDateTimeOffset(reader, "ReceiveDate"),
                ReadDateTimeOffset(reader, "ConfirmDate"),
                ReadString(reader, "En")));
        }

        return rows;
    }

    public async Task<SaveProductionResponse> SaveProductionAsync(
        SaveProductionRequest request,
        CancellationToken cancellationToken)
    {
        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        await using var command = new SqlCommand(_queries.Get("SaveProduction"), connection);
        command.Parameters.AddWithValue("@UserId", request.UserId);
        command.Parameters.AddWithValue("@MachineId", request.MachineId);
        command.Parameters.AddWithValue("@RuncardNo", request.RuncardNo);
        command.Parameters.AddWithValue("@GoodQty", request.GoodQty);
        command.Parameters.AddWithValue("@ScrapQty", request.ScrapQty);
        command.Parameters.AddWithValue("@FunctionMode", (object?)request.FunctionMode ?? DBNull.Value);
        command.Parameters.AddWithValue("@StartDate", request.StartDate);
        command.Parameters.AddWithValue("@FinishDate", request.FinishDate);
        command.Parameters.AddWithValue("@PostingDate", request.PostingDate);

        var affected = await command.ExecuteNonQueryAsync(cancellationToken);
        return new SaveProductionResponse(affected > 0, affected > 0 ? "Saved." : "No data was saved.");
    }

    private static async Task<bool> ExistsAsync(
        SqlConnection connection,
        string sql,
        Action<SqlCommand> configure,
        CancellationToken cancellationToken)
    {
        await using var command = new SqlCommand(sql, connection);
        configure(command);
        var result = await command.ExecuteScalarAsync(cancellationToken);
        return result is not null && result != DBNull.Value;
    }

    private static string? ReadString(SqlDataReader reader, string column)
    {
        var ordinal = reader.GetOrdinal(column);
        return reader.IsDBNull(ordinal) ? null : Convert.ToString(reader.GetValue(ordinal));
    }

    private static DateTimeOffset? ReadDateTimeOffset(SqlDataReader reader, string column)
    {
        var ordinal = reader.GetOrdinal(column);
        if (reader.IsDBNull(ordinal))
        {
            return null;
        }

        var value = reader.GetValue(ordinal);
        return value switch
        {
            DateTimeOffset dto => dto,
            DateTime dt => new DateTimeOffset(dt),
            _ => null
        };
    }
}
