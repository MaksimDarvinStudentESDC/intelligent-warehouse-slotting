package com.warehouse.slotting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "length_mm", nullable = false)
    private Integer lengthMm;

    @Column(name = "width_mm", nullable = false)
    private Integer widthMm;

    @Column(name = "height_mm", nullable = false)
    private Integer heightMm;

    @Column(name = "unit_volume_dm3", nullable = false, precision = 14, scale = 6)
    private BigDecimal unitVolumeDm3;

    @Column(name = "unit_weight_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal unitWeightKg;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compat_group_id")
    private CompatibilityGroup compatibilityGroup;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Convert(converter = OutboundFlowConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnTransformer(write = "?::outbound_flow")
    @Column(name = "outbound_flow", nullable = false, columnDefinition = "outbound_flow")
    private OutboundFlow outboundFlow;

    @Column(name = "is_archived", nullable = false)
    private boolean archived = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Product() {
    }

    public Product(
            Warehouse warehouse,
            String sku,
            String name,
            int lengthMm,
            int widthMm,
            int heightMm,
            BigDecimal unitWeightKg,
            CompatibilityGroup compatibilityGroup,
            Integer shelfLifeDays,
            OutboundFlow outboundFlow
    ) {
        this.warehouse = warehouse;
        this.sku = sku;
        this.name = name;
        this.lengthMm = lengthMm;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.unitWeightKg = unitWeightKg;
        this.compatibilityGroup = compatibilityGroup;
        this.shelfLifeDays = shelfLifeDays;
        this.outboundFlow = outboundFlow;
        this.unitVolumeDm3 = BigDecimal.valueOf(lengthMm)
                .multiply(BigDecimal.valueOf(widthMm))
                .multiply(BigDecimal.valueOf(heightMm))
                .movePointLeft(6)
                .setScale(6);
    }

    public Long getId() {
        return id;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getUnitVolumeDm3() {
        return unitVolumeDm3;
    }

    public BigDecimal getUnitWeightKg() {
        return unitWeightKg;
    }

    public OutboundFlow getOutboundFlow() { return outboundFlow; }

    public boolean isArchived() {
        return archived;
    }

    public Integer getShelfLifeDays() {
        return shelfLifeDays;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }
    public Integer getLengthMm() { return lengthMm; }
    public Integer getWidthMm() { return widthMm; }
    public Integer getHeightMm() { return heightMm; }
    public CompatibilityGroup getCompatibilityGroup() { return compatibilityGroup; }
}