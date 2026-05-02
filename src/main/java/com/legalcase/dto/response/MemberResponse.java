package com.legalcase.dto.response;

import com.legalcase.entity.CaseMember;
import com.legalcase.enums.CaseMemberRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemberResponse {

    private Long id;
    private Long userId;
    private String userFullName;
    private String userEmail;
    private CaseMemberRole role;
    private String joinedAt;

    public static MemberResponse fromEntity(CaseMember member) {
        return MemberResponse.builder()
                .id(member.getId())
                .userId(member.getUser().getId())
                .userFullName(member.getUser().getFullName())
                .userEmail(member.getUser().getEmail())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt().toString())
                .build();
    }
}