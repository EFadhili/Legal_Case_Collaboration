package com.legalcase.dto.request;

import com.legalcase.enums.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * DTO for user registration requests.
 * Contains validation annotations that Spring automatically checks.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address (e.g., user@example.com)")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$",
            message = "Password must be at least 8 characters long and contain: " +
                    "at least 1 digit, 1 lowercase letter, 1 uppercase letter, " +
                    "and 1 special character (@#$%^&+=!)"
    )
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters")
    @Pattern(regexp = "^[a-zA-Z\\s'-]+$", message = "Full name can only contain letters, spaces, apostrophes, and hyphens")
    private String fullName;

    // Optional: If not provided, defaults to STAFF in service
    private Role role;
}