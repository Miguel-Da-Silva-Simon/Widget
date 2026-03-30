using System.Text.Json;
using System.Text.Json.Serialization;
using Widget.TimeTracking.Core.Enums;
using Widget.TimeTracking.Core.Models;
using Widget.TimeTracking.Core.Services;
using Widget.TimeTracking.Infrastructure.Mock.Configuration;
using Widget.TimeTracking.Infrastructure.Mock.Persistence;

namespace Widget.TimeTracking.Infrastructure.Mock.Services;

public sealed class MockAuthenticationService : IAuthenticationService
{
    private readonly LocalStateOptions _options;
    private readonly JsonSerializerOptions _serializerOptions;

    public MockAuthenticationService(LocalStateOptions options)
    {
        _options = options;
        _serializerOptions = new JsonSerializerOptions
        {
            WriteIndented = true
        };
        _serializerOptions.Converters.Add(new JsonStringEnumConverter());
    }

    public async Task<UserSession> SignInAsync(CancellationToken cancellationToken = default)
    {
        var currentUser = CreateLocalMockUser();
        var document = new UserSessionDocument
        {
            State = AuthenticationState.SignedIn,
            UserId = currentUser.UserId,
            DisplayName = currentUser.DisplayName,
            Email = currentUser.Email,
            SignedInAtUtc = DateTimeOffset.UtcNow
        };

        await SaveAsync(document, cancellationToken);

        return new UserSession(
            AuthenticationState.SignedIn,
            currentUser,
            document.SignedInAtUtc);
    }

    public Task SignOutAsync(CancellationToken cancellationToken = default) =>
        SaveAsync(new UserSessionDocument
        {
            State = AuthenticationState.SignedOut,
            SignedInAtUtc = null,
            UserId = null,
            DisplayName = null,
            Email = null
        }, cancellationToken);

    private static CurrentUser CreateLocalMockUser()
    {
        var userName = Environment.UserName;
        var safeUserName = string.IsNullOrWhiteSpace(userName) ? "local-user" : userName.Trim();
        return new CurrentUser(
            safeUserName,
            safeUserName,
            null);
    }

    private async Task SaveAsync(UserSessionDocument document, CancellationToken cancellationToken)
    {
        Directory.CreateDirectory(_options.StorageDirectory);

        var temporaryPath = $"{_options.SessionFullPath}.tmp";

        await using (var stream = File.Create(temporaryPath))
        {
            await JsonSerializer.SerializeAsync(stream, document, _serializerOptions, cancellationToken);
            await stream.FlushAsync(cancellationToken);
        }

        File.Move(temporaryPath, _options.SessionFullPath, true);
    }
}
