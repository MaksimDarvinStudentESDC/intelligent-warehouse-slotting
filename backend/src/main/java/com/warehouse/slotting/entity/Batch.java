package com.warehouse.slotting.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="batches")
public class Batch {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id") private Warehouse warehouse;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id") private Product product;
 @Column(name="batch_code",nullable=false,length=64) private String batchCode;
 @Column(nullable=false) private Integer quantity;
 @Column(name="volume_dm3",nullable=false,precision=12,scale=3) private BigDecimal volumeDm3;
 @Column(name="weight_kg",nullable=false,precision=12,scale=3) private BigDecimal weightKg;
 @Column(name="receiving_date",nullable=false) private LocalDate receivingDate;
 @Column(name="expiry_date") private LocalDate expiryDate;
 @Convert(converter=BatchStatusConverter.class) @JdbcTypeCode(SqlTypes.VARCHAR)
 @ColumnTransformer(write = "?::batch_status")
 @Column(name="status",nullable=false,columnDefinition="batch_status") private BatchStatus status=BatchStatus.AWAITING_PUTAWAY;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="current_location_id") private Location currentLocation;
 @Column(name="placed_at") private OffsetDateTime placedAt;
 @Column(name="arrival_seq") private Integer arrivalSeq;
 @Column(name="created_by") private Long createdBy;
 @Column(name="created_at",insertable=false,updatable=false) private OffsetDateTime createdAt;
 protected Batch() {}
 public Batch(Product product,String batchCode,int quantity,LocalDate receivingDate,LocalDate expiryDate){
  this.product=product;this.warehouse=product.getWarehouse();this.batchCode=batchCode;this.quantity=quantity;this.receivingDate=receivingDate;this.expiryDate=expiryDate;
  this.volumeDm3=product.getUnitVolumeDm3().multiply(BigDecimal.valueOf(quantity)).setScale(3,java.math.RoundingMode.HALF_UP);
  this.weightKg=product.getUnitWeightKg().multiply(BigDecimal.valueOf(quantity)).setScale(3,java.math.RoundingMode.HALF_UP);
 }
 public Long getId(){return id;}
 public String getBatchCode(){return batchCode;}
 public int getQuantity(){return quantity;}
 public BigDecimal getVolumeDm3(){return volumeDm3;}
 public BigDecimal getWeightKg(){return weightKg;}
 public LocalDate getReceivingDate(){return receivingDate;}
 public LocalDate getExpiryDate(){return expiryDate;}
 public BatchStatus getStatus(){return status;}
 public Product getProduct(){return product;}

 public Warehouse getWarehouse(){return warehouse;}
 public Location getCurrentLocation(){return currentLocation;}
 public OffsetDateTime getPlacedAt(){return placedAt;}
 public void placeAt(Location location, OffsetDateTime timestamp){
  java.util.Objects.requireNonNull(location);
  java.util.Objects.requireNonNull(timestamp);
  if(status != BatchStatus.AWAITING_PUTAWAY || currentLocation != null || placedAt != null)
   throw new IllegalStateException("Batch is already placed");
  if(!java.util.Objects.equals(warehouse.getId(), location.getWarehouse().getId()))
   throw new IllegalArgumentException("Location belongs to another warehouse");
  currentLocation=location;
  placedAt=timestamp;
  status=BatchStatus.PLACED;
 }
}
