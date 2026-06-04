package com.legalcase.dto.request;

import lombok.Data;

@Data
public class ReactivateUserRequest {
    private String reason;  // Optional reason for reactivation
}