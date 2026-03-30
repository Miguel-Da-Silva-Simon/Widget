using System.Globalization;
using System.Text.Json;
using Microsoft.Windows.Widgets.Providers;
using Widget.TimeTracking.Core.Design;
using Widget.TimeTracking.Core.Enums;
using Widget.TimeTracking.Core.Models;
using Widget.TimeTracking.Core.Services;

namespace Widget.TimeTracking.App.Services;

internal sealed class WidgetRefreshService
{
    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    private readonly IUserSessionService _userSessionService;
    private readonly ITimeTrackingService _timeTrackingService;

    public WidgetRefreshService(
        IUserSessionService userSessionService,
        ITimeTrackingService timeTrackingService)
    {
        _userSessionService = userSessionService;
        _timeTrackingService = timeTrackingService;
    }

    public async Task RefreshAsync(CancellationToken cancellationToken = default)
    {
        IReadOnlyList<WidgetInfo> widgets;

        try
        {
            widgets = WidgetManager.GetDefault().GetWidgetInfos();
        }
        catch
        {
            return;
        }

        if (widgets.Count == 0)
        {
            return;
        }

        var session = await _userSessionService.GetCurrentSessionAsync(cancellationToken);
        var snapshot = await _timeTrackingService.GetStateAsync(cancellationToken);
        var profilePhotoUrl = session.IsAuthenticated
            ? await _userSessionService.GetProfilePhotoDataUriAsync(cancellationToken) ?? string.Empty
            : string.Empty;
        var data = BuildData(session, snapshot, profilePhotoUrl);
        var customState = session.IsAuthenticated
            ? $"{snapshot.Status}:{snapshot.ActiveBreakType}"
            : "SignedOut";

        foreach (var widget in widgets)
        {
            if (!string.Equals(widget.WidgetContext.DefinitionId, WidgetRefreshConstants.WidgetDefinitionId, StringComparison.Ordinal))
            {
                continue;
            }

            var update = new WidgetUpdateRequestOptions(widget.WidgetContext.Id)
            {
                Data = data,
                CustomState = customState
            };

            WidgetManager.GetDefault().UpdateWidget(update);
        }
    }

    private static string BuildData(UserSession session, TimeTrackingSnapshot snapshot, string profilePhotoUrl)
    {
        if (!session.IsAuthenticated)
        {
            return JsonSerializer.Serialize(new
            {
                Title = "Fichaje",
                CustomState = "SignedOut",
                SurfaceColorHex = BrandColors.White,
                AccentColorHex = BrandColors.PrimaryBlue,
                IsSignedOut = true,
                IsSignedIn = false,
                HasProfilePhoto = false,
                ProfilePhotoUrl = string.Empty,
                Message = "Inici sesin en la app para fichar con tu cuenta.",
                PrimaryActionLabel = "Abrir app",
                DisplayName = string.Empty,
                StatusHeadline = string.Empty,
                StatusDetail = string.Empty,
                LastAction = string.Empty,
                LastActionTime = string.Empty,
                LastCompletedShiftDuration = string.Empty,
                WorkedThisMonthDuration = string.Empty,
                CoffeeTodayDuration = string.Empty,
                FoodTodayDuration = string.Empty,
                TimelineText = string.Empty,
                CanClockIn = false,
                CanStartCoffeeBreak = false,
                CanEndCoffeeBreak = false,
                CanStartFoodBreak = false,
                CanEndFoodBreak = false,
                CanClockOut = false
            }, SerializerOptions);
        }

        var availableActions = snapshot.AvailableActions.ToHashSet();
        var culture = CultureInfo.CurrentCulture;

        return JsonSerializer.Serialize(new
        {
            Title = "Fichaje",
            CustomState = $"{snapshot.Status}:{snapshot.ActiveBreakType}",
            SurfaceColorHex = BrandColors.White,
            AccentColorHex = BrandColors.PrimaryBlue,
            IsSignedOut = false,
            IsSignedIn = true,
            HasProfilePhoto = !string.IsNullOrEmpty(profilePhotoUrl),
            ProfilePhotoUrl = profilePhotoUrl,
            Message = string.Empty,
            PrimaryActionLabel = string.Empty,
            DisplayName = string.IsNullOrWhiteSpace(session.User?.DisplayName) ? "Usuario autenticado" : session.User.DisplayName,
            StatusHeadline = BuildStatusHeadline(snapshot),
            StatusDetail = BuildStatusDetail(snapshot),
            LastAction = snapshot.LastAction == TimeTrackingAction.None
                ? "Sin acciones todava"
                : TranslateAction(snapshot.LastAction),
            LastActionTime = snapshot.LastActionAtUtc.HasValue
                ? snapshot.LastActionAtUtc.Value.ToLocalTime().ToString("g", culture)
                : "",
            LastCompletedShiftDuration = FormatDuration(snapshot.Summary.LastCompletedShiftWorkedDuration),
            WorkedThisMonthDuration = FormatDuration(snapshot.Summary.WorkedThisMonthDuration),
            CoffeeTodayDuration = FormatDuration(snapshot.Summary.CoffeeBreakDurationToday),
            FoodTodayDuration = FormatDuration(snapshot.Summary.FoodBreakDurationToday),
            TimelineText = BuildTimelineText(snapshot, culture),
            CanClockIn = availableActions.Contains(TimeTrackingAction.ClockIn),
            CanStartCoffeeBreak = availableActions.Contains(TimeTrackingAction.StartCoffeeBreak) || availableActions.Contains(TimeTrackingAction.StartBreak),
            CanEndCoffeeBreak = availableActions.Contains(TimeTrackingAction.EndCoffeeBreak) || (snapshot.ActiveBreakType == BreakType.Coffee && availableActions.Contains(TimeTrackingAction.EndBreak)),
            CanStartFoodBreak = availableActions.Contains(TimeTrackingAction.StartFoodBreak),
            CanEndFoodBreak = availableActions.Contains(TimeTrackingAction.EndFoodBreak),
            CanClockOut = availableActions.Contains(TimeTrackingAction.ClockOut)
        }, SerializerOptions);
    }

