using Widget.TimeTracking.Core.Enums;
using Widget.TimeTracking.Core.Models;
using Widget.TimeTracking.Core.Results;
using Widget.TimeTracking.Core.Rules;
using Widget.TimeTracking.Core.Services;
using Widget.TimeTracking.Infrastructure.Mock.Configuration;
using Widget.TimeTracking.Infrastructure.Mock.Persistence;

namespace Widget.TimeTracking.Infrastructure.Mock.Services;

public sealed class LocalJsonTimeTrackingService : ITimeTrackingService
{
    private readonly JsonTimeTrackingStore _store;
    private readonly LocalStateOptions _options;

    public LocalJsonTimeTrackingService(JsonTimeTrackingStore store, LocalStateOptions options)
    {
        _store = store;
        _options = options;
    }

    public async Task<TimeTrackingSnapshot> GetStateAsync(CancellationToken cancellationToken = default)
    {
        var document = await _store.LoadAsync(cancellationToken);
        return ToSnapshot(document, DateTimeOffset.UtcNow);
    }

    public Task<TimeTrackingCommandResult> ClockInAsync(CancellationToken cancellationToken = default) =>
        ExecuteAsync(
            TimeTrackingAction.ClockIn,
            WorkdayEventType.ClockIn,
            cancellationToken,
            onSuccess: (document, nowUtc) =>
            {
                document.CurrentShiftStartedAtUtc = nowUtc;
                document.ActiveBreakType = BreakType.None;
            });

    public Task<TimeTrackingCommandResult> StartBreakAsync(CancellationToken cancellationToken = default) =>
        StartCoffeeBreakAsync(cancellationToken);

    public async Task<TimeTrackingCommandResult> EndBreakAsync(CancellationToken cancellationToken = default)
    {
        var document = await _store.LoadAsync(cancellationToken);

        return document.ActiveBreakType switch
        {
            BreakType.Coffee => await EndCoffeeBreakAsync(cancellationToken),
            BreakType.Food => await EndFoodBreakAsync(cancellationToken),
            _ => await BuildFailureAsync(document, "No hay un descanso activo para finalizar.", cancellationToken)
        };
    }

    public Task<TimeTrackingCommandResult> StartCoffeeBreakAsync(CancellationToken cancellationToken = default) =>
        ExecuteAsync(
            TimeTrackingAction.StartCoffeeBreak,
            WorkdayEventType.StartCoffeeBreak,
            cancellationToken,
            onSuccess: (document, nowUtc) =>
            {
                document.ActiveBreakType = BreakType.Coffee;
                document.BreakSessions.Add(new BreakSession(BreakType.Coffee, nowUtc, null));
            });

    public Task<TimeTrackingCommandResult> EndCoffeeBreakAsync(CancellationToken cancellationToken = default) =>
        ExecuteAsync(
            TimeTrackingAction.EndCoffeeBreak,
            WorkdayEventType.EndCoffeeBreak,
            cancellationToken,
            onSuccess: (document, nowUtc) =>
            {
                CloseActiveBreak(document, BreakType.Coffee, nowUtc);
                document.ActiveBreakType = BreakType.None;
            });

    public Task<TimeTrackingCommandResult> StartFoodBreakAsync(CancellationToken cancellationToken = default) =>
        ExecuteAsync(
            TimeTrackingAction.StartFoodBreak,
            WorkdayEventType.StartFoodBreak,
            cancellationToken,
            onSuccess: (document, nowUtc) =>
            {
                document.ActiveBreakType = BreakType.Food;
                document.BreakSessions.Add(new BreakSession(BreakType.Food, nowUtc, null));
            });

    public Task<TimeTrackingCommandResult> EndFoodBreakAsync(CancellationToken cancellationToken = default) =>
        ExecuteAsync(
            TimeTrackingAction.EndFoodBreak,
            WorkdayEventType.EndFoodBreak,
            cancellationToken,
            onSuccess: (document, nowUtc) =>
            {
                CloseActiveBreak(document, BreakType.Food, nowUtc);
                document.ActiveBreakType = BreakType.None;
            });

