package com.example.clocking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.clocking.domain.AttendanceEventType;
import com.example.clocking.domain.AttendanceState;
import com.example.clocking.domain.ClockingMode;
import com.example.clocking.domain.User;
import com.example.clocking.repository.AttendanceEventRepository;
import com.example.clocking.repository.UserRepository;
import com.example.clocking.repository.WorkdayModePreferenceRepository;
import com.example.clocking.web.dto.ClockingStateDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class ClockingServiceIntegrationTest {

    @Autowired
    private ClockingService clockingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AttendanceEventRepository attendanceEventRepository;

    @Autowired
    private WorkdayModePreferenceRepository workdayModePreferenceRepository;

    @BeforeEach
    void setUp() {
        attendanceEventRepository.deleteAll();
        workdayModePreferenceRepository.deleteAll();
    }

    @Test
    void concurrentClockInRequestsOnlyCreateOneEvent() throws Exception {
        User user = demoUser();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Object> first = executor.submit(
                    concurrentCall(ready, start, () -> clockingService.registerAction(user.getId(), AttendanceEventType.CLOCK_IN)));
            Future<Object> second = executor.submit(
                    concurrentCall(ready, start, () -> clockingService.registerAction(user.getId(), AttendanceEventType.CLOCK_IN)));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Object firstResult = first.get(5, TimeUnit.SECONDS);
            Object secondResult = second.get(5, TimeUnit.SECONDS);

            int successCount = countInstancesOf(ClockingStateDto.class, firstResult, secondResult);
            int failureCount = countInstancesOf(Throwable.class, firstResult, secondResult);

            assertThat(successCount).isEqualTo(1);
            assertThat(failureCount).isEqualTo(1);
            assertThat(attendanceEventRepository.findAll())
                    .hasSize(1)
                    .allSatisfy(event -> assertThat(event.getEventType()).isEqualTo(AttendanceEventType.CLOCK_IN));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void setModeBeforeStartUpdatesPreference() {
        User user = demoUser();

        ClockingStateDto state = clockingService.setMode(user.getId(), ClockingMode.TWO_BREAKS);

        assertThat(state.mode()).isEqualTo(ClockingMode.TWO_BREAKS);
        assertThat(state.currentState()).isEqualTo(AttendanceState.NOT_STARTED.name());
        var preference = workdayModePreferenceRepository.findByUser(user);
        assertThat(preference).isPresent();
        assertThat(preference.orElseThrow().getMode()).isEqualTo(ClockingMode.TWO_BREAKS);
    }

    @Test
    void setModeAfterStartIsRejected() {
        User user = demoUser();
        clockingService.registerAction(user.getId(), AttendanceEventType.CLOCK_IN);

        assertThatThrownBy(() -> clockingService.setMode(user.getId(), ClockingMode.TWO_BREAKS))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Reinicia la jornada actual antes de cambiar el modo");

        assertThat(attendanceEventRepository.findAll())
                .hasSize(1)
                .allSatisfy(event -> assertThat(event.getWorkdayMode()).isEqualTo(ClockingMode.WITH_MEAL));
        assertThat(workdayModePreferenceRepository.findByUser(user)).isEmpty();
    }

    @Test
    void concurrentResetAndClockInKeepTheDayConsistent() throws Exception {
        User user = demoUser();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Object> clockIn =
                    executor.submit(concurrentCall(ready, start, () -> clockingService.registerAction(user.getId(), AttendanceEventType.CLOCK_IN)));
            Future<Object> reset =
                    executor.submit(concurrentCall(ready, start, () -> clockingService.resetToday(user.getId())));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(clockIn.get(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(reset.get(5, TimeUnit.SECONDS)).isNotNull();

            ClockingStateDto state = clockingService.getToday(user.getId());
            assertThat(attendanceEventRepository.findAll()).hasSize(2);
            assertThat(state.currentState())
                    .isIn(AttendanceState.NOT_STARTED.name(), AttendanceState.WORKING.name());
            if (AttendanceState.WORKING.name().equals(state.currentState())) {
                assertThat(state.lastActionLabel()).isEqualTo("Entrada");
                assertThat(state.nextAllowedAction()).isEqualTo(AttendanceEventType.BREAK_START.name());
            } else {
                assertThat(state.lastActionLabel()).isEqualTo("Sin fichajes todav\u00eda");
                assertThat(state.nextAllowedAction()).isEqualTo(AttendanceEventType.CLOCK_IN.name());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Object> concurrentCall(CountDownLatch ready, CountDownLatch start, Callable<Object> action) {
        return () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                return action.call();
            } catch (Exception e) {
                return e;
            }
        };
    }

    private int countInstancesOf(Class<?> type, Object... values) {
        int count = 0;
        for (Object value : values) {
            if (type.isInstance(value)) {
                count++;
            }
        }
        return count;
    }

    private User demoUser() {
        return userRepository.findByEmailIgnoreCase("test@demo.com").orElseThrow();
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-03-30T08:00:00Z"), ZoneId.of("Europe/Madrid"));
        }
    }
}
