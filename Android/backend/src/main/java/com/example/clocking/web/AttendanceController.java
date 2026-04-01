package com.example.clocking.web;

import com.example.clocking.domain.AttendanceEventType;
import com.example.clocking.service.ClockingService;
import com.example.clocking.web.dto.AttendanceActionRequest;
import com.example.clocking.web.dto.ClockingStateDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/attendance")
public class AttendanceController {

    private final ClockingService clockingService;

    public AttendanceController(ClockingService clockingService) {
        this.clockingService = clockingService;
    }

    @PostMapping("/actions")
    public ClockingStateDto action(Authentication authentication, @Valid @RequestBody AttendanceActionRequest request) {
        if (request.action() == AttendanceEventType.RESET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usa POST /clockings/reset para reiniciar la jornada");
        }
        return clockingService.registerAction(Long.parseLong(authentication.getName()), request.action());
    }
}
