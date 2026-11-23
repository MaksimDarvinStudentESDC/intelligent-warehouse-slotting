package com.warehouse.slotting.service;

import com.warehouse.slotting.exception.UseCaseException;
import com.warehouse.slotting.dto.RecommendationItemResponse;
import com.warehouse.slotting.entity.Batch;
import com.warehouse.slotting.entity.Location;
import com.warehouse.slotting.entity.OutboundFlow;
import com.warehouse.slotting.repository.LatestProductClassificationRepository;
import com.warehouse.slotting.repository.LatestProductClassificationRepository.ProductClasses;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static com.warehouse.slotting.service.RankingStrategy.distance;

@Component
public class WeightedScoreStrategy implements RankingStrategy {
    private final LatestProductClassificationRepository classification;
    private final WeightedScoreCalculator calculator = new WeightedScoreCalculator();

    public WeightedScoreStrategy(LatestProductClassificationRepository classification) {
        this.classification = classification;
    }

    @Override
    public String key() {
        return "weighted_score";
    }

    @Override
    public List<RecommendationItemResponse> rank(Batch batch, List<Location> feasible, int topN) {
        OutboundFlow flow = batch.getProduct().getOutboundFlow();
        LocalDate evaluationDate = LocalDate.now();
        if (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(evaluationDate)) {
            throw new UseCaseException(UseCaseException.Kind.INVALID_INPUT, "Expired batch cannot be ranked");
        }
        // A missing distance or capacity must not be silently replaced with a neutral value.
        List<Location> scorable = feasible.stream()
                .filter(l -> distance(l, flow) != null && distance(l, flow).signum() >= 0)
                .filter(l -> l.getVolumeCapacityDm3() != null && l.getOccupiedVolumeDm3() != null)
                .filter(l -> l.getVolumeCapacityDm3().subtract(l.getOccupiedVolumeDm3()).signum() > 0)
                .toList();
        if (scorable.isEmpty()) {
            return List.of();
        }
        var bounds = WeightedScoreCalculator.bounds(scorable.stream()
                .map(l -> distance(l, flow)).toList());

        var classes = classification.findLatest(
                batch.getProduct().getWarehouse().getId(), batch.getProduct().getId());
        String abc = classes.map(ProductClasses::abc).orElse(null);
        String xyz = classes.map(ProductClasses::xyz).orElse(null);

        record Scored(Location location, WeightedScoreCalculator.ScoreBreakdown score) {}
        List<Scored> sorted = scorable.stream().map(l -> new Scored(l, calculator.calculate(
                distance(l, flow), bounds, abc, xyz, batch.getExpiryDate(), evaluationDate,
                batch.getVolumeDm3(),
                l.getVolumeCapacityDm3().subtract(l.getOccupiedVolumeDm3()))))
                .sorted(Comparator.comparingDouble((Scored s) -> s.score().total()).reversed()
                        .thenComparing(s -> distance(s.location(), flow))
                        .thenComparing(s -> s.location().getId()))
                .toList();
        return IntStream
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
    }
}
