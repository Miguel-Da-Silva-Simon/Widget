import AppIntents
import SwiftUI
import WidgetSharedKit

// MARK: - AppIntents defined in the extension target for metadata discovery

struct WClockInIntent: AppIntent {
    static var title: LocalizedStringResource = "Fichar entrada"
    static var description = IntentDescription("Inicia la jornada laboral")
    static var openAppWhenRun: Bool { false }

    func perform() async throws -> some IntentResult {
        NSLog("[FichajeWidget] WClockInIntent.perform()")
        await WidgetIntentActions.performAction(.clockIn)
        return .result()
    }
}

struct WClockOutIntent: AppIntent {
    static var title: LocalizedStringResource = "Fichar salida"
    static var description = IntentDescription("Finaliza la jornada laboral")
    static var openAppWhenRun: Bool { false }

    func perform() async throws -> some IntentResult {
        NSLog("[FichajeWidget] WClockOutIntent.perform()")
        await WidgetIntentActions.performAction(.clockOut)
        return .result()
    }
}

struct WStartCoffeeBreakIntent: AppIntent {
    static var title: LocalizedStringResource = "Iniciar descanso"
    static var description = IntentDescription("Inicia el descanso de café")
    static var openAppWhenRun: Bool { false }

    func perform() async throws -> some IntentResult {
        NSLog("[FichajeWidget] WStartCoffeeBreakIntent.perform()")
        await WidgetIntentActions.performAction(.startCoffeeBreak)
        return .result()
    }
}

struct WEndCoffeeBreakIntent: AppIntent {
    static var title: LocalizedStringResource = "Finalizar descanso"
    static var description = IntentDescription("Finaliza el descanso de café")
    static var openAppWhenRun: Bool { false }

    func perform() async throws -> some IntentResult {
        NSLog("[FichajeWidget] WEndCoffeeBreakIntent.perform()")
        await WidgetIntentActions.performAction(.endCoffeeBreak)
        return .result()
    }
}

struct WStartFoodBreakIntent: AppIntent {
    static var title: LocalizedStringResource = "Iniciar comida"
    static var description = IntentDescription("Inicia el descanso de comida")
    static var openAppWhenRun: Bool { false }

    func perform() async throws -> some IntentResult {
        NSLog("[FichajeWidget] WStartFoodBreakIntent.perform()")
        await WidgetIntentActions.performAction(.startFoodBreak)
        return .result()
    }
}

struct WEndFoodBreakIntent: AppIntent {
    static var title: LocalizedStringResource = "Finalizar comida"
    static var description = IntentDescription("Finaliza el descanso de comida")
    static var openAppWhenRun: Bool { false }

    func perform() async throws -> some IntentResult {
        NSLog("[FichajeWidget] WEndFoodBreakIntent.perform()")
        await WidgetIntentActions.performAction(.endFoodBreak)
        return .result()
    }
}

// MARK: - Button builder for the widget view

func makeIntentButton(icon: String, size: CGFloat, action: TimeTrackingAction) -> AnyView {
    let label = Image(icon, bundle: .widgetSharedKit)
        .resizable()
        .interpolation(.high)
        .scaledToFit()
        .frame(width: size, height: size)
        .contentShape(Rectangle())

    switch action {
    case .clockIn:
        return AnyView(Button(intent: WClockInIntent()) { label }.buttonStyle(.plain))
    case .clockOut:
        return AnyView(Button(intent: WClockOutIntent()) { label }.buttonStyle(.plain))
    case .startCoffeeBreak, .startBreak:
        return AnyView(Button(intent: WStartCoffeeBreakIntent()) { label }.buttonStyle(.plain))
    case .endCoffeeBreak, .endBreak:
        return AnyView(Button(intent: WEndCoffeeBreakIntent()) { label }.buttonStyle(.plain))
    case .startFoodBreak:
        return AnyView(Button(intent: WStartFoodBreakIntent()) { label }.buttonStyle(.plain))
    case .endFoodBreak:
        return AnyView(Button(intent: WEndFoodBreakIntent()) { label }.buttonStyle(.plain))
    case .none:
        return AnyView(EmptyView())
    }
}
