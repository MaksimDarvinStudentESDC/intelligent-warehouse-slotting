package com.warehouse.slotting.entity;

import jakarta.persistence.*;
@Converter
public class BatchStatusConverter implements AttributeConverter<BatchStatus, String> {
 public String convertToDatabaseColumn(BatchStatus value) { return value == null ? null : value.name().toLowerCase(); }
 public BatchStatus convertToEntityAttribute(String value) { return value == null ? null : BatchStatus.valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
}
