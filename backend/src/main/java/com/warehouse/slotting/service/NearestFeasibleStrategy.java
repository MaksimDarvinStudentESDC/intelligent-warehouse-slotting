package com.warehouse.slotting.service;

import com.warehouse.slotting.dto.RecommendationItemResponse;
import com.warehouse.slotting.entity.Batch;
import com.warehouse.slotting.entity.Location;
import com.warehouse.slotting.entity.OutboundFlow;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static com.warehouse.slotting.service.RankingStrategy.distance;

@Component
public class NearestFeasibleStrategy implements RankingStrategy {
    public static final String KEY = "nearest_feasible";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<RecommendationItemResponse> rank(Batch batch, List<Location> feasible, int topN) {
        OutboundFlow flow = batch.getProduct().getOutboundFlow();
        List<Location> sorted = feasible.stream()
                .sorted(Comparator.comparing((Location l) -> distance(l, flow))
                        .thenComparing(Location::getCode).thenComparing(Location::getId))
                .toList();
        return IntStream
                .range(0, Math.min(topN, sorted.size()))
                .mapToObj(index -> {
                    Location location = sorted.get(index);
                    return new RecommendationItemResponse(index + 1, location.getId(),
                            location.getCode(), distance(location, flow),
                            "Passes hard constraints; nearest feasible location by distance to "
                                    + (flow == OutboundFlow.SHIPPING ? "shipping" : "picking") + " (m)", null, location.getVersion());
                }).toList();
    }
}
