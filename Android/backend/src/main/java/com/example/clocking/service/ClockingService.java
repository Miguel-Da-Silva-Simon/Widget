package com.example.clocking.service;

import com.example.clocking.domain.AttendanceEvent;
import com.example.clocking.domain.AttendanceEventType;
import com.example.clocking.domain.AttendanceState;
import com.example.clocking.domain.ClockingMode;
import com.example.clocking.domain.User;
import com.example.clocking.repository.AttendanceEventRepository;
import com.example.clocking.repository.UserRepository;
import com.example.clocking.repository.WorkdayModePreferenceRepository;
import com.example.clocking.web.dto.ClockingStateDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClockingService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Duration NEXT_DEBOUNCE_WINDOW = Duration.ofSeconds(2);

    private final AttendanceEventRepository attendanceEventRepository;
    private final UserRepository userRepository;
    private final WorkdayModePreferenceRepository modePreferenceRepository;
    private final ZoneId zoneId;
    private final Clock clock;

    public ClockingService(
            AttendanceEventRepository attendanceEventRepository,
            UserRepository userRepository,
            WorkdayModePreferenceRepository modePreferenceRepository,
            ZoneId zoneId,
            Clock clock) {
        this.attendanceEventRepository = attendanceEventRepository;
        this.userRepository = userRepository;
        this.modePreferenceRepository = modePreferenceRepository;
        this.zoneId = zoneId;
        this.clock = clock;
    }

    private record StepSlot(AttendanceEventType type, Integer order, String label) {}

    private record InternalState(
            ClockingMode mode,
            List<StepSlot> steps,
            List<AttendanceEvent> effectiveEvents,
            int progress,
            boolean finished,
            StepSlot next,
            StepSlot lastStep,
            Instant lastTs,
            AttendanceState attendanceState,
            long elapsedSeconds) {}

    @Transactional
    public ClockingStateDto getToday(long userId) {
        return deriveState(userId);
    }

    @Transactional
    public ClockingStateDto registerNext(long userId) {
        return doNext(userId, null);
    }

    @Transactional
    public ClockingStateDto registerAction(long userId, AttendanceEventType action) {
        return doNext(userId, action);
    }

    @Transactional
    public ClockingStateDto resetToday(long userId) {
        User user = mustUserForUpdate(userId);
        InternalState state = deriveInternal(user);

        AttendanceEvent event = new AttendanceEvent();
        event.setUser(user);
        event.setEventType(AttendanceEventType.RESET);
        event.setEventOrder(null);
        event.setWorkdayMode(state.mode());
        event.setTimestamp(clock.instant());
        attendanceEventRepository.save(event);

        return toDto(deriveInternal(user));
    }

    @Transactional
    public ClockingStateDto setMode(long userId, ClockingMode mode) {
        User user = mustUserForUpdate(userId);
        InternalState state = deriveInternal(user);
        if (!state.effectiveEvents().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Reinicia la jornada actual antes de cambiar el modo");
        }

        var pref = modePreferenceRepository.findByUser(user).orElseGet(() -> {
            var newPref = new com.example.clocking.domain.WorkdayModePreference();
            newPref.setUser(user);
            return newPref;
        });
        pref.setMode(mode);
        modePreferenceRepository.save(pref);

        return toDto(deriveInternal(user));
    }

    private static List<StepSlot> sequence(ClockingMode mode) {
        return switch (mode) {
            case WITH_MEAL -> List.of(
                    new StepSlot(AttendanceEventType.CLOCK_IN, null, "Entrada"),
                    new StepSlot(AttendanceEventType.BREAK_START, 1, "Inicio descanso"),
                    new StepSlot(AttendanceEventType.BREAK_END, 1, "Fin descanso"),
                    new StepSlot(AttendanceEventType.MEAL_START, null, "Inicio comida"),
                    new StepSlot(AttendanceEventType.MEAL_END, null, "Fin comida"),
                    new StepSlot(AttendanceEventType.CLOCK_OUT, null, "Salida"));
            case TWO_BREAKS -> List.of(
                    new StepSlot(AttendanceEventType.CLOCK_IN, null, "Entrada"),
                    new StepSlot(AttendanceEventType.BREAK_START, 1, "Inicio descanso 1"),
                    new StepSlot(AttendanceEventType.BREAK_END, 1, "Fin descanso 1"),
                    new StepSlot(AttendanceEventType.BREAK_START, 2, "Inicio descanso 2"),
                    new StepSlot(AttendanceEventType.BREAK_END, 2, "Fin descanso 2"),
                    new StepSlot(AttendanceEventType.CLOCK_OUT, null, "Salida"));
        };
    }

    private ClockingStateDto doNext(long userId, AttendanceEventType requestedAction) {
        User user = mustUserForUpdate(userId);
        InternalState state = deriveInternal(user);

        if (state.finished()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La jornada ya est\u00e1 terminada");
        }
        if (state.next() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay acci\u00f3n siguiente disponible");
        }
        if (isRapidSequentialNext(state)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Acci\u00f3n duplicada. Actualiza el estado antes de continuar");
        }
        if (requestedAction != null && state.next().type() != requestedAction) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acci\u00f3n no v\u00e1lida para el estado actual");
        }

        AttendanceEvent event = new AttendanceEvent();
        event.setUser(user);
        event.setEventType(state.next().type());
        event.setEventOrder(state.next().order());
        event.setWorkdayMode(state.mode());
        event.setTimestamp(clock.instant());
        attendanceEventRepository.save(event);

        return toDto(deriveInternal(user));
    }

    private ClockingStateDto deriveState(long userId) {
        return toDto(deriveInternal(mustUser(userId)));
    }

    private User mustUser(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private User mustUserForUpdate(long userId) {
        return userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private ClockingMode resolveMode(User user) {
        return modePreferenceRepository.findByUser(user)
                .map(com.example.clocking.domain.WorkdayModePreference::getMode)
                .orElse(ClockingMode.WITH_MEAL);
    }

    private boolean isRapidSequentialNext(InternalState state) {
        if (state.lastTs() == null) {
            return false;
        }
        Duration sinceLastEvent = Duration.between(state.lastTs(), clock.instant());
        return !sinceLastEvent.isNegative() && sinceLastEvent.compareTo(NEXT_DEBOUNCE_WINDOW) < 0;
    }

    private long computeElapsedSinceClockIn(List<AttendanceEvent> events, boolean finished, Instant now) {
        Instant clockIn = null;
        Instant clockOut = null;
        for (AttendanceEvent event : events) {
            Instant ts = event.getTimestamp();
            switch (event.getEventType()) {
                case CLOCK_IN -> {
                    if (clockIn == null) {
                        clockIn = ts;
                    }
                }
                case CLOCK_OUT -> clockOut = ts;
                default -> {
                }
            }
        }
        if (clockIn == null) {
            return 0;
        }
        Instant end = (finished && clockOut != null) ? clockOut : now;
        return Math.max(0, end.getEpochSecond() - clockIn.getEpochSecond());
    }

    private InternalState deriveInternal(User user) {
        LocalDate today = LocalDate.now(zoneId);
        Instant from = today.atStartOfDay(zoneId).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zoneId).toInstant();
        List<AttendanceEvent> all =
                attendanceEventRepository.findByUserAndTimestampBetweenOrderByTimestampAscIdAsc(user, from, to);

        int lastReset = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getEventType() == AttendanceEventType.RESET) {
                lastReset = i;
            }
        }

        List<AttendanceEvent> events = new ArrayList<>();
        for (int i = lastReset + 1; i < all.size(); i++) {
            if (all.get(i).getEventType() != AttendanceEventType.RESET) {
                events.add(all.get(i));
            }
        }

        ClockingMode mode = events.isEmpty() ? resolveMode(user) : events.get(0).getWorkdayMode();
        List<StepSlot> steps = sequence(mode);
        int progress = Math.min(events.size(), steps.size());
        boolean finished = progress >= steps.size();
        StepSlot next = finished ? null : steps.get(progress);
        StepSlot lastStep = progress <= 0 ? null : steps.get(progress - 1);
        Instant lastTs = events.isEmpty() ? null : events.get(events.size() - 1).getTimestamp();

        AttendanceState attendanceState;
        if (finished) {
            attendanceState = AttendanceState.FINISHED;
        } else if (lastStep == null) {
            attendanceState = AttendanceState.NOT_STARTED;
        } else if (lastStep.type() == AttendanceEventType.BREAK_START) {
            attendanceState = AttendanceState.BREAK_ACTIVE;
        } else if (lastStep.type() == AttendanceEventType.MEAL_START) {
            attendanceState = AttendanceState.MEAL_ACTIVE;
        } else {
            attendanceState = AttendanceState.WORKING;
        }

        long elapsedSeconds = computeElapsedSinceClockIn(events, finished, clock.instant());
        return new InternalState(mode, steps, events, progress, finished, next, lastStep, lastTs, attendanceState, elapsedSeconds);
    }

    private ClockingStateDto toDto(InternalState state) {
        String nextLabel;
        if (state.finished()) {
            nextLabel = "Jornada terminada";
        } else if (state.next() != null) {
            nextLabel = state.next().label();
        } else {
            nextLabel = "No disponible";
        }

        String lastLabel = state.lastStep() == null ? "Sin fichajes todav\u00eda" : state.lastStep().label();
        String timeStr = state.lastTs() == null ? "--:--" : TIME_FMT.withZone(zoneId).format(state.lastTs());
        List<String> enabledActions = state.next() == null ? List.of() : List.of(state.next().type().name());

        return new ClockingStateDto(
                state.mode(),
                state.finished() ? state.steps().size() - 1 : state.progress(),
                state.finished(),
                lastLabel,
                timeStr,
                nextLabel,
                state.attendanceState().name(),
                state.next() == null ? null : state.next().type().name(),
                enabledActions,
                state.lastStep() == null ? null : state.lastStep().type().name(),
                state.elapsedSeconds());
    }
}
