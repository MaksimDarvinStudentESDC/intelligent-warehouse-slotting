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
class StockMovementTest {
    private final Warehouse w = PlacementFixtures.warehouse(1);
    private final Batch b = PlacementFixtures.batch(w);
    private final Location l = PlacementFixtures.location(w);
    private final OffsetDateTime now = OffsetDateTime.now();
    @Test void createsPutaway() { b.placeAt(l,now); var m = StockMovement.putaway(b,l,2L,now); assertSame(w,m.getWarehouse()); assertSame(b,m.getBatch()); assertSame(l,m.getToLocation()); assertNull(m.getFromLocation()); assertEquals(MovementType.PUTAWAY,m.getMovementType()); assertEquals(2L,m.getPerformedBy()); assertEquals(now,m.getPerformedAt()); assertFalse(m.isOverride()); assertNull(m.getOverrideReason()); assertNull(m.getRecommendationItemId()); }
    @Test void rejectsUnplacedBatch() { assertThrows(IllegalStateException.class,()->StockMovement.putaway(b,l,2L,now)); }
    @Test void rejectsWrongTimestamp() { b.placeAt(l,now); assertThrows(IllegalStateException.class,()->StockMovement.putaway(b,l,2L,now.plusSeconds(1))); }
    @Test void rejectsWrongLocation() { b.placeAt(l,now); var other = PlacementFixtures.location(w); ReflectionTestUtils.setField(other,"id",7L); assertThrows(IllegalStateException.class,()->StockMovement.putaway(b,other,2L,now)); }
    @Test void convertsMovementTypes() { var c = new MovementTypeConverter(); for(var type:MovementType.values()) assertEquals(type,c.convertToEntityAttribute(c.convertToDatabaseColumn(type))); assertEquals("putaway",c.convertToDatabaseColumn(MovementType.PUTAWAY)); assertNull(c.convertToDatabaseColumn(null)); assertNull(c.convertToEntityAttribute(null)); }
}
