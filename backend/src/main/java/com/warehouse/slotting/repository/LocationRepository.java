package com.warehouse.slotting.repository;

import com.warehouse.slotting.entity.Location;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByWarehouseIdAndIsActiveTrue(Long warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Location e where e.id = :id")
    Optional<Location> findByIdForUpdate(@Param("id") Long id);
}
