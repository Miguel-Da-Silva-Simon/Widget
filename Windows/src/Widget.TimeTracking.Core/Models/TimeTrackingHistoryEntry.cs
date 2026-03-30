using Widget.TimeTracking.Core.Enums;

namespace Widget.TimeTracking.Core.Models;

public sealed record TimeTrackingHistoryEntry(
    DateTimeOffset OccurredAtUtc,
    TimeTrackingAction Action,
    TimeTrackingStatus Status,
    BreakType BreakType);
