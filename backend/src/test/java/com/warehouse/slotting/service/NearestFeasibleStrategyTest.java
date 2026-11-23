package com.warehouse.slotting.service;

import com.warehouse.slotting.entity.Batch;
import com.warehouse.slotting.entity.Location;
import com.warehouse.slotting.entity.OutboundFlow;
import com.warehouse.slotting.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pure JUnit/Mockito tests; no Spring context, Docker or database. */
class NearestFeasibleStrategyTest {
    private NearestFeasibleStrategy strategy;
    private Batch batch;
    private Product product;

    @BeforeEach
    void setUp() {
        strategy = new NearestFeasibleStrategy();
        batch = mock(Batch.class);
        product = mock(Product.class);
        when(batch.getProduct()).thenReturn(product);
        when(product.getOutboundFlow()).thenReturn(OutboundFlow.PICKING);
    }

    private static Location location(long id, String code, String picking, String shipping) {
        Location location = mock(Location.class);
        when(location.getId()).thenReturn(id);
        when(location.getCode()).thenReturn(code);
        lenient().when(location.getDistanceToPickingM()).thenReturn(new BigDecimal(picking));
        lenient().when(location.getDistanceToShippingM()).thenReturn(new BigDecimal(shipping));
        return location;
    }

    @Test
    void nearestPickingLocationRanksFirstRegardlessOfInputOrder() {
        var far = location(1, "FAR", "100", "1");
        var near = location(2, "NEAR", "5", "999");
        var result = strategy.rank(batch, List.of(far, near), 10);
        assertEquals(List.of(2L, 1L), result.stream().map(r -> r.locationId()).toList());
        assertEquals(0, new BigDecimal("5").compareTo(result.getFirst().distanceMeters()));
    }

    @Test
    void shippingFlowUsesShippingDistanceRatherThanPickingDistance() {
        when(product.getOutboundFlow()).thenReturn(OutboundFlow.SHIPPING);
        var nearPicking = location(1, "PICK", "1", "90");
        var nearShipping = location(2, "SHIP", "90", "2");
        var result = strategy.rank(batch, List.of(nearPicking, nearShipping), 10);
        assertEquals(2L, result.getFirst().locationId());
        assertEquals(0, new BigDecimal("2").compareTo(result.getFirst().distanceMeters()));
        assertTrue(result.getFirst().explanation().contains("shipping"));
    }

    @Test
    void equalDistanceUsesLocationCodeAsTieBreaker() {
        var z = location(1, "Z-01", "10", "10");
        var a = location(2, "A-01", "10", "10");
        var result = strategy.rank(batch, List.of(z, a), 10);
        assertEquals(List.of("A-01", "Z-01"), result.stream().map(r -> r.locationCode()).toList());
    }

    @Test
    void equalDistanceAndCodeUsesIdAsTieBreaker() {
        var higher = location(9, "SAME", "10", "10");
        var lower = location(3, "SAME", "10", "10");
        var result = strategy.rank(batch, List.of(higher, lower), 10);
        assertEquals(List.of(3L, 9L), result.stream().map(r -> r.locationId()).toList());
    }

    @Test
    void topNLimitsResultsAndRankNumbersAreSequential() {
        var result = strategy.rank(batch, List.of(
                location(3, "C", "30", "30"),
                location(1, "A", "10", "10"),
                location(2, "B", "20", "20")), 2);
        assertEquals(2, result.size());
        assertEquals(List.of(1L, 2L), result.stream().map(r -> r.locationId()).toList());
        assertEquals(List.of(1, 2), result.stream().map(r -> r.rank()).toList());
    }

    @Test
    void emptyCandidatesReturnEmptyRecommendations() {
        assertTrue(strategy.rank(batch, List.of(), 5).isEmpty());
    }

    @Test
    void keyAndExplanationIdentifyNearestFeasiblePickingStrategy() {
        assertEquals(NearestFeasibleStrategy.KEY, strategy.key());
        assertEquals("nearest_feasible", strategy.key());
        var result = strategy.rank(batch, List.of(location(5, "P-05", "12.5", "50")), 5).getFirst();
        assertEquals(5L, result.locationId());
        assertEquals("P-05", result.locationCode());
        assertTrue(result.explanation().contains("hard constraints"));
        assertTrue(result.explanation().contains("picking"));
        assertNull(result.scoreBreakdown());
    }
}
