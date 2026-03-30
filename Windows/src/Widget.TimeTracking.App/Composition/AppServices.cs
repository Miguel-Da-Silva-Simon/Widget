using Widget.TimeTracking.App.Services;
using Widget.TimeTracking.Core.Services;
using Widget.TimeTracking.Infrastructure.Mock.Configuration;
using Widget.TimeTracking.Infrastructure.Mock.Persistence;
using Widget.TimeTracking.Infrastructure.Mock.Services;

namespace Widget.TimeTracking.App.Composition;

internal static class AppServices
{
    private static bool _isInitialized;

    public static LocalStateOptions LocalStateOptions { get; private set; } = default!;
    public static ITimeTrackingService TimeTrackingService { get; private set; } = default!;
    public static IUserSessionService UserSessionService { get; private set; } = default!;
    public static IAuthenticationService AuthenticationService { get; private set; } = default!;
    public static WidgetRefreshService WidgetRefreshService { get; private set; } = default!;

    public static void Initialize()
    {
        if (_isInitialized)
        {
            return;
        }

        var options = new LocalStateOptions();
        var store = new JsonTimeTrackingStore(options);

        LocalStateOptions = options;
        TimeTrackingService = new LocalJsonTimeTrackingService(store, options);
        UserSessionService = new LocalJsonUserSessionService(options);
        AuthenticationService = new MockAuthenticationService(options);
        WidgetRefreshService = new WidgetRefreshService(UserSessionService, TimeTrackingService);
        _isInitialized = true;
    }
}
