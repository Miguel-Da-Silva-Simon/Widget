using System.Globalization;
using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.Graphics;
using WinRT.Interop;
using Widget.TimeTracking.App.Composition;
using Widget.TimeTracking.Core.Enums;
using Widget.TimeTracking.Core.Models;
using Widget.TimeTracking.Core.Results;

namespace Widget.TimeTracking.App;

public sealed partial class MainWindow : Window
{
    private static readonly SizeInt32 InitialWindowSize = new(960, 820);
    private static readonly TimeSpan DefaultReferenceTime = new(9, 0, 0);

    public MainWindow()
    {
        InitializeComponent();
        ConfigureWindow();
        ReferenceTimePicker.Time = DefaultReferenceTime;
        UpdateReferenceTimeView(ReferenceTimePicker.Time);
        SessionFilePathTextBlock.Text = $"Sesi�n: {AppServices.LocalStateOptions.SessionFullPath}";
        TimeTrackingFilePathTextBlock.Text = $"Fichaje: {AppServices.LocalStateOptions.FullPath}";
        _ = RefreshAsync();
    }

    private void ConfigureWindow()
    {
        var windowHandle = WindowNative.GetWindowHandle(this);
        var windowId = Win32Interop.GetWindowIdFromWindow(windowHandle);
        var appWindow = AppWindow.GetFromWindowId(windowId);
        appWindow.Resize(InitialWindowSize);
    }

    private async void SignInButton_Click(object sender, RoutedEventArgs e)
    {
        await AppServices.AuthenticationService.SignInAsync();
        await AppServices.WidgetRefreshService.RefreshAsync();
        await RefreshAsync();
    }

    private async void SignOutButton_Click(object sender, RoutedEventArgs e)
    {
        await AppServices.AuthenticationService.SignOutAsync();
        await AppServices.WidgetRefreshService.RefreshAsync();
        await RefreshAsync();
    }

    private async void RefreshButton_Click(object sender, RoutedEventArgs e)
    {
        await AppServices.WidgetRefreshService.RefreshAsync();
        await RefreshAsync();
    }

    private async void ClockInButton_Click(object sender, RoutedEventArgs e) =>
        await ExecuteCommandAsync(() => AppServices.TimeTrackingService.ClockInAsync());

    private async void StartCoffeeBreakButton_Click(object sender, RoutedEventArgs e) =>
        await ExecuteCommandAsync(() => AppServices.TimeTrackingService.StartCoffeeBreakAsync());

    private async void EndCoffeeBreakButton_Click(object sender, RoutedEventArgs e) =>
        await ExecuteCommandAsync(() => AppServices.TimeTrackingService.EndCoffeeBreakAsync());

    private async void StartFoodBreakButton_Click(object sender, RoutedEventArgs e) =>
        await ExecuteCommandAsync(() => AppServices.TimeTrackingService.StartFoodBreakAsync());

    private async void EndFoodBreakButton_Click(object sender, RoutedEventArgs e) =>
        await ExecuteCommandAsync(() => AppServices.TimeTrackingService.EndFoodBreakAsync());

    private async void ClockOutButton_Click(object sender, RoutedEventArgs e) =>
        await ExecuteCommandAsync(() => AppServices.TimeTrackingService.ClockOutAsync());

    private void ReferenceTimePicker_TimeChanged(object sender, TimePickerValueChangedEventArgs e) =>
        UpdateReferenceTimeView(e.NewTime);

    private async Task ExecuteCommandAsync(Func<Task<TimeTrackingCommandResult>> action)
    {
        var result = await action();
        await AppServices.WidgetRefreshService.RefreshAsync();
        await RefreshAsync(result.Snapshot);
    }

    private async Task RefreshAsync(TimeTrackingSnapshot? snapshot = null)
    {
        var session = await AppServices.UserSessionService.GetCurrentSessionAsync();
        var resolvedSnapshot = snapshot ?? await AppServices.TimeTrackingService.GetStateAsync();

        UpdateSessionView(session);
        UpdateTimeTrackingView(session, resolvedSnapshot);
    }

    private void UpdateSessionView(UserSession session)
    {
        var culture = CultureInfo.CurrentCulture;
        var stateLabel = session.State switch
        {
            AuthenticationState.SignedIn => "Autenticado",
            AuthenticationState.SignedOut => "Sin sesión",
            _ => "Desconocido"
        };

        AuthenticationStateTextBlock.Text = $"Estado: {stateLabel}";
        CurrentUserTextBlock.Text = session.IsAuthenticated
            ? $"Usuario: {session.User!.DisplayName}"
            : "Usuario: —";
        SignedInAtTextBlock.Text = session.SignedInAtUtc.HasValue
            ? $"Sesión iniciada: {session.SignedInAtUtc.Value.ToLocalTime().ToString("g", culture)}"
            : "Sesión iniciada: —";

        SignInButton.IsEnabled = !session.IsAuthenticated;
        SignOutButton.IsEnabled = session.IsAuthenticated;
    }

