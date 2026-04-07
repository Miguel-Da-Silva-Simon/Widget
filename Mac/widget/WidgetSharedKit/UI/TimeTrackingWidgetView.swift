import AppKit
import SwiftUI
import WidgetKit

// MARK: - Layout constants

private enum WidgetWindowMargins {
    static var top: CGFloat { WidgetRuntime.isWidgetExtensionProcess ? 4 : 36 }
    static var bottom: CGFloat { WidgetRuntime.isWidgetExtensionProcess ? 4 : 36 }
}

// MARK: - Action button builder type

public typealias ActionButtonBuilder = (_ icon: String, _ size: CGFloat, _ action: TimeTrackingAction) -> AnyView

// MARK: - Main view

public struct TimeTrackingWidgetView: View {
    @ObservedObject private var model: WidgetAppModel
    private let timelinePayload: (UserSession, TimeTrackingSnapshot, NSImage?)?
    private let widgetFamily: WidgetFamily
    private let actionButtonBuilder: ActionButtonBuilder?

    public init(model: WidgetAppModel) {
        _model = ObservedObject(wrappedValue: model)
        timelinePayload = nil
        widgetFamily = .systemLarge
        actionButtonBuilder = nil
    }

    public init(
        timelineSession: UserSession,
        snapshot: TimeTrackingSnapshot,
        profileImage: NSImage?,
        family: WidgetFamily = .systemLarge,
        actionButtonBuilder: ActionButtonBuilder? = nil
    ) {
        _model = ObservedObject(wrappedValue: WidgetAppModel(skipInitialRefresh: true))
        timelinePayload = (timelineSession, snapshot, profileImage)
        widgetFamily = family
        self.actionButtonBuilder = actionButtonBuilder
    }

    public var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { timeline in
            let pres = presentation(at: timeline.date)
            Group {
                switch widgetFamily {
                case .systemSmall: smallBody(pres)
                case .systemMedium: mediumBody(pres)
                default: largeBody(pres)
                }
            }
        }
    }

    private func presentation(at date: Date) -> TimeTrackingWidgetPresentation {
        if let pl = timelinePayload {
            TimeTrackingWidgetViewModelMapper.map(session: pl.0, snapshot: pl.1, profileImage: pl.2, now: date)
        } else {
            model.presentation(now: date)
        }
    }
}

// MARK: - Small layout

extension TimeTrackingWidgetView {
    @ViewBuilder
    private func smallBody(_ pres: TimeTrackingWidgetPresentation) -> some View {
        compactChrome {
            switch pres {
            case .signedOut(let s): smallSignedOut(s)
            case .signedIn(let s): smallSignedIn(s)
            }
        }
    }

