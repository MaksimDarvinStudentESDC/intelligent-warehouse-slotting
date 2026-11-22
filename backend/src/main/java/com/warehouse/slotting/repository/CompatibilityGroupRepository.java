package com.warehouse.slotting.repository;

import com.warehouse.slotting.entity.CompatibilityGroup;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CompatibilityGroupRepository extends JpaRepository<CompatibilityGroup,Long> { java.util.Optional<CompatibilityGroup> findByIdAndWarehouseId(Long id,Long warehouseId); }
