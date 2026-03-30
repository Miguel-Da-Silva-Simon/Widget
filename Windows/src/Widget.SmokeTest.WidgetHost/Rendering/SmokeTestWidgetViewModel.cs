namespace Widget.SmokeTest.WidgetHost.Rendering;

internal sealed record SmokeTestWidgetViewModel(
    string Title,
    string Status,
    string PingCount,
    string LastAction,
    string LastActionTime);
