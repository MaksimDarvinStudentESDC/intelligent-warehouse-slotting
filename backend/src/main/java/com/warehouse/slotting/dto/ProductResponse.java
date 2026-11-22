package com.warehouse.slotting.dto;

import com.warehouse.slotting.entity.Product;
import java.math.BigDecimal;
public record ProductResponse(Long id,String sku,String name,BigDecimal unitVolumeDm3,BigDecimal unitWeightKg){
 public static ProductResponse from(Product p){return new ProductResponse(p.getId(),p.getSku(),p.getName(),p.getUnitVolumeDm3(),p.getUnitWeightKg());}
}
