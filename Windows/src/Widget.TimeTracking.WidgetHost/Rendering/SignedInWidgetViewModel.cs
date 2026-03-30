using Widget.TimeTracking.Core.Enums;

namespace Widget.TimeTracking.WidgetHost.Rendering;

internal sealed record SignedInWidgetViewModel(
    string Title,
    string CustomState,
    string SurfaceColorHex,
    string AccentColorHex,
    string DisplayName,
    string StatusHeadline,
    string StatusDetail,
    string SessionCounter,
    string LastAction,
    string LastActionTime,
    string CurrentShiftDuration,
    string LastCompletedShiftDuration,
    string WorkedThisMonthDuration,
    string CoffeeTodayDuration,
    string FoodTodayDuration,
    string TimelineText,
    BreakType ActiveBreakType,
    bool CanClockIn,
    bool CanStartCoffeeBreak,
    bool CanEndCoffeeBreak,
    bool CanStartFoodBreak,
    bool CanEndFoodBreak,
    bool CanClockOut)
    : TimeTrackingWidgetViewModel(Title, CustomState, SurfaceColorHex, AccentColorHex);
