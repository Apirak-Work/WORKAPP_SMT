namespace StarsOne.Api;

public sealed record ValidateScanRequest(
    string UserId,
    string MachineId,
    string RuncardNo);

public sealed record ValidateScanResponse(
    bool IsAllowed,
    string Message,
    bool UserValid,
    bool MachineValid,
    bool RuncardValid);

public sealed record ProductionDetailResponse(
    string RuncardNo,
    string? Description,
    string? Material,
    string? RcQuantity,
    string? QtyRc,
    string? QtyWo,
    string? DateCode,
    string? WorkOrder,
    string? Mpq,
    string? AssyLot,
    string? WaferLot,
    string? OrderType,
    string? Uom,
    string? LotType,
    string? ReelNumber);

public sealed record OperTrackingRow(
    string? Oper,
    string? Wc,
    string? Description,
    string? WorkCenter,
    string? Receive,
    string? Yield,
    string? Scrap,
    string? Move,
    string? PercentYield,
    DateTimeOffset? ReceiveDate,
    DateTimeOffset? ConfirmDate,
    string? En);

public sealed record SaveProductionRequest(
    string UserId,
    string MachineId,
    string RuncardNo,
    int GoodQty,
    int ScrapQty,
    string? FunctionMode,
    DateTimeOffset StartDate,
    DateTimeOffset FinishDate,
    DateTimeOffset PostingDate);

public sealed record SaveProductionResponse(
    bool Success,
    string Message);
