package com.example.clocking.web.dto;

import com.example.clocking.domain.ClockingMode;
import jakarta.validation.constraints.NotNull;

public record SetModeRequest(@NotNull ClockingMode mode) {}
