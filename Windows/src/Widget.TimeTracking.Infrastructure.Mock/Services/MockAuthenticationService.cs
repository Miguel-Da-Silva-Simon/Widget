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

        TryCarryOverProfilePhoto(document);

        await SaveAsync(document, cancellationToken);

        return new UserSession(
            AuthenticationState.SignedIn,
            currentUser,
            document.SignedInAtUtc);
    }

    public async Task SignOutAsync(CancellationToken cancellationToken = default)
    {
        await DeleteStoredProfilePhotoIfAnyAsync(cancellationToken);
        await SaveAsync(new UserSessionDocument
        {
            State = AuthenticationState.SignedOut,
            SignedInAtUtc = null,
            UserId = null,
            DisplayName = null,
            Email = null,
            ProfilePhotoFileName = null
        }, cancellationToken);
    }

    private void TryCarryOverProfilePhoto(UserSessionDocument incoming)
    {
        if (!File.Exists(_options.SessionFullPath))
        {
            return;
        }

        try
        {
            var json = File.ReadAllText(_options.SessionFullPath);
            var previous = JsonSerializer.Deserialize<UserSessionDocument>(json, _serializerOptions);
            if (string.IsNullOrWhiteSpace(previous?.ProfilePhotoFileName))
            {
                return;
            }

            var path = Path.Combine(_options.StorageDirectory, previous.ProfilePhotoFileName);
            if (File.Exists(path))
            {
                incoming.ProfilePhotoFileName = previous.ProfilePhotoFileName;
            }
        }
        catch (JsonException)
        {
            // Ignorar sesión previa corrupta.
        }
    }

    private async Task DeleteStoredProfilePhotoIfAnyAsync(CancellationToken cancellationToken)
    {
        if (!File.Exists(_options.SessionFullPath))
        {
            return;
        }

        try
        {
            await using var stream = File.OpenRead(_options.SessionFullPath);
            var previous = await JsonSerializer.DeserializeAsync<UserSessionDocument>(
                stream,
                _serializerOptions,
                cancellationToken);
            if (string.IsNullOrWhiteSpace(previous?.ProfilePhotoFileName))
            {
                return;
            }

            var path = Path.Combine(_options.StorageDirectory, previous.ProfilePhotoFileName);
            if (File.Exists(path))
            {
                File.Delete(path);
            }
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or JsonException)
        {
        }
    }

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
