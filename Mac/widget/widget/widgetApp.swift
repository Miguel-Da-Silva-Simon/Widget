//
//  widgetApp.swift
//  widget
//

import SwiftUI

@main
struct widgetApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .defaultSize(width: 400, height: 612)
        .windowResizability(.contentSize)
    }
}