    private static string BuildStatusHeadline(TimeTrackingSnapshot snapshot) =>
        snapshot.Status switch
        {
            TimeTrackingStatus.NotClockedIn => "Sin fichar",
            TimeTrackingStatus.Working => "Ests trabajando",
            TimeTrackingStatus.OnBreak when snapshot.ActiveBreakType == BreakType.Coffee => "En descanso de caf",
            TimeTrackingStatus.OnBreak when snapshot.ActiveBreakType == BreakType.Food => "En descanso de comida",
            TimeTrackingStatus.OnBreak => "En descanso",
            TimeTrackingStatus.OffDuty => "Fuera de jornada",
            _ => snapshot.Status.ToString()
        };

    private static string BuildStatusDetail(TimeTrackingSnapshot snapshot) =>
        snapshot.ActiveBreak switch
        {
            { IsActive: true, Type: BreakType.Coffee } => $"Caf activo  {FormatDuration(snapshot.ActiveBreak.GetDuration(DateTimeOffset.UtcNow))}",
            { IsActive: true, Type: BreakType.Food } => $"Comida activa  {FormatDuration(snapshot.ActiveBreak.GetDuration(DateTimeOffset.UtcNow))}",
            _ => $"Jornada actual  {FormatDuration(snapshot.Summary.CurrentShiftWorkedDuration)}"
        };

    private static string TranslateAction(TimeTrackingAction action) =>
        action switch
        {
            TimeTrackingAction.ClockIn => "Entrada",
            TimeTrackingAction.StartBreak => "Iniciar descanso",
            TimeTrackingAction.EndBreak => "Reanudar",
            TimeTrackingAction.StartCoffeeBreak => "Iniciar caf",
            TimeTrackingAction.EndCoffeeBreak => "Finalizar caf",
            TimeTrackingAction.StartFoodBreak => "Iniciar comida",
            TimeTrackingAction.EndFoodBreak => "Finalizar comida",
            TimeTrackingAction.ClockOut => "Salida",
            _ => "Sin acciones"
        };

    private static string BuildTimelineText(TimeTrackingSnapshot snapshot, CultureInfo culture)
    {
        if (snapshot.WorkdayEvents.Count == 0)
        {
            return "Todava no hay hitos registrados hoy.";
        }

        return string.Join("  ", snapshot.WorkdayEvents.Select(item =>
            $"{item.OccurredAtUtc.ToLocalTime().ToString("HH:mm", culture)} {TranslateEvent(item)}"));
    }

    private static string TranslateEvent(WorkdayEvent item) =>
        item.EventType switch
        {
            WorkdayEventType.ClockIn => "Entrada",
            WorkdayEventType.StartCoffeeBreak => "Caf",
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
}
