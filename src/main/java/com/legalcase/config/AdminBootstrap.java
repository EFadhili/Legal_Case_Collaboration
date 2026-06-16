package com.legalcase.config;

import com.legalcase.entity.User;
import com.legalcase.enums.Role;
import com.legalcase.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.default.username:admin}")
    private String defaultAdminUsername;

    @Value("${admin.default.email:admin@legalcase.com}")
    private String defaultAdminEmail;

    @Value("${admin.default.password:Admin@1234}")
    private String defaultAdminPassword;

    @EventListener(ApplicationReadyEvent.class)
    public void createDefaultAdmin() {
        long adminCount = userRepository.countByRoleAndIsDeletedFalse(Role.ADMIN);

        if (adminCount == 0) {
            log.info("No admin users found. Creating default admin...");

            User admin = new User();
            admin.setUsername(defaultAdminUsername);
            admin.setEmail(defaultAdminEmail);
            admin.setPassword(passwordEncoder.encode(defaultAdminPassword));
            admin.setFullName("System Admin");
            admin.setRole(Role.ADMIN);
            admin.setActive(true);
            admin.setDeleted(false);
            admin.setCreatedAt(LocalDateTime.now());
            admin.setUpdatedAt(LocalDateTime.now());

            userRepository.save(admin);

            log.info("============================================");
            log.info("DEFAULT ADMIN CREATED");
            log.info("Username: {}", defaultAdminUsername);
            log.info("Email: {}", defaultAdminEmail);
            log.info("Password: {}", defaultAdminPassword);
            log.info("PLEASE CHANGE THE PASSWORD AFTER FIRST LOGIN!");
            log.info("============================================");
        } else {
            log.info("Admin users already exist. Skipping admin creation.");
        }
    }
}