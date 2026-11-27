package com.warehouse.slotting.service;

import com.warehouse.slotting.entity.AccessTier;
import com.warehouse.slotting.entity.Location;
import com.warehouse.slotting.entity.OutboundFlow;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class PickFrequencyZoneResolver {

    private PickFrequencyZoneResolver() {
    }

    public static Optional<AccessTier> resolveTargetTier(
            String abcClass
    ) {
        if (abcClass == null || abcClass.isBlank()) {
            return Optional.empty();
        }

        return switch (abcClass.trim().toUpperCase(Locale.ROOT)) {
            case "A" -> Optional.of(AccessTier.FAST);
            case "B" -> Optional.of(AccessTier.MIDDLE);
            case "C" -> Optional.of(AccessTier.REMAINING);
            default -> throw new IllegalArgumentException(
                    "Invalid ABC class: " + abcClass
            );
        };
    }

    public static AccessTier resolveLocationTier(
            Location location,
            OutboundFlow flow
    ) {
        Objects.requireNonNull(location, "Location is required");
        Objects.requireNonNull(flow, "Outbound flow is required");

        var zone = Objects.requireNonNull(
                location.getZone(),
                "Location zone is required"
        );

        AccessTier tier = switch (flow) {
            case PICKING -> zone.getTierToPicking();
            case SHIPPING -> zone.getTierToShipping();
        };

        return Objects.requireNonNull(
                tier,
                "Storage zone access tier is required"
        );
    }

    public static boolean matchesTargetTier(
            Location location,
            OutboundFlow flow,
            AccessTier targetTier
    ) {
        Objects.requireNonNull(
                targetTier,
                "Target tier is required"
        );

        return resolveLocationTier(location, flow) == targetTier;
    }
}