package com.warehouse.slotting.service;

import com.warehouse.slotting.dto.RecommendationItemResponse;
import com.warehouse.slotting.entity.*;
import com.warehouse.slotting.repository.LatestProductClassificationRepository;
import com.warehouse.slotting.repository.LatestProductClassificationRepository.ProductClasses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Compares two ranking strategies on identical pre-filtered feasible candidates. */
class StrategyComparisonTest {
    private NearestFeasibleStrategy nearest;
    private WeightedScoreStrategy weighted;
    private LatestProductClassificationRepository classifications;
    private Batch batch;
    private Product product;

    @BeforeEach
    void setUp() {
        nearest = new NearestFeasibleStrategy();
        classifications = mock(LatestProductClassificationRepository.class);
        weighted = new WeightedScoreStrategy(classifications);
        batch = mock(Batch.class);
        product = mock(Product.class);
        Warehouse warehouse = mock(Warehouse.class);
        when(batch.getProduct()).thenReturn(product);
        when(product.getWarehouse()).thenReturn(warehouse);
        when(product.getOutboundFlow()).thenReturn(OutboundFlow.PICKING);
        when(batch.getVolumeDm3()).thenReturn(new BigDecimal("10"));
        when(classifications.findLatest(any(), any()))
                .thenReturn(Optional.of(new ProductClasses("C", "X")));
    }

    private static Location location(long id, String code, String picking,
                                     String shipping, String capacity, String occupied) {
        Location l = mock(Location.class);
        lenient().when(l.getId()).thenReturn(id);
        lenient().when(l.getCode()).thenReturn(code);
        lenient().when(l.getDistanceToPickingM()).thenReturn(new BigDecimal(picking));
        lenient().when(l.getDistanceToShippingM()).thenReturn(new BigDecimal(shipping));
        lenient().when(l.getVolumeCapacityDm3()).thenReturn(new BigDecimal(capacity));
        lenient().when(l.getOccupiedVolumeDm3()).thenReturn(new BigDecimal(occupied));
        return l;
    }

    private static List<Long> ids(List<RecommendationItemResponse> items) {
        return items.stream().map(RecommendationItemResponse::locationId).toList();
    }

    @Test
    void sameFeasibleLocationsCanProduceDifferentFirstRecommendations() {
        // C-class product: intermediate distance and tighter volume fit can beat nearest.
        var a01 = location(1, "A-01", "10", "10", "100", "0"); // free 100, fit 0.1
        var a02 = location(2, "A-02", "25", "25", "10", "0");  // free 10, fit 1.0
        var a03 = location(3, "A-03", "40", "40", "100", "0");
        var candidates = List.of(a03, a02, a01);

        var nearestResults = nearest.rank(batch, candidates, 3);
        var weightedResults = weighted.rank(batch, candidates, 3);

        assertEquals(1L, nearestResults.getFirst().locationId());
        assertEquals(2L, weightedResults.getFirst().locationId());
        assertNotEquals(nearestResults.getFirst().locationId(), weightedResults.getFirst().locationId());
        assertTrue(weightedResults.getFirst().scoreBreakdown().total()
                > weightedResults.get(1).scoreBreakdown().total());
        assertNull(nearestResults.getFirst().scoreBreakdown());
        assertNotNull(weightedResults.getFirst().scoreBreakdown());
    }

    @Test
    void bothStrategiesRespectTopNAndReturnOnlyInputCandidates() {
        var candidates = List.of(
                location(1, "A", "10", "10", "100", "0"),
                location(2, "B", "25", "25", "10", "0"),
                location(3, "C", "40", "40", "100", "0"));
        for (RankingStrategy strategy : List.of(nearest, weighted)) {
            var result = strategy.rank(batch, candidates, 2);
            assertEquals(2, result.size(), strategy.key());
            assertEquals(List.of(1, 2), result.stream().map(RecommendationItemResponse::rank).toList());
            assertTrue(List.of(1L, 2L, 3L).containsAll(ids(result)));
            assertEquals(2, ids(result).stream().distinct().count());
        }
    }

    @Test
    void identicalDistancesAndVolumeFitsProduceConsistentOrder() {
        var first = location(1, "A", "20", "20", "20", "0");
        var second = location(2, "B", "20", "20", "20", "0");
        var candidates = List.of(second, first);
        assertEquals(List.of(1L, 2L), ids(nearest.rank(batch, candidates, 5)));
        assertEquals(List.of(1L, 2L), ids(weighted.rank(batch, candidates, 5)));
    }

    @Test
    void bothStrategiesSwitchToShippingDistancesForShippingFlow() {
        when(product.getOutboundFlow()).thenReturn(OutboundFlow.SHIPPING);
        var a = location(1, "A", "5", "60", "20", "0");
        var b = location(2, "B", "60", "5", "20", "0");
        var candidates = List.of(a, b);
        assertEquals(2L, nearest.rank(batch, candidates, 1).getFirst().locationId());
        assertEquals(2L, weighted.rank(batch, candidates, 1).getFirst().locationId());
        assertEquals(0, new BigDecimal("5").compareTo(
                weighted.rank(batch, candidates, 1).getFirst().distanceMeters()));
    }

    @Test
    void bothStrategiesReturnEmptyForNoFeasibleCandidates() {
        assertTrue(nearest.rank(batch, List.of(), 5).isEmpty());
        assertTrue(weighted.rank(batch, List.of(), 5).isEmpty());
    }
}
