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
                Message = "Inicia sesion en la app para fichar con tu cuenta.",
                PrimaryActionLabel = "Abrir app",
                DisplayName = string.Empty,
                StatusHeadline = string.Empty,
                StatusDetail = string.Empty,
                SessionCounter = "00:00:00",
                LastAction = string.Empty,
                LastActionTime = string.Empty,
                LastCompletedShiftDuration = string.Empty,
                WorkedThisMonthDuration = string.Empty,
                CoffeeTodayDuration = string.Empty,
                FoodTodayDuration = string.Empty,
                TimelineText = string.Empty,
                CoffeeVerb = "start-coffee-break",
                FoodVerb = "start-food-break",
                ShowEntryButton = false,
                ShowClockOutButton = false,
                ShowClockOutDisabled = false,
                ShowCoffeeActive = false,
                ShowCoffeeEndBreak = false,
                ShowCoffeeDisabled = false,
                ShowFoodActive = false,
                ShowFoodEndBreak = false,
                ShowFoodDisabled = false
            }, SerializerOptions);
        }

        var availableActions = snapshot.AvailableActions.ToHashSet();
        var culture = CultureInfo.CurrentCulture;
        var activeBreakType = snapshot.ActiveBreakType;
        var canClockIn = availableActions.Contains(TimeTrackingAction.ClockIn);
        var canStartCoffeeBreak =
            availableActions.Contains(TimeTrackingAction.StartCoffeeBreak)
            || availableActions.Contains(TimeTrackingAction.StartBreak);
        var canEndCoffeeBreak =
            availableActions.Contains(TimeTrackingAction.EndCoffeeBreak)
            || (activeBreakType == BreakType.Coffee && availableActions.Contains(TimeTrackingAction.EndBreak));
        var canStartFoodBreak = availableActions.Contains(TimeTrackingAction.StartFoodBreak);
        var canEndFoodBreak =
            availableActions.Contains(TimeTrackingAction.EndFoodBreak)
            || (activeBreakType == BreakType.Food && availableActions.Contains(TimeTrackingAction.EndBreak));
        var canClockOut = availableActions.Contains(TimeTrackingAction.ClockOut);
        var sessionCounter = activeBreakType is BreakType.Coffee or BreakType.Food
            ? FormatDurationWithSeconds(snapshot.ActiveBreak?.GetDuration(DateTimeOffset.UtcNow) ?? TimeSpan.Zero)
            : FormatDurationWithSeconds(snapshot.Summary.CurrentShiftWorkedDuration);

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
            SessionCounter = sessionCounter,
            LastAction = snapshot.LastAction == TimeTrackingAction.None
                ? "Sin acciones todavia"
                : TranslateAction(snapshot.LastAction),
            LastActionTime = snapshot.LastActionAtUtc.HasValue
                ? snapshot.LastActionAtUtc.Value.ToLocalTime().ToString("g", culture)
                : "--",
            LastCompletedShiftDuration = FormatDuration(snapshot.Summary.LastCompletedShiftWorkedDuration),
            WorkedThisMonthDuration = FormatDuration(snapshot.Summary.WorkedThisMonthDuration),
            CoffeeTodayDuration = FormatDuration(snapshot.Summary.CoffeeBreakDurationToday),
            FoodTodayDuration = FormatDuration(snapshot.Summary.FoodBreakDurationToday),
            TimelineText = BuildTimelineText(snapshot, culture),
            CoffeeVerb = activeBreakType == BreakType.Coffee
                ? "end-coffee-break"
                : "start-coffee-break",
            FoodVerb = activeBreakType == BreakType.Food
                ? "end-food-break"
                : "start-food-break",
            ShowEntryButton = canClockIn,
            ShowClockOutButton = activeBreakType == BreakType.None && canClockOut,
            ShowClockOutDisabled = activeBreakType != BreakType.None,
            ShowCoffeeActive = activeBreakType == BreakType.None && canStartCoffeeBreak,
            ShowCoffeeEndBreak = activeBreakType == BreakType.Coffee && canEndCoffeeBreak,
            ShowCoffeeDisabled = activeBreakType == BreakType.Food
                || (activeBreakType == BreakType.None && !canStartCoffeeBreak)
                || (activeBreakType == BreakType.Coffee && !canEndCoffeeBreak),
            ShowFoodActive = activeBreakType == BreakType.None && canStartFoodBreak,
            ShowFoodEndBreak = activeBreakType == BreakType.Food && canEndFoodBreak,
            ShowFoodDisabled = activeBreakType == BreakType.Coffee
                || (activeBreakType == BreakType.None && !canStartFoodBreak)
                || (activeBreakType == BreakType.Food && !canEndFoodBreak)
        }, SerializerOptions);
    }

    private static string BuildStatusHeadline(TimeTrackingSnapshot snapshot) =>
        snapshot.Status switch
        {
            TimeTrackingStatus.NotClockedIn => "Sin fichar",
            TimeTrackingStatus.Working => "Estas trabajando",
            TimeTrackingStatus.OnBreak when snapshot.ActiveBreakType == BreakType.Coffee => "En descanso de cafe",
            TimeTrackingStatus.OnBreak when snapshot.ActiveBreakType == BreakType.Food => "En descanso de comida",
            TimeTrackingStatus.OnBreak => "En descanso",
            TimeTrackingStatus.OffDuty => "Jornada finalizada",
            _ => snapshot.Status.ToString()
        };

    private static string BuildStatusDetail(TimeTrackingSnapshot snapshot) =>
        snapshot.ActiveBreak switch
        {
            { IsActive: true, Type: BreakType.Coffee } => $"Cafe activo - {FormatDuration(snapshot.ActiveBreak.GetDuration(DateTimeOffset.UtcNow))}",
            { IsActive: true, Type: BreakType.Food } => $"Comida activa - {FormatDuration(snapshot.ActiveBreak.GetDuration(DateTimeOffset.UtcNow))}",
            _ => $"Jornada actual - {FormatDuration(snapshot.Summary.CurrentShiftWorkedDuration)}"
        };

    private static string TranslateAction(TimeTrackingAction action) =>
        action switch
        {
            TimeTrackingAction.ClockIn => "Entrada",
            TimeTrackingAction.StartBreak => "Iniciar cafe",
            TimeTrackingAction.EndBreak => "Finalizar descanso",
            TimeTrackingAction.StartCoffeeBreak => "Iniciar cafe",
            TimeTrackingAction.EndCoffeeBreak => "Finalizar cafe",
            TimeTrackingAction.StartFoodBreak => "Iniciar comida",
            TimeTrackingAction.EndFoodBreak => "Finalizar comida",
            TimeTrackingAction.ClockOut => "Salida",
            _ => "Sin acciones"
        };

    private static string BuildTimelineText(TimeTrackingSnapshot snapshot, CultureInfo culture)
    {
        if (snapshot.WorkdayEvents.Count == 0)
        {
            return "Todavia no hay hitos registrados hoy.";
        }

        return string.Join(" | ", snapshot.WorkdayEvents.Select(item =>
            $"{item.OccurredAtUtc.ToLocalTime().ToString("HH:mm", culture)} {TranslateEvent(item)}"));
    }

    private static string TranslateEvent(WorkdayEvent item) =>
        item.EventType switch
        {
            WorkdayEventType.ClockIn => "Entrada",
            WorkdayEventType.StartCoffeeBreak => "Cafe",
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
