package com.warehouse.slotting.service;

import com.warehouse.slotting.dto.StockMovementResponse;
import com.warehouse.slotting.exception.UseCaseException;
import com.warehouse.slotting.repository.StockMovementRepository;
import com.warehouse.slotting.repository.WarehouseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class StockMovementService {
    private final StockMovementRepository movements;
    private final WarehouseRepository warehouses;

    public StockMovementService(StockMovementRepository movements, WarehouseRepository warehouses) {
        this.movements = movements;
        this.warehouses = warehouses;
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> history(Long warehouseId, Long productId, Long locationId,
                                               OffsetDateTime from, OffsetDateTime to, int page, int size) {
        if (warehouseId == null || warehouseId <= 0 || (productId != null && productId <= 0)
                || (locationId != null && locationId <= 0) || page < 0 || size < 1 || size > 100
                || (from != null && to != null && !from.isBefore(to))) {
            throw new UseCaseException(UseCaseException.Kind.INVALID_INPUT, "Invalid movement history filters");
        }
        if (!warehouses.existsById(warehouseId)) {
            throw new UseCaseException(UseCaseException.Kind.NOT_FOUND, "Warehouse not found");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "performedAt", "id"));
        return movements.findHistory(warehouseId, productId, locationId, from, to, pageable)
                .map(StockMovementResponse::from);
    }
}
