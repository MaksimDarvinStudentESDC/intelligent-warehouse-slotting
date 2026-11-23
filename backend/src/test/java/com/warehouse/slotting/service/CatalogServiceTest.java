package com.warehouse.slotting.service;

import com.warehouse.slotting.exception.UseCaseException;
import com.warehouse.slotting.dto.CreateBatchRequest;
import com.warehouse.slotting.dto.CreateProductRequest;
import com.warehouse.slotting.entity.Batch;
import com.warehouse.slotting.entity.CompatibilityGroup;
import com.warehouse.slotting.entity.OutboundFlow;
import com.warehouse.slotting.entity.Product;
import com.warehouse.slotting.entity.Warehouse;
import com.warehouse.slotting.repository.BatchRepository;
import com.warehouse.slotting.repository.CompatibilityGroupRepository;
import com.warehouse.slotting.repository.ProductRepository;
import com.warehouse.slotting.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock private WarehouseRepository warehouses;
    @Mock private CompatibilityGroupRepository groups;
    @Mock private ProductRepository products;
    @Mock private BatchRepository batches;
    @InjectMocks private CatalogService service;

    private Warehouse warehouse;
    private Product product;

    @BeforeEach
    void setUp() {
        warehouse = mock(Warehouse.class);
        product = new Product(
                warehouse, "DEMO-001", "Demo Product",
                200, 150, 100, new BigDecimal("1.5"),
                null, 180, OutboundFlow.PICKING
        );
    }

    private CreateProductRequest productRequest(Long groupId) {
        return new CreateProductRequest(
                1L, "DEMO-002", "Second Product",
                200, 150, 100, new BigDecimal("1.5"),
                groupId, 180, OutboundFlow.PICKING
        );
    }

    private CreateBatchRequest batchRequest(String code, LocalDate expiry) {
        return new CreateBatchRequest(
                1L, code, 100, LocalDate.of(2026, 11, 20), expiry
        );
    }

    @Test
    void createProduct_savesProductAndCalculatesUnitVolume() {
        when(warehouses.findById(1L)).thenReturn(Optional.of(warehouse));
        when(products.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createProduct(productRequest(null));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(products).saveAndFlush(captor.capture());
        Product saved = captor.getValue();
        assertEquals("DEMO-002", saved.getSku());
        assertEquals(0, new BigDecimal("3.000000").compareTo(saved.getUnitVolumeDm3()));
        assertEquals(0, new BigDecimal("1.5").compareTo(saved.getUnitWeightKg()));
    }

    @Test
    void createProduct_rejectsDuplicateSku() {
        when(warehouses.findById(1L)).thenReturn(Optional.of(warehouse));
        when(products.existsByWarehouseIdAndSku(1L, "DEMO-002")).thenReturn(true);

        UseCaseException exception = assertThrows(
                UseCaseException.class,
                () -> service.createProduct(productRequest(null))
        );
        assertEquals(UseCaseException.Kind.CONFLICT, exception.kind());
        verify(products, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void createProduct_rejectsUnknownCompatibilityGroup() {
        when(warehouses.findById(1L)).thenReturn(Optional.of(warehouse));
        when(groups.findByIdAndWarehouseId(999999L, 1L)).thenReturn(Optional.empty());

        UseCaseException exception = assertThrows(
                UseCaseException.class,
                () -> service.createProduct(productRequest(999999L))
        );
        assertEquals(UseCaseException.Kind.INVALID_INPUT, exception.kind());
        verify(products, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void createBatch_savesBatchAndCalculatesTotals() {
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(warehouse.getId()).thenReturn(1L);
        when(batches.saveAndFlush(any(Batch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createBatch(batchRequest("BATCH-001", LocalDate.of(2027, 5, 19)));

        ArgumentCaptor<Batch> captor = ArgumentCaptor.forClass(Batch.class);
        verify(batches).saveAndFlush(captor.capture());
        Batch saved = captor.getValue();
        assertEquals(100, saved.getQuantity());
        assertEquals(0, new BigDecimal("300.000").compareTo(saved.getVolumeDm3()));
        assertEquals(0, new BigDecimal("150.000").compareTo(saved.getWeightKg()));
        assertEquals("AWAITING_PUTAWAY", saved.getStatus().name());
    }

    @Test
    void createBatch_rejectsExpiryDateOnReceivingDate() {
        when(products.findById(1L)).thenReturn(Optional.of(product));

        UseCaseException exception = assertThrows(
                UseCaseException.class,
                () -> service.createBatch(batchRequest("BATCH-002", LocalDate.of(2026, 11, 20)))
        );
        assertEquals(UseCaseException.Kind.INVALID_INPUT, exception.kind());
        verify(batches, never()).saveAndFlush(any(Batch.class));
    }

    @Test
    void createBatch_rejectsDuplicateBatchCode() {
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(warehouse.getId()).thenReturn(1L);
        when(batches.existsByWarehouseIdAndBatchCode(1L, "BATCH-001"))
                .thenReturn(true);

        UseCaseException exception = assertThrows(
                UseCaseException.class,
                () -> service.createBatch(batchRequest("BATCH-001", LocalDate.of(2027, 5, 19)))
        );
        assertEquals(UseCaseException.Kind.CONFLICT, exception.kind());
        verify(batches, never()).saveAndFlush(any(Batch.class));
    }
}
