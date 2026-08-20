package com.ahyrnsrlh.splitbill.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateGroupRequest(
        @NotBlank(message = "Group name must not be blank")
        String name,

        @NotEmpty(message = "Participant list must not be empty")
        @Valid
        List<ParticipantRequest> participants
) {}
