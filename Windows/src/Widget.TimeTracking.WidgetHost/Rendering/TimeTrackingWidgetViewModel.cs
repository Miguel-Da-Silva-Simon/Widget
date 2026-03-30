using Widget.TimeTracking.Core.Design;

namespace Widget.TimeTracking.WidgetHost.Rendering;

internal abstract record TimeTrackingWidgetViewModel(
    string Title,
    string CustomState,
    string SurfaceColorHex,
    string AccentColorHex)
{
    public bool IsSignedOut => this is SignedOutWidgetViewModel;
    public bool IsSignedIn => this is SignedInWidgetViewModel;

    public static TimeTrackingWidgetViewModel SignedOut(string title, string message, string primaryActionLabel) =>
        new SignedOutWidgetViewModel(
            Title: title,
            CustomState: "SignedOut",
            SurfaceColorHex: BrandColors.White,
            AccentColorHex: BrandColors.PrimaryBlue,
            Message: message,
            PrimaryActionLabel: primaryActionLabel);
}
