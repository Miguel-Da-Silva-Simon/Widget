//
//  ContentView.swift
//  widget
//

import SwiftUI

struct ContentView: View {
    @StateObject private var model = WidgetAppModel()

    var body: some View {
        TimeTrackingWidgetView(model: model)
            .frame(width: 400, height: 612)
            .overlay(alignment: .bottom) {
                if let err = model.lastError {
                    Text(err)
                        .font(.caption)
                        .foregroundStyle(.red)
                        .padding(8)
                        .frame(maxWidth: .infinity)
                        .background(.ultraThinMaterial)
                }
            }
    }
}

#Preview {
    ContentView()
}
