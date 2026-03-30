using Widget.SmokeTest.WidgetHost.Actions;
using Widget.SmokeTest.WidgetHost.State;

namespace Widget.SmokeTest.WidgetHost.Composition;

internal static class AppServices
{
    private static bool _initialized;

    public static SmokeTestStateStore StateStore { get; private set; } = default!;

    public static SmokeTestActionRouter ActionRouter { get; private set; } = default!;

    public static void Initialize()
    {
        if (_initialized)
        {
            return;
        }

        StateStore = new SmokeTestStateStore();
        ActionRouter = new SmokeTestActionRouter(StateStore);
        _initialized = true;
    }
}
