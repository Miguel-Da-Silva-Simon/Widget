package com.example.clocking.service;

import com.example.clocking.domain.User;
import com.example.clocking.repository.UserRepository;
import com.example.clocking.web.dto.LoginRequest;
import com.example.clocking.web.dto.LoginResponse;
import com.example.clocking.web.dto.SessionResponse;
import com.example.clocking.web.dto.UserDto;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SessionService sessionService,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.clock = clock;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }
        Instant expiresAt = clock.instant().plusMillis(jwtService.getExpirationMs());
        var session = sessionService.createSession(user, expiresAt);
        String token = jwtService.generateToken(user, session.getId(), expiresAt);
        sessionService.attachToken(session.getId(), token);
        return new LoginResponse(token, session.getId(), expiresAt.toString(), new UserDto(user.getId(), user.getName(), user.getEmail()));
    }

    @Transactional
    public LoginResponse refresh(String rawToken) {
        var payloadOpt = jwtService.parseTokenForRefresh(rawToken);
        if (payloadOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var payload = payloadOpt.get();
        var sessionOpt = sessionService.validateForRefresh(payload.sessionId(), payload.userId());
        if (sessionOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var session = sessionOpt.get();
        var user = session.getUser();
        Instant newExpiresAt = clock.instant().plusMillis(jwtService.getExpirationMs());
        session.setExpiresAt(newExpiresAt);
        session.setLastSeenAt(clock.instant());
        String newToken = jwtService.generateToken(user, session.getId(), newExpiresAt);
        session.setTokenHash(SessionService.hashToken(newToken));
        return new LoginResponse(newToken, session.getId(), newExpiresAt.toString(), new UserDto(user.getId(), user.getName(), user.getEmail()));
    }

    public SessionResponse currentSession(long userId, long sessionId, Instant expiresAt) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new SessionResponse(true, sessionId, expiresAt.toString(), new UserDto(user.getId(), user.getName(), user.getEmail()));
    }

    public void logout(long sessionId) {
        sessionService.revoke(sessionId);
    }
}
