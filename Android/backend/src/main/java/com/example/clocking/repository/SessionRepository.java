package com.example.clocking.repository;

import com.example.clocking.domain.Session;
import com.example.clocking.domain.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByIdAndRevokedAtIsNull(Long id);

    long deleteByExpiresAtBefore(Instant instant);

    long countByUserAndRevokedAtIsNullAndExpiresAtAfter(User user, Instant instant);
}
