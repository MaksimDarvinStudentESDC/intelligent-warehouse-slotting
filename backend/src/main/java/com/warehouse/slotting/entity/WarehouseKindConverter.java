
package com.warehouse.slotting.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class WarehouseKindConverter
        implements AttributeConverter<WarehouseKind, String> {

    @Override
    public String convertToDatabaseColumn(WarehouseKind attribute) {
        if (attribute == null) {
            return null;
        }

        return attribute.name().toLowerCase();
    }

    @Override
    public WarehouseKind convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        return WarehouseKind.valueOf(dbData.toUpperCase());
    }
}
