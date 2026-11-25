package com.warehouse.slotting.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter
public class MovementTypeConverter implements AttributeConverter<MovementType, String> {
    public String convertToDatabaseColumn(MovementType value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }
    public MovementType convertToEntityAttribute(String value) {
        return value == null ? null : MovementType.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
