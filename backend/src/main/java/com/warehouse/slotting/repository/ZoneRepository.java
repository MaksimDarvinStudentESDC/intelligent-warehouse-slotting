package com.warehouse.slotting.repository;

import com.warehouse.slotting.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ZoneRepository extends JpaRepository<Zone,Long> { java.util.List<Zone> findByWarehouseId(Long warehouseId); }
