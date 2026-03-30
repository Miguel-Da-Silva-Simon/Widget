using System.Globalization;
using Widget.SmokeTest.WidgetHost.State;

namespace Widget.SmokeTest.WidgetHost.Rendering;

internal static class SmokeTestWidgetViewModelMapper
{
    public static SmokeTestWidgetViewModel Map(SmokeTestState state)
    {
        var culture = CultureInfo.CurrentCulture;

        return new SmokeTestWidgetViewModel(
            Title: "Smoke Test Widget",
            Status: "Listo para probar el host",
            PingCount: state.PingCount.ToString(culture),
            LastAction: state.LastAction,
            LastActionTime: state.LastActionAtUtc?.ToLocalTime().ToString("g", culture) ?? "—");
    }
}
