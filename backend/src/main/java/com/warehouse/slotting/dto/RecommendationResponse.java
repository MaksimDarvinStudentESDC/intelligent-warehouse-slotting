package com.warehouse.slotting.dto;

import java.util.List;

public record RecommendationResponse(
        Long batchId,
        String strategy,
        int feasibleCount,
        List<RecommendationItemResponse> recommendations
) {}
