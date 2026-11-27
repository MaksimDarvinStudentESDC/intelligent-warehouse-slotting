package com.warehouse.slotting.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "zones")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Convert(converter = ZoneTypeConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnTransformer(write = "?::zone_type")
    @Column(name = "zone_type", nullable = false,
            columnDefinition = "zone_type")
    private ZoneType zoneType;

    @Convert(converter = AccessTierConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnTransformer(write = "?::access_tier")
    @Column(name = "tier_to_picking",
            columnDefinition = "access_tier")
    private AccessTier tierToPicking;

    @Convert(converter = AccessTierConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnTransformer(write = "?::access_tier")
    @Column(name = "tier_to_shipping",
            columnDefinition = "access_tier")
    private AccessTier tierToShipping;

    protected Zone() {
    }

    public Zone(
            Warehouse warehouse,
            String code,
            String name,
            ZoneType zoneType
    ) {
        this.warehouse = warehouse;
        this.code = code;
        this.name = name;
        this.zoneType = zoneType;
    }

    public Long getId() {
        return id;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public ZoneType getZoneType() {
        return zoneType;
    }

    public AccessTier getTierToPicking() {
        return tierToPicking;
    }

    public AccessTier getTierToShipping() {
        return tierToShipping;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setZoneType(ZoneType zoneType) {
        this.zoneType = zoneType;
    }

    public void setTierToPicking(AccessTier tierToPicking) {
        this.tierToPicking = tierToPicking;
    }

    public void setTierToShipping(AccessTier tierToShipping) {
        this.tierToShipping = tierToShipping;
    }
}