//
//  widgetTests.swift
//  widgetTests
//

import Foundation
import Testing
@testable import widget

struct TimeTrackingStateMachineTests {
    @Test func clockInFromNotClockedInSucceeds() {
        let result = TimeTrackingStateMachine.tryTransition(
            currentStatus: .notClockedIn,
            activeBreakType: .none,
            action: .clockIn
        )
        guard case .success(let next) = result else {
            Issue.record("Expected success")
            return
        }
        #expect(next == .working)
    }

    @Test func clockInFromWorkingFails() {
        let result = TimeTrackingStateMachine.tryTransition(
            currentStatus: .working,
            activeBreakType: .none,
            action: .clockIn
        )
        guard case .failure = result else {
            Issue.record("Expected failure")
            return
        }
    }

    @Test func startCoffeeOnWorkingSucceeds() {
        let result = TimeTrackingStateMachine.tryTransition(
            currentStatus: .working,
            activeBreakType: .none,
            action: .startCoffeeBreak
        )
        guard case .success(let next) = result else {
            Issue.record("Expected success")
            return
        }
        #expect(next == .onBreak)
    }

    @Test func endCoffeeRequiresActiveCoffeeBreak() {
        let wrong = TimeTrackingStateMachine.tryTransition(
            currentStatus: .onBreak,
            activeBreakType: .food,
            action: .endCoffeeBreak
        )
        guard case .failure = wrong else {
            Issue.record("Expected failure for wrong break type")
            return
        }
        let ok = TimeTrackingStateMachine.tryTransition(
            currentStatus: .onBreak,
            activeBreakType: .coffee,
            action: .endCoffeeBreak
        )
        guard case .success(let next) = ok else {
            Issue.record("Expected success")
            return
        }
        #expect(next == .working)
    }
}

struct JsonPersistenceTests {
    @Test func decodesPascalCaseEnumsAndIsoDates() throws {
        let json = """
        {
          "Status": "NotClockedIn",
          "LastAction": "None",
          "LastActionAtUtc": null,
          "CurrentShiftStartedAtUtc": null,
          "ActiveBreakType": "None",
          "LastCompletedShiftWorkedSeconds": 0,
          "WorkedThisMonthSeconds": 0,
          "History": [],
          "WorkdayEvents": [],
          "BreakSessions": []
        }
        """.data(using: .utf8)!

        let decoder = WidgetJsonCoding.makeDecoder()
        let doc = try decoder.decode(TimeTrackingStateDocument.self, from: json)
        #expect(doc.status == .notClockedIn)
        #expect(doc.lastAction == .none)
        #expect(doc.activeBreakType == .none)
    }

    @Test func roundTripEncodesAndDecodesDefaultDocument() throws {
        let original = TimeTrackingStateDocument.createDefault()
        let encoder = WidgetJsonCoding.makeEncoder()
        let decoder = WidgetJsonCoding.makeDecoder()
        let data = try encoder.encode(original)
        let copy = try decoder.decode(TimeTrackingStateDocument.self, from: data)
        #expect(copy.status == original.status)
        #expect(copy.lastCompletedShiftWorkedSeconds == original.lastCompletedShiftWorkedSeconds)
    }
}
