package com.warehouse.slotting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record PlacementRequest(
        @NotNull @Positive Long batchId,
        @NotNull @Positive Long locationId,
        @NotNull @PositiveOrZero Long locationVersion
) {}
