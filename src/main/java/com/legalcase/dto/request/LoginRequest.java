package com.legalcase.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Username or email is required")
    private String identifier;  // Can be username OR email

    @NotBlank(message = "Password is required")
    private String password;
}