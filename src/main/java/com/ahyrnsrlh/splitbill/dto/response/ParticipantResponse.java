package com.ahyrnsrlh.splitbill.dto.response;

import java.time.Instant;

public record ParticipantResponse(
        Long id,
        String name,
        Instant createdAt
) {}
