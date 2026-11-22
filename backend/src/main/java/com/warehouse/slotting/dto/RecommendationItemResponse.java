package com.warehouse.slotting.dto;

import java.math.BigDecimal;

public record RecommendationItemResponse(
        int rank,
        Long locationId,
        String locationCode,
        BigDecimal distanceMeters,
        String explanation
) {}
