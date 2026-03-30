package com.example.clocking.web.dto;

import com.example.clocking.domain.AttendanceEventType;
import jakarta.validation.constraints.NotNull;

public record AttendanceActionRequest(@NotNull AttendanceEventType action) {}
