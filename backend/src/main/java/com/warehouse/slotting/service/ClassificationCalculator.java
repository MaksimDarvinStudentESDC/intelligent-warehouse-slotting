package com.warehouse.slotting.service;

import java.util.List;

/** Pure calculations for ABC (pick frequency) and XYZ (daily unit demand). */
public class ClassificationCalculator {

    public enum AbcClass { A, B, C }
    public enum XyzClass { X, Y, Z }

    /** Classifies using the cumulative share BEFORE this product is added. */
    public AbcClass calculateAbc(
            long picksBefore,
            long totalPicks,
            double aShare,
            double bShare
    ) {
        if (picksBefore < 0 || totalPicks < 0 || picksBefore > totalPicks) {
            throw new IllegalArgumentException("Invalid picks counts");
        }

        if (!Double.isFinite(aShare)
                || !Double.isFinite(bShare)
                || aShare <= 0
                || bShare <= 0
                || aShare + bShare >= 1.0) {
            throw new IllegalArgumentException("Invalid ABC thresholds");
        }
        if (totalPicks <= 0) {
            return AbcClass.C;
        }

        double shareBefore = (double) picksBefore / totalPicks;

        if (shareBefore < aShare - 1e-10) {
            return AbcClass.A;
        }

        if (shareBefore < aShare + bShare - 1e-10) {
            return AbcClass.B;
        }

        return AbcClass.C;
    }

    /** Population CV; the list must contain one entry for every calendar day, including zero days. */
    public Double calculateCv(List<Integer> dailyDemand) {
        if (dailyDemand != null &&
                dailyDemand.stream().anyMatch(value -> value == null || value < 0)) {
            throw new IllegalArgumentException(
                    "Daily demand cannot contain null or negative values"
            );
        }
        if (dailyDemand == null || dailyDemand.isEmpty()) return null;
        double sum = 0;
        for (Integer value : dailyDemand) {
            if (value == null || value < 0) throw new IllegalArgumentException("Daily demand must be nonnegative");
            sum += value;
        }
        if (sum == 0) return null;
        double mean = sum / dailyDemand.size();
        double squaredDeviation = 0;
        for (int value : dailyDemand) {
            double delta = value - mean;
            squaredDeviation += delta * delta;
        }
        return Math.sqrt(squaredDeviation / dailyDemand.size()) / mean;
    }

    public XyzClass calculateXyz(Double cv, double xMax, double yMax) {
        if (!Double.isFinite(xMax)
                || !Double.isFinite(yMax)
                || xMax < 0
                || yMax <= xMax) {
            throw new IllegalArgumentException("Invalid XYZ thresholds");
        }

        if (cv != null && (!Double.isFinite(cv) || cv < 0)) {
            throw new IllegalArgumentException("Invalid coefficient of variation");
        }
        if (!(xMax > 0 && yMax > xMax)) throw new IllegalArgumentException("Invalid XYZ thresholds");
        if (cv == null) return null;
        if (!Double.isFinite(cv) || cv < 0) throw new IllegalArgumentException("Invalid CV");
        if (cv <= xMax) return XyzClass.X;
        if (cv <= yMax) return XyzClass.Y;
        return XyzClass.Z;
    }
}
