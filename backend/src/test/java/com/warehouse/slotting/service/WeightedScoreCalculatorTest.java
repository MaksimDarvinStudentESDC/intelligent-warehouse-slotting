package com.warehouse.slotting.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WeightedScoreCalculatorTest {
    private final WeightedScoreCalculator calculator = new WeightedScoreCalculator();
    private final LocalDate date = LocalDate.of(2026, 11, 23);
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private final WeightedScoreCalculator.DistanceBounds bounds =
            new WeightedScoreCalculator.DistanceBounds(n("10"), n("110"));

    private WeightedScoreCalculator.ScoreBreakdown score(String abc, String xyz, LocalDate expiry,
                                                         String distance, String batch, String free) {
        return calculator.calculate(n(distance), bounds, abc, xyz, expiry, date, n(batch), n(free));
    }

    @Test void distanceNormalization() {
        assertEquals(1.0, score("A", "X", null, "10", "1", "2").distance(), 1e-12);
        assertEquals(0.0, score("A", "X", null, "110", "1", "2").distance(), 1e-12);
        assertEquals(0.5, score("A", "X", null, "60", "1", "2").distance(), 1e-12);
    }

    @Test void classificationAffinityUsesAbcAndXyz() {
        assertEquals(1.0, score("A", "X", null, "10", "1", "2").classification(), 1e-12);
        assertEquals(1.0, score("C", "X", null, "90", "1", "2").classification(), 1e-12);
        assertEquals(0.95, score("B", "Z", null, "45", "1", "2").classification(), 1e-12);
    }

    @Test void missingClassificationAndExpiryAreNeutral() {
        var s = score(null, null, null, "60", "1", "2");
        assertEquals(0.5, s.classification(), 1e-12);
        assertEquals(0.5, s.expiry(), 1e-12);
        assertTrue(s.classificationMissing());
        assertTrue(s.expiryUnknown());
    }

    @Test void expiryUrgencyUsesFixedDateAndHorizon() {
        assertEquals(1.0, score("A", "X", date, "10", "1", "2").expiry(), 1e-12);
        assertEquals(0.75, score("A", "X", date.plusDays(15), "10", "1", "2").expiry(), 1e-12);
        assertEquals(0.5, score("A", "X", date.plusDays(30), "10", "1", "2").expiry(), 1e-12);
        assertEquals(0.5, score("A", "X", date.plusDays(300), "10", "1", "2").expiry(), 1e-12);
    }

    @Test void volumeAndWeightedContributions() {
        var s = score("A", "X", date, "10", "1", "2");
        assertEquals(0.5, s.utilization(), 1e-12);
        assertEquals(0.35 + 0.25 + 0.25 + 0.075, s.total(), 1e-12);
        assertEquals(s.total(), s.distanceContribution() + s.classificationContribution()
                + s.expiryContribution() + s.utilizationContribution(), 1e-12);
    }

    @Test void equalDistancesScoreOne() {
        var same = new WeightedScoreCalculator.DistanceBounds(n("20"), n("20"));
        assertEquals(1.0, calculator.calculate(n("20"), same, "A", "X", null,
                date, n("1"), n("2")).distance(), 1e-12);
    }

    @Test void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> score("A", "X", date.minusDays(1), "10", "1", "2"));
        assertThrows(IllegalArgumentException.class, () -> score("Q", "X", null, "10", "1", "2"));
        assertThrows(IllegalArgumentException.class, () -> score("A", "X", null, "10", "3", "2"));
        assertThrows(IllegalArgumentException.class, () -> score("A", "X", null, "200", "1", "2"));
        assertThrows(IllegalArgumentException.class, () -> WeightedScoreCalculator.bounds(List.of()));
    }

    @Test void boundsUseAllCandidateDistances() {
        var b = WeightedScoreCalculator.bounds(List.of(n("20"), n("80"), n("5")));
        assertEquals(0, b.min().compareTo(n("5")));
        assertEquals(0, b.max().compareTo(n("80")));
    }
}
