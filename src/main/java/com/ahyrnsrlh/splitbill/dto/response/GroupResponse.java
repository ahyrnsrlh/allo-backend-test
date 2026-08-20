package com.ahyrnsrlh.splitbill.dto.response;

import java.time.Instant;
import java.util.List;

public record GroupResponse(
        Long id,
        String name,
        Instant createdAt,
        List<ParticipantResponse> participants,
        List<ExpenseResponse> expenses
) {}
