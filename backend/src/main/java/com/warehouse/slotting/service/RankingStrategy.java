package com.warehouse.slotting.service;

import com.warehouse.slotting.dto.RecommendationItemResponse;
import com.warehouse.slotting.entity.Batch;
import com.warehouse.slotting.entity.Location;
import com.warehouse.slotting.entity.OutboundFlow;

import java.math.BigDecimal;
import java.util.List;

public interface RankingStrategy {
    String key();

    List<RecommendationItemResponse> rank(Batch batch, List<Location> feasible, int topN);

    static BigDecimal distance(Location location, OutboundFlow flow) {
        return flow == OutboundFlow.SHIPPING
                ? location.getDistanceToShippingM()
                : location.getDistanceToPickingM();
    }
}
