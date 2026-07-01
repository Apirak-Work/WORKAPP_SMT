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
        var machineValid = !string.IsNullOrWhiteSpace(request.MachineId);

        bool userValid;
        bool runcardValid;
        try
        {
            await using var connection = _connectionFactory.Create();
            await connection.OpenAsync(cancellationToken);
            userValid = await ReadEmployeeProfileAsync(connection, request.UserId, cancellationToken) is not null;
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
                $"Database error while validating scan data: {ex.Message}",
                false,
                machineValid,
                false);
        }

        var allowed = userValid && machineValid && runcardValid;
        var message = allowed
            ? "Scan data is valid."
            : $"Validation failed — User:{(userValid ? "OK" : "FAIL (not found in DB)")} | Machine:{(machineValid ? "OK" : "FAIL (empty)")} | Runcard:{(runcardValid ? "OK" : "FAIL (not found in DB)")}";

        return new ValidateScanResponse(allowed, message, userValid, machineValid, runcardValid);
    }

    public async Task<EmployeeProfileResponse?> VerifyEmployeeAsync(
        string empId,
        CancellationToken cancellationToken)
    {
        var normalizedEmpId = empId?.Trim();
        if (string.IsNullOrWhiteSpace(normalizedEmpId) || !normalizedEmpId.All(char.IsDigit))
        {
            return null;
        }

        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        return await ReadEmployeeProfileAsync(connection, normalizedEmpId, cancellationToken);
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
        var employeeId = await ResolveEmployeeIdAsync(connection, request.UserId, cancellationToken)
            ?? request.UserId;
        var context = await ReadSaveConfirmContextAsync(connection, request, cancellationToken);
        var standards = await ReadConfirmStandardsAsync(connection, request, cancellationToken);
        var localNow = DateTime.Now;
        var totalQty = request.GoodQty + request.ScrapQty;
        var yieldTrig = totalQty <= 0
            ? 0d
            : Math.Round(request.GoodQty * 100d / totalQty, 2);
        var execStart = request.StartDate == default ? localNow : request.StartDate.LocalDateTime;

        await using var command = new SqlCommand(_queries.Get("SaveProduction"), connection);
        command.Parameters.AddWithValue("@Plant", "1200");
        command.Parameters.AddWithValue("@UserId", employeeId);
        command.Parameters.AddWithValue("@MachineId", request.MachineId);
        command.Parameters.AddWithValue("@RuncardNo", request.RuncardNo);
        command.Parameters.AddWithValue("@WorkOrder", (object?)context.WorkOrder ?? DBNull.Value);
        command.Parameters.AddWithValue("@RoutingNo", (object?)context.RoutingNo ?? DBNull.Value);
        command.Parameters.AddWithValue("@Material", (object?)context.Material ?? DBNull.Value);
        command.Parameters.AddWithValue("@MaterialDesc", (object?)context.MaterialDesc ?? DBNull.Value);
        command.Parameters.AddWithValue("@Operation", (object?)context.Operation ?? DBNull.Value);
        command.Parameters.AddWithValue("@WorkCenter", (object?)context.WorkCenter ?? DBNull.Value);
        command.Parameters.AddWithValue("@WorkCenterText", (object?)context.WorkCenterText ?? DBNull.Value);
        command.Parameters.AddWithValue("@ReceiveQty", context.ReceiveQty);
        command.Parameters.AddWithValue("@GoodQty", request.GoodQty);
        command.Parameters.AddWithValue("@ScrapQty", request.ScrapQty);
        command.Parameters.AddWithValue("@YieldTrig", yieldTrig);
        command.Parameters.AddWithValue("@ConfQuanUnit", (object?)context.Unit ?? DBNull.Value);
        command.Parameters.AddWithValue("@ConfActivity1", 0);
        command.Parameters.AddWithValue("@ConfActivity2", standards.McTime);
        command.Parameters.AddWithValue("@ConfActivity3", standards.LaborTime);
        command.Parameters.AddWithValue("@ExecStartDate", execStart.ToString("yyyyMMdd"));
        command.Parameters.AddWithValue("@ExecStartTime", execStart.ToString("HH:mm:ss"));
        command.Parameters.AddWithValue("@ExecFinDate", localNow.ToString("yyyyMMdd"));
        command.Parameters.AddWithValue("@ExecFinTime", localNow.ToString("HH:mm:ss"));
        command.Parameters.AddWithValue("@PostingDate", localNow.ToString("yyyyMMdd"));
        command.Parameters.AddWithValue("@ConfirmDate", localNow);
        command.Parameters.AddWithValue("@ReceiveDate", request.StartDate);
        command.Parameters.AddWithValue("@BreakTime", 0);
        command.Parameters.AddWithValue("@ResultType", "I");
        command.Parameters.AddWithValue("@ResultMessage", $"Confirmation of order {context.WorkOrder ?? request.RuncardNo} saved");
        command.Parameters.AddWithValue("@FunctionMode", "CONFIRM");

        var affected = await command.ExecuteNonQueryAsync(cancellationToken);
        return new SaveProductionResponse(affected > 0, affected > 0 ? "Saved." : "No data was saved.");
    }

    public async Task<SaveRejectResponse> SaveRejectDetailsAsync(
        SaveRejectRequest request,
        CancellationToken cancellationToken)
    {
        var runcardNo = FirstNonBlank(request.RuncardNo, request.Runcard);
        var workOrder = FirstNonBlank(request.Wo, request.WorkOrder);
        var cby = FirstNonBlank(request.EmpId, request.Cby);
        var rows = request.Rejects?
            .Where(row => row.RejectQty > 0 && !string.IsNullOrWhiteSpace(row.ReasonCode))
            .ToList() ?? new List<RejectDetailItem>();
        if (request.RejectQty.GetValueOrDefault() > 0 && !string.IsNullOrWhiteSpace(request.ReasonCode))
        {
            rows.Add(new RejectDetailItem(
                request.ReasonCode,
                request.ReasonDesc ?? "",
                request.RejectQty.GetValueOrDefault()));
        }

        if (string.IsNullOrWhiteSpace(runcardNo))
        {
            return new SaveRejectResponse(false, "Runcard is required.");
        }
        if (rows.Count == 0)
        {
            return new SaveRejectResponse(false, "At least one reject row is required.");
        }

        var totalRejectQty = rows.Sum(row => row.RejectQty);
        if (totalRejectQty <= 0)
        {
            return new SaveRejectResponse(false, "Reject qty must be greater than zero.");
        }

        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        var employeeId = await ResolveEmployeeIdAsync(connection, cby, cancellationToken)
            ?? cby;
        await using var transaction = (SqlTransaction)await connection.BeginTransactionAsync(cancellationToken);
        try
        {
            var currentQty = await ReadRequiredIntAsync(
                connection,
                transaction,
                _queries.Get("GetRejectCurrentQty"),
                command => command.Parameters.AddWithValue("@RuncardNo", runcardNo),
                cancellationToken);
            if (totalRejectQty > currentQty)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new SaveRejectResponse(false, "Reject qty must not exceed current runcard qty.");
            }

            foreach (var row in rows)
            {
                await ExecuteNonQueryAsync(
                    connection,
                    transaction,
                    _queries.Get("InsertRejectDetail"),
                    command =>
                    {
                        command.Parameters.AddWithValue("@Wo", (object?)workOrder ?? DBNull.Value);
                        command.Parameters.AddWithValue("@Runcard", runcardNo);
                        command.Parameters.AddWithValue("@WcNo", (object?)request.WcNo ?? DBNull.Value);
                        command.Parameters.AddWithValue("@Operation", (object?)request.Operation ?? DBNull.Value);
                        command.Parameters.AddWithValue("@RejectQty", row.RejectQty);
                        command.Parameters.AddWithValue("@ReasonCode", row.ReasonCode);
                        command.Parameters.AddWithValue("@ReasonDesc", (object?)row.ReasonDesc ?? DBNull.Value);
                        command.Parameters.AddWithValue("@Station", (object?)request.Station ?? DBNull.Value);
                        command.Parameters.AddWithValue("@Cby", (object?)employeeId ?? DBNull.Value);
                        command.Parameters.AddWithValue("@Cdate", DateTimeOffset.Now);
                    },
                    cancellationToken);
            }

            var deductedRows = await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("DeductRejectQty"),
                command =>
                {
                    command.Parameters.AddWithValue("@RuncardNo", runcardNo);
                    command.Parameters.AddWithValue("@RejectQty", totalRejectQty);
                    command.Parameters.AddWithValue("@Cby", (object?)employeeId ?? DBNull.Value);
                },
                cancellationToken);
            if (deductedRows <= 0)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new SaveRejectResponse(false, "Runcard qty was not updated. Please reload and try again.");
            }

            await transaction.CommitAsync(cancellationToken);
            return new SaveRejectResponse(true, $"Reject saved. Total reject qty: {totalRejectQty}.");
        }
        catch
        {
            await transaction.RollbackAsync(cancellationToken);
            throw;
        }
    }

    public async Task<IReadOnlyList<RejectReasonDto>> GetRejectReasonsAsync(CancellationToken cancellationToken)
    {
        var rows = new List<RejectReasonDto>();
        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        await using var command = new SqlCommand(_queries.Get("GetRejectReasons"), connection);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        while (await reader.ReadAsync(cancellationToken))
        {
            rows.Add(new RejectReasonDto(
                ReadString(reader, "ReasonCode"),
                ReadString(reader, "Description"),
                ReadString(reader, "ReasonGroup")));
        }

        return rows;
    }

    public async Task<IReadOnlyList<HoldReasonDto>> GetHoldReasonsAsync(CancellationToken cancellationToken)
    {
        var rows = new List<HoldReasonDto>();
        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        await using var command = new SqlCommand(_queries.Get("GetHoldReasons"), connection);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        while (await reader.ReadAsync(cancellationToken))
        {
            rows.Add(new HoldReasonDto(
                ReadString(reader, "ReasonCode"),
                ReadString(reader, "Description")));
        }

        return rows;
    }

    public async Task<HoldResponse> SaveHoldActionAsync(
        HoldRequest request,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Runcard))
        {
            return new HoldResponse(false, "Runcard is required.");
        }

        if (string.IsNullOrWhiteSpace(request.SelectReason)
            || string.IsNullOrWhiteSpace(request.HoldComment))
        {
            return new HoldResponse(false, "Reason and hold comment are required.");
        }

        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        var employeeId = await ResolveEmployeeIdAsync(connection, request.Cby, cancellationToken)
            ?? request.Cby;
        var cdate = DateTimeOffset.Now;
        await using var transaction = (SqlTransaction)await connection.BeginTransactionAsync(cancellationToken);
        try
        {
            var affected = await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("UpdateHoldTransactionStatus"),
                command => AddHoldParameters(command, request, employeeId, cdate, "HOLD"),
                cancellationToken);
            if (affected <= 0)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new HoldResponse(false, "No active transaction row was placed on hold.");
            }

            await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("InsertHoldHistory"),
                command => AddHoldParameters(command, request, employeeId, cdate, "HOLD"),
                cancellationToken);

            await transaction.CommitAsync(cancellationToken);
            return new HoldResponse(true, "HOLD saved.");
        }
        catch
        {
            await transaction.RollbackAsync(cancellationToken);
            throw;
        }
    }

    public async Task<HoldResponse> ReleaseHoldAsync(
        HoldRequest request,
        CancellationToken cancellationToken)
    {
        // TODO: Implement Admin Permission Check here based on dbo.USER_DETAIL before proceeding.
        if (string.IsNullOrWhiteSpace(request.Runcard))
        {
            return new HoldResponse(false, "Runcard is required.");
        }

        if (string.IsNullOrWhiteSpace(request.ReleaseComment))
        {
            return new HoldResponse(false, "Release hold comment is required.");
        }

        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        var employeeId = await ResolveEmployeeIdAsync(connection, request.Cby, cancellationToken)
            ?? request.Cby;
        var cdate = DateTimeOffset.Now;
        await using var transaction = (SqlTransaction)await connection.BeginTransactionAsync(cancellationToken);
        try
        {
            var affected = await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("ReleaseHoldTransactionStatus"),
                command => AddHoldParameters(command, request, employeeId, cdate, "RELEASE"),
                cancellationToken);
            if (affected <= 0)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new HoldResponse(false, "No active hold transaction row was released.");
            }

            await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("InsertHoldHistory"),
                command => AddHoldParameters(command, request, employeeId, cdate, "RELEASE"),
                cancellationToken);

            await transaction.CommitAsync(cancellationToken);
            return new HoldResponse(true, "RELEASE saved.");
        }
        catch
        {
            await transaction.RollbackAsync(cancellationToken);
            throw;
        }
    }

    public async Task<SplitResponse> SplitRuncardAsync(
        SplitRequest request,
        CancellationToken cancellationToken)
    {
        var motherRuncard = FirstNonBlank(request.RuncardNo, request.MotherRuncard);
        if (string.IsNullOrWhiteSpace(motherRuncard))
        {
            return new SplitResponse(false, "Mother runcard is required.", "", "", 0, 0, null, DateTimeOffset.UtcNow);
        }

        if (request.SplitQty <= 0)
        {
            return new SplitResponse(false, "Split qty must be greater than zero.", "", "", 0, 0, null, DateTimeOffset.UtcNow);
        }

        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        var employeeId = await ResolveEmployeeIdAsync(connection, FirstNonBlank(request.EmpId, request.Cby), cancellationToken);
        if (employeeId is null)
        {
            return new SplitResponse(false, "Cannot map AD username to 6-digit employee ID.", "", "", 0, 0, null, DateTimeOffset.UtcNow);
        }

        await using var transaction = (SqlTransaction)await connection.BeginTransactionAsync(cancellationToken);

        try
        {
            var mother = await ReadSplitMotherRowAsync(connection, transaction, motherRuncard, cancellationToken);
            if (mother is null)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new SplitResponse(false, "Active mother runcard was not found.", "", "", request.SplitQty, 0, null, DateTimeOffset.UtcNow);
            }

            var originalMotherQty = mother.Qty;

            if (request.SplitQty >= originalMotherQty)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new SplitResponse(false, "Split qty must be less than mother qty.", "", "", request.SplitQty, originalMotherQty, null, DateTimeOffset.UtcNow);
            }

            var childCount = await ReadRequiredIntAsync(
                connection,
                transaction,
                _queries.Get("CountSplitChildrenByMotherDetail"),
                command => command.Parameters.AddWithValue("@MotherRuncard", motherRuncard),
                cancellationToken);

            var newRuncard = await GenerateSplitChildRuncardAsync(connection, transaction, motherRuncard, childCount + 1, cancellationToken);
            var newAssy = newRuncard;
            var remainingMotherQty = originalMotherQty - request.SplitQty;
            var cdate = DateTimeOffset.UtcNow;

            var voidedMotherRows = await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("VoidSplitMother"),
                command =>
                {
                    command.Parameters.AddWithValue("@MotherRuncard", motherRuncard);
                    command.Parameters.AddWithValue("@Cby", employeeId);
                },
                cancellationToken);
            if (voidedMotherRows <= 0)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new SplitResponse(false, "Mother runcard could not be voided. Please reload and try again.", "", "", request.SplitQty, originalMotherQty, null, DateTimeOffset.UtcNow);
            }

            await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("InsertSplitMotherClone"),
                command =>
                {
                    AddSplitCloneParameters(command, mother, motherRuncard, mother.AssyLot, employeeId, cdate);
                    command.Parameters.AddWithValue("@RemainingQty", remainingMotherQty);
                    command.Parameters.AddWithValue("@OriginalQty", originalMotherQty);
                },
                cancellationToken);

            await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("InsertSplitChildLot"),
                command =>
                {
                    AddSplitCloneParameters(command, mother, motherRuncard, newAssy, employeeId, cdate);
                    command.Parameters.AddWithValue("@ChildWo", mother.WorkOrder ?? "");
                    command.Parameters.AddWithValue("@NewRuncard", newRuncard);
                    command.Parameters.AddWithValue("@NewAssy", newAssy);
                    command.Parameters.AddWithValue("@SplitQty", request.SplitQty);
                    command.Parameters.AddWithValue("@OriginalQty", originalMotherQty);
                    command.Parameters.AddWithValue("@MotherQty", originalMotherQty);
                },
                cancellationToken);

            await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("UpdateActiveTransactionSplitFlag"),
                command =>
                {
                    command.Parameters.AddWithValue("@MotherRuncard", motherRuncard);
                    command.Parameters.AddWithValue("@SplitTo", newRuncard);
                    command.Parameters.AddWithValue("@Cby", employeeId);
                    command.Parameters.AddWithValue("@Cdate", cdate);
                },
                cancellationToken);

            await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("CloneSplitTransactionsForMother"),
                command =>
                {
                    command.Parameters.AddWithValue("@MotherRuncard", motherRuncard);
                    command.Parameters.AddWithValue("@Cby", employeeId);
                    command.Parameters.AddWithValue("@Cdate", cdate);
                },
                cancellationToken);

            await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("CloneSplitTransactionsForChild"),
                command =>
                {
                    command.Parameters.AddWithValue("@MotherRuncard", motherRuncard);
                    command.Parameters.AddWithValue("@ChildRuncard", newRuncard);
                    command.Parameters.AddWithValue("@SplitQty", request.SplitQty);
                    command.Parameters.AddWithValue("@Cby", employeeId);
                    command.Parameters.AddWithValue("@Cdate", cdate);
                },
                cancellationToken);

            await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("InsertSplitHistory"),
                command =>
                {
                    command.Parameters.AddWithValue("@MotherWo", mother.WorkOrder ?? "");
                    command.Parameters.AddWithValue("@MotherRuncard", motherRuncard);
                    command.Parameters.AddWithValue("@MotherAssy", mother.AssyLot ?? "");
                    command.Parameters.AddWithValue("@MotherQty", originalMotherQty);
                    command.Parameters.AddWithValue("@ChildWo", mother.WorkOrder ?? "");
                    command.Parameters.AddWithValue("@NewRuncard", newRuncard);
                    command.Parameters.AddWithValue("@NewAssy", newAssy);
                    command.Parameters.AddWithValue("@SplitQty", request.SplitQty);
                    command.Parameters.AddWithValue("@SumQty", remainingMotherQty);
                    command.Parameters.AddWithValue("@B2BQty", originalMotherQty);
                    command.Parameters.AddWithValue("@WorkCenter", (object?)FirstNonBlank(request.WorkCenter, mother.StartWc) ?? DBNull.Value);
                    command.Parameters.AddWithValue("@Operation", (object?)request.Operation ?? DBNull.Value);
                    command.Parameters.AddWithValue("@Cby", employeeId);
                    command.Parameters.AddWithValue("@Cdate", cdate);
                },
                cancellationToken);

            await transaction.CommitAsync(cancellationToken);
            return new SplitResponse(true, "Split saved.", newRuncard, newAssy, request.SplitQty, remainingMotherQty, FirstNonBlank(request.WorkCenter, mother.StartWc), cdate);
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

    // MERGE: return one or more split CHILD lots back into their original MOTHER lot.
    // The mother keeps its RUNCARD identity (old mother record FLAG='X', re-cloned with the combined qty);
    // all merged children are voided (FLAG='X'). History is written with RCTYPE='MERGE'.
    // NOTE: this is intentionally NOT "COMBINE" (which mints a brand-new combined runcard) — COMBINE is a separate, later feature.
    public async Task<MergeResponse> MergeRuncardsAsync(
        MergeRequest request,
        CancellationToken cancellationToken)
    {
        var mainRuncard = request.MainRuncard?.Trim() ?? "";

        // Children to merge back: prefer ChildRuncards, but accept the legacy Source/Merged fields too.
        var requestedChildren = new List<string>();
        if (request.ChildRuncards is not null)
        {
            requestedChildren.AddRange(request.ChildRuncards);
        }
        if (request.SourceRuncards is not null)
        {
            requestedChildren.AddRange(request.SourceRuncards);
        }
        if (request.MergedRuncards is not null)
        {
            requestedChildren.AddRange(request.MergedRuncards);
        }

        var childRuncards = requestedChildren
            .Select(value => value?.Trim())
            .Where(value => !string.IsNullOrWhiteSpace(value))
            .Select(value => value!)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();

        if (string.IsNullOrWhiteSpace(mainRuncard))
        {
            return new MergeResponse(false, "Main (mother) runcard is required.", 0, mainRuncard);
        }
        if (childRuncards.Count == 0)
        {
            return new MergeResponse(false, "At least one child runcard is required to merge.", 0, mainRuncard);
        }
        if (childRuncards.Any(child => string.Equals(child, mainRuncard, StringComparison.OrdinalIgnoreCase)))
        {
            return new MergeResponse(false, "A child runcard cannot be the same as the mother runcard.", 0, mainRuncard);
        }

        await using var connection = _connectionFactory.Create();
        await connection.OpenAsync(cancellationToken);
        var employeeId = await ResolveEmployeeIdAsync(connection, FirstNonBlank(request.EmpId, request.Cby), cancellationToken);
        if (employeeId is null)
        {
            return new MergeResponse(false, "Cannot map AD username to 6-digit employee ID.", 0, mainRuncard);
        }

        await using var transaction = (SqlTransaction)await connection.BeginTransactionAsync(cancellationToken);
        try
        {
            // Lock + read the active mother row (UPDLOCK/HOLDLOCK in GetSplitMotherForClone).
            var mother = await ReadSplitMotherRowAsync(connection, transaction, mainRuncard, cancellationToken);
            if (mother is null)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new MergeResponse(false, "Mother runcard was not found or already closed.", 0, mainRuncard);
            }

            var oldMotherQty = mother.Qty;

            // Lock + read all rows (mother + children) so we can read child qtys and validate they are still active.
            var allRuncards = new List<string> { mainRuncard };
            allRuncards.AddRange(childRuncards);
            allRuncards = allRuncards.Distinct(StringComparer.OrdinalIgnoreCase).ToList();

            var rows = await ReadMergeRuncardRowsAsync(connection, transaction, allRuncards, cancellationToken);
            var childRows = rows
                .Where(row => childRuncards.Any(child => string.Equals(child, row.Runcard, StringComparison.OrdinalIgnoreCase)))
                .ToList();
            if (childRows.Count != childRuncards.Count)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new MergeResponse(false, "One or more child runcards were not found or already closed.", 0, mainRuncard);
            }

            var sumChildQty = childRows.Sum(row => (long)row.Qty);
            if (sumChildQty <= 0)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new MergeResponse(false, "Total child qty to merge must be greater than zero.", 0, mainRuncard);
            }

            // New mother qty = old mother qty + sum of all merged child qtys. Guard against int overflow.
            var newMotherQtyLong = (long)oldMotherQty + sumChildQty;
            if (newMotherQtyLong <= 0 || newMotherQtyLong > int.MaxValue)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new MergeResponse(false, "Merged mother qty is out of range.", 0, mainRuncard);
            }
            var newMotherQty = (int)newMotherQtyLong;
            var motherAssy = mother.AssyLot ?? "";
            var cdate = DateTimeOffset.UtcNow;

            // Void the old mother record AND all merged children (FLAG='X').
            var voidedRows = await ExecuteMergeInQueryAsync(
                connection,
                transaction,
                _queries.Get("CloseSourceRuncardsForMerge"),
                allRuncards,
                cancellationToken);
            if (voidedRows < allRuncards.Count)
            {
                await transaction.RollbackAsync(cancellationToken);
                return new MergeResponse(false, "Not all runcards were voided for merge. Please reload and try again.", 0, mainRuncard);
            }

            // Clone the mother back under the SAME runcard with the combined qty; OLD_QTY keeps the pre-merge qty.
            await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("InsertMergeCombinedRuncard"),
                command =>
                {
                    AddMergeCombinedParameters(command, mother, mainRuncard, motherAssy, newMotherQty, oldMotherQty, employeeId, cdate);
                },
                cancellationToken);

            // Flag the mother's active transaction(s) FLAG_MERGE='Y' so the routing clone can pick them up.
            await ExecuteMergeInQueryAsync(
                connection,
                transaction,
                _queries.Get("FlagMergeTransactions"),
                new List<string> { mainRuncard },
                cancellationToken);

            // Clone the active routing back onto the (same) mother runcard so it can keep being scanned.
            await ExecuteNonQueryAsync(
                connection,
                transaction,
                _queries.Get("CloneMergeTransactionsForCombined"),
                command =>
                {
                    command.Parameters.AddWithValue("@MainRuncard", mainRuncard);
                    command.Parameters.AddWithValue("@NewRuncard", mainRuncard);
                    command.Parameters.AddWithValue("@TotalQty", newMotherQty);
                    command.Parameters.AddWithValue("@Cby", employeeId);
                    command.Parameters.AddWithValue("@Cdate", cdate);
                },
                cancellationToken);

            // One MERGE history row per merged child.
            foreach (var child in childRows)
            {
                await ExecuteNonQueryAsync(
                    connection,
                    transaction,
                    _queries.Get("InsertMergeHistory"),
                    command =>
                    {
                        command.Parameters.AddWithValue("@MotherWo", (object?)mother.WorkOrder ?? DBNull.Value);
                        command.Parameters.AddWithValue("@MainRuncard", mainRuncard);
                        command.Parameters.AddWithValue("@MotherAssy", (object?)motherAssy ?? DBNull.Value);
                        command.Parameters.AddWithValue("@MotherQty", oldMotherQty);
                        command.Parameters.AddWithValue("@ChildWo", (object?)child.WorkOrder ?? DBNull.Value);
                        command.Parameters.AddWithValue("@SourceRuncard", child.Runcard);
                        command.Parameters.AddWithValue("@SourceAssy", (object?)child.AssyLot ?? DBNull.Value);
                        command.Parameters.AddWithValue("@SourceQty", child.Qty);
                        command.Parameters.AddWithValue("@SumQty", newMotherQty);
                        command.Parameters.AddWithValue("@B2BQty", oldMotherQty);
                        command.Parameters.AddWithValue("@WorkCenter", (object?)FirstNonBlank(request.WorkCenter, mother.StartWc) ?? DBNull.Value);
                        command.Parameters.AddWithValue("@Operation", (object?)request.Operation ?? DBNull.Value);
                        command.Parameters.AddWithValue("@Cby", employeeId);
                        command.Parameters.AddWithValue("@Cdate", cdate);
                    },
                    cancellationToken);
            }

            await transaction.CommitAsync(cancellationToken);
            return new MergeResponse(
                true,
                $"Merge saved. Mother {mainRuncard} qty {oldMotherQty} + {sumChildQty} = {newMotherQty} (merged {childRows.Count} child lot(s)).",
                newMotherQty,
                mainRuncard);
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

    private async Task<SplitMotherRow?> ReadSplitMotherRowAsync(
        SqlConnection connection,
        SqlTransaction transaction,
        string motherRuncard,
        CancellationToken cancellationToken)
    {
        await using var command = new SqlCommand(_queries.Get("GetSplitMotherForClone"), connection, transaction);
        command.Parameters.AddWithValue("@MotherRuncard", motherRuncard);

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
        {
            return null;
        }

        return new SplitMotherRow(
            ReadString(reader, "PLANT"),
            ReadString(reader, "WorkOrder"),
            ReadString(reader, "MATERIAL"),
            ReadString(reader, "RcType"),
            ReadString(reader, "RUNCARD") ?? motherRuncard,
            ReadString(reader, "AssyLot"),
            ReadString(reader, "DateCode"),
            ReadInt(reader, "Qty") ?? 0,
            ReadString(reader, "StartWc"));
    }

    private async Task<string> GenerateSplitChildRuncardAsync(
        SqlConnection connection,
        SqlTransaction transaction,
        string motherRuncard,
        int initialSequence,
        CancellationToken cancellationToken)
    {
        var baseRuncard = motherRuncard.StartsWith("S4", StringComparison.OrdinalIgnoreCase)
            ? "S5" + motherRuncard[2..]
            : motherRuncard;

        for (var sequence = initialSequence; sequence < initialSequence + 702; sequence++)
        {
            var candidate = baseRuncard + ToAlphabetSuffix(sequence);
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

        throw new InvalidOperationException("Unable to generate a unique split child runcard.");
    }

    private async Task<string> GenerateMergedRuncardAsync(
        SqlConnection connection,
        SqlTransaction transaction,
        string mainRuncard,
        CancellationToken cancellationToken)
    {
        var baseRuncard = mainRuncard + "M";
        for (var sequence = 0; sequence <= 702; sequence++)
        {
            var candidate = sequence == 0
                ? baseRuncard
                : baseRuncard + ToAlphabetSuffix(sequence);
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

        throw new InvalidOperationException("Unable to generate a unique merged runcard.");
    }

    private static void AddSplitCloneParameters(
        SqlCommand command,
        SplitMotherRow mother,
        string motherRuncard,
        string? assyLot,
        string employeeId,
        DateTimeOffset cdate)
    {
        command.Parameters.AddWithValue("@Plant", (object?)mother.Plant ?? DBNull.Value);
        command.Parameters.AddWithValue("@WorkOrder", (object?)mother.WorkOrder ?? DBNull.Value);
        command.Parameters.AddWithValue("@Material", (object?)mother.Material ?? DBNull.Value);
        command.Parameters.AddWithValue("@RcType", (object?)mother.RcType ?? DBNull.Value);
        command.Parameters.AddWithValue("@MotherRuncard", motherRuncard);
        command.Parameters.AddWithValue("@MotherAssy", (object?)assyLot ?? DBNull.Value);
        command.Parameters.AddWithValue("@DateCode", (object?)mother.DateCode ?? DBNull.Value);
        command.Parameters.AddWithValue("@StartWc", (object?)mother.StartWc ?? DBNull.Value);
        command.Parameters.AddWithValue("@WorkCenter", (object?)mother.StartWc ?? DBNull.Value);
        command.Parameters.AddWithValue("@Cby", employeeId);
        command.Parameters.AddWithValue("@Cdate", cdate);
    }

    private static void AddMergeCombinedParameters(
        SqlCommand command,
        SplitMotherRow main,
        string newRuncard,
        string newAssy,
        int totalQty,
        int originalQty,
        string employeeId,
        DateTimeOffset cdate)
    {
        command.Parameters.AddWithValue("@Plant", (object?)main.Plant ?? DBNull.Value);
        command.Parameters.AddWithValue("@WorkOrder", (object?)main.WorkOrder ?? DBNull.Value);
        command.Parameters.AddWithValue("@Material", (object?)main.Material ?? DBNull.Value);
        command.Parameters.AddWithValue("@RcType", (object?)main.RcType ?? DBNull.Value);
        command.Parameters.AddWithValue("@NewRuncard", newRuncard);
        command.Parameters.AddWithValue("@NewAssy", newAssy);
        command.Parameters.AddWithValue("@DateCode", (object?)main.DateCode ?? DBNull.Value);
        command.Parameters.AddWithValue("@TotalQty", totalQty);
        command.Parameters.AddWithValue("@OriginalQty", originalQty);
        command.Parameters.AddWithValue("@StartWc", (object?)main.StartWc ?? DBNull.Value);
        command.Parameters.AddWithValue("@Cby", employeeId);
        command.Parameters.AddWithValue("@Cdate", cdate);
    }

    private static string? FirstNonBlank(params string?[] values)
    {
        foreach (var value in values)
        {
            if (!string.IsNullOrWhiteSpace(value))
            {
                return value.Trim();
            }
        }

        return null;
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

        if (trimmed.All(char.IsDigit))
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

    private async Task<EmployeeProfileResponse?> ReadEmployeeProfileAsync(
        SqlConnection connection,
        string? empId,
        CancellationToken cancellationToken)
    {
        var normalizedEmpId = empId?.Trim();
        if (string.IsNullOrWhiteSpace(normalizedEmpId) || !normalizedEmpId.All(char.IsDigit))
        {
            return null;
        }

        await using var command = new SqlCommand(_queries.Get("VerifyEmployeeProfile"), connection);
        command.Parameters.AddWithValue("@EmpId", normalizedEmpId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
        {
            return null;
        }

        var resolvedEmpId = ReadString(reader, "EmpId") ?? normalizedEmpId;
        return new EmployeeProfileResponse(
            resolvedEmpId,
            ReadString(reader, "EmpName") ?? resolvedEmpId,
            ReadString(reader, "Position") ?? "");
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

    private static string NormalizeCustomerType(string? customerType)
    {
        var normalized = (customerType ?? "microchip").Trim().ToLowerInvariant();
        return normalized switch
        {
            "onsemi" => "onsemi",
            _ => "microchip"
        };
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

    private async Task<SaveConfirmContext> ReadSaveConfirmContextAsync(
        SqlConnection connection,
        SaveProductionRequest request,
        CancellationToken cancellationToken)
    {
        await using var command = new SqlCommand(_queries.Get("GetSaveConfirmContext"), connection);
        command.Parameters.AddWithValue("@RuncardNo", request.RuncardNo);
        command.Parameters.AddWithValue("@WorkCenter", DbText(request.WorkCenter));
        command.Parameters.AddWithValue("@Operation", DbText(request.Operation));
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
        {
            return new SaveConfirmContext(
                request.RuncardNo,
                request.RuncardNo,
                null,
                null,
                null,
                null,
                request.Operation,
                request.WorkCenter ?? request.MachineId,
                null,
                request.GoodQty + request.ScrapQty,
                null);
        }

        return new SaveConfirmContext(
            ReadString(reader, "WorkOrder"),
            ReadString(reader, "RoutingNo"),
            ReadString(reader, "Material"),
            ReadString(reader, "MaterialDesc"),
            ReadString(reader, "Unit"),
            ReadString(reader, "Runcard"),
            ReadString(reader, "Operation"),
            ReadString(reader, "WorkCenter"),
            ReadString(reader, "WorkCenterText"),
            ReadInt(reader, "ReceiveQty") ?? request.GoodQty + request.ScrapQty,
            ReadString(reader, "Description"));
    }

    private async Task<ConfirmStandards> ReadConfirmStandardsAsync(
        SqlConnection connection,
        SaveProductionRequest request,
        CancellationToken cancellationToken)
    {
        await using var command = new SqlCommand(_queries.Get("GetConfirmStandards"), connection);
        command.Parameters.AddWithValue("@RuncardNo", request.RuncardNo);
        command.Parameters.AddWithValue("@WorkCenter", DbText(request.WorkCenter));
        command.Parameters.AddWithValue("@Operation", DbText(request.Operation));
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
        {
            return new ConfirmStandards(0m, 0m);
        }

        return new ConfirmStandards(
            ReadDecimal(reader, "MCTime") ?? 0m,
            ReadDecimal(reader, "LaborTime") ?? 0m);
    }

    private static string BuildInClauseSql(string sqlTemplate, IReadOnlyList<string> values)
    {
        var parameterNames = values
            .Select((_, index) => "@Runcard" + index)
            .ToArray();
        return sqlTemplate.Replace("{RuncardList}", string.Join(", ", parameterNames), StringComparison.OrdinalIgnoreCase);
    }

    private static object DbText(string? value)
    {
        return string.IsNullOrWhiteSpace(value) ? DBNull.Value : value.Trim();
    }

    private static void AddHoldParameters(
        SqlCommand command,
        HoldRequest request,
        string? employeeId,
        DateTimeOffset cdate,
        string actionType)
    {
        command.Parameters.AddWithValue("@ActionType", actionType);
        command.Parameters.AddWithValue("@WorkOrder", DbText(request.WorkOrder));
        command.Parameters.AddWithValue("@Runcard", request.Runcard);
        command.Parameters.AddWithValue("@WorkCenter", DbText(request.WorkCenter));
        command.Parameters.AddWithValue("@Operation", DbText(request.Operation));
        command.Parameters.AddWithValue("@SelectReason", DbText(request.SelectReason));
        command.Parameters.AddWithValue("@HoldComment", DbText(request.HoldComment));
        command.Parameters.AddWithValue("@ReleaseComment", DbText(request.ReleaseComment));
        command.Parameters.AddWithValue("@Cby", DbText(employeeId));
        command.Parameters.AddWithValue("@Cdate", cdate);
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

    private sealed record SplitMotherRow(
        string? Plant,
        string? WorkOrder,
        string? Material,
        string? RcType,
        string Runcard,
        string? AssyLot,
        string? DateCode,
        int Qty,
        string? StartWc);

    private sealed record SaveConfirmContext(
        string? WorkOrder,
        string? RoutingNo,
        string? Material,
        string? MaterialDesc,
        string? Unit,
        string? Runcard,
        string? Operation,
        string? WorkCenter,
        string? WorkCenterText,
        int ReceiveQty,
        string? Description);

    private sealed record ConfirmStandards(
        decimal McTime,
        decimal LaborTime);

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

    private static decimal? ReadDecimal(SqlDataReader reader, string column)
    {
        var ordinal = reader.GetOrdinal(column);
        return reader.IsDBNull(ordinal) ? null : Convert.ToDecimal(reader.GetValue(ordinal));
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
