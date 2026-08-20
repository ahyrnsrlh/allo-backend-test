package com.ahyrnsrlh.splitbill.dto.response;

import java.math.BigDecimal;

public record ShareResponse(
        Long participantId,
        String participantName,
        BigDecimal shareAmount
) {}
