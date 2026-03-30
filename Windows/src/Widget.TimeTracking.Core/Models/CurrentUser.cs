namespace Widget.TimeTracking.Core.Models;

public sealed record CurrentUser(
    string UserId,
    string DisplayName,
    string? Email);
