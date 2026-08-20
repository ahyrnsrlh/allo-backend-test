package com.ahyrnsrlh.splitbill.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record CreateExpenseRequest(
        @NotBlank(message = "Expense description must not be blank")
        String description,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotNull(message = "Payer participant ID is required")
        Long paidByParticipantId,

        @NotBlank(message = "Split type is required")
        String splitType,

        @NotEmpty(message = "Shares list must not be empty")
        @Valid
        List<ShareRequest> shares
) {}
