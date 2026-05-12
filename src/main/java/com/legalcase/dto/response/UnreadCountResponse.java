package com.legalcase.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class UnreadCountResponse {
    private Long totalUnread;
    private Map<Long, Long> unreadByCase;
}


