using Widget.TimeTracking.Core.Models;

namespace Widget.TimeTracking.Core.Services;

public interface IUserSessionService
{
    Task<UserSession> GetCurrentSessionAsync(CancellationToken cancellationToken = default);
}
