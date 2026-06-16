package com.legalcase.dto.response;

import com.legalcase.entity.User;
import com.legalcase.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String displayName;
    private Role role;  // Only ADMIN or STAFF now
    private boolean isActive;
    private boolean isDeleted;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private String deletedReason;

    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .isActive(user.isActive())
                .isDeleted(user.isDeleted())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .deletedAt(user.getDeletedAt())
                .deletedReason(user.getDeletedReason())
                .build();
    }
}