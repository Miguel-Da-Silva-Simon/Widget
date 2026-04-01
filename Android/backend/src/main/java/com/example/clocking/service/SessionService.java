package com.example.clocking.service;

import com.example.clocking.domain.Session;
import com.example.clocking.domain.User;
import com.example.clocking.repository.SessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final Clock clock;

    public SessionService(SessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Transactional
    public Session createSession(User user, Instant expiresAt) {
        Session session = new Session();
        session.setUser(user);
        session.setExpiresAt(expiresAt);
        session.setTokenHash("PENDING");
        session.setLastSeenAt(clock.instant());
        return sessionRepository.save(session);
    }

    @Transactional
    public void attachToken(long sessionId, String rawToken) {
        Session session = sessionRepository.findById(sessionId).orElseThrow();
        session.setTokenHash(hashToken(rawToken));
        sessionRepository.save(session);
    }

    @Transactional
    public Optional<Session> validate(long sessionId, long userId, String rawToken) {
        Instant now = clock.instant();
        Optional<Session> found = sessionRepository.findByIdAndRevokedAtIsNull(sessionId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Session session = found.get();
        if (!session.isActive(now)) {
            return Optional.empty();
        }
        if (!session.getUser().getId().equals(userId)) {
            return Optional.empty();
        }
        session.setLastSeenAt(now);
        return Optional.of(sessionRepository.save(session));
    }

    @Transactional
    public Optional<Session> validateForRefresh(long sessionId, long userId) {
        Instant now = clock.instant();
        Optional<Session> found = sessionRepository.findByIdAndRevokedAtIsNull(sessionId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Session session = found.get();
        if (!session.isActive(now)) {
            return Optional.empty();
        }
        if (!session.getUser().getId().equals(userId)) {
            return Optional.empty();
        }
        session.setLastSeenAt(now);
        return Optional.of(sessionRepository.save(session));
    }

    @Transactional
    public void revoke(long sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setRevokedAt(clock.instant());
            sessionRepository.save(session);
        });
    }

    @Transactional
    public void setSessionExpiresAt(Session session, Instant newExpiresAt) {
        session.setExpiresAt(newExpiresAt);
        sessionRepository.save(session);
    }

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash token", e);
        }
    }
}
