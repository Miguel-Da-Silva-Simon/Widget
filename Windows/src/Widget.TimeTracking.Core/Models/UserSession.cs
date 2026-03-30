using Widget.TimeTracking.Core.Enums;

namespace Widget.TimeTracking.Core.Models;

public sealed record UserSession(
    AuthenticationState State,
    CurrentUser? User,
    DateTimeOffset? SignedInAtUtc)
{
    public bool IsAuthenticated => State == AuthenticationState.SignedIn && User is not null;

    public static UserSession SignedOut() =>
        new(AuthenticationState.SignedOut, null, null);
}
