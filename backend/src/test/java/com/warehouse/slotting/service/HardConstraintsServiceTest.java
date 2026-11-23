package com.warehouse.slotting.service;

import com.warehouse.slotting.exception.UseCaseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.slotting.dto.ConstraintCheckResponse;
import com.warehouse.slotting.entity.*;
import com.warehouse.slotting.repository.BatchRepository;
import com.warehouse.slotting.repository.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.warehouse.slotting.repository.CompatibilityConflictRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HardConstraintsServiceTest {

    @Mock private BatchRepository batches;
    @Mock private LocationRepository locations;
    @Mock private JdbcTemplate jdbc;
    @Mock private Batch batch;
    @Mock private Product product;
    @Mock private Location location;
    @Mock private Warehouse warehouse;
    @Mock private CompatibilityGroup compatibilityGroup;

    private HardConstraintsService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new HardConstraintsService(batches, locations, new CompatibilityConflictRepository(jdbc));

        // Lenient baseline: each test changes only the property relevant to its scenario.
        lenient().when(batches.findById(1L)).thenReturn(Optional.of(batch));
        lenient().when(locations.findById(6L)).thenReturn(Optional.of(location));
        lenient().when(batch.getProduct()).thenReturn(product);
        lenient().when(product.getWarehouse()).thenReturn(warehouse);
        lenient().when(warehouse.getId()).thenReturn(1L);
        lenient().when(location.getWarehouse()).thenReturn(warehouse);
        lenient().when(location.getId()).thenReturn(6L);
        lenient().when(location.getCode()).thenReturn("ST-06");
        lenient().when(batch.getStatus()).thenReturn(BatchStatus.AWAITING_PUTAWAY);
        lenient().when(location.getIsActive()).thenReturn(true);
        lenient().when(location.getZoneType()).thenReturn(ZoneType.STORAGE);
        lenient().when(location.getVolumeCapacityDm3()).thenReturn(decimal("300"));
        lenient().when(location.getOccupiedVolumeDm3()).thenReturn(BigDecimal.ZERO);
        lenient().when(batch.getVolumeDm3()).thenReturn(decimal("120"));
        lenient().when(location.getMaxWeightKg()).thenReturn(decimal("200"));
        lenient().when(location.getOccupiedWeightKg()).thenReturn(BigDecimal.ZERO);
        lenient().when(batch.getWeightKg()).thenReturn(decimal("50"));
        lenient().when(product.getLengthMm()).thenReturn(200);
        lenient().when(product.getWidthMm()).thenReturn(150);
        lenient().when(product.getHeightMm()).thenReturn(100);
        lenient().when(location.getMaxLengthMm()).thenReturn(300);
        lenient().when(location.getMaxWidthMm()).thenReturn(250);
        lenient().when(location.getMaxHeightMm()).thenReturn(200);
    }

    @Test
    void acceptsFeasibleLocation() {
        JsonNode response = check();
        assertTrue(response.path("feasible").asBoolean());
        assertTrue(response.path("rejectionReasons").isEmpty());
        assertEquals(1, response.path("batchId").asLong());
        assertEquals(6, response.path("locationId").asLong());
        assertEquals("ST-06", response.path("locationCode").asText());
    }

    @Test
    void rejectsInsufficientVolume() {
        when(location.getOccupiedVolumeDm3()).thenReturn(decimal("200"));
        assertRejectedFor("INSUFFICIENT_VOLUME");
    }

    @Test
    void rejectsInsufficientWeightCapacity() {
        when(location.getOccupiedWeightKg()).thenReturn(decimal("180"));
        assertRejectedFor("INSUFFICIENT_WEIGHT_CAPACITY");
    }

    @Test
    void rejectsInactiveLocation() {
        when(location.getIsActive()).thenReturn(false);
        assertRejectedFor("LOCATION_INACTIVE");
    }

    @Test
    void rejectsNonStorageLocation() {
        when(location.getZoneType()).thenReturn(ZoneType.PICKING);
        assertRejectedFor("NOT_STORAGE_LOCATION");
    }

    @Test
    void rejectsOversizedProduct() {
        when(product.getHeightMm()).thenReturn(201);
        assertRejectedFor("DIMENSIONS_EXCEEDED");
    }

    @Test
    void acceptsHorizontalRotation() {
        when(product.getLengthMm()).thenReturn(250);
        when(product.getWidthMm()).thenReturn(300);
        assertTrue(check().path("feasible").asBoolean());
    }

    @Test
    void rejectsWarehouseMismatch() {
        Warehouse otherWarehouse = mock(Warehouse.class);
        when(otherWarehouse.getId()).thenReturn(2L);
        when(location.getWarehouse()).thenReturn(otherWarehouse);
        assertRejectedFor("WAREHOUSE_MISMATCH");
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsBatchNotAwaitingPutaway() {
        when(batch.getStatus()).thenReturn(BatchStatus.PLACED);
        assertRejectedFor("BATCH_NOT_AWAITING_PUTAWAY");
    }

    @Test
    void rejectsIncompatibleProducts() {
        when(product.getCompatibilityGroup()).thenReturn(compatibilityGroup);
        when(compatibilityGroup.getId()).thenReturn(5L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(6L), eq(5L), eq(5L)))
                .thenReturn(1);
        assertRejectedFor("INCOMPATIBLE_PRODUCTS");
    }

    @Test
    void acceptsCompatibleProducts() {
        when(product.getCompatibilityGroup()).thenReturn(compatibilityGroup);
        when(compatibilityGroup.getId()).thenReturn(5L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(6L), eq(5L), eq(5L)))
                .thenReturn(0);
        assertTrue(check().path("feasible").asBoolean());
    }

    @Test
    void rejectsUnknownBatchWith404() {
        when(batches.findById(1L)).thenReturn(Optional.empty());
        UseCaseException error = assertThrows(UseCaseException.class,
                () -> service.check(1L, 6L));
        assertEquals(UseCaseException.Kind.NOT_FOUND, error.kind());
    }

    @Test
    void rejectsUnknownLocationWith404() {
        when(locations.findById(6L)).thenReturn(Optional.empty());
        UseCaseException error = assertThrows(UseCaseException.class,
                () -> service.check(1L, 6L));
        assertEquals(UseCaseException.Kind.NOT_FOUND, error.kind());
    }

    @Test
    void reportsMultipleReasonsTogether() {
        when(location.getOccupiedVolumeDm3()).thenReturn(decimal("250"));
        when(location.getOccupiedWeightKg()).thenReturn(decimal("190"));
        JsonNode response = check();
        assertFalse(response.path("feasible").asBoolean());
        assertContains(response, "INSUFFICIENT_VOLUME");
        assertContains(response, "INSUFFICIENT_WEIGHT_CAPACITY");
    }

    private void assertRejectedFor(String reason) {
        JsonNode response = check();
        assertFalse(response.path("feasible").asBoolean());
        assertContains(response, reason);
    }

    private static void assertContains(JsonNode response, String expected) {
        JsonNode reasons = response.path("rejectionReasons");
        assertTrue(reasons.isArray(), "rejectionReasons must be an array");
        boolean found = false;
        for (JsonNode reason : reasons) {
            if (expected.equals(reason.asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Expected " + expected + " in " + reasons);
    }

    private JsonNode check() {
        ConstraintCheckResponse response = service.check(1L, 6L);
        return mapper.valueToTree(response);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
