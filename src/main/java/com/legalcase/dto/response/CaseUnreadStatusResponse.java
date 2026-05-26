package com.legalcase.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CaseUnreadStatusResponse {
    private Long totalUnread;
    private List<CaseUnreadInfo> casesWithUnread;
    private List<CaseUnreadInfo> allCases;
}

