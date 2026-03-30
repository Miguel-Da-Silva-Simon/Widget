using Widget.TimeTracking.Core.Enums;

namespace Widget.TimeTracking.Core.Models;

public sealed record WorkdayEvent(
    DateTimeOffset OccurredAtUtc,
    WorkdayEventType EventType,
    TimeTrackingStatus StatusAfterEvent,
    BreakType BreakType);
