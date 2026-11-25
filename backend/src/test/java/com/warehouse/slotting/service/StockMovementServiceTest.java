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
import org.springframework.data.domain.*;
class StockMovementServiceTest {
    private final StockMovementRepository movements = mock(StockMovementRepository.class);
    private final WarehouseRepository warehouses = mock(WarehouseRepository.class);
    private final StockMovementService service = new StockMovementService(movements,warehouses);
    @Test void mapsHistoryAndPreservesFilters() {
        var w = PlacementFixtures.warehouse(1); var b = PlacementFixtures.batch(w); var l = PlacementFixtures.location(w);
        var from = OffsetDateTime.parse("2026-11-25T00:00:00Z"); var to = from.plusDays(1);
        b.placeAt(l,from); var m = StockMovement.putaway(b,l,2L,from);
        ReflectionTestUtils.setField(m,"id",9L);
        var pageable = PageRequest.of(0,10,Sort.by(Sort.Direction.DESC,"performedAt","id"));
        when(warehouses.existsById(1L)).thenReturn(true);
        when(movements.findHistory(1L,3L,6L,from,to,pageable)).thenReturn(new PageImpl<>(List.of(m),pageable,1));
        var result = service.history(1L,3L,6L,from,to,0,10);
        assertEquals(1,result.getTotalElements()); var dto = result.getContent().getFirst();
        assertEquals(9L,dto.id()); assertEquals(3L,dto.productId()); assertNull(dto.fromLocationId());
        assertEquals("ST-06",dto.toLocationCode()); assertEquals("putaway",dto.movementType()); assertEquals(from,dto.performedAt());
        verify(movements).findHistory(1L,3L,6L,from,to,pageable);
    }
    @Test void missingWarehouse() { var ex = assertThrows(UseCaseException.class,()->service.history(1L,null,null,null,null,0,10)); assertEquals(UseCaseException.Kind.NOT_FOUND,ex.kind()); verifyNoInteractions(movements); }
    @Test void invalidFilters() {
        var now = OffsetDateTime.now();
        List<org.junit.jupiter.api.function.Executable> cases = List.of(
            ()->service.history(null,null,null,null,null,0,10),()->service.history(0L,null,null,null,null,0,10),
            ()->service.history(1L,0L,null,null,null,0,10),()->service.history(1L,null,0L,null,null,0,10),
            ()->service.history(1L,null,null,null,null,-1,10),()->service.history(1L,null,null,null,null,0,0),
            ()->service.history(1L,null,null,null,null,0,101),()->service.history(1L,null,null,now,now,0,10));
        for(var c:cases) assertEquals(UseCaseException.Kind.INVALID_INPUT,assertThrows(UseCaseException.class,c).kind());
        verifyNoInteractions(warehouses,movements);
    }
}
