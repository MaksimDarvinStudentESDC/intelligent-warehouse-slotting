package com.warehouse.slotting.service;

import com.warehouse.slotting.entity.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickFrequencyZoneResolverTest {
    @Test void shouldResolveClassA() {
        assertEquals(Optional.of(AccessTier.FAST), PickFrequencyZoneResolver.resolveTargetTier("A"));
    }
    @Test void shouldResolveClassB() {
        assertEquals(Optional.of(AccessTier.MIDDLE), PickFrequencyZoneResolver.resolveTargetTier("B"));
    }
    @Test void shouldResolveClassC() {
        assertEquals(Optional.of(AccessTier.REMAINING), PickFrequencyZoneResolver.resolveTargetTier("C"));
    }
    @Test void shouldNormalizeAbcClassification() {
        assertEquals(Optional.of(AccessTier.FAST), PickFrequencyZoneResolver.resolveTargetTier(" a "));
    }
    @Test void shouldReturnEmptyWhenClassificationIsMissing() {
        for (String abc : new String[]{null, "", "   "})
            assertTrue(PickFrequencyZoneResolver.resolveTargetTier(abc).isEmpty());
    }
    @Test void shouldRejectInvalidAbcClassification() {
        assertThrows(IllegalArgumentException.class, () -> PickFrequencyZoneResolver.resolveTargetTier("D"));
    }
    private Location location(AccessTier picking, AccessTier shipping) {
        Location location = mock(Location.class);
        Zone zone = mock(Zone.class);
        when(location.getZone()).thenReturn(zone);
        when(zone.getTierToPicking()).thenReturn(picking);
        when(zone.getTierToShipping()).thenReturn(shipping);
        return location;
    }
    @Test void shouldResolvePickingAccessTier() {
        assertEquals(AccessTier.FAST, PickFrequencyZoneResolver.resolveLocationTier(location(AccessTier.FAST, AccessTier.MIDDLE), OutboundFlow.PICKING));
    }
    @Test void shouldResolveShippingAccessTier() {
        assertEquals(AccessTier.MIDDLE, PickFrequencyZoneResolver.resolveLocationTier(location(AccessTier.FAST, AccessTier.MIDDLE), OutboundFlow.SHIPPING));
    }
    @Test void shouldMatchTargetTier() {
        assertTrue(PickFrequencyZoneResolver.matchesTargetTier(location(AccessTier.FAST, AccessTier.MIDDLE), OutboundFlow.PICKING, AccessTier.FAST));
    }
    @Test void shouldNotMatchDifferentTargetTier() {
        assertFalse(PickFrequencyZoneResolver.matchesTargetTier(location(AccessTier.REMAINING, AccessTier.MIDDLE), OutboundFlow.PICKING, AccessTier.FAST));
    }
    @Test void shouldRejectMissingLocationTier() {
        assertThrows(NullPointerException.class, () -> PickFrequencyZoneResolver.resolveLocationTier(location(null, null), OutboundFlow.PICKING));
    }
}
