package com.example.clocking.web.dto;

import com.example.clocking.domain.ClockingMode;
import java.util.List;

public record ClockingStateDto(
        ClockingMode mode,
        int currentStepIndex,
        boolean finished,
        String lastActionLabel,
        String lastActionTime,
        String nextStepLabel,
        String currentState,
        String nextAllowedAction,
        List<String> enabledActions,
        String lastEventType,
        long elapsedSeconds) {}
