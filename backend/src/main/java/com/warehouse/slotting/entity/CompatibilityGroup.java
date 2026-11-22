package com.warehouse.slotting.entity;

import jakarta.persistence.*;
@Entity @Table(name="compat_groups")
public class CompatibilityGroup {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="warehouse_id") private Warehouse warehouse;
 @Column(nullable=false,length=32) private String code;
 @Column(nullable=false,length=128) private String name;
 protected CompatibilityGroup() {}
 public Long getId(){return id;}
 public Warehouse getWarehouse(){return warehouse;}
 public String getCode(){return code;}
 public String getName(){return name;}
}
