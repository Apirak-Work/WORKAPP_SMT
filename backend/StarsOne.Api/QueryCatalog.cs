namespace StarsOne.Api;

public sealed class QueryCatalog
{
    private readonly IConfiguration _configuration;
    private readonly DbEnvOptions _db;

    public QueryCatalog(IConfiguration configuration, DbEnvOptions db)
    {
        _configuration = configuration;
        _db = db;
    }

    public string Get(string name)
    {
        var query = _configuration[$"Queries:{name}"];
        if (string.IsNullOrWhiteSpace(query))
        {
            throw new InvalidOperationException($"Query '{name}' is not configured.");
        }

        return query.Replace("{schema}", QuoteIdentifier(_db.Schema), StringComparison.OrdinalIgnoreCase);
    }

    private static string QuoteIdentifier(string identifier)
    {
        return $"[{identifier.Replace("]", "]]")}]";
    }
}
