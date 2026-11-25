package com.warehouse.slotting.service;

import com.warehouse.slotting.entity.*;
import com.warehouse.slotting.dto.*;
import com.warehouse.slotting.repository.*;
import com.warehouse.slotting.exception.UseCaseException;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class PlacementServiceTest {
    private final BatchRepository batches = mock(BatchRepository.class);
    private final LocationRepository locations = mock(LocationRepository.class);
    private final StockMovementRepository movements = mock(StockMovementRepository.class);
    private final HardConstraintsService constraints = mock(HardConstraintsService.class);
    private final PlacementService service = new PlacementService(batches,locations,movements,constraints);
    private final Warehouse warehouse = PlacementFixtures.warehouse(1);
    private final Batch batch = PlacementFixtures.batch(warehouse);
    private final Location location = PlacementFixtures.location(warehouse);
    private final PlacementRequest request = new PlacementRequest(1L,6L,0L);
    @BeforeEach void setup() {
        when(batches.findByIdForUpdate(1L)).thenReturn(Optional.of(batch));
        when(locations.findByIdForUpdate(6L)).thenReturn(Optional.of(location));
    }
    @Test void confirmsPlacement() {
        when(constraints.check(1L,6L)).thenReturn(new ConstraintCheckResponse(1L,6L,"ST-06",true,List.of()));
        when(movements.saveAndFlush(any(StockMovement.class))).thenAnswer(inv -> {
            StockMovement m = inv.getArgument(0); ReflectionTestUtils.setField(m,"id",9L); return m;
        });
        var result = service.confirm(request,2L);
        assertEquals(9L,result.movementId()); assertEquals(1L,result.batchId());
        assertEquals(6L,result.locationId()); assertEquals("placed",result.status());
        assertEquals(BatchStatus.PLACED,batch.getStatus()); assertSame(location,batch.getCurrentLocation());
        assertEquals(result.placedAt(),batch.getPlacedAt());
        assertEquals(0,new BigDecimal("10").compareTo(location.getOccupiedVolumeDm3()));
        assertEquals(0,new BigDecimal("20").compareTo(location.getOccupiedWeightKg()));
        var order = inOrder(batches,locations,constraints,movements);
        order.verify(batches).findByIdForUpdate(1L); order.verify(locations).findByIdForUpdate(6L);
        order.verify(constraints).check(1L,6L); order.verify(locations).flush();
        var captor = org.mockito.ArgumentCaptor.forClass(StockMovement.class);
        order.verify(movements).saveAndFlush(captor.capture());
        assertEquals(2L,captor.getValue().getPerformedBy()); assertEquals(MovementType.PUTAWAY,captor.getValue().getMovementType());
    }
    private void rejected(PlacementRequest r, Long operator, UseCaseException.Kind kind,String message) {
        var ex = assertThrows(UseCaseException.class,()->service.confirm(r,operator));
        assertEquals(kind,ex.kind()); assertEquals(message,ex.getMessage());
        verifyNoInteractions(movements); verify(locations,never()).flush();
        assertEquals(0,location.getOccupiedVolumeDm3().signum());
    }
    @Test void invalidInput() {
        for (var r : Arrays.asList(null,new PlacementRequest(null,6L,0L),new PlacementRequest(0L,6L,0L),new PlacementRequest(1L,null,0L),new PlacementRequest(1L,0L,0L),new PlacementRequest(1L,6L,null),new PlacementRequest(1L,6L,-1L)))
            rejected(r,2L,UseCaseException.Kind.INVALID_INPUT,"Invalid placement request");
        rejected(request,null,UseCaseException.Kind.INVALID_INPUT,"Invalid placement request");
        rejected(request,0L,UseCaseException.Kind.INVALID_INPUT,"Invalid placement request");
        verifyNoInteractions(batches,constraints);
    }
    @Test void missingBatch() { when(batches.findByIdForUpdate(1L)).thenReturn(Optional.empty()); rejected(request,2L,UseCaseException.Kind.NOT_FOUND,"Batch not found"); }
    @Test void missingLocation() { when(locations.findByIdForUpdate(6L)).thenReturn(Optional.empty()); rejected(request,2L,UseCaseException.Kind.NOT_FOUND,"Location not found"); }
    @Test void alreadyPlaced() { batch.placeAt(location,OffsetDateTime.now()); rejected(request,2L,UseCaseException.Kind.CONFLICT,"BATCH_NOT_AWAITING_PUTAWAY"); verifyNoInteractions(constraints); }
    @Test void differentWarehouse() { ReflectionTestUtils.setField(location,"warehouse",PlacementFixtures.warehouse(2)); rejected(request,2L,UseCaseException.Kind.CONFLICT,"WAREHOUSE_MISMATCH"); verifyNoInteractions(constraints); }
    @Test void staleVersion() { rejected(new PlacementRequest(1L,6L,1L),2L,UseCaseException.Kind.CONFLICT,"STALE_RECOMMENDATION"); verifyNoInteractions(constraints); }
    @Test void infeasible() { when(constraints.check(1L,6L)).thenReturn(new ConstraintCheckResponse(1L,6L,"ST-06",false,List.of("INSUFFICIENT_VOLUME","INCOMPATIBLE_PRODUCTS"))); rejected(request,2L,UseCaseException.Kind.CONFLICT,"INSUFFICIENT_VOLUME, INCOMPATIBLE_PRODUCTS"); assertEquals(BatchStatus.AWAITING_PUTAWAY,batch.getStatus()); }
}
