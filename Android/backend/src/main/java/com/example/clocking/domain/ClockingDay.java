package com.example.clocking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "clocking_days",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "day_date"}))
public class ClockingDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "day_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClockingMode mode = ClockingMode.WITH_MEAL;

    @Column(name = "current_step_index", nullable = false)
    private int currentStepIndex = 0;

    @Column(nullable = false)
    private boolean finished = false;

    @Column(name = "last_action_label", nullable = false)
    private String lastActionLabel = "Sin fichajes todavía";

    @Column(name = "last_action_time")
    private Instant lastActionTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public ClockingMode getMode() {
        return mode;
    }

    public void setMode(ClockingMode mode) {
        this.mode = mode;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void setCurrentStepIndex(int currentStepIndex) {
        this.currentStepIndex = currentStepIndex;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public String getLastActionLabel() {
        return lastActionLabel;
    }

    public void setLastActionLabel(String lastActionLabel) {
        this.lastActionLabel = lastActionLabel;
    }

    public Instant getLastActionTime() {
        return lastActionTime;
    }

    public void setLastActionTime(Instant lastActionTime) {
        this.lastActionTime = lastActionTime;
    }
}
