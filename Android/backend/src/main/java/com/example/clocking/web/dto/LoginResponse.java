package com.example.clocking.web.dto;

public record LoginResponse(String token, long sessionId, String expiresAt, UserDto user) {}
