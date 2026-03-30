using Widget.TimeTracking.Core.Enums;

namespace Widget.TimeTracking.Core.Models;

public readonly record struct TimeTrackingTransition(
    TimeTrackingStatus From,
    TimeTrackingAction Action,
    TimeTrackingStatus To);