    public Task<TimeTrackingCommandResult> ClockOutAsync(CancellationToken cancellationToken = default) =>
        ExecuteAsync(
            TimeTrackingAction.ClockOut,
            WorkdayEventType.ClockOut,
            cancellationToken,
            onSuccess: (document, nowUtc) =>
            {
                CloseActiveBreakIfAny(document, nowUtc);

                if (document.CurrentShiftStartedAtUtc.HasValue)
                {
                    var completedDuration = CalculateWorkedDurationForShift(document, document.CurrentShiftStartedAtUtc.Value, nowUtc);
                    document.LastCompletedShiftWorkedSeconds = (long)completedDuration.TotalSeconds;
                    document.WorkedThisMonthSeconds += (long)completedDuration.TotalSeconds;
                }

                document.CurrentShiftStartedAtUtc = null;
                document.ActiveBreakType = BreakType.None;
            });

    private async Task<TimeTrackingCommandResult> ExecuteAsync(
        TimeTrackingAction action,
        WorkdayEventType eventType,
        CancellationToken cancellationToken,
        Action<TimeTrackingStateDocument, DateTimeOffset> onSuccess)
    {
        var document = await _store.LoadAsync(cancellationToken);
        var nowUtc = DateTimeOffset.UtcNow;

        if (!TimeTrackingStateMachine.TryTransition(
                document.Status,
                document.ActiveBreakType,
                action,
                out var nextStatus,
                out var errorMessage))
        {
            return await BuildFailureAsync(document, errorMessage ?? "La acción no es válida.", cancellationToken);
        }

        var breakTypeForAction = ResolveBreakTypeForAction(action, document.ActiveBreakType);
        onSuccess(document, nowUtc);

        document.Status = nextStatus;
        document.LastAction = action;
        document.LastActionAtUtc = nowUtc;
        document.History.Add(new TimeTrackingHistoryEntry(nowUtc, action, nextStatus, breakTypeForAction));
        document.WorkdayEvents.Add(new WorkdayEvent(nowUtc, eventType, nextStatus, breakTypeForAction));

        TrimHistory(document);
        await _store.SaveAsync(document, cancellationToken);

        return TimeTrackingCommandResult.Success(ToSnapshot(document, nowUtc));
    }

    private async Task<TimeTrackingCommandResult> BuildFailureAsync(
        TimeTrackingStateDocument document,
        string message,
        CancellationToken cancellationToken)
    {
        var snapshot = ToSnapshot(document, DateTimeOffset.UtcNow);
        await Task.CompletedTask;
        return TimeTrackingCommandResult.Failure(snapshot, message);
    }

    private TimeTrackingSnapshot ToSnapshot(TimeTrackingStateDocument document, DateTimeOffset nowUtc)
    {
        var currentDay = DateOnly.FromDateTime(nowUtc.LocalDateTime);
        var activeBreak = document.BreakSessions.LastOrDefault(session => session.IsActive);
        var todayBreakSessions = document.BreakSessions
            .Where(session => DateOnly.FromDateTime(session.StartedAtUtc.LocalDateTime) == currentDay)
            .OrderBy(session => session.StartedAtUtc)
            .ToArray();
        var todayEvents = document.WorkdayEvents
            .Where(item => DateOnly.FromDateTime(item.OccurredAtUtc.LocalDateTime) == currentDay)
            .OrderBy(item => item.OccurredAtUtc)
            .ToArray();
        var currentShiftWorked = document.CurrentShiftStartedAtUtc.HasValue
            ? CalculateWorkedDurationForShift(document, document.CurrentShiftStartedAtUtc.Value, nowUtc)
            : TimeSpan.Zero;
        var summary = new DailyWorkSummary(
            Day: currentDay,
            CurrentShiftWorkedDuration: currentShiftWorked,
            LastCompletedShiftWorkedDuration: TimeSpan.FromSeconds(document.LastCompletedShiftWorkedSeconds),
            WorkedThisMonthDuration: TimeSpan.FromSeconds(document.WorkedThisMonthSeconds) + currentShiftWorked,
            CoffeeBreakDurationToday: SumBreakDuration(todayBreakSessions, BreakType.Coffee, nowUtc),
            FoodBreakDurationToday: SumBreakDuration(todayBreakSessions, BreakType.Food, nowUtc));

        return new TimeTrackingSnapshot(
            Status: document.Status,
            LastAction: document.LastAction,
            LastActionAtUtc: document.LastActionAtUtc,
            ActiveBreakType: document.ActiveBreakType,
            ActiveBreak: activeBreak,
            History: document.History
                .OrderByDescending(item => item.OccurredAtUtc)
                .ToArray(),
            WorkdayEvents: todayEvents,
            BreakSessions: todayBreakSessions,
            Summary: summary,
            AvailableActions: TimeTrackingStateMachine.GetAvailableActions(document.Status, document.ActiveBreakType));
    }

