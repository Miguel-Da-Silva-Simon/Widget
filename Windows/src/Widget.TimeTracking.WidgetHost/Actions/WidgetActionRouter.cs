using System.ComponentModel;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using Widget.TimeTracking.Core.Results;
using Widget.TimeTracking.Core.Services;

namespace Widget.TimeTracking.WidgetHost.Actions;

internal sealed class WidgetActionRouter
{
    private const int ErrorInsufficientBuffer = 122;
    private const int AppModelErrorNoPackage = 15700;
    private const string CompanionAppApplicationId = "TimeTrackingApp";

    private readonly ITimeTrackingService _timeTrackingService;
    private readonly IUserSessionService _userSessionService;

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetCurrentPackageFamilyName(ref int packageFamilyNameLength, StringBuilder? packageFamilyName);

    public WidgetActionRouter(
        ITimeTrackingService timeTrackingService,
        IUserSessionService userSessionService)
    {
        _timeTrackingService = timeTrackingService;
        _userSessionService = userSessionService;
    }

    public async Task<TimeTrackingCommandResult> RouteAsync(
        string verb,
        CancellationToken cancellationToken = default)
    {
        return verb switch
        {
            WidgetHostConstants.OpenAppVerb => await OpenAppAsync(cancellationToken),
            WidgetHostConstants.ClockInVerb => await _timeTrackingService.ClockInAsync(cancellationToken),
            WidgetHostConstants.StartBreakVerb => await _timeTrackingService.StartBreakAsync(cancellationToken),
            WidgetHostConstants.EndBreakVerb => await _timeTrackingService.EndBreakAsync(cancellationToken),
            WidgetHostConstants.StartCoffeeBreakVerb => await _timeTrackingService.StartCoffeeBreakAsync(cancellationToken),
            WidgetHostConstants.EndCoffeeBreakVerb => await _timeTrackingService.EndCoffeeBreakAsync(cancellationToken),
            WidgetHostConstants.StartFoodBreakVerb => await _timeTrackingService.StartFoodBreakAsync(cancellationToken),
            WidgetHostConstants.EndFoodBreakVerb => await _timeTrackingService.EndFoodBreakAsync(cancellationToken),
            WidgetHostConstants.ClockOutVerb => await _timeTrackingService.ClockOutAsync(cancellationToken),
            _ => await BuildUnknownActionResultAsync(verb, cancellationToken)
        };
    }

    private async Task<TimeTrackingCommandResult> OpenAppAsync(CancellationToken cancellationToken)
    {
        var session = await _userSessionService.GetCurrentSessionAsync(cancellationToken);
        var snapshot = await _timeTrackingService.GetStateAsync(cancellationToken);

        try
        {
            var packageFamilyName = TryGetCurrentPackageFamilyName();
            if (string.IsNullOrWhiteSpace(packageFamilyName))
            {
                Debug.WriteLine("Open app requested from widget, but no package family name is available yet.");
                return TimeTrackingCommandResult.Success(snapshot);
            }

            var shellTarget = $"shell:AppsFolder\\{packageFamilyName}!{CompanionAppApplicationId}";
            Process.Start(new ProcessStartInfo("explorer.exe", shellTarget)
            {
                UseShellExecute = true
            });

            Debug.WriteLine($"Open app requested from widget. Authenticated: {session.IsAuthenticated}. Target: {shellTarget}");
            return TimeTrackingCommandResult.Success(snapshot);
        }
        catch (Exception exception) when (exception is Win32Exception or InvalidOperationException)
        {
            Debug.WriteLine($"Open app requested from widget, but launch failed: {exception.Message}");
            return TimeTrackingCommandResult.Success(snapshot);
        }
    }

    private static string? TryGetCurrentPackageFamilyName()
    {
        var length = 0;
        var result = GetCurrentPackageFamilyName(ref length, null);
        if (result == AppModelErrorNoPackage)
        {
            return null;
        }

        if (result != ErrorInsufficientBuffer || length <= 0)
        {
            return null;
        }

        var builder = new StringBuilder(length);
        result = GetCurrentPackageFamilyName(ref length, builder);
        if (result != 0)
        {
            return null;
        }

        return builder.ToString();
    }

    private async Task<TimeTrackingCommandResult> BuildUnknownActionResultAsync(
        string verb,
        CancellationToken cancellationToken)
    {
        var snapshot = await _timeTrackingService.GetStateAsync(cancellationToken);
        return TimeTrackingCommandResult.Failure(snapshot, $"El widget recibió una acción desconocida: {verb}.");
    }
}
