package com.legalcase.dto.request;

import com.legalcase.enums.CaseMemberRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AddMembersRequest {

    @NotEmpty(message = "At least one identifier is required")
    private List<String> identifiers;  // Changed from "emails" to "identifiers"

    @NotNull(message = "Role is required")
    private CaseMemberRole role;
}

