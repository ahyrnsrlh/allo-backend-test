package com.ahyrnsrlh.splitbill.dto.response;

import java.math.BigDecimal;

public record TransferResponse(
        Long fromParticipantId,
        String fromParticipantName,
        Long toParticipantId,
        String toParticipantName,
        BigDecimal amount
) {}
