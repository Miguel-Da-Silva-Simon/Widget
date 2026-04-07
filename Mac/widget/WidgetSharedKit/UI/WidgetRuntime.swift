import Foundation

/// `WidgetSharedKit` se compila **una sola vez** para app y extensión; no use `WIDGET_EXTENSION` en el framework.
/// En la extensión el bundle principal es el `.appex`.
public enum WidgetRuntime {
    public static var isWidgetExtensionProcess: Bool {
        Bundle.main.bundleURL.pathExtension.lowercased() == "appex"
    }
}
