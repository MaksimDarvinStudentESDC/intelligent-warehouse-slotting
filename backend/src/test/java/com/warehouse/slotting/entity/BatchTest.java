package com.warehouse.slotting.entity;

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
class BatchTest {
    private final Warehouse w = PlacementFixtures.warehouse(1);
    private final Batch b = PlacementFixtures.batch(w);
    private final Location l = PlacementFixtures.location(w);
    private final OffsetDateTime now = OffsetDateTime.now();
    @Test void placesBatch() { b.placeAt(l,now); assertEquals(BatchStatus.PLACED,b.getStatus()); assertSame(l,b.getCurrentLocation()); assertEquals(now,b.getPlacedAt()); }
    @Test void rejectsDuplicate() { b.placeAt(l,now); assertThrows(IllegalStateException.class,()->b.placeAt(l,now.plusSeconds(1))); assertEquals(now,b.getPlacedAt()); }
    @Test void rejectsOtherWarehouse() { assertThrows(IllegalArgumentException.class,()->b.placeAt(PlacementFixtures.location(PlacementFixtures.warehouse(2)),now)); assertEquals(BatchStatus.AWAITING_PUTAWAY,b.getStatus()); assertNull(b.getCurrentLocation()); }
    @Test void rejectsNulls() { assertThrows(NullPointerException.class,()->b.placeAt(null,now)); assertThrows(NullPointerException.class,()->b.placeAt(l,null)); }
}
