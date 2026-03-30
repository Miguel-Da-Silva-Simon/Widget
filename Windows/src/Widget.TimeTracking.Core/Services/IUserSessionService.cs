using Widget.TimeTracking.Core.Models;

namespace Widget.TimeTracking.Core.Services;

public interface IUserSessionService
{
    Task<UserSession> GetCurrentSessionAsync(CancellationToken cancellationToken = default);

    /// <summary>Devuelve la foto de perfil como data URI para el widget, o null si no hay imagen.</summary>
    Task<string?> GetProfilePhotoDataUriAsync(CancellationToken cancellationToken = default);

    /// <summary>Guarda la imagen de perfil (sesión iniciada). Extensiones permitidas: .png, .jpg, .jpeg.</summary>
    Task SaveProfilePhotoAsync(Stream imageStream, string fileExtension, CancellationToken cancellationToken = default);

    /// <summary>Elimina la imagen de perfil y actualiza el documento de sesión.</summary>
    Task ClearProfilePhotoAsync(CancellationToken cancellationToken = default);

    /// <summary>Ruta absoluta del archivo de foto si existe y la sesión está iniciada; para vista previa en la app.</summary>
    Task<string?> GetProfilePhotoAbsolutePathIfExistsAsync(CancellationToken cancellationToken = default);
}
