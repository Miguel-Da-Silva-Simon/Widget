using System.Text.Json;
using System.Text.Json.Serialization;
using Widget.TimeTracking.Core.Enums;
using Widget.TimeTracking.Core.Models;
using Widget.TimeTracking.Core.Services;
using Widget.TimeTracking.Infrastructure.Mock.Configuration;
using Widget.TimeTracking.Infrastructure.Mock.Persistence;

namespace Widget.TimeTracking.Infrastructure.Mock.Services;

public sealed class LocalJsonUserSessionService : IUserSessionService
{
    private readonly LocalStateOptions _options;
    private readonly JsonSerializerOptions _serializerOptions;

    public LocalJsonUserSessionService(LocalStateOptions options)
    {
        _options = options;
        _serializerOptions = new JsonSerializerOptions
        {
            WriteIndented = true
        };
        _serializerOptions.Converters.Add(new JsonStringEnumConverter());
    }

    public async Task<UserSession> GetCurrentSessionAsync(CancellationToken cancellationToken = default)
    {
        if (!File.Exists(_options.SessionFullPath))
        {
            return UserSession.SignedOut();
        }

        try
        {
            await using var stream = File.OpenRead(_options.SessionFullPath);
            var document = await JsonSerializer.DeserializeAsync<UserSessionDocument>(
                stream,
                _serializerOptions,
                cancellationToken);

            return ToSession(document);
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or JsonException)
        {
            return UserSession.SignedOut();
        }
    }

    private static UserSession ToSession(UserSessionDocument? document)
    {
        if (document is null || document.State != AuthenticationState.SignedIn)
        {
            return UserSession.SignedOut();
        }

        if (string.IsNullOrWhiteSpace(document.UserId) || string.IsNullOrWhiteSpace(document.DisplayName))
        {
            return UserSession.SignedOut();
        }

        return new UserSession(
            AuthenticationState.SignedIn,
            new CurrentUser(document.UserId, document.DisplayName, document.Email),
            document.SignedInAtUtc);
    }
}
