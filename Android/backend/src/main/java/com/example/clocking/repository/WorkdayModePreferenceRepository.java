package com.example.clocking.repository;

import com.example.clocking.domain.User;
import com.example.clocking.domain.WorkdayModePreference;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkdayModePreferenceRepository extends JpaRepository<WorkdayModePreference, Long> {

    Optional<WorkdayModePreference> findByUser(User user);
}
