package com.warehouse.slotting.service;

import com.warehouse.slotting.dto.ConstraintCheckResponse;
import com.warehouse.slotting.entity.*;
import com.warehouse.slotting.repository.BatchRepository;
import com.warehouse.slotting.repository.LocationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class HardConstraintsService {
    private final BatchRepository batches;
    private final LocationRepository locations;
    private final JdbcTemplate jdbc;

    public HardConstraintsService(BatchRepository batches, LocationRepository locations, JdbcTemplate jdbc) {
        this.batches = batches;
        this.locations = locations;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ConstraintCheckResponse check(Long batchId, Long locationId) {
        Batch batch = batches.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found"));
        Location location = locations.findById(locationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found"));
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
            Integer conflicts = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM batches existing
                    JOIN products p ON p.id = existing.product_id
                    JOIN compat_conflicts cc ON cc.warehouse_id = existing.warehouse_id
                    WHERE existing.current_location_id = ?
                      AND existing.status = 'placed'
                      AND ((cc.group_a_id = ? AND cc.group_b_id = p.compat_group_id)
                        OR (cc.group_b_id = ? AND cc.group_a_id = p.compat_group_id))
                    """, Integer.class, locationId, group.getId(), group.getId());
            if (conflicts != null && conflicts > 0) reasons.add("INCOMPATIBLE_PRODUCTS");
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