    private static TimeSpan SumBreakDuration(IEnumerable<BreakSession> sessions, BreakType type, DateTimeOffset nowUtc) =>
        sessions
            .Where(session => session.Type == type)
            .Aggregate(TimeSpan.Zero, (total, session) => total + session.GetDuration(nowUtc));

    private static void CloseActiveBreak(TimeTrackingStateDocument document, BreakType breakType, DateTimeOffset nowUtc)
    {
        var activeBreakIndex = document.BreakSessions.FindLastIndex(session => session.IsActive && session.Type == breakType);
        if (activeBreakIndex < 0)
        {
            return;
        }

        var activeBreak = document.BreakSessions[activeBreakIndex];
        document.BreakSessions[activeBreakIndex] = activeBreak with { EndedAtUtc = nowUtc };
    }

    private static void CloseActiveBreakIfAny(TimeTrackingStateDocument document, DateTimeOffset nowUtc)
    {
        var activeBreakIndex = document.BreakSessions.FindLastIndex(session => session.IsActive);
        if (activeBreakIndex < 0)
        {
            return;
        }

        var activeBreak = document.BreakSessions[activeBreakIndex];
        document.BreakSessions[activeBreakIndex] = activeBreak with { EndedAtUtc = nowUtc };
    }

    private static TimeSpan CalculateWorkedDurationForShift(
        TimeTrackingStateDocument document,
        DateTimeOffset shiftStartUtc,
        DateTimeOffset nowUtc)
    {
        if (nowUtc <= shiftStartUtc)
        {
            return TimeSpan.Zero;
        }

        var grossDuration = nowUtc - shiftStartUtc;
        var breakDuration = document.BreakSessions
            .Where(session => session.StartedAtUtc >= shiftStartUtc)
            .Aggregate(TimeSpan.Zero, (total, session) => total + session.GetDuration(nowUtc));

        var netDuration = grossDuration - breakDuration;
        return netDuration < TimeSpan.Zero ? TimeSpan.Zero : netDuration;
    }

    private void TrimHistory(TimeTrackingStateDocument document)
    {
        if (document.History.Count > _options.MaxHistoryEntries)
        {
            document.History = document.History
                .OrderByDescending(item => item.OccurredAtUtc)
                .Take(_options.MaxHistoryEntries)
                .OrderBy(item => item.OccurredAtUtc)
                .ToList();
        }

        var cutoffDate = DateTimeOffset.UtcNow.AddDays(-30);
        document.WorkdayEvents = document.WorkdayEvents
            .Where(item => item.OccurredAtUtc >= cutoffDate)
            .OrderBy(item => item.OccurredAtUtc)
            .ToList();
        document.BreakSessions = document.BreakSessions
            .Where(item => item.StartedAtUtc >= cutoffDate)
            .OrderBy(item => item.StartedAtUtc)
            .ToList();
    }

    private static BreakType ResolveBreakTypeForAction(TimeTrackingAction action, BreakType activeBreakTypeBeforeAction) =>
        action switch
        {
            TimeTrackingAction.StartCoffeeBreak or TimeTrackingAction.EndCoffeeBreak => BreakType.Coffee,
            TimeTrackingAction.StartFoodBreak or TimeTrackingAction.EndFoodBreak => BreakType.Food,
            TimeTrackingAction.StartBreak or TimeTrackingAction.EndBreak => activeBreakTypeBeforeAction,
            _ => BreakType.None
        };
}
