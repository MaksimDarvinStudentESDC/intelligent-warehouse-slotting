package com.warehouse.slotting.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PlacementResponse(
        Long movementId,
        Long batchId,
        Long locationId,
        String locationCode,
        String status,
        BigDecimal occupiedVolumeDm3,
        BigDecimal occupiedWeightKg,
        OffsetDateTime placedAt
) {}
