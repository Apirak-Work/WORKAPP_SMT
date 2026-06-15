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

    public async Task<ValidationResponse> ValidateRuncardGatesAsync(
        string runcardNo,
        string? workCenter,
        CancellationToken cancellationToken)
    {
        var errors = new List<string>();
        if (string.IsNullOrWhiteSpace(runcardNo))
        {
            errors.Add("Runcard is required.");
            return new ValidationResponse(false, errors);
        }

        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);

        var flag = await ReadOptionalStringAsync(
            connection,
            _queries.Get("GateGetRuncardFlag"),
            command => command.Parameters.AddWithValue("@RC", runcardNo),
            cancellationToken);
        if (flag is null)
        {
            errors.Add("Runcard was not found in Runcard_Detail.");
            return new ValidationResponse(false, errors);
        }

        if (string.Equals(flag, "H", StringComparison.OrdinalIgnoreCase))
        {
            errors.Add("Runcard is on HOLD.");
        }
        if (string.Equals(flag, "X", StringComparison.OrdinalIgnoreCase))
        {
            errors.Add("Runcard is closed or scrapped.");
        }

        if (!string.IsNullOrWhiteSpace(workCenter))
        {
            var atWorkCenter = await ExistsAsync(
                connection,
                _queries.Get("GateRuncardAtWorkCenter"),
                command =>
                {
                    command.Parameters.AddWithValue("@RC", runcardNo);
                    command.Parameters.AddWithValue("@WorkCenter", workCenter);
                },
                cancellationToken);
            if (!atWorkCenter)
            {
                errors.Add($"Runcard is not at current work center {workCenter}.");
            }
        }

        var hasPendingActivity = await ExistsAsync(
            connection,
            _queries.Get("GatePendingActivities"),
            command =>
            {
                command.Parameters.AddWithValue("@RC", runcardNo);
                command.Parameters.AddWithValue("@WorkCenter", (object?)workCenter ?? DBNull.Value);
            },
            cancellationToken);
        if (hasPendingActivity)
        {
            errors.Add("Runcard has pending activity at the current work center.");
        }

        var hasActiveBlock = await ExistsAsync(
            connection,
            _queries.Get("GateActiveBlocks"),
            command =>
            {
                command.Parameters.AddWithValue("@RC", runcardNo);
                command.Parameters.AddWithValue("@WorkCenter", (object?)workCenter ?? DBNull.Value);
            },
            cancellationToken);
        if (hasActiveBlock)
        {
            errors.Add("Runcard has an active B2B or block flag.");
        }

        return new ValidationResponse(errors.Count == 0, errors);
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

    public async Task<IReadOnlyList<RuncardOverviewRow>> GetRuncardsByWorkOrderAsync(
        string workOrderNo,
        CancellationToken cancellationToken)
    {
        var rows = new List<RuncardOverviewRow>();
        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        await using var command = new SqlCommand(_queries.Get("GetRuncardsByWorkOrder"), connection);
        command.Parameters.AddWithValue("@WorkOrderNo", workOrderNo);

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        while (await reader.ReadAsync(cancellationToken))
        {
            rows.Add(new RuncardOverviewRow(
                ReadString(reader, "Type"),
                ReadString(reader, "Rc"),
                ReadString(reader, "Assy"),
                ReadString(reader, "Qty"),
                ReadString(reader, "RcAction"),
                ReadString(reader, "Status")));
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

    public async Task<HoldResponse> SaveHoldActionAsync(
        HoldRequest request,
        CancellationToken cancellationToken)
    {
        var actionType = (request.ActionType ?? "").Trim().ToUpperInvariant();
        if (actionType is not ("HOLD" or "RELEASE"))
        {
            return new HoldResponse(false, "ActionType must be HOLD or RELEASE.");
        }

        if (string.IsNullOrWhiteSpace(request.Runcard))
        {
            return new HoldResponse(false, "Runcard is required.");
        }

        if (actionType == "HOLD"
            && (string.IsNullOrWhiteSpace(request.SelectReason)
                || string.IsNullOrWhiteSpace(request.TopicDamage)
                || string.IsNullOrWhiteSpace(request.HoldComment)))
        {
            return new HoldResponse(false, "Reason, topic damage, and hold comment are required.");
        }

        if (actionType == "RELEASE" && string.IsNullOrWhiteSpace(request.ReleaseComment))
        {
            return new HoldResponse(false, "Release hold comment is required.");
        }

        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        await using var command = new SqlCommand(_queries.Get("SaveHoldAction"), connection);
        command.Parameters.AddWithValue("@WorkOrder", (object?)request.WorkOrder ?? DBNull.Value);
        command.Parameters.AddWithValue("@Runcard", request.Runcard);
        command.Parameters.AddWithValue("@Material", (object?)request.Material ?? DBNull.Value);
        command.Parameters.AddWithValue("@SelectReason", (object?)request.SelectReason ?? DBNull.Value);
        command.Parameters.AddWithValue("@TopicDamage", (object?)request.TopicDamage ?? DBNull.Value);
        command.Parameters.AddWithValue("@HoldComment", (object?)request.HoldComment ?? DBNull.Value);
        command.Parameters.AddWithValue("@ReleaseComment", (object?)request.ReleaseComment ?? DBNull.Value);
        command.Parameters.AddWithValue("@ActionType", actionType);

        var affected = await command.ExecuteNonQueryAsync(cancellationToken);
        return new HoldResponse(
            affected > 0,
            affected > 0 ? $"{actionType} saved." : "No runcard was updated.");
    }

    public async Task<SplitResponse> SplitRuncardAsync(
        SplitRequest request,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.MotherRuncard))
        {
            return new SplitResponse(false, "Mother runcard is required.", "", "", 0, 0, null, DateTimeOffset.UtcNow);
        }

        if (request.SplitQty <= 0)
        {
            return new SplitResponse(false, "Split qty must be greater than zero.", "", "", 0, 0, null, DateTimeOffset.UtcNow);
        }

        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        var employeeId = await ResolveEmployeeIdAsync(connection, request.Cby, cancellationToken);
        if (employeeId is null)
        {
            return new SplitResponse(false, "Cannot map AD username to 6-digit employee ID.", "", "", 0, 0, null, DateTimeOffset.UtcNow);
        }

        await using var transaction = (SqlTransaction)await connection.BeginTransactionAsync(cancellationToken);

        try
        {
            var databaseMotherQty = await ReadRequiredIntAsync(
                connection,
                transaction,
                _queries.Get("GetMotherQtyForSplit"),
                command => command.Parameters.AddWithValue("@MotherRuncard", request.MotherRuncard),
                cancellationToken);
            var originalMotherQty = request.MotherQty > 0 ? request.MotherQty : databaseMotherQty;

            if (request.SplitQty > databaseMotherQty)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new SplitResponse(false, "Split qty must not exceed mother qty.", "", "", request.SplitQty, databaseMotherQty, null, DateTimeOffset.UtcNow);
            }

            var childCount = await ReadRequiredIntAsync(
                connection,
                transaction,
                _queries.Get("CountSplitChildren"),
                command => command.Parameters.AddWithValue("@MotherRuncard", request.MotherRuncard),
                cancellationToken);

            var newRuncard = await GenerateUniqueRuncardAsync(connection, transaction, cancellationToken);
            var newAssy = GenerateChildAssyLot(request.MotherAssyLot, request.CustomerType, childCount + 1);
            var remainingMotherQty = databaseMotherQty - request.SplitQty;
            var cdate = DateTimeOffset.UtcNow;

            await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("InsertSplitChildLot"),
                command =>
                {
                    command.Parameters.AddWithValue("@ChildWo", request.WorkOrder);
                    command.Parameters.AddWithValue("@NewRuncard", newRuncard);
                    command.Parameters.AddWithValue("@NewAssy", newAssy);
                    command.Parameters.AddWithValue("@Material", (object?)request.Material ?? DBNull.Value);
                    command.Parameters.AddWithValue("@SplitQty", request.SplitQty);
                    command.Parameters.AddWithValue("@MotherRuncard", request.MotherRuncard);
                    command.Parameters.AddWithValue("@MotherQty", originalMotherQty);
                    command.Parameters.AddWithValue("@Cby", employeeId);
                    command.Parameters.AddWithValue("@Cdate", cdate);
                },
                cancellationToken);

            var updatedMotherRows = await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("UpdateMotherQtyAfterSplit"),
                command =>
                {
                    command.Parameters.AddWithValue("@MotherRuncard", request.MotherRuncard);
                    command.Parameters.AddWithValue("@SplitQty", request.SplitQty);
                },
                cancellationToken);
            if (updatedMotherRows <= 0)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new SplitResponse(false, "Mother qty was not updated. Please reload and try again.", "", "", request.SplitQty, databaseMotherQty, null, DateTimeOffset.UtcNow);
            }

            await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("InsertSplitHistory"),
                command =>
                {
                    command.Parameters.AddWithValue("@MotherWo", request.WorkOrder);
                    command.Parameters.AddWithValue("@MotherRuncard", request.MotherRuncard);
                    command.Parameters.AddWithValue("@MotherAssy", request.MotherAssyLot);
                    command.Parameters.AddWithValue("@MotherQty", originalMotherQty);
                    command.Parameters.AddWithValue("@ChildWo", request.WorkOrder);
                    command.Parameters.AddWithValue("@NewRuncard", newRuncard);
                    command.Parameters.AddWithValue("@NewAssy", newAssy);
                    command.Parameters.AddWithValue("@SplitQty", request.SplitQty);
                    command.Parameters.AddWithValue("@WorkCenter", (object?)request.WorkCenter ?? DBNull.Value);
                    command.Parameters.AddWithValue("@Cby", employeeId);
                    command.Parameters.AddWithValue("@Cdate", cdate);
                },
                cancellationToken);

            await transaction.CommitAsync(cancellationToken);
            return new SplitResponse(true, "Split saved.", newRuncard, newAssy, request.SplitQty, remainingMotherQty, request.WorkCenter, cdate);
        }
        catch
        {
            await transaction.RollbackAsync(cancellationToken);
            throw;
        }
    }

    public async Task<IEnumerable<SplitHistoryDto>> GetSplitHistoryAsync(
        string runcardNo,
        CancellationToken cancellationToken)
    {
        var rows = new List<SplitHistoryDto>();
        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        await using var command = new SqlCommand(_queries.Get("GetSplitHistory"), connection);
        command.Parameters.AddWithValue("@RC", runcardNo);

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        while (await reader.ReadAsync(cancellationToken))
        {
            rows.Add(new SplitHistoryDto(
                ReadString(reader, "Runcard"),
                ReadString(reader, "AssyLot"),
                ReadInt(reader, "Qty"),
                ReadString(reader, "Mother"),
                ReadInt(reader, "MotherQty"),
                ReadString(reader, "Wc"),
                ReadDateTimeOffset(reader, "Cdate")));
        }

        return rows;
    }

    public async Task<MergeResponse> MergeRuncardsAsync(
        MergeRequest request,
        CancellationToken cancellationToken)
    {
        var mainRuncard = request.MainRuncard?.Trim() ?? "";
        var sourceRuncards = (request.SourceRuncards ?? new List<string>())
            .Select(value => value.Trim())
            .Where(value => !string.IsNullOrWhiteSpace(value))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();

        if (string.IsNullOrWhiteSpace(mainRuncard))
        {
            return new MergeResponse(false, "Main runcard is required.", 0, mainRuncard);
        }
        if (sourceRuncards.Count == 0)
        {
            return new MergeResponse(false, "At least one source runcard is required.", 0, mainRuncard);
        }
        if (sourceRuncards.Any(source => string.Equals(source, mainRuncard, StringComparison.OrdinalIgnoreCase)))
        {
            return new MergeResponse(false, "Source runcard cannot be the same as main runcard.", 0, mainRuncard);
        }

        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        var employeeId = await ResolveEmployeeIdAsync(connection, request.Cby, cancellationToken);
        if (employeeId is null)
        {
            return new MergeResponse(false, "Cannot map AD username to 6-digit employee ID.", 0, mainRuncard);
        }

        await using var transaction = (SqlTransaction)await connection.BeginTransactionAsync(cancellationToken);
        try
        {
            var allRuncards = new List<string> { mainRuncard };
            allRuncards.AddRange(sourceRuncards);
            var rows = await ReadMergeRuncardRowsAsync(connection, transaction, allRuncards, cancellationToken);
            var mainRow = rows.FirstOrDefault(row => string.Equals(row.Runcard, mainRuncard, StringComparison.OrdinalIgnoreCase));
            if (mainRow is null)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new MergeResponse(false, "Main runcard was not found or already closed.", 0, mainRuncard);
            }

            var sourceRows = rows
                .Where(row => sourceRuncards.Any(source => string.Equals(source, row.Runcard, StringComparison.OrdinalIgnoreCase)))
                .ToList();
            if (sourceRows.Count != sourceRuncards.Count)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new MergeResponse(false, "One or more source runcards were not found or already closed.", 0, mainRuncard);
            }

            var totalSourceQty = sourceRows.Sum(row => row.Qty);
            if (totalSourceQty <= 0)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new MergeResponse(false, "Total source qty must be greater than zero.", 0, mainRuncard);
            }

            var updatedMainRows = await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("UpdateMainRuncardQtyForMerge"),
                command =>
                {
                    command.Parameters.AddWithValue("@MainRuncard", mainRuncard);
                    command.Parameters.AddWithValue("@TotalSourceQty", totalSourceQty);
                },
                cancellationToken);
            if (updatedMainRows <= 0)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new MergeResponse(false, "Main runcard qty was not updated.", 0, mainRuncard);
            }

            var closedSourceRows = await ExecuteMergeInQueryAsync(
                connection,
                transaction,
                _queries.Get("CloseSourceRuncardsForMerge"),
                sourceRuncards,
                cancellationToken);
            if (closedSourceRows != sourceRuncards.Count)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new MergeResponse(false, "Not all source runcards were closed.", 0, mainRuncard);
            }

            var mergedMainQty = mainRow.Qty + totalSourceQty;
            var cdate = DateTimeOffset.UtcNow;
            foreach (var source in sourceRows)
            {
                await ExecuteNonQueryAsync(
                    connection,
                    transaction,
                    _queries.Get("InsertMergeHistory"),
                    command =>
                    {
                        command.Parameters.AddWithValue("@MotherWo", (object?)mainRow.WorkOrder ?? DBNull.Value);
                        command.Parameters.AddWithValue("@MainRuncard", mainRuncard);
                        command.Parameters.AddWithValue("@MotherAssy", (object?)mainRow.AssyLot ?? DBNull.Value);
                        command.Parameters.AddWithValue("@MotherQty", mergedMainQty);
                        command.Parameters.AddWithValue("@ChildWo", (object?)source.WorkOrder ?? DBNull.Value);
                        command.Parameters.AddWithValue("@SourceRuncard", source.Runcard);
                        command.Parameters.AddWithValue("@SourceAssy", (object?)source.AssyLot ?? DBNull.Value);
                        command.Parameters.AddWithValue("@SourceQty", source.Qty);
                        command.Parameters.AddWithValue("@WorkCenter", (object?)request.WorkCenter ?? DBNull.Value);
                        command.Parameters.AddWithValue("@Cby", employeeId);
                        command.Parameters.AddWithValue("@Cdate", cdate);
                    },
                    cancellationToken);
            }

            await transaction.CommitAsync(cancellationToken);
            return new MergeResponse(true, $"Merge saved. Total merged qty: {totalSourceQty}.", totalSourceQty, mainRuncard);
        }
        catch
        {
            await transaction.RollbackAsync(cancellationToken);
            throw;
        }
    }

    private async Task<string> GenerateUniqueRuncardAsync(
        SqlConnection connection,
        SqlTransaction transaction,
        CancellationToken cancellationToken)
    {
        var prefix = DateTime.UtcNow.ToString("yyMMdd");
        for (var running = 1; running <= 9999; running++)
        {
            var candidate = prefix + running.ToString("D4");
            var exists = await ExistsAsync(
                connection,
                transaction,
                _queries.Get("RuncardExists"),
                command => command.Parameters.AddWithValue("@Runcard", candidate),
                cancellationToken);
            if (!exists)
            {
                return candidate;
            }
        }

        throw new InvalidOperationException("Unable to generate a unique 10-digit child runcard.");
    }

    private async Task<string?> ResolveEmployeeIdAsync(
        SqlConnection connection,
        string? username,
        CancellationToken cancellationToken)
    {
        var trimmed = username?.Trim();
        if (string.IsNullOrWhiteSpace(trimmed))
        {
            return null;
        }

        if (trimmed.Length <= 6 && trimmed.All(char.IsLetterOrDigit))
        {
            return trimmed;
        }

        await using var command = new SqlCommand(_queries.Get("GetEmployeeIdByUsername"), connection);
        command.Parameters.AddWithValue("@Username", trimmed);
        var result = await command.ExecuteScalarAsync(cancellationToken);
        var employeeId = Convert.ToString(result)?.Trim();
        return string.IsNullOrWhiteSpace(employeeId) || employeeId.Length > 6
            ? null
            : employeeId;
    }

    private static string GenerateChildAssyLot(string? motherAssy, string? customerType, int splitSequence)
    {
        var baseAssy = (motherAssy ?? "").Trim();
        if (string.Equals(customerType, "onsemi", StringComparison.OrdinalIgnoreCase))
        {
            if (baseAssy.StartsWith("S4", StringComparison.OrdinalIgnoreCase))
            {
                baseAssy = "S5" + baseAssy[2..];
            }

            return baseAssy + ToAlphabetSuffix(splitSequence);
        }

        return string.IsNullOrWhiteSpace(baseAssy)
            ? $"CHILD{ToAlphabetSuffix(splitSequence)}"
            : $"{baseAssy}-{ToAlphabetSuffix(splitSequence)}";
    }

    private static string ToAlphabetSuffix(int sequence)
    {
        if (sequence <= 0)
        {
            return "A";
        }

        var value = sequence;
        var chars = new Stack<char>();
        while (value > 0)
        {
            value--;
            chars.Push((char)('A' + value % 26));
            value /= 26;
        }

        return new string(chars.ToArray());
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

    private static async Task<string?> ReadOptionalStringAsync(
        SqlConnection connection,
        string sql,
        Action<SqlCommand> configure,
        CancellationToken cancellationToken)
    {
        await using var command = new SqlCommand(sql, connection);
        configure(command);
        var result = await command.ExecuteScalarAsync(cancellationToken);
        return result is null || result == DBNull.Value ? null : Convert.ToString(result);
    }

    private static async Task<bool> ExistsAsync(
        SqlConnection connection,
        SqlTransaction transaction,
        string sql,
        Action<SqlCommand> configure,
        CancellationToken cancellationToken)
    {
        await using var command = new SqlCommand(sql, connection, transaction);
        configure(command);
        var result = await command.ExecuteScalarAsync(cancellationToken);
        return result is not null && result != DBNull.Value;
    }

    private static async Task<int> ReadRequiredIntAsync(
        SqlConnection connection,
        SqlTransaction transaction,
        string sql,
        Action<SqlCommand> configure,
        CancellationToken cancellationToken)
    {
        await using var command = new SqlCommand(sql, connection, transaction);
        configure(command);
        var result = await command.ExecuteScalarAsync(cancellationToken);
        if (result is null || result == DBNull.Value)
        {
            throw new InvalidOperationException("Expected integer query result was empty.");
        }

        return Convert.ToInt32(result);
    }

    private static async Task<int> ExecuteNonQueryAsync(
        SqlConnection connection,
        SqlTransaction transaction,
        string sql,
        Action<SqlCommand> configure,
        CancellationToken cancellationToken)
    {
        await using var command = new SqlCommand(sql, connection, transaction);
        configure(command);
        return await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private async Task<List<MergeRuncardRow>> ReadMergeRuncardRowsAsync(
        SqlConnection connection,
        SqlTransaction transaction,
        IReadOnlyList<string> runcards,
        CancellationToken cancellationToken)
    {
        var rows = new List<MergeRuncardRow>();
        var sql = BuildInClauseSql(_queries.Get("GetMergeRuncardRows"), runcards);
        await using var command = new SqlCommand(sql, connection, transaction);
        AddRuncardListParameters(command, runcards);

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        while (await reader.ReadAsync(cancellationToken))
        {
            rows.Add(new MergeRuncardRow(
                ReadString(reader, "Runcard") ?? "",
                ReadString(reader, "WorkOrder"),
                ReadString(reader, "AssyLot"),
                ReadInt(reader, "Qty") ?? 0));
        }

        return rows;
    }

    private static async Task<int> ExecuteMergeInQueryAsync(
        SqlConnection connection,
        SqlTransaction transaction,
        string sqlTemplate,
        IReadOnlyList<string> runcards,
        CancellationToken cancellationToken)
    {
        var sql = BuildInClauseSql(sqlTemplate, runcards);
        await using var command = new SqlCommand(sql, connection, transaction);
        AddRuncardListParameters(command, runcards);
        return await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static string BuildInClauseSql(string sqlTemplate, IReadOnlyList<string> values)
    {
        var parameterNames = values
            .Select((_, index) => "@Runcard" + index)
            .ToArray();
        return sqlTemplate.Replace("{RuncardList}", string.Join(", ", parameterNames), StringComparison.OrdinalIgnoreCase);
    }

    private static void AddRuncardListParameters(SqlCommand command, IReadOnlyList<string> values)
    {
        for (var index = 0; index < values.Count; index++)
        {
            command.Parameters.AddWithValue("@Runcard" + index, values[index]);
        }
    }

    private sealed record MergeRuncardRow(
        string Runcard,
        string? WorkOrder,
        string? AssyLot,
        int Qty);

    private static string? ReadString(SqlDataReader reader, string column)
    {
        var ordinal = reader.GetOrdinal(column);
        return reader.IsDBNull(ordinal) ? null : Convert.ToString(reader.GetValue(ordinal));
    }

    private static int? ReadInt(SqlDataReader reader, string column)
    {
        var ordinal = reader.GetOrdinal(column);
        return reader.IsDBNull(ordinal) ? null : Convert.ToInt32(reader.GetValue(ordinal));
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
