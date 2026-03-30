using Widget.TimeTracking.Core.Enums;

namespace Widget.TimeTracking.Core.Models;

public sealed record BreakSession(
    BreakType Type,
    DateTimeOffset StartedAtUtc,
    DateTimeOffset? EndedAtUtc)
{
    public bool IsActive => EndedAtUtc is null;

    public TimeSpan GetDuration(DateTimeOffset nowUtc)
    {
        var effectiveEnd = EndedAtUtc ?? nowUtc;
        return effectiveEnd <= StartedAtUtc
            ? TimeSpan.Zero
            : effectiveEnd - StartedAtUtc;
    }
}
