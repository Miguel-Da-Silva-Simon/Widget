package com.example.clocking.repository;

import com.example.clocking.domain.ClockingDay;
import com.example.clocking.domain.ClockingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClockingEventRepository extends JpaRepository<ClockingEvent, Long> {

    void deleteByClockingDay(ClockingDay clockingDay);
}
