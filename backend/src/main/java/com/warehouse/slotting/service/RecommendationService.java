package com.warehouse.slotting.service;

import com.warehouse.slotting.exception.UseCaseException;
import com.warehouse.slotting.dto.ConstraintCheckResponse;
import com.warehouse.slotting.dto.RecommendationResponse;
import com.warehouse.slotting.entity.Batch;
import com.warehouse.slotting.entity.Location;
import com.warehouse.slotting.repository.BatchRepository;
import com.warehouse.slotting.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private final BatchRepository batches;
    private final LocationRepository locations;
    private final HardConstraintsService constraints;
    private final Map<String, RankingStrategy> strategies;

    public RecommendationService(BatchRepository batches, LocationRepository locations,
                                 HardConstraintsService constraints, List<RankingStrategy> strategies) {
        this.batches = batches;
        this.locations = locations;
        this.constraints = constraints;
        this.strategies = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(RankingStrategy::key, Function.identity()));
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Long batchId, int topN) {
        return recommend(batchId, topN, NearestFeasibleStrategy.KEY);
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Long batchId, int topN, String strategy) {
        if (topN < 1 || topN > 100) {
            throw new UseCaseException(UseCaseException.Kind.INVALID_INPUT, "topN must be between 1 and 100");
        }
        RankingStrategy ranking = strategy == null ? null : strategies.get(strategy);
        if (ranking == null) {
            throw new UseCaseException(UseCaseException.Kind.INVALID_INPUT, "Unsupported strategy: " + strategy);
        }
        Batch batch = batches.findById(batchId)
                .orElseThrow(() -> new UseCaseException(UseCaseException.Kind.NOT_FOUND, "Batch not found"));
        Long warehouseId = batch.getProduct().getWarehouse().getId();

        List<Location> feasible = locations.findByWarehouseIdAndIsActiveTrue(warehouseId).stream()
                .filter(location -> {
                    ConstraintCheckResponse check = constraints.check(batchId, location.getId());
                    return check.feasible();
                }).toList();

        return new RecommendationResponse(batchId, strategy, feasible.size(),
                ranking.rank(batch, feasible, topN));
    }
}
