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
class LocationTest {
    private final Location l = PlacementFixtures.location(PlacementFixtures.warehouse(1));
    @Test void accumulatesOccupancy() { l.occupy(BigDecimal.TEN,BigDecimal.ONE); l.occupy(new BigDecimal("90"),new BigDecimal("99")); assertEquals(0,new BigDecimal("100").compareTo(l.getOccupiedVolumeDm3())); assertEquals(0,new BigDecimal("100").compareTo(l.getOccupiedWeightKg())); }
    @Test void rejectsVolumeOverflow() { assertThrows(IllegalStateException.class,()->l.occupy(new BigDecimal("101"),BigDecimal.ONE)); unchanged(); }
    @Test void rejectsWeightOverflow() { assertThrows(IllegalStateException.class,()->l.occupy(BigDecimal.ONE,new BigDecimal("101"))); unchanged(); }
    @Test void rejectsInactive() { ReflectionTestUtils.setField(l,"isActive",false); assertThrows(IllegalStateException.class,()->l.occupy(BigDecimal.ONE,BigDecimal.ONE)); unchanged(); }
    @Test void rejectsInvalidAmounts() { assertThrows(IllegalArgumentException.class,()->l.occupy(null,BigDecimal.ONE)); assertThrows(IllegalArgumentException.class,()->l.occupy(BigDecimal.ONE,null)); assertThrows(IllegalArgumentException.class,()->l.occupy(BigDecimal.ONE.negate(),BigDecimal.ONE)); assertThrows(IllegalArgumentException.class,()->l.occupy(BigDecimal.ONE,BigDecimal.ONE.negate())); unchanged(); }
    private void unchanged() { assertEquals(0,l.getOccupiedVolumeDm3().signum()); assertEquals(0,l.getOccupiedWeightKg().signum()); }
}
