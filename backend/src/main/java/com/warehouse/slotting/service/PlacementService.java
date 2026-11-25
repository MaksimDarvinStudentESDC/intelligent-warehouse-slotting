package com.warehouse.slotting.service;

import com.warehouse.slotting.dto.PlacementRequest;
import com.warehouse.slotting.dto.PlacementResponse;
import com.warehouse.slotting.entity.BatchStatus;
import com.warehouse.slotting.entity.StockMovement;
import com.warehouse.slotting.exception.UseCaseException;
import com.warehouse.slotting.repository.BatchRepository;
import com.warehouse.slotting.repository.LocationRepository;
import com.warehouse.slotting.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
public class PlacementService {
    private final BatchRepository batches;
    private final LocationRepository locations;
    private final StockMovementRepository movements;
    private final HardConstraintsService constraints;

    public PlacementService(BatchRepository batches, LocationRepository locations,
                            StockMovementRepository movements, HardConstraintsService constraints) {
        this.batches = batches;
        this.locations = locations;
        this.movements = movements;
        this.constraints = constraints;
    }

    @Transactional
    public PlacementResponse confirm(PlacementRequest request, Long operatorId) {
        if (request == null || request.batchId() == null || request.batchId() <= 0
                || request.locationId() == null || request.locationId() <= 0
                || request.locationVersion() == null || request.locationVersion() < 0
                || operatorId == null || operatorId <= 0) {
            throw new UseCaseException(UseCaseException.Kind.INVALID_INPUT, "Invalid placement request");
        }
        var batch = batches.findByIdForUpdate(request.batchId())
                .orElseThrow(() -> new UseCaseException(UseCaseException.Kind.NOT_FOUND, "Batch not found"));
        if (batch.getStatus() != BatchStatus.AWAITING_PUTAWAY
                || batch.getCurrentLocation() != null || batch.getPlacedAt() != null) {
            throw conflict("BATCH_NOT_AWAITING_PUTAWAY");
        }
        var location = locations.findByIdForUpdate(request.locationId())
                .orElseThrow(() -> new UseCaseException(UseCaseException.Kind.NOT_FOUND, "Location not found"));
        if (!Objects.equals(batch.getWarehouse().getId(), location.getWarehouse().getId())) {
            throw conflict("WAREHOUSE_MISMATCH");
        }
        if (!Objects.equals(location.getVersion(), request.locationVersion())) {
            throw conflict("STALE_RECOMMENDATION");
        }
        var check = constraints.check(batch.getId(), location.getId());
        if (!check.feasible()) {
            throw conflict(String.join(", ", check.rejectionReasons()));
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        location.occupy(batch.getVolumeDm3(), batch.getWeightKg());
        batch.placeAt(location, now);
        // Flush managed entity updates before inserting the movement audit record.
        locations.flush();
        var movement = movements.saveAndFlush(StockMovement.putaway(batch, location, operatorId, now));
        return new PlacementResponse(movement.getId(), batch.getId(), location.getId(),
                location.getCode(), "placed", location.getOccupiedVolumeDm3(),
                location.getOccupiedWeightKg(), now);
    }

    private static UseCaseException conflict(String reason) {
        return new UseCaseException(UseCaseException.Kind.CONFLICT, reason);
    }
}
