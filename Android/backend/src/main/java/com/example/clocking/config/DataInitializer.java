package com.example.clocking.config;

import com.example.clocking.domain.User;
import com.example.clocking.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final String DEMO_EMAIL = "test@demo.com";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            return;
        }
        User user = new User();
        user.setName("Usuario demo");
        user.setEmail(DEMO_EMAIL);
        user.setPasswordHash(passwordEncoder.encode("1234"));
        userRepository.save(user);
    }
}
