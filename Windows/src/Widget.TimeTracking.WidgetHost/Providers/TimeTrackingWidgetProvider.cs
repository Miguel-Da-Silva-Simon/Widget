using System.Collections.Concurrent;
using System.Runtime.InteropServices;
using Microsoft.Windows.Widgets;
using Microsoft.Windows.Widgets.Providers;
using Widget.TimeTracking.Core.Models;
using Widget.TimeTracking.WidgetHost.Composition;
using Widget.TimeTracking.WidgetHost.Rendering;

namespace Widget.TimeTracking.WidgetHost.Providers;

[ComVisible(true)]
public sealed class TimeTrackingWidgetProvider : IWidgetProvider
{
    private sealed class WidgetInstance
    {
        public required string Id { get; init; }
        public required string DefinitionId { get; init; }
        public bool IsActive { get; set; }
    }

    private static readonly ConcurrentDictionary<string, WidgetInstance> RunningWidgets = new();
    private static readonly ManualResetEvent EmptyWidgetListEvent = new(false);

    public TimeTrackingWidgetProvider()
    {
        AppServices.Initialize();
    }

    public static ManualResetEvent GetEmptyWidgetListEvent() => EmptyWidgetListEvent;

    public void CreateWidget(WidgetContext widgetContext)
    {
        RunningWidgets[widgetContext.Id] = new WidgetInstance
        {
            Id = widgetContext.Id,
            DefinitionId = widgetContext.DefinitionId,
            IsActive = false
        };

        EmptyWidgetListEvent.Reset();
        UpdateWidget(widgetContext.Id);
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
        var result = AppServices.ActionRouter
            .RouteAsync(actionInvokedArgs.Verb)
            .GetAwaiter()
            .GetResult();

        UpdateWidget(actionInvokedArgs.WidgetContext.Id, result.Snapshot);
    }

    public void OnWidgetContextChanged(WidgetContextChangedArgs contextChangedArgs)
    {
        UpdateWidget(contextChangedArgs.WidgetContext.Id);
    }

    public void Activate(WidgetContext widgetContext)
    {
        if (RunningWidgets.TryGetValue(widgetContext.Id, out var widget))
        {
            widget.IsActive = true;
            UpdateWidget(widgetContext.Id);
        }
    }

    public void Deactivate(string widgetId)
    {
        if (RunningWidgets.TryGetValue(widgetId, out var widget))
        {
            widget.IsActive = false;
        }
    }

    private static void UpdateWidget(string widgetId)
    {
        UpdateWidget(widgetId, null);
    }

    private static void UpdateWidget(string widgetId, TimeTrackingSnapshot? snapshot)
    {
        if (!RunningWidgets.TryGetValue(widgetId, out var widget))
        {
            return;
        }

        if (!string.Equals(widget.DefinitionId, WidgetHostConstants.WidgetDefinitionId, StringComparison.Ordinal))
        {
            return;
        }

        var viewModel = AppServices.ViewModelMapper
            .MapAsync(snapshot)
            .GetAwaiter()
            .GetResult();

        var update = new WidgetUpdateRequestOptions(widget.Id)
        {
            Template = AdaptiveCardTemplateBuilder.BuildTemplate(),
            Data = AdaptiveCardTemplateBuilder.BuildData(viewModel),
            CustomState = viewModel.CustomState
        };

        WidgetManager.GetDefault().UpdateWidget(update);
    }
}
