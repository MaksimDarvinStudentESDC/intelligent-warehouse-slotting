package com.warehouse.slotting.service;

import com.warehouse.slotting.entity.*;
import com.warehouse.slotting.repository.LatestProductClassificationRepository;
import com.warehouse.slotting.repository.LatestProductClassificationRepository.ProductClasses;
import com.warehouse.slotting.exception.UseCaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pure Mockito/JUnit tests: no Spring context, Docker or PostgreSQL. */
class WeightedScoreStrategyTest {
    private LatestProductClassificationRepository classification;
    private WeightedScoreStrategy strategy;
    private Batch batch;

    @BeforeEach
    void setUp() {
        classification = mock(LatestProductClassificationRepository.class);
        strategy = new WeightedScoreStrategy(classification);
        Warehouse warehouse = mock(Warehouse.class);
        Product product = mock(Product.class);
        batch = mock(Batch.class);
        when(batch.getProduct()).thenReturn(product);
        when(product.getWarehouse()).thenReturn(warehouse);
        when(product.getOutboundFlow()).thenReturn(OutboundFlow.PICKING);
        when(batch.getVolumeDm3()).thenReturn(new BigDecimal("10"));
        when(classification.findLatest(any(), any())).thenReturn(Optional.of(new ProductClasses("A", "X")));
    }

    private static Location location(long id, String code, String distance, String capacity, String occupied) {
        Location location = mock(Location.class);
        when(location.getId()).thenReturn(id);
        when(location.getCode()).thenReturn(code);
        when(location.getDistanceToPickingM()).thenReturn(distance == null ? null : new BigDecimal(distance));
        when(location.getVolumeCapacityDm3()).thenReturn(capacity == null ? null : new BigDecimal(capacity));
        when(location.getOccupiedVolumeDm3()).thenReturn(occupied == null ? null : new BigDecimal(occupied));
        return location;
    }

    @Test
    void ranksCandidatesAndLimitsTopN() {
        var near = location(1, "NEAR", "10", "20", "0");
        var far = location(2, "FAR", "110", "20", "0");
        var results = strategy.rank(batch, List.of(far, near), 1);
        assertEquals(1, results.size());
        assertEquals(1L, results.getFirst().locationId());
        assertEquals(1, results.getFirst().rank());
        assertNotNull(results.getFirst().scoreBreakdown());
        assertTrue(results.getFirst().scoreBreakdown().total() > 0);
    }

    @Test
    void missingClassificationAndExpiryAreReportedAsNeutral() {
        when(classification.findLatest(any(), any())).thenReturn(Optional.empty());
        var results = strategy.rank(batch, List.of(location(3, "L3", "20", "20", "0")), 5);
        assertEquals(1, results.size());
        var item = results.getFirst();
        assertTrue(item.scoreBreakdown().classificationMissing());
        assertTrue(item.scoreBreakdown().expiryUnknown());
        assertEquals(0.5, item.scoreBreakdown().classification(), 1e-12);
        assertEquals(0.5, item.scoreBreakdown().expiry(), 1e-12);
        assertTrue(item.explanation().contains("classification missing"));
        assertTrue(item.explanation().contains("expiry unknown"));
    }

    @Test
    void excludesLocationsWithMissingOrNonpositiveFreeCapacity() {
        var missingDistance = location(1, "NO-DIST", null, "20", "0");
        var full = location(2, "FULL", "10", "20", "20");
        var missingCapacity = location(3, "NO-CAP", "15", null, "0");
        var valid = location(4, "VALID", "30", "20", "0");
        var results = strategy.rank(batch, List.of(missingDistance, full, missingCapacity, valid), 10);
        assertEquals(1, results.size());
        assertEquals(4L, results.getFirst().locationId());
    }

    @Test
    void returnsEmptyWhenNoScorableLocations() {
        assertTrue(strategy.rank(batch, List.of(), 5).isEmpty());
        assertTrue(strategy.rank(batch, List.of(location(1, "FULL", "10", "10", "10")), 5).isEmpty());
    }

    @Test
    void equalScoresUseDistanceThenIdForStableOrdering() {
        var largerId = location(9, "NINE", "20", "20", "0");
        var smallerId = location(2, "TWO", "20", "20", "0");
        var results = strategy.rank(batch, List.of(largerId, smallerId), 5);
        assertEquals(List.of(2L, 9L), results.stream().map(r -> r.locationId()).toList());
        assertEquals(List.of(1, 2), results.stream().map(r -> r.rank()).toList());
    }

    @Test
    void expiredBatchIsRejectedBeforeRanking() {
        when(batch.getExpiryDate()).thenReturn(LocalDate.now().minusDays(1));
        assertThrows(UseCaseException.class,
                () -> strategy.rank(batch, List.of(location(1, "L1", "10", "20", "0")), 5));
    }

    @Test
    void pickingDistanceIsUsedInsteadOfShippingDistance() {
        var location = location(1, "PICK", "25", "20", "0");
        when(location.getDistanceToShippingM()).thenReturn(new BigDecimal("900"));
        var result = strategy.rank(batch, List.of(location), 5).getFirst();
        assertEquals(0, new BigDecimal("25").compareTo(result.distanceMeters()));
    }
}
