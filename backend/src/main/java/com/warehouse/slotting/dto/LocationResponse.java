package com.warehouse.slotting.dto;

import com.warehouse.slotting.entity.Location;
import java.math.BigDecimal;
public record LocationResponse(Long id,String code,Long zoneId,BigDecimal capacityDm3,BigDecimal occupiedDm3,BigDecimal maxWeightKg,BigDecimal distanceToReceivingM,BigDecimal distanceToPickingM,BigDecimal distanceToShippingM,boolean active){
 public static LocationResponse from(Location l){return new LocationResponse(l.getId(),l.getCode(),l.getZone().getId(),l.getVolumeCapacityDm3(),l.getOccupiedVolumeDm3(),l.getMaxWeightKg(),l.getDistanceToReceivingM(),l.getDistanceToPickingM(),l.getDistanceToShippingM(),l.getIsActive());}
}
