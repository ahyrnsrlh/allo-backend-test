package com.ahyrnsrlh.splitbill.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ParticipantRequest(
        @NotBlank(message = "Participant name must not be blank")
        String name
) {}
