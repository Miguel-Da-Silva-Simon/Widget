using Widget.TimeTracking.Core.Enums;
using Widget.TimeTracking.Core.Models;

namespace Widget.TimeTracking.Infrastructure.Mock.Persistence;

public sealed class TimeTrackingStateDocument
{
    public TimeTrackingStatus Status { get; set; } = TimeTrackingStatus.NotClockedIn;

    public TimeTrackingAction LastAction { get; set; } = TimeTrackingAction.None;

    public DateTimeOffset? LastActionAtUtc { get; set; }

    public DateTimeOffset? CurrentShiftStartedAtUtc { get; set; }

    public BreakType ActiveBreakType { get; set; } = BreakType.None;

    public long LastCompletedShiftWorkedSeconds { get; set; }

    public long WorkedThisMonthSeconds { get; set; }

    public List<TimeTrackingHistoryEntry> History { get; set; } = [];

    public List<WorkdayEvent> WorkdayEvents { get; set; } = [];

    public List<BreakSession> BreakSessions { get; set; } = [];

    public static TimeTrackingStateDocument CreateDefault() => new();
}
