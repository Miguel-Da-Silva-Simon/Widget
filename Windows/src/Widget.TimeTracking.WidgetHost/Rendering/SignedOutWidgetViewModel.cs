namespace Widget.TimeTracking.WidgetHost.Rendering;

internal sealed record SignedOutWidgetViewModel(
    string Title,
    string CustomState,
    string SurfaceColorHex,
    string AccentColorHex,
    string Message,
    string PrimaryActionLabel)
    : TimeTrackingWidgetViewModel(Title, CustomState, SurfaceColorHex, AccentColorHex);
