package com.warehouse.slotting.service;

import com.warehouse.slotting.dto.ConstraintCheckResponse;
import com.warehouse.slotting.dto.RecommendationItemResponse;
import com.warehouse.slotting.dto.RecommendationResponse;
import com.warehouse.slotting.entity.Batch;
import com.warehouse.slotting.entity.Location;
import com.warehouse.slotting.entity.OutboundFlow;
import com.warehouse.slotting.repository.BatchRepository;
import com.warehouse.slotting.repository.LocationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class RecommendationService {
    private final BatchRepository batches;
    private final LocationRepository locations;
    private final HardConstraintsService constraints;

    public RecommendationService(BatchRepository batches, LocationRepository locations,
                                 HardConstraintsService constraints) {
        this.batches = batches;
        this.locations = locations;
        this.constraints = constraints;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Long batchId, int topN) {
        if (topN < 1 || topN > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topN must be between 1 and 100");
        }
        Batch batch = batches.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found"));
        Long warehouseId = batch.getProduct().getWarehouse().getId();
        OutboundFlow flow = batch.getProduct().getOutboundFlow();

        List<Location> feasible = locations.findByWarehouseIdAndIsActiveTrue(warehouseId).stream()
                .filter(location -> {
                    ConstraintCheckResponse check = constraints.check(batchId, location.getId());
                    return check.feasible();
                })
                .sorted(Comparator.comparing((Location location) -> distance(location, flow))
                        .thenComparing(Location::getCode)
                        .thenComparing(Location::getId))
                .toList();

        List<RecommendationItemResponse> ranked = java.util.stream.IntStream
                .range(0, Math.min(topN, feasible.size()))
                .mapToObj(index -> {
                    Location location = feasible.get(index);
                    BigDecimal meters = distance(location, flow);
                    return new RecommendationItemResponse(index + 1, location.getId(),
                            location.getCode(), meters,
                            "Passes hard constraints; nearest feasible location by distance to "
                                    + (flow == OutboundFlow.SHIPPING ? "shipping" : "picking") + " (m)");
                }).toList();
        return new RecommendationResponse(batchId, "nearest_feasible", feasible.size(), ranked);
    }

    private static BigDecimal distance(Location location, OutboundFlow flow) {
        return flow == OutboundFlow.SHIPPING
                ? location.getDistanceToShippingM()
                : location.getDistanceToPickingM();
    }
}
