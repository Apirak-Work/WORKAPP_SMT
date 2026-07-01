using Microsoft.AspNetCore.Mvc;
using StarsOne.Api;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddSingleton<DbEnvOptions>(serviceProvider =>
{
    var logger = serviceProvider.GetRequiredService<ILoggerFactory>().CreateLogger("Startup");
    var envPath = builder.Configuration["Database:EnvFilePath"] ?? @"C:\Users\apirak-mo\Desktop\Safe\db.env";
    try
    {
        var options = DbEnvOptions.Load(envPath);
        logger.LogInformation("[Startup] Loaded DB connection from env file '{EnvPath}' -> Host={Host}, Port={Port}, Database={Database}", envPath, options.Host, options.Port, options.Database);
        return options;
    }
    catch (Exception ex)
    {
        logger.LogCritical(ex, "[Startup] Failed to load DB env file '{EnvPath}'. Check the path and that the process has read permission.", envPath);
        throw;
    }
});
builder.Services.AddSingleton<SqlConnectionFactory>(serviceProvider =>
{
    var logger = serviceProvider.GetRequiredService<ILoggerFactory>().CreateLogger("Startup");
    var configuration = serviceProvider.GetRequiredService<IConfiguration>();
    var connectionString = configuration.GetConnectionString("DefaultConnection");
    if (!string.IsNullOrWhiteSpace(connectionString))
    {
        logger.LogInformation("[Startup] Using ConnectionStrings:DefaultConnection from appsettings.json/configuration.");
        return new SqlConnectionFactory(connectionString);
    }

    logger.LogInformation("[Startup] ConnectionStrings:DefaultConnection is empty; falling back to DbEnvOptions (db.env).");
    return new SqlConnectionFactory(serviceProvider.GetRequiredService<DbEnvOptions>());
});
builder.Services.AddSingleton<QueryCatalog>();
builder.Services.AddScoped<ProductionRepository>();
builder.Services.AddCors(options =>
{
    options.AddPolicy("HandheldApp", policy =>
    {
        policy.AllowAnyHeader()
            .AllowAnyMethod()
            .AllowAnyOrigin();
    });
});

var app = builder.Build();

app.UseCors("HandheldApp");

app.MapGet("/health", () => Results.Ok(new
{
    status = "ok",
    service = "StarsOne.Api"
}));

app.MapPost("/api/workflow/validate-scan", async (
    [FromBody] ValidateScanRequest request,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var result = await repository.ValidateScanAsync(request, cancellationToken);
    return result.IsAllowed
        ? Results.Ok(result)
        : Results.BadRequest(result);
});

app.MapGet("/api/auth/verify/{empId}", async (
    string empId,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var employee = await repository.VerifyEmployeeAsync(empId, cancellationToken);
    return employee is null
        ? Results.NotFound(new { message = "User not found." })
        : Results.Ok(employee);
});

app.MapGet("/api/production/runcards/{runcardNo}", async (
    string runcardNo,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var detail = await repository.GetProductionDetailAsync(runcardNo, cancellationToken);
    return detail is null ? Results.NotFound() : Results.Ok(detail);
});

app.MapGet("/api/production/runcard/{rc}/validate", async (
    string rc,
    [FromQuery] string? workCenter,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var result = await repository.ValidateRuncardGatesAsync(rc, workCenter, cancellationToken);
    return result.IsValid ? Results.Ok(result) : Results.BadRequest(result);
});

app.MapGet("/api/production/runcards/{runcardNo}/opers", async (
    string runcardNo,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var opers = await repository.GetOperTrackingAsync(runcardNo, cancellationToken);
    return Results.Ok(opers);
});

app.MapGet("/api/production/workorders/{workOrderNo}/runcards", async (
    string workOrderNo,
    ProductionRepository repository,
    ILoggerFactory loggerFactory,
    CancellationToken cancellationToken) =>
{
    var runcards = await repository.GetRuncardsByWorkOrderAsync(workOrderNo, cancellationToken);
    var logger = loggerFactory.CreateLogger("CheckRuncardByWo");
    logger.LogInformation("WO {WorkOrderNo} returned {Count} runcards", workOrderNo, runcards.Count);
    foreach (var row in runcards)
    {
        logger.LogInformation(
            "WO {WorkOrderNo} RC={Rc} Type={Type} Assy={Assy} Qty={Qty} Action={RcAction} Status={Status}",
            workOrderNo,
            row.Rc,
            row.Type,
            row.Assy,
            row.Qty,
            row.RcAction,
            row.Status);
    }
    return Results.Ok(runcards);
});

app.MapPost("/api/production/save", async (
    [FromBody] SaveProductionRequest request,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var result = await repository.SaveProductionAsync(request, cancellationToken);
    return result.Success ? Results.Ok(result) : Results.BadRequest(result);
});

app.MapGet("/api/production/reasons/hold", async (
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var reasons = await repository.GetHoldReasonsAsync(cancellationToken);
    return Results.Ok(reasons);
});

app.MapGet("/api/production/reasons/reject", async (
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var reasons = await repository.GetRejectReasonsAsync(cancellationToken);
    return Results.Ok(reasons);
});

app.MapPost("/api/production/hold", async (
    [FromBody] HoldRequest request,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var result = await repository.SaveHoldActionAsync(request, cancellationToken);
    return result.Success ? Results.Ok(result) : Results.BadRequest(result);
});

app.MapPost("/api/production/runcard/hold", async (
    [FromBody] HoldRequest request,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var result = await repository.SaveHoldActionAsync(request, cancellationToken);
    return result.Success ? Results.Ok(result) : Results.BadRequest(result);
});

app.MapPost("/api/production/release", async (
    [FromBody] HoldRequest request,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var result = await repository.ReleaseHoldAsync(request, cancellationToken);
    return result.Success ? Results.Ok(result) : Results.BadRequest(result);
});

app.MapPost("/api/production/reject", async (
    [FromBody] SaveRejectRequest request,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var result = await repository.SaveRejectDetailsAsync(request, cancellationToken);
    return result.Success ? Results.Ok(result) : Results.BadRequest(result);
});

app.MapPost("/api/production/split", async (
    [FromBody] SplitRequest request,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var result = await repository.SplitRuncardAsync(request, cancellationToken);
    return result.Success ? Results.Ok(result) : Results.BadRequest(result);
});

app.MapPost("/api/production/runcard/split", async (
    [FromBody] SplitRequest request,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var result = await repository.SplitRuncardAsync(request, cancellationToken);
    return result.Success ? Results.Ok(result) : Results.BadRequest(result);
});

app.MapPost("/api/production/merge", async (
    [FromBody] MergeRequest request,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var result = await repository.MergeRuncardsAsync(request, cancellationToken);
    return result.Success ? Results.Ok(result) : Results.BadRequest(result);
});

app.MapPost("/api/production/runcard/merge", async (
    [FromBody] MergeRequest request,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var result = await repository.MergeRuncardsAsync(request, cancellationToken);
    return result.Success ? Results.Ok(result) : Results.BadRequest(result);
});

app.MapGet("/api/production/runcard/{rc}/split-history", async (
    string rc,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var rows = await repository.GetSplitHistoryAsync(rc, cancellationToken);
    return Results.Ok(rows);
});

app.Run();
