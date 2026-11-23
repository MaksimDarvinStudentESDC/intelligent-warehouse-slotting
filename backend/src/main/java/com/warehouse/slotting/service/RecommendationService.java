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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class RecommendationService {
    private final BatchRepository batches;
    private final LocationRepository locations;
    private final HardConstraintsService constraints;
    private final JdbcTemplate jdbc;
    private final WeightedScoreCalculator calculator = new WeightedScoreCalculator();

    public RecommendationService(BatchRepository batches, LocationRepository locations,
                                 HardConstraintsService constraints, JdbcTemplate jdbc) {
        this.batches = batches;
        this.locations = locations;
        this.constraints = constraints;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Long batchId, int topN) {
        return recommend(batchId, topN, "nearest_feasible");
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Long batchId, int topN, String strategy) {
        if (topN < 1 || topN > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topN must be between 1 and 100");
        }
        if (!"nearest_feasible".equals(strategy) && !"weighted_score".equals(strategy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported strategy: " + strategy);
        }
        Batch batch = batches.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found"));
        Long warehouseId = batch.getProduct().getWarehouse().getId();
        OutboundFlow flow = batch.getProduct().getOutboundFlow();

        List<Location> feasible = locations.findByWarehouseIdAndIsActiveTrue(warehouseId).stream()
                .filter(location -> {
                    ConstraintCheckResponse check = constraints.check(batchId, location.getId());
                    return check.feasible();
                }).toList();

        if ("nearest_feasible".equals(strategy)) {
            List<Location> sorted = feasible.stream()
                    .sorted(Comparator.comparing((Location l) -> distance(l, flow))
                            .thenComparing(Location::getCode).thenComparing(Location::getId))
                    .toList();
            List<RecommendationItemResponse> ranked = java.util.stream.IntStream
                    .range(0, Math.min(topN, sorted.size()))
                    .mapToObj(index -> {
                        Location location = sorted.get(index);
                        return new RecommendationItemResponse(index + 1, location.getId(),
                                location.getCode(), distance(location, flow),
                                "Passes hard constraints; nearest feasible location by distance to "
                                        + (flow == OutboundFlow.SHIPPING ? "shipping" : "picking") + " (m)");
                    }).toList();
            return new RecommendationResponse(batchId, strategy, feasible.size(), ranked);
        }

        if (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expired batch cannot be ranked");
        }
        // A missing distance or capacity must not be silently replaced with a neutral value.
        List<Location> scorable = feasible.stream()
                .filter(l -> distance(l, flow) != null && distance(l, flow).signum() >= 0)
                .filter(l -> l.getVolumeCapacityDm3() != null && l.getOccupiedVolumeDm3() != null)
                .filter(l -> l.getVolumeCapacityDm3().subtract(l.getOccupiedVolumeDm3()).signum() > 0)
                .toList();
        if (scorable.isEmpty()) {
            return new RecommendationResponse(batchId, strategy, feasible.size(), List.of());
        }
        var bounds = WeightedScoreCalculator.bounds(scorable.stream()
                .map(l -> distance(l, flow)).toList());

        // The latest run for this warehouse, not an unrelated run for another warehouse.
        List<String[]> classes = jdbc.query("""
                SELECT pc.abc_class::text, pc.xyz_class::text
                FROM product_classes pc
                JOIN classification_runs cr ON cr.id = pc.classification_run_id
                WHERE cr.warehouse_id = ? AND pc.product_id = ?
                ORDER BY cr.calculated_at DESC, cr.id DESC
                LIMIT 1
                """, (rs, rowNum) -> new String[] {
                    rs.getString(1), rs.getString(2)
                }, warehouseId, batch.getProduct().getId());
        String abc = classes.isEmpty() ? null : classes.getFirst()[0];
        String xyz = classes.isEmpty() ? null : classes.getFirst()[1];
        LocalDate evaluationDate = LocalDate.now();

        record Scored(Location location, WeightedScoreCalculator.ScoreBreakdown score) {}
        List<Scored> sorted = scorable.stream().map(l -> new Scored(l, calculator.calculate(
                distance(l, flow), bounds, abc, xyz, batch.getExpiryDate(), evaluationDate,
                batch.getVolumeDm3(),
                l.getVolumeCapacityDm3().subtract(l.getOccupiedVolumeDm3()))))
                .sorted(Comparator.comparingDouble((Scored s) -> s.score().total()).reversed()
                        .thenComparing(s -> distance(s.location(), flow))
                        .thenComparing(s -> s.location().getId()))
                .toList();
        List<RecommendationItemResponse> ranked = java.util.stream.IntStream
                .range(0, Math.min(topN, sorted.size()))
                .mapToObj(index -> {
                    Scored item = sorted.get(index);
                    var s = item.score();
                    String explanation = "Passes hard constraints; weighted score uses distance, ABC/XYZ, "
                            + "expiry accessibility and volume fit"
                            + (s.classificationMissing() ? "; classification missing (neutral)" : "")
                            + (s.expiryUnknown() ? "; expiry unknown (neutral)" : "");
                    return new RecommendationItemResponse(index + 1, item.location().getId(),
                            item.location().getCode(), distance(item.location(), flow),
                            explanation, s);
                }).toList();
        return new RecommendationResponse(batchId, strategy, feasible.size(), ranked);
    }

    private static BigDecimal distance(Location location, OutboundFlow flow) {
        return flow == OutboundFlow.SHIPPING
                ? location.getDistanceToShippingM()
                : location.getDistanceToPickingM();
    }
}
