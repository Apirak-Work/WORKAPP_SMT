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

public sealed record EmployeeProfileResponse(
    string EmpId,
    string EmpName,
    string Position);

public sealed record ValidationResponse(
    bool IsValid,
    List<string> ErrorMessages);

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

public sealed record RuncardOverviewRow(
    string? Type,
    string? Rc,
    string? Assy,
    string? Qty,
    string? RcAction,
    string? Status);

public sealed record SaveProductionRequest(
    string UserId,
    string MachineId,
    string RuncardNo,
    int GoodQty,
    int ScrapQty,
    string? WorkCenter,
    string? Operation,
    string? FunctionMode,
    DateTimeOffset StartDate,
    DateTimeOffset FinishDate,
    DateTimeOffset PostingDate);

public sealed record SaveProductionResponse(
    bool Success,
    string Message);

public sealed record RejectDetailItem(
    string ReasonCode,
    string ReasonDesc,
    int RejectQty);

public sealed record SaveRejectRequest(
    string? WorkOrder,
    string? Runcard,
    string? WcNo,
    string? Operation,
    string? Station,
    string? Cby,
    List<RejectDetailItem>? Rejects,
    string? Wo,
    string? RuncardNo,
    int? RejectQty,
    string? ReasonCode,
    string? ReasonDesc,
    string? EmpId);

public sealed record SaveRejectResponse(
    bool Success,
    string Message);

public sealed record RejectReasonDto(
    string? ReasonCode,
    string? Description,
    string? ReasonGroup);

public sealed record HoldReasonDto(
    string? ReasonCode,
    string? Description);

public sealed record HoldRequest(
    string WorkOrder,
    string Runcard,
    string? Material,
    string? WorkCenter,
    string? Operation,
    string? Cby,
    string? SelectReason,
    string? TopicDamage,
    string? HoldComment,
    string? ReleaseComment,
    string ActionType);

public sealed record HoldResponse(
    bool Success,
    string Message);

public sealed record SplitRequest(
    string? WorkOrder,
    string? Material,
    string? MotherRuncard,
    string? MotherAssyLot,
    int SplitQty,
    int MotherQty,
    string? WorkCenter,
    string? Operation,
    string? Cby,
    string? CustomerType,
    string? RuncardNo,
    string? EmpId);

public sealed record SplitResponse(
    bool Success,
    string Message,
    string NewRuncard,
    string NewAssy,
    int SplitQty,
    int MotherQty,
    string? WorkCenter,
    DateTimeOffset Cdate);

public sealed record MergeRequest(
    string? MainRuncard,
    List<string>? SourceRuncards,
    string? WorkCenter,
    string? Operation,
    string? Cby,
    List<string>? MergedRuncards,
    string? EmpId,
    // MERGE = return split child lots back into their original mother.
    // Preferred payload field; SourceRuncards/MergedRuncards are still accepted for back-compat.
    List<string>? ChildRuncards = null);

public sealed record MergeResponse(
    bool Success,
    string Message,
    int TotalMergedQty,
    string MainRuncard);

public sealed record SplitHistoryDto(
    string? Runcard,
    string? AssyLot,
    int? Qty,
    string? Mother,
    int? MotherQty,
    string? Wc,
    DateTimeOffset? Cdate);
