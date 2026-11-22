package com.warehouse.slotting.entity;

import jakarta.persistence.*;
@Converter
public class OutboundFlowConverter implements AttributeConverter<OutboundFlow, String> {
 public String convertToDatabaseColumn(OutboundFlow value) { return value == null ? null : value.name().toLowerCase(); }
 public OutboundFlow convertToEntityAttribute(String value) { return value == null ? null : OutboundFlow.valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
}
