using Widget.TimeTracking.Core.Models;

namespace Widget.TimeTracking.Core.Results;

public sealed record TimeTrackingCommandResult(
    bool IsSuccess,
    string? ErrorMessage,
    TimeTrackingSnapshot Snapshot)
{
    public static TimeTrackingCommandResult Success(TimeTrackingSnapshot snapshot) =>
        new(true, null, snapshot);

    public static TimeTrackingCommandResult Failure(TimeTrackingSnapshot snapshot, string errorMessage) =>
        new(false, errorMessage, snapshot);
}
