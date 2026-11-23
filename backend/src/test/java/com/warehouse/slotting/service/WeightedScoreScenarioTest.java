package com.warehouse.slotting.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Reproducible experimental fixtures; does not modify database or production code. */
class WeightedScoreScenarioTest {
    private final WeightedScoreCalculator calculator = new WeightedScoreCalculator();
    private static final LocalDate DAY = LocalDate.of(2026, 11, 24);
    private static final WeightedScoreCalculator.DistanceBounds BOUNDS =
            WeightedScoreCalculator.bounds(List.of(BigDecimal.valueOf(10), BigDecimal.valueOf(110)));

    private WeightedScoreCalculator.ScoreBreakdown score(int distance, String abc, String xyz,
                                                         Integer daysUntilExpiry, int freeVolume) {
        return calculator.calculate(BigDecimal.valueOf(distance), BOUNDS, abc, xyz,
                daysUntilExpiry == null ? null : DAY.plusDays(daysUntilExpiry),
                DAY, BigDecimal.TEN, BigDecimal.valueOf(freeVolume));
    }

    @Test void axPrefersNearOverFarWithEqualCapacity() {
        var near = score(10, "A", "X", null, 20);
        var far = score(90, "A", "X", null, 20);
        assertTrue(near.total() > far.total());
        assertEquals(1.0, near.classification(), 1e-12);
    }

    @Test void byHasHighestClassificationAffinityNearItsTarget() {
        // BY target = 0.65; distance 45 of [10,110] => D=0.65.
        var aligned = score(45, "B", "Y", null, 20);
        var nearest = score(10, "B", "Y", null, 20);
        assertEquals(1.0, aligned.classification(), 1e-12);
        assertTrue(aligned.classification() > nearest.classification());
    }

    @Test void cxCanFavorFurtherLocationWhenVolumeFitIsBetter() {
        var near = score(10, "C", "X", null, 100); // D=1, U=.1
        var far = score(90, "C", "X", null, 10);   // D=.2, U=1
        assertTrue(far.total() > near.total(), "CX scenario should favor further best-fit location");
    }

    @Test void expiryUrgencyAtThreeFifteenAndSixtyDays() {
        var three = score(10, "A", "X", 3, 20);
        var fifteen = score(10, "A", "X", 15, 20);
        var sixty = score(10, "A", "X", 60, 20);
        assertTrue(three.expiry() > fifteen.expiry());
        assertTrue(fifteen.expiry() > sixty.expiry());
        assertEquals(0.5, sixty.expiry(), 1e-12);
        assertFalse(three.expiryUnknown());
    }

    @Test void missingClassificationAndExpiryAreExplicitlyNeutral() {
        var result = score(45, null, null, null, 20);
        assertEquals(0.5, result.classification(), 1e-12);
        assertEquals(0.5, result.expiry(), 1e-12);
        assertTrue(result.classificationMissing());
        assertTrue(result.expiryUnknown());
    }

    @Test void expiredBatchIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> score(45, "A", "X", -1, 20));
    }
}
