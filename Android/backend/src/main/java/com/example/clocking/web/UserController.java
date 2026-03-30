package com.example.clocking.web;

import com.example.clocking.domain.User;
import com.example.clocking.repository.UserRepository;
import com.example.clocking.web.dto.UserDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        long userId = Long.parseLong(authentication.getName());
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new UserDto(user.getId(), user.getName(), user.getEmail());
    }
}
