package com.warehouse.slotting.repository;

import com.warehouse.slotting.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LocationRepository extends JpaRepository<Location,Long> { java.util.List<Location> findByWarehouseIdAndIsActiveTrue(Long warehouseId); }
