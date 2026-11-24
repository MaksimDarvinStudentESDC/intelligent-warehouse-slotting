package com.warehouse.slotting.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank
        @Size(max = 64)
        String login,

        @NotBlank
        @Size(max = 72)
        String password

) {}