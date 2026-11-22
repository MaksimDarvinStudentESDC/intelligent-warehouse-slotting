package com.warehouse.slotting.repository;

import com.warehouse.slotting.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductRepository extends JpaRepository<Product,Long> { boolean existsByWarehouseIdAndSku(Long warehouseId,String sku); java.util.List<Product> findByWarehouseIdAndArchivedFalse(Long warehouseId); }
