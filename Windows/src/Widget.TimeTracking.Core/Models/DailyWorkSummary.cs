namespace Widget.TimeTracking.Core.Models;

public sealed record DailyWorkSummary(
    DateOnly Day,
    TimeSpan CurrentShiftWorkedDuration,
    TimeSpan LastCompletedShiftWorkedDuration,
    TimeSpan WorkedThisMonthDuration,
    TimeSpan CoffeeBreakDurationToday,
    TimeSpan FoodBreakDurationToday)
{
    public static DailyWorkSummary Empty(DateOnly day) =>
        new(
            day,
            TimeSpan.Zero,
            TimeSpan.Zero,
            TimeSpan.Zero,
            TimeSpan.Zero,
            TimeSpan.Zero);
}
