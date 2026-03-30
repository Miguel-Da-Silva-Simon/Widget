package com.example.clocking.web.dto;

public record SessionResponse(boolean authenticated, long sessionId, String expiresAt, UserDto user) {}
