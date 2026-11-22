package com.warehouse.slotting.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ZoneTypeConverter
        implements AttributeConverter<ZoneType, String> {

    @Override
    public String convertToDatabaseColumn(ZoneType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public ZoneType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ZoneType.valueOf(dbData.toUpperCase());
    }
}