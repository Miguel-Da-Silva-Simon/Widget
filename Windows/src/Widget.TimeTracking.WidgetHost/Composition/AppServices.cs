using Widget.TimeTracking.Core.Services;
using Widget.TimeTracking.Infrastructure.Mock.Configuration;
using Widget.TimeTracking.Infrastructure.Mock.Persistence;
using Widget.TimeTracking.Infrastructure.Mock.Services;
using Widget.TimeTracking.WidgetHost.Actions;
using Widget.TimeTracking.WidgetHost.Rendering;

namespace Widget.TimeTracking.WidgetHost.Composition;

internal static class AppServices
{
    private static bool _isInitialized;

    public static ITimeTrackingService TimeTrackingService { get; private set; } = default!;

    public static IUserSessionService UserSessionService { get; private set; } = default!;

    public static IAuthenticationService AuthenticationService { get; private set; } = default!;

    public static TimeTrackingWidgetViewModelMapper ViewModelMapper { get; private set; } = default!;

    public static WidgetActionRouter ActionRouter { get; private set; } = default!;

    public static void Initialize()
    {
        if (_isInitialized)
        {
            return;
        }

        var options = new LocalStateOptions();
        var store = new JsonTimeTrackingStore(options);
        var timeTrackingService = new LocalJsonTimeTrackingService(store, options);
        var userSessionService = new LocalJsonUserSessionService(options);
        var authenticationService = new MockAuthenticationService(options);

        TimeTrackingService = timeTrackingService;
        UserSessionService = userSessionService;
        AuthenticationService = authenticationService;
        ViewModelMapper = new TimeTrackingWidgetViewModelMapper(userSessionService, timeTrackingService);
        ActionRouter = new WidgetActionRouter(timeTrackingService, userSessionService);
        _isInitialized = true;
    }
}
