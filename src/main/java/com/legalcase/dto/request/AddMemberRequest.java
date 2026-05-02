package com.legalcase.dto.request;

import com.legalcase.enums.CaseMemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddMemberRequest {

    @NotBlank(message = "Username or email is required")
    private String identifier;  // Can be username OR email


    @NotNull(message = "Role is required")
    private CaseMemberRole role;
}



