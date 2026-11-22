package com.warehouse.slotting.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassificationCalculatorTest {

    private final ClassificationCalculator calculator = new ClassificationCalculator();

    @Test
    void abcUsesShareBeforeCurrentProduct() {
        assertEquals(ClassificationCalculator.AbcClass.A,
                calculator.calculateAbc(0, 100, 0.80, 0.15));
        assertEquals(ClassificationCalculator.AbcClass.A,
                calculator.calculateAbc(79, 100, 0.80, 0.15));
        assertEquals(ClassificationCalculator.AbcClass.B,
                calculator.calculateAbc(80, 100, 0.80, 0.15));
        assertEquals(ClassificationCalculator.AbcClass.C,
                calculator.calculateAbc(95, 100, 0.80, 0.15));
    }

    @Test
    void zeroTotalPicksBelongsToC() {
        assertEquals(ClassificationCalculator.AbcClass.C,
                calculator.calculateAbc(0, 0, 0.80, 0.15));
    }

    @Test
    void stableDemandIsX() {
        Double cv = calculator.calculateCv(List.of(10, 10, 10, 10, 10));
        assertNotNull(cv);
        assertEquals(0.0, cv, 1e-9);
        assertEquals(ClassificationCalculator.XyzClass.X,
                calculator.calculateXyz(cv, 0.5, 1.0));
    }

    @Test
    void moderatelyVariableDemandIsY() {
        Double cv = calculator.calculateCv(List.of(2, 2, 2, 2, 12));
        assertNotNull(cv);
        assertEquals(1, cv, 1e-9);
        assertEquals(ClassificationCalculator.XyzClass.Y,
                calculator.calculateXyz(cv, 0.5, 1.0));
    }

    @Test
    void intermittentDemandIncludingZeroDaysIsZ() {
        Double cv = calculator.calculateCv(List.of(0, 0, 0, 0, 20));
        assertNotNull(cv);
        assertEquals(2.0, cv, 1e-9);
        assertEquals(ClassificationCalculator.XyzClass.Z,
                calculator.calculateXyz(cv, 0.5, 1.0));
    }

    @Test
    void noDemandHasUndefinedXyz() {
        Double cv = calculator.calculateCv(List.of(0, 0, 0));
        assertNull(cv);
        assertNull(calculator.calculateXyz(cv, 0.5, 1.0));
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateAbc(-1, 100, 0.80, 0.15),
                "ABC must reject negative picks"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateCv(List.of(1, -1)),
                "CV must reject negative demand"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateXyz(Double.NaN, 0.5, 1.0),
                "XYZ must reject NaN"
        );
    }
}
