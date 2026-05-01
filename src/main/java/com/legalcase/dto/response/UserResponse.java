package com.legalcase.dto.response;

import com.legalcase.entity.User;
import com.legalcase.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO for sending user data to clients.
 * Notice: NO password field!
 */
@Data
@Builder
public class UserResponse {

    private Long id;
    private String email;
    private String fullName;
    private Role role;
    private boolean isActive;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Convert User entity to UserResponse DTO.
     * This hides sensitive fields like password.
     */
    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .isActive(user.isActive())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}