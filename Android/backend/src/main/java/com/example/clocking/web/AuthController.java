package com.example.clocking.web;

import com.example.clocking.service.AuthService;
import com.example.clocking.config.JwtAuthFilter.AuthDetails;
import com.example.clocking.web.dto.LoginRequest;
import com.example.clocking.web.dto.LoginResponse;
import com.example.clocking.web.dto.SessionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return authService.refresh(authHeader.substring(7).trim());
    }

    @GetMapping("/session")
    public SessionResponse session(Authentication authentication) {
        long userId = Long.parseLong(authentication.getName());
        if (!(authentication.getDetails() instanceof AuthDetails details)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return authService.currentSession(userId, details.sessionId(), java.time.Instant.parse(details.expiresAt()));
    }

    @PostMapping("/logout")
    public void logout(Authentication authentication) {
        if (authentication.getDetails() instanceof AuthDetails details) {
            authService.logout(details.sessionId());
        }
    }
}
