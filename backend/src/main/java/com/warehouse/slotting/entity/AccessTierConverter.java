package com.warehouse.slotting.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AccessTierConverter
        implements AttributeConverter<AccessTier, String> {

    @Override
    public String convertToDatabaseColumn(AccessTier attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public AccessTier convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AccessTier.valueOf(dbData.toUpperCase());
    }
}