package com.warehouse.slotting.controller;

import com.warehouse.slotting.dto.LocationResponse;
import com.warehouse.slotting.repository.LocationRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@RestController @RequestMapping("/api/warehouses/{warehouseId}/locations")
public class LocationController {
 private final LocationRepository repository;
 public LocationController(LocationRepository repository){this.repository=repository;}
 @GetMapping @Transactional(readOnly=true)
 public List<LocationResponse> list(@PathVariable Long warehouseId){return repository.findByWarehouseIdAndIsActiveTrue(warehouseId).stream().map(LocationResponse::from).toList();}
}
