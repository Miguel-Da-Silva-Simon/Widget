using Widget.TimeTracking.Core.Models;

namespace Widget.TimeTracking.Core.Services;

public interface IAuthenticationService
{
    Task<UserSession> SignInAsync(CancellationToken cancellationToken = default);

    Task SignOutAsync(CancellationToken cancellationToken = default);
}
