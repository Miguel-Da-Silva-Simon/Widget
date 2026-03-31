using System.Globalization;
using Widget.TimeTracking.Core.Design;
using Widget.TimeTracking.Core.Enums;
using Widget.TimeTracking.Core.Models;
using Widget.TimeTracking.Core.Services;

namespace Widget.TimeTracking.WidgetHost.Rendering;

internal sealed class TimeTrackingWidgetViewModelMapper
{
    private readonly IUserSessionService _userSessionService;
    private readonly ITimeTrackingService _timeTrackingService;

    public TimeTrackingWidgetViewModelMapper(
        IUserSessionService userSessionService,
        ITimeTrackingService timeTrackingService)
    {
        _userSessionService = userSessionService;
        _timeTrackingService = timeTrackingService;
    }

    public async Task<TimeTrackingWidgetViewModel> MapAsync(
        TimeTrackingSnapshot? snapshot = null,
        CancellationToken cancellationToken = default)
    {
        var session = await _userSessionService.GetCurrentSessionAsync(cancellationToken);

        if (!session.IsAuthenticated)
        {
            return TimeTrackingWidgetViewModel.SignedOut(
                title: "Fichaje",
                message: "Iniciá sesión en la app para fichar con tu cuenta.",
                primaryActionLabel: "Abrir app");
        }

        var resolvedSnapshot = snapshot ?? await _timeTrackingService.GetStateAsync(cancellationToken);
        var availableActions = resolvedSnapshot.AvailableActions.ToHashSet();
        var culture = CultureInfo.CurrentCulture;
        var displayName = session.User?.DisplayName;
        var welcomeName = string.IsNullOrWhiteSpace(displayName)
            ? "Usuario autenticado"
            : displayName.Trim();
        var profilePhotoUrl = await _userSessionService.GetProfilePhotoDataUriAsync(cancellationToken) ?? string.Empty;
        var activeBreakType = resolvedSnapshot.ActiveBreakType;
        var sessionCounter = activeBreakType is BreakType.Coffee or BreakType.Food
            ? FormatDurationWithSeconds(resolvedSnapshot.ActiveBreak?.GetDuration(DateTimeOffset.UtcNow) ?? TimeSpan.Zero)
            : FormatDurationWithSeconds(resolvedSnapshot.Summary.CurrentShiftWorkedDuration);

        return new SignedInWidgetViewModel(
            Title: "Fichaje",
            CustomState: $"{resolvedSnapshot.Status}:{resolvedSnapshot.ActiveBreakType}",
            SurfaceColorHex: BrandColors.White,
            AccentColorHex: BrandColors.PrimaryBlue,
            ProfilePhotoUrl: profilePhotoUrl,
            DisplayName: $"Bienvenido, {welcomeName}",
            StatusHeadline: BuildStatusHeadline(resolvedSnapshot),
            StatusDetail: BuildStatusDetail(resolvedSnapshot),
            SessionCounter: sessionCounter,
            LastAction: resolvedSnapshot.LastAction == TimeTrackingAction.None
                ? "Sin acciones todavía"
                : TranslateAction(resolvedSnapshot.LastAction),
            LastActionTime: resolvedSnapshot.LastActionAtUtc.HasValue
                ? resolvedSnapshot.LastActionAtUtc.Value.ToLocalTime().ToString("g", culture)
                : "—",
            CurrentShiftDuration: FormatDuration(resolvedSnapshot.Summary.CurrentShiftWorkedDuration),
            LastCompletedShiftDuration: FormatDuration(resolvedSnapshot.Summary.LastCompletedShiftWorkedDuration),
            WorkedThisMonthDuration: FormatDuration(resolvedSnapshot.Summary.WorkedThisMonthDuration),
            CoffeeTodayDuration: FormatDuration(resolvedSnapshot.Summary.CoffeeBreakDurationToday),
            FoodTodayDuration: FormatDuration(resolvedSnapshot.Summary.FoodBreakDurationToday),
            TimelineText: BuildTimelineText(resolvedSnapshot, culture),
            ActiveBreakType: activeBreakType,
            CanClockIn: availableActions.Contains(TimeTrackingAction.ClockIn),
            CanStartCoffeeBreak: availableActions.Contains(TimeTrackingAction.StartCoffeeBreak) || availableActions.Contains(TimeTrackingAction.StartBreak),
            CanEndCoffeeBreak: availableActions.Contains(TimeTrackingAction.EndCoffeeBreak) || (activeBreakType == BreakType.Coffee && availableActions.Contains(TimeTrackingAction.EndBreak)),
            CanStartFoodBreak: availableActions.Contains(TimeTrackingAction.StartFoodBreak),
            CanEndFoodBreak: availableActions.Contains(TimeTrackingAction.EndFoodBreak) || (activeBreakType == BreakType.Food && availableActions.Contains(TimeTrackingAction.EndBreak)),
            CanClockOut: availableActions.Contains(TimeTrackingAction.ClockOut));
    }

