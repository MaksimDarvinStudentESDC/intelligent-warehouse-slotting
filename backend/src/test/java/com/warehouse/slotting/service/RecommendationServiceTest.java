package com.warehouse.slotting.service;

import com.warehouse.slotting.exception.UseCaseException;
import com.warehouse.slotting.dto.ConstraintCheckResponse;
import com.warehouse.slotting.dto.RecommendationResponse;
import com.warehouse.slotting.entity.*;
import com.warehouse.slotting.repository.BatchRepository;
import com.warehouse.slotting.repository.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.warehouse.slotting.repository.LatestProductClassificationRepository;
import com.warehouse.slotting.repository.LatestProductClassificationRepository.ProductClasses;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
    @Mock BatchRepository batches;
    @Mock LocationRepository locations;
    @Mock HardConstraintsService constraints;
    @Mock JdbcTemplate jdbc;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(batches, locations, constraints,
                List.of(new NearestFeasibleStrategy(), new WeightedScoreStrategy(new LatestProductClassificationRepository(jdbc))));
    }

    private Location location(long id, int distance, int freeVolume) {
        Location l = mock(Location.class);
        lenient().when(l.getId()).thenReturn(id);
        lenient().when(l.getCode()).thenReturn("ST-" + id);
        lenient().when(l.getDistanceToReceivingM()).thenReturn(BigDecimal.valueOf(distance));
        lenient().when(l.getDistanceToPickingM()).thenReturn(BigDecimal.valueOf(distance));
        lenient().when(l.getVolumeCapacityDm3()).thenReturn(BigDecimal.valueOf(freeVolume));
        lenient().when(l.getOccupiedVolumeDm3()).thenReturn(BigDecimal.ZERO);
        return l;
    }

    private void setupBatch(LocalDate expiry, Location... candidateLocations) {
        Warehouse warehouse = mock(Warehouse.class);
        when(warehouse.getId()).thenReturn(1L);
        Product product = mock(Product.class);
        when(product.getWarehouse()).thenReturn(warehouse);
        lenient().when(product.getOutboundFlow()).thenReturn(OutboundFlow.PICKING);
        lenient().when(product.getId()).thenReturn(1L);
        Batch batch = mock(Batch.class);
        when(batch.getProduct()).thenReturn(product);
        lenient().when(batch.getExpiryDate()).thenReturn(expiry);
        lenient().when(batch.getVolumeDm3()).thenReturn(BigDecimal.TEN);
        when(batches.findById(1L)).thenReturn(Optional.of(batch));
        when(locations.findByWarehouseIdAndIsActiveTrue(1L)).thenReturn(List.of(candidateLocations));
        lenient().when(constraints.check(eq(1L), anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(1);
            return new ConstraintCheckResponse(1L, id, "ST-" + id, true, List.of());
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void classification(String abc, String xyz) {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L), eq(1L)))
                .thenReturn(List.of(new ProductClasses(abc, xyz)));
    }

    @Test
    void weightedCanChooseDifferentLocationThanNearest() {
        // CX: near has D=1, far has D=.2. Far also fits volume better.
        Location near = location(1, 10, 100);  // U=.1
        Location far = location(2, 90, 10);   // U=1
        Location furthest = location(3, 110, 100);
        setupBatch(null, near, far, furthest);
        classification("C", "X");

        RecommendationResponse nearest = service.recommend(1L, 3, "nearest_feasible");
        RecommendationResponse weighted = service.recommend(1L, 3, "weighted_score");

        assertEquals(1L, nearest.recommendations().getFirst().locationId());
        assertEquals(2L, weighted.recommendations().getFirst().locationId());
        assertEquals(3, weighted.feasibleCount());
        assertNotNull(weighted.recommendations().getFirst().scoreBreakdown());
        assertTrue(weighted.recommendations().getFirst().scoreBreakdown().total() >
                weighted.recommendations().get(1).scoreBreakdown().total());
    }

    @Test
    void defaultStrategyRemainsNearestFeasible() {
        setupBatch(null, location(1, 20, 100), location(2, 10, 100));
        RecommendationResponse result = service.recommend(1L, 1);
        assertEquals("nearest_feasible", result.strategy());
        assertEquals(2L, result.recommendations().getFirst().locationId());
        assertNull(result.recommendations().getFirst().scoreBreakdown());
        verifyNoInteractions(jdbc);
    }

    @Test
    void weightedUsesNeutralClassificationWhenMissing() {
        setupBatch(null, location(1, 10, 20), location(2, 20, 20));
        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L), eq(1L)))
                .thenReturn(List.<String[]>of());
        var result = service.recommend(1L, 2, "weighted_score");
        assertEquals(2, result.recommendations().size());
        result.recommendations().forEach(r -> {
            assertTrue(r.scoreBreakdown().classificationMissing());
            assertEquals(0.5, r.scoreBreakdown().classification(), 1e-12);
            assertTrue(r.scoreBreakdown().expiryUnknown());
        });
    }

    @Test
    void rejectedLocationNeverAppears() {
        Location near = location(1, 10, 20);
        Location far = location(2, 20, 20);
        setupBatch(null, near, far);
        when(constraints.check(1L, 1L))
                .thenReturn(new ConstraintCheckResponse(1L, 1L, "ST-1", false, List.of("CAPACITY")));
        classification("A", "X");
        var result = service.recommend(1L, 5, "weighted_score");
        assertEquals(1, result.feasibleCount());
        assertEquals(2L, result.recommendations().getFirst().locationId());
    }

    @Test
    void invalidStrategyReturnsBadRequest() {
        var error = assertThrows(UseCaseException.class,
                () -> service.recommend(1L, 5, "unknown"));
        assertEquals(UseCaseException.Kind.INVALID_INPUT, error.kind());
        verifyNoInteractions(batches, locations, constraints, jdbc);
    }

    @Test
    void topNOutsideRangeReturnsBadRequest() {
        assertEquals(UseCaseException.Kind.INVALID_INPUT,
                assertThrows(UseCaseException.class,
                        () -> service.recommend(1L, 0, "weighted_score")).kind());
    }

    @Test
    void missingBatchReturnsNotFound() {
        when(batches.findById(99L)).thenReturn(Optional.empty());
        assertEquals(UseCaseException.Kind.NOT_FOUND,
                assertThrows(UseCaseException.class,
                        () -> service.recommend(99L, 5, "weighted_score")).kind());
    }

    @Test
    void expiredBatchIsRejectedForWeightedScore() {
        setupBatch(LocalDate.now().minusDays(1), location(1, 10, 20));
        assertEquals(UseCaseException.Kind.INVALID_INPUT,
                assertThrows(UseCaseException.class,
                        () -> service.recommend(1L, 5, "weighted_score")).kind());
        verifyNoInteractions(jdbc);
    }
}
