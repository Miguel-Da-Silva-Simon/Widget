using System.Collections.Concurrent;
using System.Runtime.InteropServices;
using Microsoft.Windows.Widgets;
using Microsoft.Windows.Widgets.Providers;
using Widget.SmokeTest.WidgetHost;
using Widget.SmokeTest.WidgetHost.Composition;
using Widget.SmokeTest.WidgetHost.Rendering;
using Widget.SmokeTest.WidgetHost.State;

namespace Widget.SmokeTest.WidgetHost.Providers;

[ComVisible(true)]
[Guid("2E2F6D0C-7B6E-4B4C-9C2D-6D8E7A2B1F44")]
public sealed class SmokeTestWidgetProvider : IWidgetProvider
{
    private sealed class WidgetInstance
    {
        public required string Id { get; init; }
        public required string DefinitionId { get; init; }
        public bool IsActive { get; set; }
    }

    private static readonly ConcurrentDictionary<string, WidgetInstance> RunningWidgets = new();
    private static readonly ManualResetEvent EmptyWidgetListEvent = new(false);

    public SmokeTestWidgetProvider()
    {
        AppServices.Initialize();
        EmptyWidgetListEvent.Reset();
    }

    public static WaitHandle GetEmptyWidgetListEvent() => EmptyWidgetListEvent;

    public void CreateWidget(WidgetContext widgetContext)
    {
        RunningWidgets[widgetContext.Id] = new WidgetInstance
        {
            Id = widgetContext.Id,
            DefinitionId = widgetContext.DefinitionId,
            IsActive = false
        };

        EmptyWidgetListEvent.Reset();
        RefreshWidget(widgetContext.Id);
    }

    public void DeleteWidget(string widgetId, string customState)
    {
        RunningWidgets.TryRemove(widgetId, out _);

        if (RunningWidgets.IsEmpty)
        {
            EmptyWidgetListEvent.Set();
        }
    }

    public void OnActionInvoked(WidgetActionInvokedArgs actionInvokedArgs)
    {
        var state = AppServices.ActionRouter
            .RouteAsync(actionInvokedArgs.Verb)
            .GetAwaiter()
            .GetResult();

        RefreshWidget(actionInvokedArgs.WidgetContext.Id, state);
    }

    public void OnWidgetContextChanged(WidgetContextChangedArgs contextChangedArgs)
    {
        RefreshWidget(contextChangedArgs.WidgetContext.Id);
    }

    public void Activate(WidgetContext widgetContext)
    {
        if (RunningWidgets.TryGetValue(widgetContext.Id, out var widget))
        {
            widget.IsActive = true;
            RefreshWidget(widgetContext.Id);
        }
    }

    public void Deactivate(string widgetId)
    {
        if (RunningWidgets.TryGetValue(widgetId, out var widget))
        {
            widget.IsActive = false;
        }
    }

    private static void RefreshWidget(string widgetId, SmokeTestState? state = null)
    {
        if (!RunningWidgets.TryGetValue(widgetId, out var widget))
        {
            return;
        }

        var currentState = state ?? AppServices.StateStore.GetStateAsync().GetAwaiter().GetResult();
        UpdateWidget(widget, currentState);
    }

    private static void UpdateWidget(WidgetInstance widget, SmokeTestState state)
    {
        if (!string.Equals(widget.DefinitionId, WidgetHostConstants.WidgetDefinitionId, StringComparison.Ordinal))
        {
            return;
        }

        var viewModel = SmokeTestWidgetViewModelMapper.Map(state);
        var update = new WidgetUpdateRequestOptions(widget.Id)
        {
            Template = SmokeTestAdaptiveCardBuilder.BuildTemplate(),
            Data = SmokeTestAdaptiveCardBuilder.BuildData(viewModel),
            CustomState = state.PingCount.ToString()
        };

        WidgetManager.GetDefault().UpdateWidget(update);
    }
}
