package com.example.clocking.web;

import com.example.clocking.service.ClockingService;
import com.example.clocking.web.dto.ClockingStateDto;
import com.example.clocking.web.dto.SetModeRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clockings")
public class ClockingController {

    private final ClockingService clockingService;

    public ClockingController(ClockingService clockingService) {
        this.clockingService = clockingService;
    }

    @GetMapping("/today")
    public ClockingStateDto today(Authentication authentication) {
        return clockingService.getToday(userId(authentication));
    }

    @PostMapping("/next")
    public ClockingStateDto next(Authentication authentication) {
        return clockingService.registerNext(userId(authentication));
    }

    @PostMapping("/reset")
    public ClockingStateDto reset(Authentication authentication) {
        return clockingService.resetToday(userId(authentication));
    }

    @PostMapping("/mode")
    public ClockingStateDto mode(Authentication authentication, @Valid @RequestBody SetModeRequest request) {
        return clockingService.setMode(userId(authentication), request.mode());
    }

    private static long userId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
