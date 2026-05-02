package com.legalcase.dto.request;

import com.legalcase.enums.CaseMemberRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AddMembersRequest {

    @NotEmpty(message = "At least one email is required")
    private List<String> emails;

    @NotNull(message = "Role is required")
    private CaseMemberRole role;
}