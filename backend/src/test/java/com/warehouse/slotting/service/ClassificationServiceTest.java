package com.warehouse.slotting.service;

import com.warehouse.slotting.exception.UseCaseException;
import com.warehouse.slotting.entity.OutboundFlow;
import com.warehouse.slotting.entity.Product;
import com.warehouse.slotting.entity.Warehouse;
import com.warehouse.slotting.entity.WarehouseKind;
import com.warehouse.slotting.repository.ClassificationRepository;
import com.warehouse.slotting.repository.ProductRepository;
import com.warehouse.slotting.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassificationServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock ProductRepository products;
    @Mock WarehouseRepository warehouses;

    private ClassificationService service;
    private final LocalDate start = LocalDate.of(2026, 8, 23);
    private final LocalDate end = LocalDate.of(2026, 8, 27);

    @BeforeEach
    void setUp() {
        service = new ClassificationService(new ClassificationRepository(jdbc), products, warehouses);
    }

    private void liveWarehouse() {
        when(warehouses.findById(1L))
                .thenReturn(Optional.of(new Warehouse("Main", WarehouseKind.LIVE)));
    }

    private Product product(long id, String sku) {
        Product p = new Product(new Warehouse("Main", WarehouseKind.LIVE), sku, sku,
                100, 100, 100, BigDecimal.ONE, null, null, OutboundFlow.PICKING);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private void runId(long id) {
        when(jdbc.queryForObject(contains("INSERT INTO classification_runs"), eq(Long.class), any(Object[].class)))
                .thenReturn(id);
    }

    private void outboundRow(long productId, LocalDate date, int quantity, int picksCount) {
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("product_id")).thenReturn(productId);
            when(rs.getDate("event_date")).thenReturn(Date.valueOf(date));
            when(rs.getInt("quantity")).thenReturn(quantity);
            when(rs.getInt("picks_count")).thenReturn(picksCount);
            handler.processRow(rs);
            return null;
        }).when(jdbc).query(contains("FROM outbound_history"), any(RowCallbackHandler.class), any(Object[].class));
    }

    @Test
    void rejectsInvalidDatesBeforeDatabaseAccess() {
        assertEquals(UseCaseException.Kind.INVALID_INPUT,
                assertThrows(UseCaseException.class,
                        () -> service.run(1L, end, start)).kind());
        assertEquals(UseCaseException.Kind.INVALID_INPUT,
                assertThrows(UseCaseException.class,
                        () -> service.run(null, start, end)).kind());
        assertEquals(UseCaseException.Kind.INVALID_INPUT,
                assertThrows(UseCaseException.class,
                        () -> service.run(1L, null, end)).kind());
        verifyNoInteractions(jdbc, warehouses, products);
    }

    @Test
    void rejectsExcessivelyLongWindow() {
        assertEquals(UseCaseException.Kind.INVALID_INPUT,
                assertThrows(UseCaseException.class,
                        () -> service.run(1L, start, start.plusDays(3651))).kind());
        verifyNoInteractions(jdbc, warehouses, products);
    }

    @Test
    void returnsNotFoundForUnknownWarehouse() {
        when(warehouses.findById(99L)).thenReturn(Optional.empty());
        assertEquals(UseCaseException.Kind.NOT_FOUND,
                assertThrows(UseCaseException.class,
                        () -> service.run(99L, start, end)).kind());
        verifyNoInteractions(jdbc, products);
    }

    @Test
    void rejectsPersistedRunForSimulationWarehouse() {
        when(warehouses.findById(1L))
                .thenReturn(Optional.of(new Warehouse("Test", WarehouseKind.SIMULATION)));
        assertEquals(UseCaseException.Kind.INVALID_INPUT,
                assertThrows(UseCaseException.class,
                        () -> service.run(1L, start, end)).kind());
        verifyNoInteractions(jdbc, products);
    }

    @Test
    void persistsRunWithNoProducts() {
        liveWarehouse();
        when(products.findByWarehouseIdAndArchivedFalse(1L)).thenReturn(List.of());
        runId(10L);

        var result = service.run(1L, start, end);

        assertEquals(10L, result.runId());
        assertEquals(1L, result.warehouseId());
        assertTrue(result.products().isEmpty());
        verify(jdbc, never()).update(contains("INSERT INTO product_classes"), any(Object[].class));
    }

    @Test
    void missingHistoryProducesClassCAndNullXyz() {
        liveWarehouse();
        when(products.findByWarehouseIdAndArchivedFalse(1L)).thenReturn(List.of(product(1L, "EMPTY")));
        runId(11L);

        var result = service.run(1L, start, end);
        var classified = result.products().getFirst();

        assertEquals("C", classified.abcClass());
        assertNull(classified.xyzClass());
        assertEquals(0L, classified.movementCount());
        assertNull(classified.cv());
        assertEquals("no data", classified.note());
        verify(jdbc).update(contains("INSERT INTO product_classes"), any(Object[].class));
    }

    @Test
    void usesMissingCalendarDaysAsZeroAndPersistsCalculatedClasses() {
        liveWarehouse();
        when(products.findByWarehouseIdAndArchivedFalse(1L))
                .thenReturn(List.of(product(1L, "SPARSE")));
        outboundRow(1L, start, 20, 3); // 5 days: [20,0,0,0,0], CV = 2 -> Z
        runId(12L);

        var result = service.run(1L, start, end);
        var classified = result.products().getFirst();

        assertEquals("A", classified.abcClass());
        assertEquals("Z", classified.xyzClass());
        assertEquals(3L, classified.movementCount());
        assertEquals(0, classified.cv().compareTo(new BigDecimal("2.0000")));
        assertEquals(0, classified.cumulativeShare().compareTo(BigDecimal.ONE));
        verify(jdbc).update(contains("INSERT INTO product_classes"), any(Object[].class));
    }
}
