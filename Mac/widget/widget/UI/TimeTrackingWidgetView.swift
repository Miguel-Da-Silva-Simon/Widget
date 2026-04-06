import AppKit
import SwiftUI

private enum WidgetTimelineLayout {
    /// Misma idea que `maxLines: 6` del Adaptive Card de Windows; altura fija evita que la ventana crezca.
    static let panelHeight: CGFloat = 118
}

private enum WidgetContentInsets {
    /// Separación extra respecto al padding del cuerpo de la tarjeta (saludo + fila de acciones).
    static let headerBlockHorizontal: CGFloat = 10
    /// Aire dentro del panel “Acciones registradas” (lateral e inferior).
    static let timelineHorizontal: CGFloat = 16
    static let timelineTop: CGFloat = 12
    static let timelineBottom: CGFloat = 12
}

private enum WidgetWindowMargins {
    /// Separación del header azul respecto a la barra de título / borde superior de la ventana.
    static let top: CGFloat = 36
    /// Separación de la tarjeta respecto al borde inferior de la ventana.
    static let bottom: CGFloat = 36
}

struct TimeTrackingWidgetView: View {
    @ObservedObject var model: WidgetAppModel
    @State private var loginName = ""

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { timeline in
            let presentation = model.presentation(now: timeline.date)
            widgetChrome {
                switch presentation {
                case .signedOut(let state):
                    signedOutContent(state)
                case .signedIn(let state):
                    signedInContent(state)
                }
            }
        }
        .sheet(isPresented: $model.showLoginSheet) {
            MockLoginSheet(
                displayName: $loginName,
                onSignIn: {
                    model.signInMock(displayName: loginName)
                    loginName = ""
                },
                onCancel: {
                    model.showLoginSheet = false
                    loginName = ""
                }
            )
        }
    }

    @ViewBuilder
    private func widgetChrome<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        let cardRadius: CGFloat = 18

        ZStack(alignment: .top) {
            BrandColors.surfaceTint
                .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()
                    .frame(height: WidgetWindowMargins.top)

                ZStack {
                    RoundedRectangle(cornerRadius: cardRadius, style: .continuous)
                        .fill(BrandColors.shadowSoft)
                        .padding(.horizontal, 4)
                        .offset(y: 6)
                        .allowsHitTesting(false)

                    VStack(spacing: 0) {
                        HStack {
                            Text("Fichaje")
                                .font(.headline.weight(.bold))
                                .foregroundStyle(BrandColors.white)
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 16)
                        .frame(maxWidth: .infinity)
                        .background(BrandColors.primaryBlue)

                        VStack(alignment: .leading, spacing: 0) {
                            content()
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(BrandColors.white)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: cardRadius, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: cardRadius, style: .continuous)
                            .stroke(BrandColors.cardBorder, lineWidth: 1)
                    )
                }
                .padding(.horizontal, 12)

                Spacer(minLength: WidgetWindowMargins.bottom)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    @ViewBuilder
    private func signedOutContent(_ state: SignedOutWidgetPresentation) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(state.message)
                .font(.body)
                .foregroundStyle(BrandColors.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            HStack {
                Spacer()
                Button(action: { model.openApp() }) {
                    windowsWidgetSVG(WindowsWidgetIcon.openAppBlue, size: 44)
                }
                .buttonStyle(.plain)
                Spacer()
            }
            .padding(.top, 8)
        }
    }

    @ViewBuilder
    private func signedInContent(_ state: SignedInWidgetPresentation) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 4) {
                        Spacer().frame(height: 4)
                        Text(state.displayName)
                            .font(.body.weight(.bold))
                            .foregroundStyle(BrandColors.textPrimary)
                            .lineLimit(2)
                    }
                    Spacer()
                    if let img = state.profileImage {
                        Image(nsImage: img)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 32, height: 32)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(BrandColors.white, lineWidth: 1))
                    }
                }

                HStack(alignment: .center, spacing: 12) {
                    timerChip(counter: state.sessionCounter, isActive: state.timerBulletActive)

                    Spacer(minLength: 8)

                    HStack(spacing: 8) {
                        primaryActionButton(state: state)
                        coffeeButton(state: state)
                        foodButton(state: state)
                    }
                }
            }
            .padding(.horizontal, WidgetContentInsets.headerBlockHorizontal)

            Divider()
                .background(BrandColors.cardBorder)

            VStack(spacing: 8) {
                Spacer().frame(height: 4)
                Text(state.statusHeadline)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(BrandColors.textPrimary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)

                Spacer().frame(height: 8)

                summaryRow(state: state)
            }
            .padding(.horizontal, WidgetContentInsets.headerBlockHorizontal)

            VStack(alignment: .leading, spacing: 6) {
                Text("Última acción: \(state.lastAction)")
                    .font(.caption)
                    .foregroundStyle(BrandColors.textSecondary)
                Text("Hora: \(state.lastActionTime)")
                    .font(.caption)
                    .foregroundStyle(BrandColors.textSecondary)
            }
            .padding(.top, 8)
            .padding(.horizontal, WidgetContentInsets.headerBlockHorizontal)

            if !state.timelineFormatted.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Acciones registradas")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(BrandColors.textPrimary)
                    ScrollView {
                        Text(state.timelineFormatted)
                            .font(.system(.caption, design: .monospaced))
                            .foregroundStyle(BrandColors.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(height: WidgetTimelineLayout.panelHeight)
                    .clipped()
                }
                .padding(.horizontal, WidgetContentInsets.timelineHorizontal)
                .padding(.top, WidgetContentInsets.timelineTop)
                .padding(.bottom, WidgetContentInsets.timelineBottom)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(BrandColors.timelinePanelBackground)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(BrandColors.cardBorder.opacity(0.65), lineWidth: 1)
                        )
                )
                .padding(.top, 8)
                .padding(.horizontal, WidgetContentInsets.headerBlockHorizontal)
            }

            Button("Cerrar sesión (mock)") {
                model.signOutMock()
            }
            .buttonStyle(.borderless)
            .font(.caption)
            .foregroundStyle(BrandColors.primaryBlue)
            .padding(.top, 8)
            .padding(.horizontal, WidgetContentInsets.headerBlockHorizontal)
        }
    }

    private func timerChip(counter: String, isActive: Bool) -> some View {
        let normalized = normalizeTimer(counter)
        return HStack(spacing: 8) {
            Circle()
                .fill(isActive ? BrandColors.timerActive : BrandColors.timerInactive)
                .frame(width: 8, height: 8)
            Text(normalized)
                .font(.system(size: 16, weight: .medium, design: .monospaced))
                .foregroundStyle(BrandColors.textPrimary)
        }
        .padding(.horizontal, 12)
        .frame(height: 44)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(BrandColors.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(BrandColors.chipBorder, lineWidth: 1)
                )
        )
    }

    private func normalizeTimer(_ value: String) -> String {
        let filtered = value.filter { $0.isNumber || $0 == ":" }
        let chars = Array(filtered)
        guard chars.count == 8, chars[2] == ":", chars[5] == ":" else {
            return "00:00:00"
        }
        return String(chars)
    }

    @ViewBuilder
    private func primaryActionButton(state: SignedInWidgetPresentation) -> some View {
        if state.showPrimaryInteractive {
            Button(action: {
                if state.primaryIsClockIn {
                    model.clockIn()
                } else {
                    model.clockOut()
                }
            }) {
                windowsWidgetSVG(
                    state.primaryIsClockIn ? WindowsWidgetIcon.entryBlue : WindowsWidgetIcon.stopBlue,
                    size: 44
                )
            }
            .buttonStyle(.plain)
        } else if state.showPrimaryDisabled {
            windowsWidgetSVG(WindowsWidgetIcon.stopBlue, size: 44)
        }
    }

    @ViewBuilder
    private func coffeeButton(state: SignedInWidgetPresentation) -> some View {
        if state.showCoffeeActive {
            Button(action: { model.toggleCoffee() }) {
                windowsWidgetSVG(WindowsWidgetIcon.coffeeBlue, size: 44)
            }
            .buttonStyle(.plain)
        } else if state.showCoffeeEndBreak {
            Button(action: { model.toggleCoffee() }) {
                windowsWidgetSVG(WindowsWidgetIcon.coffeeBreak, size: 44)
            }
            .buttonStyle(.plain)
        } else if state.showCoffeeDisabled {
            windowsWidgetSVG(WindowsWidgetIcon.coffeeDisabled, size: 44)
        }
    }

    @ViewBuilder
    private func foodButton(state: SignedInWidgetPresentation) -> some View {
        if state.showFoodActive {
            Button(action: { model.toggleFood() }) {
                windowsWidgetSVG(WindowsWidgetIcon.foodBlue, size: 44)
            }
            .buttonStyle(.plain)
        } else if state.showFoodEndBreak {
            Button(action: { model.toggleFood() }) {
                windowsWidgetSVG(WindowsWidgetIcon.foodBreak, size: 44)
            }
            .buttonStyle(.plain)
        } else if state.showFoodDisabled {
            windowsWidgetSVG(WindowsWidgetIcon.foodDisabled, size: 44)
        }
    }

    private func windowsWidgetSVG(_ name: String, size: CGFloat) -> some View {
        Image(name)
            .resizable()
            .interpolation(.high)
            .scaledToFit()
            .frame(width: size, height: size)
    }

    private func summaryRow(state: SignedInWidgetPresentation) -> some View {
        HStack {
            Spacer()
            summaryColumn(asset: WindowsWidgetIcon.lastShift, title: "Jornada", value: state.lastCompletedShiftDuration)
            Spacer().frame(width: 20)
            summaryColumn(asset: WindowsWidgetIcon.coffeeSummary, title: "Descanso", value: state.coffeeTodayDuration)
            Spacer().frame(width: 20)
            summaryColumn(asset: WindowsWidgetIcon.foodSummary, title: "Comida", value: state.foodTodayDuration)
            Spacer()
        }
    }

    private func summaryColumn(asset: String, title: String, value: String) -> some View {
        VStack(spacing: 4) {
            windowsWidgetSVG(asset, size: 30)
            Text(title)
                .font(.caption2)
                .foregroundStyle(BrandColors.textSecondary)
            Text(value)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(BrandColors.textPrimary)
        }
        .multilineTextAlignment(.center)
    }
}
