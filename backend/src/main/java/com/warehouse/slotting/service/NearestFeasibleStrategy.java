package com.warehouse.slotting.service;

import com.warehouse.slotting.dto.RecommendationItemResponse;
import com.warehouse.slotting.entity.Batch;
import com.warehouse.slotting.entity.Location;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;


@Component
public class NearestFeasibleStrategy implements RankingStrategy {
    public static final String KEY = "nearest_feasible";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<RecommendationItemResponse> rank(Batch batch, List<Location> feasible, int topN) {
        List<Location> sorted = feasible.stream()
                .sorted(Comparator.comparing(Location::getDistanceToReceivingM)
                        .thenComparing(Location::getId))
                .toList();
        return IntStream
                .range(0, Math.min(topN, sorted.size()))
                .mapToObj(index -> {
                    Location location = sorted.get(index);
                    return new RecommendationItemResponse(index + 1, location.getId(),
                            location.getCode(), location.getDistanceToReceivingM(),
                            "Passes hard constraints; nearest feasible location by distance from receiving (m)", null, location.getVersion());
                }).toList();
    }
}
