package com.warehouse.slotting.service;

import com.warehouse.slotting.exception.UseCaseException;
import com.warehouse.slotting.repository.*;
import com.warehouse.slotting.entity.*;
import com.warehouse.slotting.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class CatalogService {
 private final WarehouseRepository warehouses;private final CompatibilityGroupRepository groups;private final ProductRepository products;private final BatchRepository batches;
 public CatalogService(WarehouseRepository warehouses,CompatibilityGroupRepository groups,ProductRepository products,BatchRepository batches){this.warehouses=warehouses;this.groups=groups;this.products=products;this.batches=batches;}
 @Transactional public ProductResponse createProduct(CreateProductRequest q){
  Warehouse w=warehouses.findById(q.warehouseId()).orElseThrow(()->new UseCaseException(UseCaseException.Kind.NOT_FOUND,"Warehouse not found"));
  if(products.existsByWarehouseIdAndSku(q.warehouseId(),q.sku()))throw new UseCaseException(UseCaseException.Kind.CONFLICT,"SKU already exists in warehouse");
  CompatibilityGroup group=q.compatibilityGroupId()==null?null:groups.findByIdAndWarehouseId(q.compatibilityGroupId(),q.warehouseId()).orElseThrow(()->new UseCaseException(UseCaseException.Kind.INVALID_INPUT,"Compatibility group not found in warehouse"));
  return ProductResponse.from(products.saveAndFlush(new Product(w,q.sku(),q.name(),q.lengthMm(),q.widthMm(),q.heightMm(),q.unitWeightKg(),group,q.shelfLifeDays(),q.outboundFlow())));
 }
 @Transactional public BatchResponse createBatch(CreateBatchRequest q){
  Product p=products.findById(q.productId()).orElseThrow(()->new UseCaseException(UseCaseException.Kind.NOT_FOUND,"Product not found"));
  if(p.isArchived())throw new UseCaseException(UseCaseException.Kind.INVALID_INPUT,"Archived product cannot receive batches");
  if(q.expiryDate()!=null && !q.expiryDate().isAfter(q.receivingDate()))throw new UseCaseException(UseCaseException.Kind.INVALID_INPUT,"Expiry date must be after receiving date");
  if(batches.existsByWarehouseIdAndBatchCode(p.getWarehouse().getId(),q.batchCode()))throw new UseCaseException(UseCaseException.Kind.CONFLICT,"Batch code already exists in warehouse");
  return BatchResponse.from(batches.saveAndFlush(new Batch(p,q.batchCode(),q.quantity(),q.receivingDate(),q.expiryDate())));
 }
 @Transactional(readOnly=true) public java.util.List<BatchResponse> batches(Long warehouseId){return batches.findByWarehouseId(warehouseId).stream().map(BatchResponse::from).toList();}
}
