package com.example.clocking.repository;

import com.example.clocking.domain.AttendanceEvent;
import com.example.clocking.domain.User;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, Long> {

    List<AttendanceEvent> findByUserAndTimestampBetweenOrderByTimestampAscIdAsc(
            User user, Instant fromInclusive, Instant toExclusive);
}
