package com.ahyrnsrlh.splitbill.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ShareRequest(
        @NotNull(message = "Participant ID is required in share")
        Long participantId,

        /**
         * Used for EXACT split: the monetary amount this participant owes.
         * Null for EQUAL and PERCENTAGE splits.
         */
        BigDecimal amount,

        /**
         * Used for PERCENTAGE split: the percentage (0–100) this participant bears.
         * Null for EQUAL and EXACT splits.
         */
        BigDecimal percentage
) {}
