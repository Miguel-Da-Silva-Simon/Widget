using Widget.TimeTracking.Core.Models;
using Widget.TimeTracking.Core.Results;

namespace Widget.TimeTracking.Core.Services;

public interface ITimeTrackingService
{
    Task<TimeTrackingSnapshot> GetStateAsync(CancellationToken cancellationToken = default);

    Task<TimeTrackingCommandResult> ClockInAsync(CancellationToken cancellationToken = default);

    Task<TimeTrackingCommandResult> StartBreakAsync(CancellationToken cancellationToken = default);

    Task<TimeTrackingCommandResult> EndBreakAsync(CancellationToken cancellationToken = default);

    Task<TimeTrackingCommandResult> StartCoffeeBreakAsync(CancellationToken cancellationToken = default);

    Task<TimeTrackingCommandResult> EndCoffeeBreakAsync(CancellationToken cancellationToken = default);

    Task<TimeTrackingCommandResult> StartFoodBreakAsync(CancellationToken cancellationToken = default);

    Task<TimeTrackingCommandResult> EndFoodBreakAsync(CancellationToken cancellationToken = default);

    Task<TimeTrackingCommandResult> ClockOutAsync(CancellationToken cancellationToken = default);
}
