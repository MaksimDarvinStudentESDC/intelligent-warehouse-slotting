package com.warehouse.slotting.service;

import com.warehouse.slotting.exception.UseCaseException;
import com.warehouse.slotting.dto.ConstraintCheckResponse;
import com.warehouse.slotting.entity.*;
import com.warehouse.slotting.repository.BatchRepository;
import com.warehouse.slotting.repository.CompatibilityConflictRepository;
import com.warehouse.slotting.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class HardConstraintsService {
    private final BatchRepository batches;
    private final LocationRepository locations;
    private final CompatibilityConflictRepository compatibility;

    public HardConstraintsService(BatchRepository batches, LocationRepository locations, CompatibilityConflictRepository compatibility) {
        this.batches = batches;
        this.locations = locations;
        this.compatibility = compatibility;
    }

    @Transactional(readOnly = true)
    public ConstraintCheckResponse check(Long batchId, Long locationId) {
        Batch batch = batches.findById(batchId)
                .orElseThrow(() -> new UseCaseException(UseCaseException.Kind.NOT_FOUND, "Batch not found"));
        Location location = locations.findById(locationId)
                .orElseThrow(() -> new UseCaseException(UseCaseException.Kind.NOT_FOUND, "Location not found"));
        List<String> reasons = new ArrayList<>();

        if (!batch.getProduct().getWarehouse().getId().equals(location.getWarehouse().getId())) {
            reasons.add("WAREHOUSE_MISMATCH");
            return result(batchId, location, reasons);
        }
        if (batch.getStatus() != BatchStatus.AWAITING_PUTAWAY) reasons.add("BATCH_NOT_AWAITING_PUTAWAY");
        if (!Boolean.TRUE.equals(location.getIsActive())) reasons.add("LOCATION_INACTIVE");
        if (location.getZoneType() != ZoneType.STORAGE) reasons.add("NOT_STORAGE_LOCATION");
        if (location.getVolumeCapacityDm3().subtract(location.getOccupiedVolumeDm3())
                .compareTo(batch.getVolumeDm3()) < 0) reasons.add("INSUFFICIENT_VOLUME");
        if (location.getMaxWeightKg().subtract(location.getOccupiedWeightKg())
                .compareTo(batch.getWeightKg()) < 0) reasons.add("INSUFFICIENT_WEIGHT_CAPACITY");

        Product product = batch.getProduct();
        // One item must fit the opening of the location. Allow horizontal rotation.
        boolean fits = fits(product.getLengthMm(), product.getWidthMm(), product.getHeightMm(), location);
        if (!fits) reasons.add("DIMENSIONS_EXCEEDED");

        CompatibilityGroup group = product.getCompatibilityGroup();
        if (group != null) {
            if (compatibility.hasConflict(locationId, group.getId())) reasons.add("INCOMPATIBLE_PRODUCTS");
        }
        return result(batchId, location, reasons);
    }

    private static boolean fits(int length, int width, int height, Location location) {
        return height <= location.getMaxHeightMm() &&
                ((length <= location.getMaxLengthMm() && width <= location.getMaxWidthMm()) ||
                 (width <= location.getMaxLengthMm() && length <= location.getMaxWidthMm()));
    }

    private static ConstraintCheckResponse result(Long batchId, Location location, List<String> reasons) {
        return new ConstraintCheckResponse(batchId, location.getId(), location.getCode(),
                reasons.isEmpty(), List.copyOf(reasons));
    }
}
