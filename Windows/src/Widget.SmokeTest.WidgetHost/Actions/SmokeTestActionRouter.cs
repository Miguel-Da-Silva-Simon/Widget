using Widget.SmokeTest.WidgetHost;
using Widget.SmokeTest.WidgetHost.State;

namespace Widget.SmokeTest.WidgetHost.Actions;

internal sealed class SmokeTestActionRouter
{
    private readonly SmokeTestStateStore _stateStore;

    public SmokeTestActionRouter(SmokeTestStateStore stateStore)
    {
        _stateStore = stateStore;
    }

    public async Task<SmokeTestState> RouteAsync(string verb, CancellationToken cancellationToken = default)
    {
        if (!string.Equals(verb, WidgetHostConstants.PingVerb, StringComparison.Ordinal))
        {
            return await _stateStore.GetStateAsync(cancellationToken);
        }

        var state = await _stateStore.GetStateAsync(cancellationToken);
        state = state with
        {
            PingCount = state.PingCount + 1,
            LastAction = "Ping",
            LastActionAtUtc = DateTimeOffset.UtcNow
        };

        await _stateStore.SaveAsync(state, cancellationToken);
        return state;
    }
}
