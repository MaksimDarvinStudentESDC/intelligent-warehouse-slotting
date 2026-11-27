package com.warehouse.slotting.service;

import com.warehouse.slotting.dto.RecommendationItemResponse;
import com.warehouse.slotting.entity.Batch;
import com.warehouse.slotting.entity.Location;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NearestFeasibleStrategyTest {
    private final NearestFeasibleStrategy strategy = new NearestFeasibleStrategy();
    private final Batch batch = mock(Batch.class);

    private static Location location(long id, String code, String receiving) {
        Location l = mock(Location.class);
        when(l.getId()).thenReturn(id);
        when(l.getCode()).thenReturn(code);
        when(l.getDistanceToReceivingM()).thenReturn(new BigDecimal(receiving));
        when(l.getVersion()).thenReturn(7L);
        return l;
    }

    @Test
    void ranksByReceivingDistanceAndIgnoresOutboundDistances() {
        var a = location(1, "A", "20");
        var b = location(2, "B", "5");
        when(a.getDistanceToPickingM()).thenReturn(BigDecimal.ONE);
        when(a.getDistanceToShippingM()).thenReturn(BigDecimal.ONE);
        when(b.getDistanceToPickingM()).thenReturn(BigDecimal.TEN);
        when(b.getDistanceToShippingM()).thenReturn(BigDecimal.TEN);
        var result = strategy.rank(batch, List.of(a, b), 5);
        assertEquals(List.of(2L, 1L), result.stream().map(RecommendationItemResponse::locationId).toList());
        assertEquals(0, new BigDecimal("5").compareTo(result.getFirst().distanceMeters()));
        verifyNoInteractions(batch);
        verify(a, never()).getDistanceToPickingM();
        verify(a, never()).getDistanceToShippingM();
        verify(b, never()).getDistanceToPickingM();
        verify(b, never()).getDistanceToShippingM();
    }

    @Test
    void equalDistanceUsesIdNotCodeOrInputOrder() {
        var a = location(9, "A", "10.00");
        var z = location(3, "Z", "10");
        assertEquals(List.of(3L, 9L), strategy.rank(batch, List.of(a, z), 5)
                .stream().map(RecommendationItemResponse::locationId).toList());
    }

    @Test
    void topNLimitsResultsAndRanksAreSequential() {
        var result = strategy.rank(batch, List.of(location(3, "C", "30"),
                location(1, "A", "10"), location(2, "B", "20")), 2);
        assertEquals(List.of(1L, 2L), result.stream().map(RecommendationItemResponse::locationId).toList());
        assertEquals(List.of(1, 2), result.stream().map(RecommendationItemResponse::rank).toList());
    }

    @Test
    void emptyCandidatesReturnEmptyRecommendations() {
        assertTrue(strategy.rank(batch, List.of(), 5).isEmpty());
    }

    @Test
    void preservesResponseContract() {
        assertEquals("nearest_feasible", strategy.key());
        var result = strategy.rank(batch, List.of(location(5, "ST-05", "12.5")), 5).getFirst();
        assertEquals(5L, result.locationId());
        assertEquals("ST-05", result.locationCode());
        assertEquals(7L, result.locationVersion());
        assertTrue(result.explanation().contains("hard constraints"));
        assertTrue(result.explanation().contains("receiving"));
        assertNull(result.scoreBreakdown());
    }
}