    private static string BuildStatusHeadline(TimeTrackingSnapshot snapshot) =>
        snapshot.Status switch
        {
            TimeTrackingStatus.NotClockedIn => "Sin fichar",
            TimeTrackingStatus.Working => "Estás trabajando",
            TimeTrackingStatus.OnBreak when snapshot.ActiveBreakType == BreakType.Coffee => "En descanso de café",
            TimeTrackingStatus.OnBreak when snapshot.ActiveBreakType == BreakType.Food => "En descanso de comida",
            TimeTrackingStatus.OnBreak => "En descanso",
            TimeTrackingStatus.OffDuty => "Jornada finalizada",
            _ => snapshot.Status.ToString()
        };

    private static string BuildStatusDetail(TimeTrackingSnapshot snapshot) =>
        snapshot.ActiveBreak switch
        {
            { IsActive: true, Type: BreakType.Coffee } => $"Café activo · {FormatDuration(snapshot.ActiveBreak.GetDuration(DateTimeOffset.UtcNow))}",
            { IsActive: true, Type: BreakType.Food } => $"Comida activa · {FormatDuration(snapshot.ActiveBreak.GetDuration(DateTimeOffset.UtcNow))}",
            _ => $"Jornada actual · {FormatDuration(snapshot.Summary.CurrentShiftWorkedDuration)}"
        };

    private static string TranslateAction(TimeTrackingAction action) =>
        action switch
        {
            TimeTrackingAction.ClockIn => "Entrada",
            TimeTrackingAction.StartBreak => "Iniciar café",
            TimeTrackingAction.EndBreak => "Finalizar descanso",
            TimeTrackingAction.StartCoffeeBreak => "Iniciar café",
            TimeTrackingAction.EndCoffeeBreak => "Finalizar café",
            TimeTrackingAction.StartFoodBreak => "Iniciar comida",
            TimeTrackingAction.EndFoodBreak => "Finalizar comida",
            TimeTrackingAction.ClockOut => "Salida",
            _ => "Sin acciones"
        };

    private static string BuildTimelineText(TimeTrackingSnapshot snapshot, CultureInfo culture)
    {
        if (snapshot.WorkdayEvents.Count == 0)
        {
            return "Todavía no hay hitos registrados hoy.";
        }

        return string.Join(" · ",
            snapshot.WorkdayEvents.Select(item =>
                $"{item.OccurredAtUtc.ToLocalTime().ToString("HH:mm", culture)} {TranslateEvent(item)}"));
    }

    private static string TranslateEvent(WorkdayEvent item) =>
        item.EventType switch
        {
            WorkdayEventType.ClockIn => "Entrada",
            WorkdayEventType.StartCoffeeBreak => "Café",
            WorkdayEventType.EndCoffeeBreak => "Reanudar",
            WorkdayEventType.StartFoodBreak => "Comida",
            WorkdayEventType.EndFoodBreak => "Reanudar",
            WorkdayEventType.ClockOut => "Salida",
            _ => item.EventType.ToString()
        };

    private static string FormatDuration(TimeSpan value)
    {
        var totalHours = (int)value.TotalHours;
        return $"{totalHours:00}h {value.Minutes:00}m";
    }

    private static string FormatDurationWithSeconds(TimeSpan value)
    {
        var totalHours = (int)value.TotalHours;
        return $"{totalHours:00}:{value.Minutes:00}:{value.Seconds:00}";
    }
}
