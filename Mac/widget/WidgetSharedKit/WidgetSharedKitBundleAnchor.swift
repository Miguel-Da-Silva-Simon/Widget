import Foundation

/// Ancla para localizar el bundle del framework (imágenes en `Assets.xcassets`).
public final class WidgetSharedKitBundleAnchor: NSObject {}

extension Bundle {
    public static var widgetSharedKit: Bundle {
        Bundle(for: WidgetSharedKitBundleAnchor.self)
    }
}
