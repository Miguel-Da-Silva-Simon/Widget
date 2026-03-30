using Widget.TimeTracking.Core.Enums;
using Widget.TimeTracking.Core.Models;

namespace Widget.TimeTracking.Core.Rules;

public static class TimeTrackingStateMachine
{
    private static readonly IReadOnlyList<TimeTrackingTransition> AllowedTransitions =
    [
        new(TimeTrackingStatus.NotClockedIn, TimeTrackingAction.ClockIn, TimeTrackingStatus.Working),
        new(TimeTrackingStatus.OffDuty, TimeTrackingAction.ClockIn, TimeTrackingStatus.Working),
        new(TimeTrackingStatus.Working, TimeTrackingAction.StartBreak, TimeTrackingStatus.OnBreak),
        new(TimeTrackingStatus.Working, TimeTrackingAction.StartCoffeeBreak, TimeTrackingStatus.OnBreak),
        new(TimeTrackingStatus.Working, TimeTrackingAction.StartFoodBreak, TimeTrackingStatus.OnBreak),
        new(TimeTrackingStatus.OnBreak, TimeTrackingAction.EndBreak, TimeTrackingStatus.Working),
        new(TimeTrackingStatus.OnBreak, TimeTrackingAction.EndCoffeeBreak, TimeTrackingStatus.Working),
        new(TimeTrackingStatus.OnBreak, TimeTrackingAction.EndFoodBreak, TimeTrackingStatus.Working),
        new(TimeTrackingStatus.Working, TimeTrackingAction.ClockOut, TimeTrackingStatus.OffDuty),
        new(TimeTrackingStatus.OnBreak, TimeTrackingAction.ClockOut, TimeTrackingStatus.OffDuty)
    ];

    private static readonly IReadOnlyDictionary<(TimeTrackingStatus Status, TimeTrackingAction Action), TimeTrackingStatus> Lookup =
        AllowedTransitions.ToDictionary(
            transition => (transition.From, transition.Action),
            transition => transition.To);

    private static readonly TimeTrackingAction[] OrderedActions =
    [
        TimeTrackingAction.ClockIn,
        TimeTrackingAction.StartCoffeeBreak,
        TimeTrackingAction.StartFoodBreak,
        TimeTrackingAction.EndCoffeeBreak,
        TimeTrackingAction.EndFoodBreak,
        TimeTrackingAction.ClockOut
    ];

    public static bool TryTransition(
        TimeTrackingStatus currentStatus,
        BreakType activeBreakType,
        TimeTrackingAction action,
        out TimeTrackingStatus nextStatus,
        out string? errorMessage)
    {
        if (Lookup.TryGetValue((currentStatus, action), out nextStatus))
        {
            if (!IsBreakTypeCompatible(activeBreakType, action, out errorMessage))
            {
                nextStatus = currentStatus;
                return false;
            }

            errorMessage = null;
            return true;
        }

        nextStatus = currentStatus;
        errorMessage = BuildInvalidTransitionMessage(currentStatus, action);
        return false;
    }

    public static IReadOnlyList<TimeTrackingAction> GetAvailableActions(TimeTrackingStatus status, BreakType activeBreakType) =>
        OrderedActions
            .Where(action => Lookup.ContainsKey((status, action)))
            .Where(action => IsBreakTypeCompatible(activeBreakType, action, out _))
            .ToArray();

    private static bool IsBreakTypeCompatible(
        BreakType activeBreakType,
        TimeTrackingAction action,
        out string? errorMessage)
    {
        switch (action)
        {
            case TimeTrackingAction.StartBreak:
            case TimeTrackingAction.StartCoffeeBreak:
            case TimeTrackingAction.StartFoodBreak:
                if (activeBreakType is not BreakType.None)
                {
                    errorMessage = $"Ya hay un descanso activo de tipo {activeBreakType}.";
                    return false;
                }

                errorMessage = null;
                return true;

            case TimeTrackingAction.EndBreak:
                if (activeBreakType is BreakType.None)
                {
                    errorMessage = "No hay un descanso activo para finalizar.";
                    return false;
                }

                errorMessage = null;
                return true;

            case TimeTrackingAction.EndCoffeeBreak:
                if (activeBreakType is BreakType.Coffee)
                {
                    errorMessage = null;
                    return true;
                }

                errorMessage = activeBreakType is BreakType.None
                    ? "No hay un descanso de café activo para finalizar."
                    : $"El descanso activo actual es {activeBreakType}, no café.";
                return false;

            case TimeTrackingAction.EndFoodBreak:
                if (activeBreakType is BreakType.Food)
                {
                    errorMessage = null;
                    return true;
                }

                errorMessage = activeBreakType is BreakType.None
                    ? "No hay un descanso de comida activo para finalizar."
                    : $"El descanso activo actual es {activeBreakType}, no comida.";
                return false;

            default:
                errorMessage = null;
                return true;
        }
    }

    private static string BuildInvalidTransitionMessage(TimeTrackingStatus status, TimeTrackingAction action) =>
        action switch
        {
            TimeTrackingAction.ClockIn => $"No podés entrar cuando el estado actual es {status}.",
            TimeTrackingAction.StartBreak => $"No podés iniciar descanso cuando el estado actual es {status}.",
            TimeTrackingAction.StartCoffeeBreak => $"No podés iniciar un descanso de café cuando el estado actual es {status}.",
            TimeTrackingAction.StartFoodBreak => $"No podés iniciar un descanso de comida cuando el estado actual es {status}.",
            TimeTrackingAction.EndBreak => $"No podés finalizar descanso cuando el estado actual es {status}.",
            TimeTrackingAction.EndCoffeeBreak => $"No podés finalizar un descanso de café cuando el estado actual es {status}.",
            TimeTrackingAction.EndFoodBreak => $"No podés finalizar un descanso de comida cuando el estado actual es {status}.",
            TimeTrackingAction.ClockOut => $"No podés salir cuando el estado actual es {status}.",
            _ => $"La acción {action} no es válida para el estado {status}."
        };
}
