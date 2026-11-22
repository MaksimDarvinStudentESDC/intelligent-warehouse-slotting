package com.warehouse.slotting.repository;

import com.warehouse.slotting.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BatchRepository extends JpaRepository<Batch,Long> { boolean existsByWarehouseIdAndBatchCode(Long warehouseId,String batchCode); java.util.List<Batch> findByWarehouseId(Long warehouseId); }
