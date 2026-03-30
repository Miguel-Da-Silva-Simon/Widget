package com.example.clocking.repository;

import com.example.clocking.domain.ClockingDay;
import com.example.clocking.domain.User;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClockingDayRepository extends JpaRepository<ClockingDay, Long> {

    Optional<ClockingDay> findByUserAndDate(User user, LocalDate date);
}
