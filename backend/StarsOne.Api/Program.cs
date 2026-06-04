using Microsoft.AspNetCore.Mvc;
using Microsoft.Data.SqlClient;
using StarsOne.Api;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddSingleton<DbEnvOptions>(_ =>
{
    var envPath = builder.Configuration["Database:EnvFilePath"] ?? @"C:\Users\apirak-mo\Desktop\Safe\db.env";
    return DbEnvOptions.Load(envPath);
});
builder.Services.AddSingleton<SqlConnectionFactory>();
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

app.MapGet("/api/production/runcards/{runcardNo}", async (
    string runcardNo,
    ProductionRepository repository,
    CancellationToken cancellationToken) =>
{
    var detail = await repository.GetProductionDetailAsync(runcardNo, cancellationToken);
    return detail is null ? Results.NotFound() : Results.Ok(detail);
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
    CancellationToken cancellationToken) =>
{
    var runcards = await repository.GetRuncardsByWorkOrderAsync(workOrderNo, cancellationToken);
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

app.Run();
