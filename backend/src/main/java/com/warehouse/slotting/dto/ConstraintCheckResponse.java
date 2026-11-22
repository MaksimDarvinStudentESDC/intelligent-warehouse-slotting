package com.warehouse.slotting.dto;

import java.util.List;

public record ConstraintCheckResponse(
        Long batchId,
        Long locationId,
        String locationCode,
        boolean feasible,
        List<String> rejectionReasons
) {}
