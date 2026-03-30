namespace Widget.SmokeTest.WidgetHost.State;

internal sealed record SmokeTestState(
    int PingCount,
    string LastAction,
    DateTimeOffset? LastActionAtUtc)
{
    public static SmokeTestState Initial =>
        new(0, "Sin acciones", null);
}
