package com.legalcase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling  // ← ADD THIS
public class LegalCaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegalCaseApplication.class, args);
    }
}

