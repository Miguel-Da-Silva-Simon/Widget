namespace Widget.TimeTracking.WidgetHost;

internal static class WidgetHostConstants
{
    public const string WidgetDefinitionId = "TimeTracking_Widget";

    public const string OpenAppVerb = "open-app";
    public const string ClockInVerb = "clock-in";
    public const string StartBreakVerb = "start-break";
    public const string EndBreakVerb = "end-break";
    public const string StartCoffeeBreakVerb = "start-coffee-break";
    public const string EndCoffeeBreakVerb = "end-coffee-break";
    public const string StartFoodBreakVerb = "start-food-break";
    public const string EndFoodBreakVerb = "end-food-break";
    public const string ClockOutVerb = "clock-out";

    public static readonly Guid WidgetProviderClsid = new("5B1890A1-3B2A-4A8A-95B6-7E77B18A55B8");
}
