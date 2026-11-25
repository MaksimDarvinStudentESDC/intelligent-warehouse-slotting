
package com.warehouse.slotting.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;

@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Convert(converter = ZoneTypeConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnTransformer(write = "?::zone_type")
    @Column(
            name = "zone_type",
            nullable = false,
            columnDefinition = "zone_type"
    )
    private ZoneType zoneType;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "volume_capacity_dm3",
            nullable = false, precision = 12, scale = 3)
    private BigDecimal volumeCapacityDm3;

    @Column(name = "max_weight_kg",
            nullable = false, precision = 12, scale = 3)
    private BigDecimal maxWeightKg;

    @Column(name = "max_length_mm", nullable = false)
    private Integer maxLengthMm;

    @Column(name = "max_width_mm", nullable = false)
    private Integer maxWidthMm;

    @Column(name = "max_height_mm", nullable = false)
    private Integer maxHeightMm;

    @Column(name = "distance_to_receiving_m",
            nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceToReceivingM;

    @Column(name = "distance_to_picking_m",
            nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceToPickingM;

    @Column(name = "distance_to_shipping_m",
            nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceToShippingM;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "occupied_volume_dm3",
            nullable = false, precision = 12, scale = 3)
    private BigDecimal occupiedVolumeDm3 = BigDecimal.ZERO;

    @Column(name = "occupied_weight_kg",
            nullable = false, precision = 12, scale = 3)
    private BigDecimal occupiedWeightKg = BigDecimal.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Location() { }
    public Long getId() { return id; }
    public Warehouse getWarehouse() { return warehouse; }
    public Zone getZone() { return zone; }
    public String getCode() { return code; }
    public ZoneType getZoneType() { return zoneType; }
    public BigDecimal getVolumeCapacityDm3() { return volumeCapacityDm3; }
    public BigDecimal getMaxWeightKg() { return maxWeightKg; }
    public Integer getMaxLengthMm() { return maxLengthMm; }
    public Integer getMaxWidthMm() { return maxWidthMm; }
    public Integer getMaxHeightMm() { return maxHeightMm; }
    public BigDecimal getDistanceToReceivingM() { return distanceToReceivingM; }
    public BigDecimal getDistanceToPickingM() { return distanceToPickingM; }
    public BigDecimal getDistanceToShippingM() { return distanceToShippingM; }
    public Boolean getIsActive() { return isActive; }
    public BigDecimal getOccupiedVolumeDm3() { return occupiedVolumeDm3; }
    public BigDecimal getOccupiedWeightKg() { return occupiedWeightKg; }
    public Long getVersion() { return version; }

    public void occupy(BigDecimal volume, BigDecimal weight) {
        if (volume == null || weight == null || volume.signum() < 0 || weight.signum() < 0) {
            throw new IllegalArgumentException("Volume and weight must be non-negative");
        }
        if (!Boolean.TRUE.equals(isActive)) {
            throw new IllegalStateException("Location is inactive");
        }
        BigDecimal nextVolume = occupiedVolumeDm3.add(volume);
        BigDecimal nextWeight = occupiedWeightKg.add(weight);
        if (nextVolume.compareTo(volumeCapacityDm3) > 0 || nextWeight.compareTo(maxWeightKg) > 0) {
            throw new IllegalStateException("Location capacity exceeded");
        }
        occupiedVolumeDm3 = nextVolume;
        occupiedWeightKg = nextWeight;
    }
}
