using System.Collections.ObjectModel;

namespace StarsOne.Api;

public sealed class DbEnvOptions
{
    public required string Provider { get; init; }
    public required string Host { get; init; }
    public required int Port { get; init; }
    public required string Database { get; init; }
    public required string User { get; init; }
    public required string Password { get; init; }
    public required bool Encrypt { get; init; }
    public required bool TrustServerCertificate { get; init; }
    public required string Schema { get; init; }
    public required IReadOnlyDictionary<string, string> Raw { get; init; }

    public static DbEnvOptions Load(string path)
    {
        if (!File.Exists(path))
        {
            throw new FileNotFoundException("Database environment file was not found.", path);
        }

        var values = File.ReadAllLines(path)
            .Select(line => line.Trim())
            .Where(line => !string.IsNullOrWhiteSpace(line) && !line.StartsWith("#"))
            .Select(line => line.Split('=', 2))
            .Where(parts => parts.Length == 2)
            .ToDictionary(
                parts => parts[0].Trim(),
                parts => parts[1].Trim().Trim('"'),
                StringComparer.OrdinalIgnoreCase);

        return new DbEnvOptions
        {
            Provider = Required(values, "DB_PROVIDER"),
            Host = Required(values, "DB_HOST"),
            Port = int.Parse(Required(values, "DB_PORT")),
            Database = Required(values, "DB_NAME"),
            User = Required(values, "DB_USER"),
            Password = Required(values, "DB_PASSWORD"),
            Encrypt = ParseBool(values, "DB_ENCRYPT"),
            TrustServerCertificate = ParseBool(values, "DB_TRUST_SERVER_CERTIFICATE"),
            Schema = Required(values, "DB_SCHEMA"),
            Raw = new ReadOnlyDictionary<string, string>(values)
        };
    }

    private static string Required(IReadOnlyDictionary<string, string> values, string key)
    {
        if (!values.TryGetValue(key, out var value) || string.IsNullOrWhiteSpace(value))
        {
            throw new InvalidOperationException($"{key} is missing from the database environment file.");
        }

        return value;
    }

    private static bool ParseBool(IReadOnlyDictionary<string, string> values, string key)
    {
        var value = Required(values, key);
        return value.Equals("true", StringComparison.OrdinalIgnoreCase)
               || value.Equals("1", StringComparison.OrdinalIgnoreCase)
               || value.Equals("yes", StringComparison.OrdinalIgnoreCase);
    }
}
