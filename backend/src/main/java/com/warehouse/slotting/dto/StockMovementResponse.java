package com.warehouse.slotting.dto;

import com.warehouse.slotting.entity.StockMovement;
import java.time.OffsetDateTime;
import java.util.Locale;

public record StockMovementResponse(
        Long id,
        Long warehouseId,
        Long batchId,
        String batchCode,
        Long productId,
        Long fromLocationId,
        String fromLocationCode,
        Long toLocationId,
        String toLocationCode,
        String movementType,
        Long performedBy,
        OffsetDateTime performedAt
) {
    public static StockMovementResponse from(StockMovement movement) {
        var batch = movement.getBatch();
        var source = movement.getFromLocation();
        var target = movement.getToLocation();
        return new StockMovementResponse(
                movement.getId(), movement.getWarehouse().getId(),
                batch.getId(), batch.getBatchCode(), batch.getProduct().getId(),
                source == null ? null : source.getId(),
                source == null ? null : source.getCode(),
                target.getId(), target.getCode(),
                movement.getMovementType().name().toLowerCase(Locale.ROOT),
                movement.getPerformedBy(), movement.getPerformedAt());
    }
}
