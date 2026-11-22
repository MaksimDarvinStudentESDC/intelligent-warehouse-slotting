package com.warehouse.slotting.dto;

import com.warehouse.slotting.entity.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public record BatchResponse(Long id,String batchCode,int quantity,BigDecimal volumeDm3,BigDecimal weightKg,LocalDate receivingDate,LocalDate expiryDate,BatchStatus status){
 public static BatchResponse from(Batch b){return new BatchResponse(b.getId(),b.getBatchCode(),b.getQuantity(),b.getVolumeDm3(),b.getWeightKg(),b.getReceivingDate(),b.getExpiryDate(),b.getStatus());}
}
