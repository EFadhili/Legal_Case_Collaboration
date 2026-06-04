package com.legalcase.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {

    private String message;
    private boolean success;
    private UserResponse user;
    private String token;

    public static AuthResponse success(String message, UserResponse user) {
        return AuthResponse.builder()
                .message(message)
                .success(true)
                .user(user)
                .build();
    }

    public static AuthResponse successWithToken(String message, UserResponse user, String token) {
        return AuthResponse.builder()
                .message(message)
                .success(true)
                .user(user)
                .token(token)
                .build();
    }

    public static AuthResponse error(String message) {
        return AuthResponse.builder()
                .message(message)
                .success(false)
                .build();
    }
}