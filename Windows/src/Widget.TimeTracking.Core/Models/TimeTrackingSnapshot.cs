using Widget.TimeTracking.Core.Enums;

using Widget.TimeTracking.Core.Rules;

namespace Widget.TimeTracking.Core.Models;

public sealed record TimeTrackingSnapshot(
    TimeTrackingStatus Status,
    TimeTrackingAction LastAction,
    DateTimeOffset? LastActionAtUtc,
    BreakType ActiveBreakType,
    BreakSession? ActiveBreak,
    IReadOnlyList<TimeTrackingHistoryEntry> History,
    IReadOnlyList<WorkdayEvent> WorkdayEvents,
    IReadOnlyList<BreakSession> BreakSessions,
    DailyWorkSummary Summary,
    IReadOnlyList<TimeTrackingAction> AvailableActions)
{
    public static TimeTrackingSnapshot Initial =>
        new(
            TimeTrackingStatus.NotClockedIn,
            TimeTrackingAction.None,
            null,
            BreakType.None,
            null,
            Array.Empty<TimeTrackingHistoryEntry>(),
            Array.Empty<WorkdayEvent>(),
            Array.Empty<BreakSession>(),
            DailyWorkSummary.Empty(DateOnly.FromDateTime(DateTime.UtcNow)),
            TimeTrackingStateMachine.GetAvailableActions(TimeTrackingStatus.NotClockedIn, BreakType.None));
}
