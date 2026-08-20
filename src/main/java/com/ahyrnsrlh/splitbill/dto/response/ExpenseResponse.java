package com.ahyrnsrlh.splitbill.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ExpenseResponse(
        Long id,
        String description,
        BigDecimal amount,
        String splitType,
        Long paidByParticipantId,
        String paidByParticipantName,
        Instant createdAt,
        List<ShareResponse> shares
) {}
