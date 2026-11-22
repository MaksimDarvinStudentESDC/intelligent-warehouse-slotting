package com.warehouse.slotting.controller;

import com.warehouse.slotting.dto.*;
import com.warehouse.slotting.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api")
public class CatalogController {
 private final CatalogService service;
 public CatalogController(CatalogService service){this.service=service;}
 @PostMapping("/products") public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest req){return ResponseEntity.status(HttpStatus.CREATED).body(service.createProduct(req));}
 @PostMapping("/batches") public ResponseEntity<BatchResponse> createBatch(@Valid @RequestBody CreateBatchRequest req){return ResponseEntity.status(HttpStatus.CREATED).body(service.createBatch(req));}
 @GetMapping("/warehouses/{warehouseId}/batches") public List<BatchResponse> batches(@PathVariable Long warehouseId){return service.batches(warehouseId);}
}