    @ViewBuilder
    private func smallSignedOut(_ state: SignedOutWidgetPresentation) -> some View {
        VStack(spacing: 10) {
            Text("Fichaje")
                .font(.footnote.weight(.bold))
                .foregroundStyle(BrandColors.primaryBlue)

            Text(state.message)
                .font(.caption2)
                .foregroundStyle(BrandColors.textSecondary)
                .multilineTextAlignment(.center)
                .lineLimit(3)

            widgetSVG(WindowsWidgetIcon.openAppBlue, size: 40)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .widgetURL(WidgetDeepLinks.openApp)
    }

    @ViewBuilder
    private func smallSignedIn(_ state: SignedInWidgetPresentation) -> some View {
        VStack(spacing: 0) {
            timerChip(counter: state.sessionCounter, isActive: state.timerBulletActive, size: .small)
                .padding(.bottom, 8)

            Text(state.shortStatusHeadline)
                .font(.footnote.weight(.bold))
                .foregroundStyle(BrandColors.textPrimary)
                .lineLimit(1)
                .padding(.bottom, 2)

            if let hint = state.returnHint {
                Text(hint)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(BrandColors.primaryBlue)
                    .lineLimit(1)
                    .padding(.bottom, 6)
            } else {
                Spacer().frame(height: 6)
            }

            smallActionButtons(state: state)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    @ViewBuilder
    private func smallActionButtons(state: SignedInWidgetPresentation) -> some View {
        let btnSize: CGFloat = 36

        HStack(spacing: 8) {
            if state.isOnBreak {
                if state.showCoffeeEndBreak {
                    actionButton(icon: WindowsWidgetIcon.coffeeBreak, size: btnSize, action: .endCoffeeBreak)
                } else if state.showFoodEndBreak {
                    actionButton(icon: WindowsWidgetIcon.foodBreak, size: btnSize, action: .endFoodBreak)
                }
            } else {
                if state.showPrimaryInteractive {
                    let icon = state.primaryIsClockIn ? WindowsWidgetIcon.entryBlue : WindowsWidgetIcon.stopBlue
                    actionButton(icon: icon, size: btnSize, action: state.primaryIsClockIn ? .clockIn : .clockOut)
                }

                if state.showCoffeeActive {
                    actionButton(icon: WindowsWidgetIcon.coffeeBlue, size: btnSize, action: .startCoffeeBreak)
                } else if state.showFoodActive {
                    actionButton(icon: WindowsWidgetIcon.foodBlue, size: btnSize, action: .startFoodBreak)
                }
            }
        }
    }
}

// MARK: - Medium layout

extension TimeTrackingWidgetView {
    @ViewBuilder
    private func mediumBody(_ pres: TimeTrackingWidgetPresentation) -> some View {
        fullChrome(compactHeader: true) {
            switch pres {
            case .signedOut(let s): mediumSignedOut(s)
            case .signedIn(let s): mediumSignedIn(s)
            }
        }
    }

    @ViewBuilder
    private func mediumSignedOut(_ state: SignedOutWidgetPresentation) -> some View {
        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Text(state.message)
                    .font(.callout)
                    .foregroundStyle(BrandColors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            openAppButton(size: 44)
        }
        .frame(maxHeight: .infinity)
    }

    @ViewBuilder
    private func mediumSignedIn(_ state: SignedInWidgetPresentation) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 10) {
                timerChip(counter: state.sessionCounter, isActive: state.timerBulletActive, size: .medium)

                VStack(alignment: .leading, spacing: 2) {
                    Text(state.statusHeadline)
                        .font(.callout.weight(.bold))
                        .foregroundStyle(BrandColors.textPrimary)
                        .lineLimit(1)

                    if let hint = state.returnHint {
                        Text(hint)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(BrandColors.primaryBlue)
                            .lineLimit(1)
                    }
                }

                Spacer(minLength: 0)
            }

            HStack(spacing: 12) {
                Spacer(minLength: 0)
                primaryButton(state: state, size: 44)
                coffeeButton(state: state, size: 44)
                foodButton(state: state, size: 44)
                Spacer(minLength: 0)
            }
        }
    }
}

// MARK: - Large layout

extension TimeTrackingWidgetView {
    @ViewBuilder
    private func largeBody(_ pres: TimeTrackingWidgetPresentation) -> some View {
        fullChrome(compactHeader: false) {
            switch pres {
            case .signedOut(let s): largeSignedOut(s)
            case .signedIn(let s): largeSignedIn(s)
            }
        }
    }

    @ViewBuilder
    private func largeSignedOut(_ state: SignedOutWidgetPresentation) -> some View {
        VStack(spacing: 20) {
            Spacer(minLength: 8)
            Text(state.message)
                .font(.body)
                .foregroundStyle(BrandColors.textPrimary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            openAppButton(size: 48)
            Spacer(minLength: 8)
        }
    }

    @ViewBuilder
    private func largeSignedIn(_ state: SignedInWidgetPresentation) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            // --- Zone 1: Identity ---
            HStack(alignment: .center) {
                Text(state.displayName)
                    .font(.callout.weight(.bold))
                    .foregroundStyle(BrandColors.textPrimary)
                    .lineLimit(1)
                Spacer(minLength: 4)
                if let img = state.profileImage {
                    Image(nsImage: img)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 28, height: 28)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(BrandColors.cardBorder, lineWidth: 1))
                }
            }
            .padding(.horizontal, 10)
            .padding(.bottom, 14)

            // --- Zone 2: Status block ---
            VStack(spacing: 6) {
                timerChip(counter: state.sessionCounter, isActive: state.timerBulletActive, size: .large)

                Text(state.statusHeadline)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(BrandColors.textPrimary)

                if let hint = state.returnHint {
                    Text(hint)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(BrandColors.primaryBlue)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.bottom, 14)

            // --- Zone 3: Actions ---
            HStack(spacing: 12) {
                Spacer(minLength: 0)
                primaryButton(state: state, size: 44)
                coffeeButton(state: state, size: 44)
                foodButton(state: state, size: 44)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10)
            .padding(.bottom, 14)

            thinDivider()
                .padding(.horizontal, 10)
                .padding(.bottom, 12)

            // --- Zone 4: Summary ---
            summaryRow(state: state)
                .padding(.horizontal, 10)
                .padding(.bottom, 12)

            thinDivider()
                .padding(.horizontal, 10)
                .padding(.bottom, 10)

            // --- Zone 5: Timeline ---
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    Text("Última acción:")
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(BrandColors.textSecondary)
                    Text(state.lastAction)
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(BrandColors.textPrimary)
                    Spacer(minLength: 0)
                    Text(state.lastActionTime)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(BrandColors.textSecondary)
                }

                if !state.timelineItems.isEmpty {
                    VStack(alignment: .leading, spacing: 3) {
                        ForEach(state.timelineItems, id: \.self) { item in
                            Text(item)
                                .font(.system(.caption2, design: .monospaced))
                                .foregroundStyle(BrandColors.textSecondary)
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(BrandColors.timelinePanelBackground)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(BrandColors.cardBorder.opacity(0.5), lineWidth: 0.5)
                            )
                    )
                }
            }
            .padding(.horizontal, 10)
        }
    }
}

