import SwiftUI
import WidgetKit
import WidgetSharedKit
#if os(macOS)
import AppKit
#endif

@main
struct widgetApp: App {
    @StateObject private var model = WidgetAppModel()

    var body: some Scene {
        Window("Fichaje", id: "main") {
            ContentView(model: model)
                .onOpenURL { url in
                    if let action = WidgetDeepLinkParser.parse(url) {
                        model.applyDeepLinkAction(action)
                    }
                }
                #if os(macOS)
                .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
                    model.refresh()
                    WidgetCenter.shared.reloadAllTimelines()
                }
                #endif
        }
        .defaultSize(width: 360, height: 280)
        .windowResizability(.contentSize)
    }
}
