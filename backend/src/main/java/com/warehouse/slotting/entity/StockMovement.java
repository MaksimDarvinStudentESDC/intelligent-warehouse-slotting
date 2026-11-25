package com.warehouse.slotting.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "stock_movements")
public class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id")
    private Location fromLocation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_location_id", nullable = false)
    private Location toLocation;
    @Convert(converter = MovementTypeConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnTransformer(write = "?::movement_type")
    @Column(name = "movement_type", nullable = false, columnDefinition = "movement_type")
    private MovementType movementType;
    @Column(name = "recommendation_item_id")
    private Long recommendationItemId;
    @Column(name = "is_override", nullable = false)
    private boolean override;
    @Column(name = "override_reason", columnDefinition = "text")
    private String overrideReason;
    @Column(name = "performed_by")
    private Long performedBy;
    @Column(name = "performed_at", nullable = false)
    private OffsetDateTime performedAt;

    protected StockMovement() {}

    public static StockMovement putaway(Batch batch, Location location,
                                        Long performedBy, OffsetDateTime performedAt) {
        Objects.requireNonNull(batch);
        Objects.requireNonNull(location);
        Objects.requireNonNull(performedBy);
        Objects.requireNonNull(performedAt);
        if (batch.getStatus() != BatchStatus.PLACED || batch.getCurrentLocation() == null
                || !Objects.equals(batch.getCurrentLocation().getId(), location.getId())
                || !Objects.equals(batch.getWarehouse().getId(), location.getWarehouse().getId())
                || !performedAt.equals(batch.getPlacedAt())) {
            throw new IllegalStateException("Batch must be placed at the target location first");
        }
        StockMovement movement = new StockMovement();
        movement.warehouse = batch.getWarehouse();
        movement.batch = batch;
        movement.toLocation = location;
        movement.movementType = MovementType.PUTAWAY;
        movement.performedBy = performedBy;
        movement.performedAt = performedAt;
        return movement;
    }

    public Long getId() { return id; }
    public Warehouse getWarehouse() { return warehouse; }
    public Batch getBatch() { return batch; }
    public Location getFromLocation() { return fromLocation; }
    public Location getToLocation() { return toLocation; }
    public MovementType getMovementType() { return movementType; }
    public Long getRecommendationItemId() { return recommendationItemId; }
    public boolean isOverride() { return override; }
    public String getOverrideReason() { return overrideReason; }
    public Long getPerformedBy() { return performedBy; }
    public OffsetDateTime getPerformedAt() { return performedAt; }
}