    private void UpdateTimeTrackingView(UserSession session, TimeTrackingSnapshot snapshot)
    {
        var culture = CultureInfo.CurrentCulture;
        var isAuthenticated = session.IsAuthenticated;
        var availableActions = snapshot.AvailableActions.ToHashSet();

        StatusHeadlineTextBlock.Text = BuildStatusHeadline(snapshot);
        StatusDetailTextBlock.Text = BuildStatusDetail(snapshot);
        LastActionTextBlock.Text = snapshot.LastAction == TimeTrackingAction.None
            ? "�ltima acci�n: Sin acciones todav�a"
            : $"�ltima acci�n: {TranslateAction(snapshot.LastAction)}";
        LastActionTimeTextBlock.Text = snapshot.LastActionAtUtc.HasValue
            ? $"Hora: {snapshot.LastActionAtUtc.Value.ToLocalTime().ToString("g", culture)}"
            : "Hora: �";

        LastCompletedShiftDurationTextBlock.Text = FormatDuration(snapshot.Summary.LastCompletedShiftWorkedDuration);
        WorkedThisMonthDurationTextBlock.Text = FormatDuration(snapshot.Summary.WorkedThisMonthDuration);
        CoffeeTodayDurationTextBlock.Text = FormatDuration(snapshot.Summary.CoffeeBreakDurationToday);
        FoodTodayDurationTextBlock.Text = FormatDuration(snapshot.Summary.FoodBreakDurationToday);

        ClockInButton.IsEnabled = isAuthenticated && availableActions.Contains(TimeTrackingAction.ClockIn);
        StartCoffeeBreakButton.IsEnabled = isAuthenticated && (availableActions.Contains(TimeTrackingAction.StartCoffeeBreak) || availableActions.Contains(TimeTrackingAction.StartBreak));
        EndCoffeeBreakButton.IsEnabled = isAuthenticated && (availableActions.Contains(TimeTrackingAction.EndCoffeeBreak) || (snapshot.ActiveBreakType == BreakType.Coffee && availableActions.Contains(TimeTrackingAction.EndBreak)));
        StartFoodBreakButton.IsEnabled = isAuthenticated && availableActions.Contains(TimeTrackingAction.StartFoodBreak);
        EndFoodBreakButton.IsEnabled = isAuthenticated && availableActions.Contains(TimeTrackingAction.EndFoodBreak);
        ClockOutButton.IsEnabled = isAuthenticated && availableActions.Contains(TimeTrackingAction.ClockOut);

        BuildTimeline(snapshot, culture);
    }

    private void BuildTimeline(TimeTrackingSnapshot snapshot, CultureInfo culture)
    {
        TimelinePanel.Children.Clear();

        if (snapshot.WorkdayEvents.Count == 0)
        {
            TimelinePanel.Children.Add(new TextBlock
            {
                Text = "Todav�a no hay hitos registrados hoy.",
                Foreground = new SolidColorBrush(ColorHelper.FromArgb(255, 92, 92, 92)),
                TextWrapping = TextWrapping.WrapWholeWords
            });
            return;
        }

        foreach (var workdayEvent in snapshot.WorkdayEvents)
        {
            var chip = new Border
            {
                Background = new SolidColorBrush(ColorHelper.FromArgb(255, 255, 255, 255)),
                BorderBrush = new SolidColorBrush(ColorHelper.FromArgb(255, 215, 227, 251)),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(12),
                Padding = new Thickness(14, 10, 14, 10)
            };

            var stack = new StackPanel
            {
                Spacing = 4
            };

            stack.Children.Add(new TextBlock
            {
                Text = workdayEvent.OccurredAtUtc.ToLocalTime().ToString("HH:mm", culture),
                FontWeight = Microsoft.UI.Text.FontWeights.SemiBold,
                Foreground = new SolidColorBrush(ColorHelper.FromArgb(255, 95, 150, 249))
            });

            stack.Children.Add(new TextBlock
            {
                Text = TranslateEvent(workdayEvent),
                Foreground = new SolidColorBrush(ColorHelper.FromArgb(255, 31, 31, 31))
            });

            chip.Child = stack;
            TimelinePanel.Children.Add(chip);
        }
    }

    private void UpdateReferenceTimeView(TimeSpan referenceTime)
    {
        var culture = CultureInfo.CurrentCulture;
        var formattedTime = DateTime.Today.Add(referenceTime).ToString("t", culture);
        ReferenceTimeTextBlock.Text = $"Hora elegida: {formattedTime}";
    }

    private static string BuildStatusHeadline(TimeTrackingSnapshot snapshot) =>
        snapshot.Status switch
        {
            TimeTrackingStatus.NotClockedIn => "Sin fichar",
            TimeTrackingStatus.Working => "�Est�s trabajando!",
            TimeTrackingStatus.OnBreak when snapshot.ActiveBreakType == BreakType.Coffee => "En descanso de caf�",
            TimeTrackingStatus.OnBreak when snapshot.ActiveBreakType == BreakType.Food => "En descanso de comida",
            TimeTrackingStatus.OnBreak => "En descanso",
            TimeTrackingStatus.OffDuty => "Fuera de jornada",
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
            TimeTrackingAction.StartBreak => "Iniciar descanso",
            TimeTrackingAction.EndBreak => "Reanudar",
            TimeTrackingAction.StartCoffeeBreak => "Iniciar caf�",
            TimeTrackingAction.EndCoffeeBreak => "Finalizar caf�",
            TimeTrackingAction.StartFoodBreak => "Iniciar comida",
            TimeTrackingAction.EndFoodBreak => "Finalizar comida",
            TimeTrackingAction.ClockOut => "Salida",
            _ => "Sin acciones"
        };

    private static string TranslateEvent(WorkdayEvent item) =>
        item.EventType switch
        {
            WorkdayEventType.ClockIn => "Entrada",
            WorkdayEventType.StartCoffeeBreak => "Caf�",
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