// MARK: - Chrome

extension TimeTrackingWidgetView {
    @ViewBuilder
    private func compactChrome<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        let cardRadius: CGFloat = 14

        ZStack {
            BrandColors.surfaceTint.ignoresSafeArea()

            VStack(spacing: 0) {
                content()
            }
            .padding(10)
            .background(
                RoundedRectangle(cornerRadius: cardRadius, style: .continuous)
                    .fill(BrandColors.white)
            )
            .overlay(
                RoundedRectangle(cornerRadius: cardRadius, style: .continuous)
                    .stroke(BrandColors.cardBorder, lineWidth: 1)
            )
            .padding(6)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    @ViewBuilder
    private func fullChrome<Content: View>(compactHeader: Bool, @ViewBuilder content: () -> Content) -> some View {
        let cardRadius: CGFloat = 18

        ZStack(alignment: .top) {
            BrandColors.surfaceTint.ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer().frame(height: WidgetWindowMargins.top)

                ZStack {
                    RoundedRectangle(cornerRadius: cardRadius, style: .continuous)
                        .fill(BrandColors.shadowSoft)
                        .padding(.horizontal, 4)
                        .offset(y: 6)
                        .allowsHitTesting(false)

                    VStack(spacing: 0) {
                        HStack {
                            Text("Fichaje")
                                .font(compactHeader ? .subheadline.weight(.bold) : .headline.weight(.bold))
                                .foregroundStyle(BrandColors.white)
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, compactHeader ? 16 : 20)
                        .padding(.vertical, compactHeader ? 10 : 14)
                        .frame(maxWidth: .infinity)
                        .background(BrandColors.primaryBlue)

                        VStack(alignment: .leading, spacing: 0) {
                            content()
                        }
                        .padding(compactHeader ? 12 : 14)
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
        .frame(
            maxWidth: .infinity,
            maxHeight: WidgetRuntime.isWidgetExtensionProcess ? .infinity : nil,
            alignment: .top
        )
    }
}

// MARK: - Shared components

extension TimeTrackingWidgetView {
    private enum TimerSize {
        case small, medium, large
    }

    private func timerChip(counter: String, isActive: Bool, size: TimerSize) -> some View {
        let normalized = normalizeTimer(counter)
        let dotSize: CGFloat
        let fontSize: CGFloat
        let height: CGFloat
        let hPad: CGFloat
        let cornerRadius: CGFloat

        switch size {
        case .small:
            dotSize = 6; fontSize = 12; height = 30; hPad = 6; cornerRadius = 8
        case .medium:
            dotSize = 7; fontSize = 15; height = 38; hPad = 10; cornerRadius = 10
        case .large:
            dotSize = 8; fontSize = 17; height = 42; hPad = 12; cornerRadius = 12
        }

        return HStack(spacing: 6) {
            Circle()
                .fill(isActive ? BrandColors.timerActive : BrandColors.timerInactive)
                .frame(width: dotSize, height: dotSize)
            Text(normalized)
                .font(.system(size: fontSize, weight: .semibold, design: .monospaced))
                .foregroundStyle(BrandColors.textPrimary)
                .fixedSize(horizontal: true, vertical: false)
                .lineLimit(1)
        }
        .padding(.horizontal, hPad)
        .frame(height: height)
        .background(
            RoundedRectangle(cornerRadius: cornerRadius)
                .fill(BrandColors.white)
                .overlay(
                    RoundedRectangle(cornerRadius: cornerRadius)
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

    private func thinDivider() -> some View {
        Rectangle()
            .fill(BrandColors.cardBorder.opacity(0.6))
            .frame(height: 1)
    }

    // MARK: Open App button

    @ViewBuilder
    private func openAppButton(size: CGFloat) -> some View {
        if WidgetRuntime.isWidgetExtensionProcess {
            Link(destination: WidgetDeepLinks.openApp) {
                widgetSVG(WindowsWidgetIcon.openAppBlue, size: size)
            }
            .buttonStyle(.plain)
        } else {
            Button(action: { model.openApp() }) {
                widgetSVG(WindowsWidgetIcon.openAppBlue, size: size)
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: Action buttons

    @ViewBuilder
    private func primaryButton(state: SignedInWidgetPresentation, size: CGFloat) -> some View {
        if state.showPrimaryInteractive {
            let icon = state.primaryIsClockIn ? WindowsWidgetIcon.entryBlue : WindowsWidgetIcon.stopBlue
            actionButton(icon: icon, size: size, action: state.primaryIsClockIn ? .clockIn : .clockOut)
        } else if state.showPrimaryDisabled {
            widgetSVG(WindowsWidgetIcon.stopBlue, size: size).opacity(0.55)
        }
    }

    @ViewBuilder
    private func coffeeButton(state: SignedInWidgetPresentation, size: CGFloat) -> some View {
        if state.showCoffeeActive {
            actionButton(icon: WindowsWidgetIcon.coffeeBlue, size: size, action: .startCoffeeBreak)
        } else if state.showCoffeeEndBreak {
            actionButton(icon: WindowsWidgetIcon.coffeeBreak, size: size, action: .endCoffeeBreak)
        } else if state.showCoffeeDisabled {
            widgetSVG(WindowsWidgetIcon.coffeeDisabled, size: size)
        }
    }

    @ViewBuilder
    private func foodButton(state: SignedInWidgetPresentation, size: CGFloat) -> some View {
        if state.showFoodActive {
            actionButton(icon: WindowsWidgetIcon.foodBlue, size: size, action: .startFoodBreak)
        } else if state.showFoodEndBreak {
            actionButton(icon: WindowsWidgetIcon.foodBreak, size: size, action: .endFoodBreak)
        } else if state.showFoodDisabled {
            widgetSVG(WindowsWidgetIcon.foodDisabled, size: size)
        }
    }

    @ViewBuilder
    private func actionButton(icon: String, size: CGFloat, action: TimeTrackingAction) -> some View {
        if let builder = actionButtonBuilder {
            builder(icon, size, action)
        } else {
            widgetSVG(icon, size: size)
                .contentShape(Rectangle())
        }
    }

    // MARK: Summary

    private func summaryRow(state: SignedInWidgetPresentation) -> some View {
        HStack(spacing: 0) {
            summaryColumn(asset: WindowsWidgetIcon.lastShift, title: "Jornada", value: state.lastCompletedShiftDuration)
            Spacer(minLength: 0)
            summaryColumn(asset: WindowsWidgetIcon.coffeeSummary, title: "Descanso", value: state.coffeeTodayDuration)
            Spacer(minLength: 0)
            summaryColumn(asset: WindowsWidgetIcon.foodSummary, title: "Comida", value: state.foodTodayDuration)
        }
    }

    private func summaryColumn(asset: String, title: String, value: String) -> some View {
        VStack(spacing: 3) {
            widgetSVG(asset, size: 24)
            Text(title)
                .font(.system(size: 9, weight: .medium))
                .foregroundStyle(BrandColors.textSecondary)
            Text(value)
                .font(.caption2.weight(.bold))
                .foregroundStyle(BrandColors.textPrimary)
        }
        .multilineTextAlignment(.center)
    }

    // MARK: SVG helper

    private func widgetSVG(_ name: String, size: CGFloat) -> some View {
        Image(name, bundle: .widgetSharedKit)
            .resizable()
            .interpolation(.high)
            .scaledToFit()
            .frame(width: size, height: size)
    }
}
