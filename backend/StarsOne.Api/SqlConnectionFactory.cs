using Microsoft.Data.SqlClient;

namespace StarsOne.Api;

public sealed class SqlConnectionFactory
{
    private readonly DbEnvOptions _options;

    public SqlConnectionFactory(DbEnvOptions options)
    {
        _options = options;
    }

    public SqlConnection Create()
    {
        var builder = new SqlConnectionStringBuilder
        {
            DataSource = $"{_options.Host},{_options.Port}",
            InitialCatalog = _options.Database,
            UserID = _options.User,
            Password = _options.Password,
            Encrypt = _options.Encrypt,
            TrustServerCertificate = _options.TrustServerCertificate,
            ConnectTimeout = 15
        };

        return new SqlConnection(builder.ConnectionString);
    }
}
