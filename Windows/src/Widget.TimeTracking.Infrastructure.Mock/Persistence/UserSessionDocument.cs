using Widget.TimeTracking.Core.Enums;

namespace Widget.TimeTracking.Infrastructure.Mock.Persistence;

public sealed class UserSessionDocument
{
    public AuthenticationState State { get; set; } = AuthenticationState.SignedOut;

    public string? UserId { get; set; }

    public string? DisplayName { get; set; }

    public string? Email { get; set; }

    public DateTimeOffset? SignedInAtUtc { get; set; }

    /// <summary>Nombre de archivo en el directorio de estado local (p. ej. profile-avatar.png).</summary>
    public string? ProfilePhotoFileName { get; set; }
}
