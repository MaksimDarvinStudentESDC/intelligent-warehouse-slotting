package com.warehouse.slotting.dto;

import com.warehouse.slotting.service.WeightedScoreCalculator.ScoreBreakdown;
import java.math.BigDecimal;

public record RecommendationItemResponse(
        int rank, Long locationId, String locationCode,
        BigDecimal distanceMeters, String explanation,
        ScoreBreakdown scoreBreakdown) {
    public RecommendationItemResponse(int rank, Long locationId, String locationCode,
                                      BigDecimal distanceMeters, String explanation) {
        this(rank, locationId, locationCode, distanceMeters, explanation, null);
    }
}
