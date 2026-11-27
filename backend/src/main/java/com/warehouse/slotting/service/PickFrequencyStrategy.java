package com.warehouse.slotting.service;

import com.warehouse.slotting.dto.RecommendationItemResponse;
import com.warehouse.slotting.entity.AccessTier;
import com.warehouse.slotting.entity.Batch;
import com.warehouse.slotting.entity.Location;
import com.warehouse.slotting.entity.OutboundFlow;
import com.warehouse.slotting.repository.LatestProductClassificationRepository;
import com.warehouse.slotting.repository.LatestProductClassificationRepository.ProductClasses;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.warehouse.slotting.service.RankingStrategy.distance;

@Component
public class PickFrequencyStrategy implements RankingStrategy {

    public static final String KEY = "pick_frequency";

    private final LatestProductClassificationRepository classification;

    public PickFrequencyStrategy(
            LatestProductClassificationRepository classification
    ) {
        this.classification = classification;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<RecommendationItemResponse> rank(
            Batch batch,
            List<Location> feasible,
            int topN
    ) {
        if (feasible.isEmpty() || topN <= 0) {
            return List.of();
        }

        var product = batch.getProduct();
        OutboundFlow flow = product.getOutboundFlow();

        String abc = classification.findLatest(
                        product.getWarehouse().getId(),
                        product.getId()
                )
                .map(ProductClasses::abc)
                .orElse(null);

        Optional<AccessTier> targetTier =
                PickFrequencyZoneResolver.resolveTargetTier(abc);

        List<Location> sorted = feasible.stream()
                .sorted(
                        Comparator.comparingInt(
                                        (Location location) ->
                                                priority(location, flow, targetTier)
                                )
                                .thenComparing(
                                        location -> distance(location, flow)
                                )
                                .thenComparing(Location::getId)
                )
                .toList();

        return IntStream.range(
                        0,
                        Math.min(topN, sorted.size())
                )
                .mapToObj(index -> {
                    Location location = sorted.get(index);

                    return new RecommendationItemResponse(
                            index + 1,
                            location.getId(),
                            location.getCode(),
                            distance(location, flow),
                            explanation(location, flow, abc, targetTier),
                            null,
                            location.getVersion()
                    );
                })
                .toList();
    }

    private int priority(
            Location location,
            OutboundFlow flow,
            Optional<AccessTier> targetTier
    ) {
        if (targetTier.isEmpty()) {
            return 0;
        }

        return PickFrequencyZoneResolver.matchesTargetTier(
                location,
                flow,
                targetTier.get()
        ) ? 0 : 1;
    }

    private String explanation(
            Location location,
            OutboundFlow flow,
            String abc,
            Optional<AccessTier> targetTier
    ) {
        String destination = flow == OutboundFlow.SHIPPING
                ? "shipping"
                : "picking";

        String prefix = "Passes hard constraints; pick frequency baseline";

        if (targetTier.isEmpty()) {
            return prefix
                    + "; ABC classification missing"
                    + "; ranked by distance to "
                    + destination + " (m)";
        }

        AccessTier target = targetTier.get();

        AccessTier actual =
                PickFrequencyZoneResolver.resolveLocationTier(
                        location,
                        flow
                );

        String classificationDetails =
                "; ABC class " + abc.trim().toUpperCase(Locale.ROOT)
                        + "; target tier " + target.name()
                        + "; location tier " + actual.name();

        if (actual == target) {
            return prefix
                    + classificationDetails
                    + "; target zone match"
                    + "; ranked by distance to "
                    + destination + " (m)";
        }

        return prefix
                + classificationDetails
                + "; outside target zone (fallback)"
                + "; ranked by distance to "
                + destination + " (m)";
    }
}