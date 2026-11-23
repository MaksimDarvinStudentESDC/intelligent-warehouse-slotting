package com.warehouse.slotting.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Objects;

/** Pure calculation for weighted_score v1.0. Inputs are already hard-constraint-feasible. */
public final class WeightedScoreCalculator {
    public static final double DISTANCE_WEIGHT = 0.35;
    public static final double CLASS_WEIGHT = 0.25;
    public static final double EXPIRY_WEIGHT = 0.25;
    public static final double UTILIZATION_WEIGHT = 0.15;
    public static final int EXPIRY_HORIZON_DAYS = 30;

    public record ScoreBreakdown(double distance, double classification, double expiry,
                                 double utilization, double distanceContribution,
                                 double classificationContribution, double expiryContribution,
                                 double utilizationContribution, double total,
                                 boolean classificationMissing, boolean expiryUnknown) {}

    /** Bounds must come from ALL feasible locations before applying topN. */
    public record DistanceBounds(BigDecimal min, BigDecimal max) {
        public DistanceBounds {
            Objects.requireNonNull(min, "min distance");
            Objects.requireNonNull(max, "max distance");
            if (min.signum() < 0 || max.compareTo(min) < 0)
                throw new IllegalArgumentException("Invalid distance bounds");
        }
    }

    public static DistanceBounds bounds(Collection<BigDecimal> distances) {
        if (distances == null || distances.isEmpty())
            throw new IllegalArgumentException("No feasible distances");
        BigDecimal min = null, max = null;
        for (BigDecimal d : distances) {
            if (d == null || d.signum() < 0) throw new IllegalArgumentException("Invalid distance");
            if (min == null || d.compareTo(min) < 0) min = d;
            if (max == null || d.compareTo(max) > 0) max = d;
        }
        return new DistanceBounds(min, max);
    }

    public ScoreBreakdown calculate(BigDecimal distanceMeters, DistanceBounds bounds,
                                    String abcClass, String xyzClass, LocalDate expiryDate,
                                    LocalDate evaluationDate, BigDecimal batchVolume,
                                    BigDecimal freeVolume) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(evaluationDate, "evaluationDate");
        if (distanceMeters == null || distanceMeters.compareTo(bounds.min()) < 0 ||
                distanceMeters.compareTo(bounds.max()) > 0)
            throw new IllegalArgumentException("Distance outside feasible bounds");
        if (batchVolume == null || freeVolume == null || batchVolume.signum() <= 0 ||
                freeVolume.signum() <= 0 || batchVolume.compareTo(freeVolume) > 0)
            throw new IllegalArgumentException("Invalid or infeasible volume");

        double d = bounds.max().compareTo(bounds.min()) == 0 ? 1.0 :
                bounds.max().subtract(distanceMeters).doubleValue() /
                bounds.max().subtract(bounds.min()).doubleValue();
        d = clamp(d);

        boolean classificationMissing = abcClass == null || abcClass.isBlank();
        double c;
        if (classificationMissing) {
            c = 0.5;
        } else {
            double target = switch (abcClass.toUpperCase(java.util.Locale.ROOT)) {
                case "A" -> 1.0;
                case "B" -> 0.6;
                case "C" -> 0.2;
                default -> throw new IllegalArgumentException("Invalid ABC class");
            };
            if (xyzClass != null && !xyzClass.isBlank()) {
                target += switch (xyzClass.toUpperCase(java.util.Locale.ROOT)) {
                    case "X" -> 0.0;
                    case "Y" -> 0.05;
                    case "Z" -> 0.10;
                    default -> throw new IllegalArgumentException("Invalid XYZ class");
                };
            }
            c = clamp(1.0 - Math.abs(d - Math.min(1.0, target)));
        }

        boolean expiryUnknown = expiryDate == null;
        double e = 0.5;
        if (!expiryUnknown) {
            long days = ChronoUnit.DAYS.between(evaluationDate, expiryDate);
            if (days < 0) throw new IllegalArgumentException("Expired batch");
            double urgency = clamp(1.0 - days / (double) EXPIRY_HORIZON_DAYS);
            e = urgency * d + (1.0 - urgency) * 0.5;
        }
        double u = clamp(batchVolume.doubleValue() / freeVolume.doubleValue());
        double dc = DISTANCE_WEIGHT * d;
        double cc = CLASS_WEIGHT * c;
        double ec = EXPIRY_WEIGHT * e;
        double uc = UTILIZATION_WEIGHT * u;
        return new ScoreBreakdown(d, c, e, u, dc, cc, ec, uc,
                dc + cc + ec + uc, classificationMissing, expiryUnknown);
    }

    private static double clamp(double x) {
        if (!Double.isFinite(x)) throw new IllegalArgumentException("Non-finite score");
        return Math.max(0.0, Math.min(1.0, x));
    }
}
