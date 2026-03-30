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
    private const int MaxProfilePhotoBytes = 512 * 1024;
    private const string ProfileAvatarBaseFileName = "profile-avatar";

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
        var document = await TryLoadDocumentAsync(cancellationToken);
        return ToSession(document);
    }

    public async Task<string?> GetProfilePhotoDataUriAsync(CancellationToken cancellationToken = default)
    {
        var document = await TryLoadDocumentAsync(cancellationToken);
        if (document is null
            || document.State != AuthenticationState.SignedIn
            || string.IsNullOrWhiteSpace(document.ProfilePhotoFileName))
        {
            return null;
        }

        var path = Path.Combine(_options.StorageDirectory, document.ProfilePhotoFileName);
        if (!File.Exists(path))
        {
            return null;
        }

        byte[] bytes;
        try
        {
            bytes = await File.ReadAllBytesAsync(path, cancellationToken);
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException)
        {
            return null;
        }

        if (bytes.Length == 0)
        {
            return null;
        }

        var mime = document.ProfilePhotoFileName.EndsWith(".png", StringComparison.OrdinalIgnoreCase)
            ? "image/png"
            : "image/jpeg";
        return $"data:{mime};base64,{Convert.ToBase64String(bytes)}";
    }

    public async Task SaveProfilePhotoAsync(
        Stream imageStream,
        string fileExtension,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(imageStream);

        var ext = NormalizePhotoExtension(fileExtension)
            ?? throw new ArgumentException("Usá .png, .jpg o .jpeg.", nameof(fileExtension));

        var document = await TryLoadDocumentAsync(cancellationToken);
        if (document is null
            || document.State != AuthenticationState.SignedIn
            || string.IsNullOrWhiteSpace(document.UserId))
        {
            throw new InvalidOperationException("Tenés que iniciar sesión para guardar la foto de perfil.");
        }

        await using var ms = new MemoryStream();
        var buffer = new byte[8192];
        var total = 0;
        int read;
        while ((read = await imageStream.ReadAsync(buffer.AsMemory(0, buffer.Length), cancellationToken)) > 0)
        {
            total += read;
            if (total > MaxProfilePhotoBytes)
            {
                throw new InvalidOperationException($"La imagen supera el máximo de {MaxProfilePhotoBytes / 1024} KB.");
            }

            await ms.WriteAsync(buffer.AsMemory(0, read), cancellationToken);
        }

        if (ms.Length == 0)
        {
            throw new InvalidOperationException("El archivo de imagen está vacío.");
        }

        Directory.CreateDirectory(_options.StorageDirectory);

        var newFileName = ProfileAvatarBaseFileName + ext;
        var destPath = Path.Combine(_options.StorageDirectory, newFileName);
        var tempPath = destPath + ".tmp";

        await using (var fileStream = File.Create(tempPath))
        {
            ms.Position = 0;
            await ms.CopyToAsync(fileStream, cancellationToken);
            await fileStream.FlushAsync(cancellationToken);
        }

        File.Move(tempPath, destPath, true);

        if (!string.IsNullOrWhiteSpace(document.ProfilePhotoFileName)
            && !string.Equals(document.ProfilePhotoFileName, newFileName, StringComparison.OrdinalIgnoreCase))
        {
            TryDeleteProfileFile(document.ProfilePhotoFileName);
        }

        document.ProfilePhotoFileName = newFileName;
        await SaveDocumentAsync(document, cancellationToken);
    }

    public async Task ClearProfilePhotoAsync(CancellationToken cancellationToken = default)
    {
        var document = await TryLoadDocumentAsync(cancellationToken);
        if (document is null || document.State != AuthenticationState.SignedIn)
        {
            return;
        }

        if (!string.IsNullOrWhiteSpace(document.ProfilePhotoFileName))
        {
            TryDeleteProfileFile(document.ProfilePhotoFileName);
            document.ProfilePhotoFileName = null;
            await SaveDocumentAsync(document, cancellationToken);
        }
    }

    public async Task<string?> GetProfilePhotoAbsolutePathIfExistsAsync(CancellationToken cancellationToken = default)
    {
        var document = await TryLoadDocumentAsync(cancellationToken);
        if (document is null
            || document.State != AuthenticationState.SignedIn
            || string.IsNullOrWhiteSpace(document.ProfilePhotoFileName))
        {
            return null;
        }

        var path = Path.Combine(_options.StorageDirectory, document.ProfilePhotoFileName);
        return File.Exists(path) ? path : null;
    }

    private async Task<UserSessionDocument?> TryLoadDocumentAsync(CancellationToken cancellationToken)
    {
        if (!File.Exists(_options.SessionFullPath))
        {
            return null;
        }

        try
        {
            await using var stream = File.OpenRead(_options.SessionFullPath);
            return await JsonSerializer.DeserializeAsync<UserSessionDocument>(
                stream,
                _serializerOptions,
                cancellationToken);
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or JsonException)
        {
            return null;
        }
    }

    private async Task SaveDocumentAsync(UserSessionDocument document, CancellationToken cancellationToken)
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

    private void TryDeleteProfileFile(string fileName)
    {
        try
        {
            var path = Path.Combine(_options.StorageDirectory, fileName);
            if (File.Exists(path))
            {
                File.Delete(path);
            }
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException)
        {
            // Ignorar: el widget seguirá sin mostrar la imagen si el archivo no existe.
        }
    }

    private static string? NormalizePhotoExtension(string fileExtension)
    {
        if (string.IsNullOrWhiteSpace(fileExtension))
        {
            return null;
        }

        var e = fileExtension.Trim().StartsWith('.') ? fileExtension.Trim() : "." + fileExtension.Trim();
        return e.ToLowerInvariant() switch
        {
            ".png" => ".png",
            ".jpg" => ".jpg",
            ".jpeg" => ".jpg",
            _ => null
        };
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
