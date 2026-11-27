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
import static org.mockito.Mockito.*;

class PickFrequencyStrategyTest {
    private LatestProductClassificationRepository classification;
    private PickFrequencyStrategy strategy;
    private Batch batch;
    private Product product;

    @BeforeEach void setUp() {
        classification = mock(LatestProductClassificationRepository.class);
        strategy = new PickFrequencyStrategy(classification);
        batch = mock(Batch.class);
        product = mock(Product.class);
        Warehouse warehouse = mock(Warehouse.class);
        when(batch.getProduct()).thenReturn(product);
        when(batch.getWarehouse()).thenReturn(warehouse);
        when(product.getWarehouse()).thenReturn(warehouse);
        when(product.getId()).thenReturn(1L);
        when(warehouse.getId()).thenReturn(1L);
        when(product.getOutboundFlow()).thenReturn(OutboundFlow.PICKING);
    }
    private void givenAbcClass(String abc) {
        when(classification.findLatest(1L, 1L)).thenReturn(Optional.of(new ProductClasses(abc, "X")));
    }
    private Location location(long id, AccessTier tier, int picking, int shipping) {
        Location location = mock(Location.class);
        Zone zone = mock(Zone.class);
        when(location.getId()).thenReturn(id);
        when(location.getCode()).thenReturn("ST-" + id);
        when(location.getZone()).thenReturn(zone);
        when(location.getVersion()).thenReturn(0L);
        when(zone.getTierToPicking()).thenReturn(tier);
        when(zone.getTierToShipping()).thenReturn(tier);
        when(location.getDistanceToPickingM()).thenReturn(BigDecimal.valueOf(picking));
        when(location.getDistanceToShippingM()).thenReturn(BigDecimal.valueOf(shipping));
        return location;
    }
    private List<Long> ids(List<RecommendationItemResponse> result) {
        return result.stream().map(RecommendationItemResponse::locationId).toList();
    }
    @Test void shouldReturnCorrectStrategyKey() {
        assertEquals("pick_frequency", strategy.key());
    }
    @Test void shouldPrioritizeTargetTierForClassA() {
        givenAbcClass("A");
        var target = location(1L, AccessTier.FAST, 30, 35);
        var other = location(2L, AccessTier.MIDDLE, 5, 10);
        assertEquals(List.of(1L, 2L), ids(strategy.rank(batch, List.of(other, target), 5)));
    }
    @Test void shouldPrioritizeTargetTierForClassB() {
        givenAbcClass("B");
        var target = location(1L, AccessTier.MIDDLE, 30, 35);
        var other = location(2L, AccessTier.FAST, 5, 10);
        assertEquals(List.of(1L, 2L), ids(strategy.rank(batch, List.of(other, target), 5)));
    }
    @Test void shouldPrioritizeTargetTierForClassC() {
        givenAbcClass("C");
        var target = location(1L, AccessTier.REMAINING, 30, 35);
        var other = location(2L, AccessTier.FAST, 5, 10);
        assertEquals(List.of(1L, 2L), ids(strategy.rank(batch, List.of(other, target), 5)));
    }
    @Test void shouldSortTargetTierLocationsByDistance() {
        givenAbcClass("B");
        assertEquals(List.of(2L, 1L), ids(strategy.rank(batch, List.of(location(1, AccessTier.MIDDLE, 25, 30), location(2, AccessTier.MIDDLE, 15, 20)), 5)));
    }
    @Test void shouldFallbackWhenTargetTierIsUnavailable() {
        givenAbcClass("C");
        var result = strategy.rank(batch, List.of(location(1, AccessTier.MIDDLE, 20, 25), location(2, AccessTier.FAST, 5, 10)), 5);
        assertEquals(List.of(2L, 1L), ids(result));
        assertTrue(result.getFirst().explanation().contains("fallback"));
    }
    @Test void shouldSortByLocationIdWhenDistancesAreEqual() {
        givenAbcClass("A");
        assertEquals(List.of(10L, 20L), ids(strategy.rank(batch, List.of(location(20, AccessTier.FAST, 10, 15), location(10, AccessTier.FAST, 10, 15)), 5)));
    }
    @Test void shouldUseNeutralRankingWhenClassificationIsMissing() {
        when(classification.findLatest(1L, 1L)).thenReturn(Optional.empty());
        var result = strategy.rank(batch, List.of(location(1, AccessTier.FAST, 30, 35), location(2, AccessTier.REMAINING, 5, 10)), 5);
        assertEquals(List.of(2L, 1L), ids(result));
        assertTrue(result.getFirst().explanation().contains("ABC classification missing"));
    }
    @Test void shouldRespectTopNLimit() {
        givenAbcClass("A");
        var result = strategy.rank(batch, List.of(location(3, AccessTier.FAST, 15, 20), location(2, AccessTier.FAST, 10, 15), location(1, AccessTier.FAST, 5, 10)), 2);
        assertEquals(List.of(1L, 2L), ids(result));
        assertEquals(1, result.get(0).rank());
        assertEquals(2, result.get(1).rank());
    }
    @Test void shouldReturnEmptyWhenNoFeasibleLocationsExist() {
        assertTrue(strategy.rank(batch, List.of(), 5).isEmpty());
        verifyNoInteractions(classification);
    }
    @Test void shouldReturnEmptyWhenTopNIsZero() {
        assertTrue(strategy.rank(batch, List.of(mock(Location.class)), 0).isEmpty());
        verifyNoInteractions(classification);
    }
    @Test void shouldUseShippingDistanceAndShippingTier() {
        givenAbcClass("A");
        when(product.getOutboundFlow()).thenReturn(OutboundFlow.SHIPPING);
        var first = location(1, AccessTier.FAST, 5, 30);
        var second = location(2, AccessTier.MIDDLE, 20, 10);
        when(first.getZone().getTierToShipping()).thenReturn(AccessTier.MIDDLE);
        when(second.getZone().getTierToShipping()).thenReturn(AccessTier.FAST);
        var result = strategy.rank(batch, List.of(first, second), 5);
        assertEquals(List.of(2L, 1L), ids(result));
        assertEquals(0, BigDecimal.TEN.compareTo(result.getFirst().distanceMeters()));
        assertTrue(result.getFirst().explanation().contains("shipping"));
    }
    @Test void shouldIncludeClassificationAndTargetTierInExplanation() {
        givenAbcClass("A");
        var explanation = strategy.rank(batch, List.of(location(1, AccessTier.FAST, 5, 10)), 1).getFirst().explanation();
        for (String part : List.of("Passes hard constraints", "pick frequency baseline", "ABC class A", "target tier FAST", "location tier FAST", "target zone match", "picking"))
            assertTrue(explanation.contains(part), part);
    }
    @Test void shouldPropagateLocationVersion() {
        givenAbcClass("A");
        var location = location(1, AccessTier.FAST, 5, 10);
        when(location.getVersion()).thenReturn(7L);
        assertEquals(Long.valueOf(7), strategy.rank(batch, List.of(location), 1).getFirst().locationVersion());
    }
    @Test void shouldReturnNullScoreBreakdown() {
        givenAbcClass("A");
        assertNull(strategy.rank(batch, List.of(location(1, AccessTier.FAST, 5, 10)), 1).getFirst().scoreBreakdown());
    }
    @Test void shouldRetrieveClassificationOnlyOncePerRanking() {
        givenAbcClass("A");
        strategy.rank(batch, List.of(location(1, AccessTier.FAST, 5, 10), location(2, AccessTier.FAST, 10, 15)), 5);
        verify(classification, times(1)).findLatest(1L, 1L);
    }
}
