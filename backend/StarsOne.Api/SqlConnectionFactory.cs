using Microsoft.Data.SqlClient;

namespace StarsOne.Api;

public sealed class SqlConnectionFactory
{
    private readonly string _connectionString;

    public SqlConnectionFactory(string connectionString)
    {
        if (string.IsNullOrWhiteSpace(connectionString))
        {
            throw new InvalidOperationException("DefaultConnection is missing from ConnectionStrings.");
        }

        _connectionString = connectionString;
    }

    public SqlConnectionFactory(DbEnvOptions options)
    {
        var builder = new SqlConnectionStringBuilder
        {
            DataSource = $"{options.Host},{options.Port}",
            InitialCatalog = options.Database,
            UserID = options.User,
            Password = options.Password,
            Encrypt = options.Encrypt,
            TrustServerCertificate = options.TrustServerCertificate,
            ConnectTimeout = 15
        };

        _connectionString = builder.ConnectionString;
        if (string.IsNullOrWhiteSpace(_connectionString))
        {
            throw new InvalidOperationException("Database connection string could not be initialized.");
        }
    }

    public SqlConnection Create()
    {
        return new SqlConnection(_connectionString);
    }
}
