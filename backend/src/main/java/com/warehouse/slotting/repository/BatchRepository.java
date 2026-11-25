package com.warehouse.slotting.repository;

import com.warehouse.slotting.entity.Batch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {
    boolean existsByWarehouseIdAndBatchCode(Long warehouseId, String batchCode);
    List<Batch> findByWarehouseId(Long warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Batch e where e.id = :id")
    Optional<Batch> findByIdForUpdate(@Param("id") Long id);
}
